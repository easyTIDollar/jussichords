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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Call
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
        /** 脚本 sendInited 后完成；首访解析前要等它一次，否则页面上还没注册 handler。 */
        val inited: CompletableDeferred<VerifyResult> = CompletableDeferred(),
        val pending: ConcurrentHashMap<Int, CompletableDeferred<String?>> = ConcurrentHashMap()
    ) {
        val httpCalls = mutableSetOf<Call>()
        val httpSlots = java.util.concurrent.Semaphore(4)
        var closed = false
        /** 沙盒创建时刻：预热与解析的等待预算从这一刻开始算，不重复等。 */
        val createdAt: Long = SystemClock.elapsedRealtime()

        /** 最近一次解析的错误原因，用于判定沙盒是否结构性坏掉（脚本根本没跑起来）。 */
        @Volatile
        var lastError: String? = null
    }

    // 结构性坏掉的插件在冷却期内不再重建沙盒，避免每首歌都白等一次页面加载。
    private val failedAt = ConcurrentHashMap<String, Long>()

    /** 每个插件的沙盒创建锁：预热与解析同时到达时只建一个 WebView。 */
    private val createLocks = ConcurrentHashMap<String, Mutex>()

    // 沙盒出网用的独立客户端：不能带 NCM cookie / 加密 / 代理 base url（同 LocalSourceFetcher 原则）。
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    data class VerifyResult(
        val sources: List<String> = emptyList(),
        val wyQualities: List<String> = emptyList()
    )

    /** 脚本体检：起一个临时无头沙盒跑一遍，等 inited 事件拿支持平台与网易云音质。 */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun verify(item: LxPluginItem): VerifyResult = withContext(Dispatchers.Main) {
        val inited = CompletableDeferred<VerifyResult>()
        val sandbox = createSandbox(item.id, item, inited) ?: return@withContext VerifyResult()
        // 体检期间也要能出网（部分脚本在 inited 前就发请求），先挂进池子再执行。
        active[item.id] = sandbox
        try {
            withTimeoutOrNull(INIT_TIMEOUT_MS) { inited.await() } ?: VerifyResult()
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
        var sandbox = active[pluginId] ?: newSandbox(pluginId) ?: return null
        // 首访（或预热刚建完）时脚本往往还没跑完 sendInited：这时候注入解析调用必然
        // 报「未注册」或直接吃满超时，于是第一次点歌白白落到官方 30 秒试听。
        // 用户手动点一下「重新解析」就好，正是因为那时沙盒已经热了——这里先等一次就绪。
        awaitInited(sandbox)

        var url = invokeResolve(sandbox, musicInfoJson, quality)
        if (url.isNullOrBlank() && sandbox.lastError?.contains(HANDLER_MISSING) == true) {
            // 结构性坏沙盒（页面没就绪就注入过脚本）再给一次重建机会，而不是直接冷却
            // 整条音源：一次冷启动失败不该让之后 5 分钟里所有歌都跳过它。
            android.util.Log.w(LOG_TAG, "[$pluginId] 沙盒未注册请求处理器，重建后重试一次")
            dropSandbox(sandbox, cooldown = false)
            sandbox = newSandbox(pluginId) ?: return null
            awaitInited(sandbox, SANDBOX_READY_WAIT_MS)
            url = invokeResolve(sandbox, musicInfoJson, quality)
            if (url.isNullOrBlank()) dropSandbox(sandbox, cooldown = true)
        }
        return url
    }

    /** 新建沙盒并挂进池子；取不到脚本正文返回 null。 */
    private suspend fun newSandbox(pluginId: String): Sandbox? =
        createLocks.computeIfAbsent(pluginId) { Mutex() }.withLock {
            // 预热与解析可能同时给同一个插件建沙盒：不加锁会建出两个 WebView，
            // 输了的那份没人引用、也永远销毁不掉（泄漏）。
            active[pluginId]?.let { return it }
            val item = itemProvider(pluginId) ?: run {
                android.util.Log.w(LOG_TAG, "[$pluginId] 取不到脚本正文，跳过")
                return null
            }
            val created = createSandbox(pluginId, item) ?: return null
            active[pluginId] = created
            created
        }

    /**
     * 等脚本 sendInited（有预算）。超时也继续注入：有的脚本 inited 前就在发网络请求，
     * 硬等会把起播拖长；拿不到就让它自己报错，至少日志里能看到真实原因。
     */
    private suspend fun awaitInited(sandbox: Sandbox, maxWaitMs: Long = FIRST_READY_WAIT_MS) {
        if (sandbox.inited.isCompleted) return
        // 预热（warmUp）可能比真正解析早几秒开始：预算从「沙盒创建」算起，已经等过的
        // 时间不再重复付。首次点歌最常见的失败就是这里等 2.5s 就放弃 → 注入必然报
        // 「未注册请求处理器」→ 重建 → 再等一次，十几秒后仍旧回落 30 秒试听；
        // 而脚本往往再过一两秒就 inited 了（用户手动点「重新解析」正是踩到那一刻）。
        val elapsed = SystemClock.elapsedRealtime() - sandbox.createdAt
        val budget = (maxWaitMs - elapsed).coerceAtLeast(MIN_READY_WAIT_MS)
        val ok = withTimeoutOrNull(budget) { sandbox.inited.await() }
        if (ok == null) {
            android.util.Log.w(
                LOG_TAG,
                "[${sandbox.pluginId}] 等待脚本就绪超时（预算 ${budget}ms / 上限 ${maxWaitMs}ms），继续尝试解析"
            )
        }
    }

    /** 注入一次解析调用并等结果。 */
    private suspend fun invokeResolve(
        sandbox: Sandbox,
        musicInfoJson: String,
        quality: String
    ): String? {
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
        val pluginId = sandbox.pluginId
        if (url.isNullOrBlank()) {
            android.util.Log.w(
                LOG_TAG,
                "[$pluginId] 未命中 cost=${cost}ms error=${sandbox.lastError ?: "无响应/超时"}"
            )
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
        newSandbox(pluginId)
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
        inited: CompletableDeferred<VerifyResult> = CompletableDeferred()
    ): Sandbox? = withContext(Dispatchers.Main) {
        val ready = CompletableDeferred<Unit>()
        lateinit var sandbox: Sandbox
        val bridge = SandboxBridge(
            onHttp = { id, url, method, headersJson, body ->
                handleHttpRequest(pluginId, id, url, method, headersJson, body)
            },
            onInited = { res -> inited.complete(res) },
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

        sandbox = Sandbox(pluginId, wv, item.sources, inited)
        sandbox
    }

    private fun destroySandbox(sandbox: Sandbox) {
        synchronized(sandbox.httpCalls) {
            sandbox.closed = true
            sandbox.httpCalls.forEach { it.cancel() }
            sandbox.httpCalls.clear()
        }
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
        val sandbox = active[pluginId] ?: return
        if (!sandbox.httpSlots.tryAcquire()) {
            scope.launch {
                if (active[pluginId] === sandbox) {
                    deliver(pluginId, "window.__lx_handle_http_response($id,\"请求过多\",null,null)")
                }
            }
            return
        }
        scope.launch(Dispatchers.IO) {
            val script = "window.__lx_handle_http_response($id,"
            var call: Call? = null
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
                val requestCall = httpClient.newCall(builder.build())
                call = requestCall
                synchronized(sandbox.httpCalls) {
                    if (sandbox.closed) return@launch
                    sandbox.httpCalls.add(requestCall)
                }
                requestCall.execute().use { resp ->
                    val respJson = """{"statusCode":${resp.code}}"""
                    check(resp.body?.source()?.request(2L * 1024 * 1024 + 1) != true) {
                        "响应超过 2 MiB"
                    }
                    val text = resp.body?.string().orEmpty()
                    if (active[pluginId] !== sandbox) return@launch
                    deliver(pluginId, script + "null," + org.json.JSONObject.quote(respJson) + "," +
                        org.json.JSONObject.quote(text) + ")")
                }
            } catch (e: Exception) {
                if (active[pluginId] !== sandbox) return@launch
                deliver(
                    pluginId,
                    script + org.json.JSONObject.quote(e.message ?: "网络错误") + ",null,null)"
                )
            } finally {
                synchronized(sandbox.httpCalls) { sandbox.httpCalls.remove(call) }
                sandbox.httpSlots.release()
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
        private val onInited: (VerifyResult) -> Unit,
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
            val wyQualities = mutableListOf<String>()
            runCatching {
                val root = json.parseToJsonElement(dataJson).jsonObject
                val sourcesObj = root["sources"]?.jsonObject
                sourcesObj?.keys?.forEach { sources.add(it) }
                sourcesObj?.get("wy")?.jsonObject?.get("qualitys")?.jsonArray?.forEach { el ->
                    el.jsonPrimitive.contentOrNull?.let { wyQualities.add(it) }
                }
            }
            onInited(VerifyResult(sources, wyQualities))
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
        const val RESOLVE_TIMEOUT_MS = 5000L
        const val PAGE_READY_TIMEOUT_MS = 4000L

        /**
         * 首访解析前等脚本 sendInited 的上限（从沙盒创建时刻算，预热的时间计入）。
         * 2.5s 只够「页面加载完」，社区脚本还要跑一段初始化 + 拿音源列表才 sendInited；
         * 等不够就注入 → 未注册 handler → 重建再等，十几秒后照样回落 30 秒试听。
         * 首次多等这几秒，换来的是「第一次点歌就是完整源」。
         */
        private const val FIRST_READY_WAIT_MS = 6_000L

        /** 重建后的等待预算：这时已经确认沙盒结构性坏了，别为它再付一次完整等待。 */
        private const val SANDBOX_READY_WAIT_MS = 2_500L

        /** 即使已经等过很久，也给一次最小等待，避免 0ms 空转直接注入。 */
        private const val MIN_READY_WAIT_MS = 800L

        /** 脚本未跑起来（页面未就绪就注入）时 shim 回的错误关键字。 */
        private const val HANDLER_MISSING = "未注册"

        /**
         * 结构性坏掉的插件冷却时长，避免每首歌都重建一次沙盒。
         * 45s 而不是 5 分钟：一次冷启动失败惩罚整条音源五分钟，比失败本身更伤。
         */
        private const val ACTIVATE_FAILURE_COOLDOWN_MS = 45_000L
    }
}
