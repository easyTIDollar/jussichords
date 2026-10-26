package com.jussicodes.music.lx

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.rcmiku.ncmapi.utils.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * LX Music JS 脚本沙盒引擎（移植 Melodia LxPluginEngine，MIT；仅保留 Android/WebView 一侧）。
 *
 * 一插件一沙盒：WebView 之间彼此隔离，插件之间看不到对方的全局变量。
 * 沙盒按需创建（首次解析时才建）、禁用/删除即销毁，避免常驻内存。
 */
class LxPluginEngine(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val active = ConcurrentHashMap<String, Sandbox>()
    private val nextCallId = AtomicInteger(0)

    private class Sandbox(
        val pluginId: String,
        val webView: WebView,
        val supportedSources: List<String>,
        val pending: ConcurrentHashMap<Int, CompletableDeferred<String?>> = ConcurrentHashMap()
    ) {
        /** 最近一次解析的错误原因，用于判定沙盒是否结构性坏掉（脚本根本没跑起来）。 */
        @Volatile
        var lastError: String? = null
    }

    // 结构性坏掉的插件在冷却期内不再重建沙盒，避免每首歌都白等一次页面加载。
    private val failedAt = ConcurrentHashMap<String, Long>()

    // 沙盒出网用的独立客户端：不能带 NCM cookie / 加密 / 代理 base url（同 LocalSourceFetcher 原则）。
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** 脚本体检：起一个临时无头沙盒跑一遍，等 inited 事件拿支持平台。 */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun verify(item: LxPluginItem): List<String> = withContext(Dispatchers.Main) {
        val inited = CompletableDeferred<List<String>>()
        val sandbox = createSandbox(item.id, item, inited) ?: return@withContext emptyList()
        // 体检期间也要能出网（部分脚本在 inited 前就发请求），先挂进池子再执行。
        active[item.id] = sandbox
        try {
            val sources = withTimeoutOrNull(INIT_TIMEOUT_MS) { inited.await() }.orEmpty()
            if (sources.isEmpty()) {
                // 没触发 inited 的脚本未必非法（部分脚本懒注册），仍放行，交由实际解析验证。
                emptyList()
            } else {
                sources
            }
        } finally {
            active.remove(item.id, sandbox)
            destroySandbox(sandbox)
        }
    }

    /**
     * 按 id 取直链：依次尝试该插件支持的平台，脚本未命中返回 null。
     * @param musicInfoJson 已是 LX 标准的 musicInfo JSON。
     */
    suspend fun resolveUrl(pluginId: String, musicInfoJson: String, quality: String): String? {
        if (isCoolingDown(pluginId)) {
            android.util.Log.w(LOG_TAG, "[$pluginId] 沙盒冷却中（脚本未就绪），跳过本次解析")
            return null
        }
        val sandbox = active[pluginId] ?: run {
            val item = itemProvider(pluginId) ?: run {
                android.util.Log.w(LOG_TAG, "[$pluginId] 取不到脚本正文，跳过")
                return null
            }
            val created = createSandbox(pluginId, item, CompletableDeferred()) ?: return null
            active[pluginId] = created
            created
        }

        val t0 = SystemClock.elapsedRealtime()
        val callId = nextCallId.incrementAndGet()
        val deferred = CompletableDeferred<String?>()
        sandbox.pending[callId] = deferred
        sandbox.lastError = null
        withContext(Dispatchers.Main) {
            val js = "window.__lx_resolve_music_url($callId, 'wy', " +
                org.json.JSONObject.quote(musicInfoJson) + ", " +
                org.json.JSONObject.quote(quality) + ")"
            sandbox.webView.evaluateJavascript(js, null)
        }
        val url = try {
            withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { deferred.await() }
        } catch (e: Exception) {
            null
        } finally {
            sandbox.pending.remove(callId)
        }

        val cost = SystemClock.elapsedRealtime() - t0
        if (url.isNullOrBlank()) {
            android.util.Log.w(
                LOG_TAG,
                "[$pluginId] 未命中 cost=${cost}ms error=${sandbox.lastError ?: "无响应/超时"}"
            )
            // 脚本首行要求 window.lx 已存在；页面没就绪就注入会让脚本直接抛错，
            // 沙盒从此永久「未注册 request 处理器」。这种沙盒必须丢掉，等下次重建。
            if (sandbox.lastError?.contains(HANDLER_MISSING) == true) {
                dropSandbox(sandbox, cooldown = true)
            }
        } else {
            android.util.Log.d(LOG_TAG, "[$pluginId] 命中 cost=${cost}ms")
        }
        return url
    }

    private fun isCoolingDown(pluginId: String): Boolean {
        val at = failedAt[pluginId] ?: return false
        if (SystemClock.elapsedRealtime() - at < ACTIVATE_FAILURE_COOLDOWN_MS) return true
        failedAt.remove(pluginId)
        return false
    }

    /** 丢掉一个沙盒；结构性坏掉的顺带记冷却，避免每首歌都重建一次。 */
    private fun dropSandbox(sandbox: Sandbox, cooldown: Boolean) {
        if (!active.remove(sandbox.pluginId, sandbox)) return
        if (cooldown) failedAt[sandbox.pluginId] = SystemClock.elapsedRealtime()
        android.util.Log.w(
            LOG_TAG,
            "[${sandbox.pluginId}] 沙盒已丢弃 cooldown=$cooldown reason=${sandbox.lastError}"
        )
        scope.launch(Dispatchers.Main) { destroySandbox(sandbox) }
    }

    /** 预建沙盒（只建不解析），避免首播时多等一次脚本初始化。 */
    suspend fun warmUp(pluginId: String) {
        if (active.containsKey(pluginId)) return
        val item = itemProvider(pluginId) ?: return
        active[pluginId] = createSandbox(pluginId, item, CompletableDeferred()) ?: return
    }

    fun deactivate(pluginId: String) {
        failedAt.remove(pluginId)
        active.remove(pluginId)?.let { s ->
            scope.launch(Dispatchers.Main) { destroySandbox(s) }
        }
    }

    fun reset() {
        failedAt.clear()
        val all = active.values.toList()
        active.clear()
        scope.launch(Dispatchers.Main) { all.forEach { destroySandbox(it) } }
    }

    // item 提供方由外部注入（沙盒懒创建时才知道脚本正文）
    @Volatile
    var itemProvider: (String) -> LxPluginItem? = { null }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun createSandbox(
        pluginId: String,
        item: LxPluginItem,
        inited: CompletableDeferred<List<String>>
    ): Sandbox? = withContext(Dispatchers.Main) {
        val ready = CompletableDeferred<Unit>()
        lateinit var sandbox: Sandbox
        val bridge = SandboxBridge(
            onHttp = { id, url, method, headersJson, body ->
                handleHttpRequest(pluginId, id, url, method, headersJson, body)
            },
            onInited = { sources -> inited.complete(sources) },
            onResolved = { callId, url, error ->
                if (!error.isNullOrBlank()) {
                    sandbox.lastError = error
                    android.util.Log.w(LOG_TAG, "[$pluginId] 解析返回错误: $error")
                }
                sandbox.pending[callId]?.complete(url?.takeIf { it.isNotBlank() })
            },
            onTimer = { id -> scope.launch { fireTimer(pluginId, id) } }
        )
        val wv = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    ready.complete(Unit)
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(m: ConsoleMessage?): Boolean {
                    m?.let {
                        android.util.Log.d("LxPlugin", "[$pluginId] JS: ${it.message()}")
                    }
                    return true
                }
            }
            addJavascriptInterface(bridge, "LxHostBridge")
        }
        wv.loadDataWithBaseURL("https://jussichords.local", LxShim.html(), "text/html", "UTF-8", null)
        if (withTimeoutOrNull(PAGE_READY_TIMEOUT_MS) { ready.await() } == null) {
            // 页面没就绪就注入脚本，脚本首行 `if (!globalThis.lx) throw` 会直接抛错，
            // 沙盒从此变成「永远未注册 request 处理器」的僵尸：每次解析秒失败并回落 30 秒试听。
            android.util.Log.w(LOG_TAG, "[$pluginId] 沙盒页面未就绪，放弃本次创建")
            destroySandbox(Sandbox(pluginId, wv, item.sources))
            return@withContext null
        }

        // 注入脚本元信息后执行脚本本体
        wv.evaluateJavascript(
            "window.lx.currentScriptInfo = { name: ${org.json.JSONObject.quote(item.name)}, " +
                "description: ${org.json.JSONObject.quote(item.description)}, " +
                "version: ${org.json.JSONObject.quote(item.version)}, " +
                "author: ${org.json.JSONObject.quote(item.author)} };",
            null
        )
        wv.evaluateJavascript(item.rawScript, null)

        sandbox = Sandbox(pluginId, wv, item.sources)
        sandbox
    }

    private fun destroySandbox(sandbox: Sandbox) {
        try {
            sandbox.webView.stopLoading()
            sandbox.webView.removeJavascriptInterface("LxHostBridge")
            sandbox.webView.loadUrl("about:blank")
            sandbox.webView.destroy()
        } catch (e: Exception) {
            android.util.Log.w("LxPlugin", "销毁沙盒异常", e)
        }
    }

    /** 宿主代发脚本的网络请求；结果注回对应沙盒。 */
    private fun handleHttpRequest(
        pluginId: String,
        id: Int,
        url: String,
        method: String,
        headersJson: String,
        body: String
    ) {
        scope.launch(Dispatchers.IO) {
            val script = "window.__lx_handle_http_response($id,"
            if (!active.containsKey(pluginId)) return@launch
            try {
                val headers = mutableMapOf<String, String>()
                if (headersJson.isNotBlank()) {
                    runCatching {
                        json.parseToJsonElement(headersJson).jsonObject.forEach { (k, v) ->
                            headers[k] = v.jsonPrimitive.content
                        }
                    }
                }
                val builder = Request.Builder().url(url)
                headers.forEach { (k, v) -> builder.header(k, v) }
                if (!method.equals("GET", true) && body.isNotBlank()) {
                    builder.method(
                        method.uppercase(),
                        body.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                    )
                } else {
                    builder.get()
                }
                httpClient.newCall(builder.build()).execute().use { resp ->
                    val respJson = """{"statusCode":${resp.code}}"""
                    val text = resp.body?.string().orEmpty()
                    deliver(pluginId, script + "null," + org.json.JSONObject.quote(respJson) + "," +
                        org.json.JSONObject.quote(text) + ")")
                }
            } catch (e: Exception) {
                deliver(
                    pluginId,
                    script + org.json.JSONObject.quote(e.message ?: "网络错误") + ",null,null)"
                )
            }
        }
    }

    private fun fireTimer(pluginId: String, timerId: Int) {
        scope.launch(Dispatchers.Main) {
            active[pluginId]?.webView?.evaluateJavascript("window.__lx_fire_timer($timerId)", null)
        }
    }

    private suspend fun deliver(pluginId: String, js: String) = withContext(Dispatchers.Main) {
        active[pluginId]?.webView?.evaluateJavascript(js, null)
        Unit
    }

    private class SandboxBridge(
        private val onHttp: (Int, String, String, String, String) -> Unit,
        private val onInited: (List<String>) -> Unit,
        private val onResolved: (Int, String?, String?) -> Unit,
        private val onTimer: (Int) -> Unit
    ) {
        @JavascriptInterface
        fun httpRequest(id: Int, url: String, method: String, headersJson: String, body: String) =
            onHttp(id, url, method, headersJson, body)

        @JavascriptInterface
        fun onEvent(event: String, dataJson: String) {
            if (event != "inited") return
            val sources = mutableListOf<String>()
            runCatching {
                json.parseToJsonElement(dataJson).jsonObject["sources"]?.jsonObject?.keys?.forEach {
                    sources.add(it)
                }
            }
            onInited(sources)
        }

        @JavascriptInterface
        fun onResolveResult(callId: Int, url: String?, error: String?) {
            if (!error.isNullOrBlank()) {
                android.util.Log.w("LxPlugin", "解析失败: $error")
            }
            onResolved(callId, url, error)
        }

        @JavascriptInterface
        fun md5(input: String): String = runCatching {
            MessageDigest.getInstance("MD5").digest(input.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }.getOrDefault("")

        @JavascriptInterface
        fun setTimer(id: Int, ms: Int) = onTimer(id)
    }

    companion object {
        private const val LOG_TAG = "LxPlugin"
        const val INIT_TIMEOUT_MS = 8000L
        const val RESOLVE_TIMEOUT_MS = 12000L
        const val PAGE_READY_TIMEOUT_MS = 4000L

        /** 脚本未跑起来（页面未就绪就注入）时 shim 回的错误关键字。 */
        private const val HANDLER_MISSING = "未注册"

        /** 结构性坏掉的插件冷却时长，避免每首歌都重建一次沙盒。 */
        private const val ACTIVATE_FAILURE_COOLDOWN_MS = 5 * 60 * 1000L
    }
}