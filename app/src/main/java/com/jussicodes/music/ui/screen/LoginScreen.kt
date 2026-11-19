package com.jussicodes.music.ui.screen

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.jussicodes.music.R
import com.jussicodes.music.constants.ncmCookieKey
import com.jussicodes.music.data.AccountAddFlow
import com.jussicodes.music.data.AccountStore
import com.jussicodes.music.data.SavedAccount
import com.jussicodes.music.ui.icons.Login as LoginIcon
import com.jussicodes.music.ui.navigation.Screen
import com.jussicodes.music.utils.NcmCookieNormalizer
import com.jussicodes.music.utils.dataStore
import com.jussicodes.music.utils.rememberPreference
import com.rcmiku.ncmapi.api.account.AccountApi
import com.rcmiku.ncmapi.utils.CookieProvider
import com.rcmiku.ncmapi.utils.json
import com.rcmiku.ncmapi.utils.parseCookieString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import com.jussicodes.music.ui.components.ResponsiveAlertDialog

// 登录页 UA：去掉 WebView 默认 UA 里的 "; wv" 与 "Version/4.0" 标记。
// 网易云登录页按 UA 判断客户端类型，WebView 原生 UA 容易被降级成残缺页面
// （第三方登录按钮点了直接跳 wtloginmqq:// 这类自定义协议）。
private const val LOGIN_WEB_UA =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7 Pro) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36"

// 桌面 UA 已不再使用：QQ 登录整条链路放弃维护（见下方 EXTERNAL_LOGIN_SCHEMES 注释）。

private const val MOBILE_LOGIN_URL = "https://music.163.com/m/login"

// 登录页会跳这些自定义 scheme：QQ / 微信快捷登录。
// 它们的授权结果只能回到客户端或系统浏览器，WebView 与应用本身都拿不到登录态，
// 所以统一拦下并提示改用手机号 / 扫码，避免用户跳出去再被丢进浏览器。
private val EXTERNAL_LOGIN_SCHEMES = setOf(
    "wtloginmqq", "mqq", "mqqapi", "mqqopensdkapi", "mqqopensdkapiv2",
    "mqqwpa", "mqqbrowser", "tencent", "weixin", "weixinbridge",
)


@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    navController: NavController,
) {

    val context = LocalContext.current
    val ncmCookie by rememberPreference(ncmCookieKey, "")
    var showCookieLoginDialog by rememberSaveable { mutableStateOf(false) }
    var showQrLoginDialog by rememberSaveable { mutableStateOf(false) }
    var webView: WebView? = null
    val coroutineScope = rememberCoroutineScope()
    val addingAccount = rememberSaveable { AccountAddFlow.addAccountMode }
    var loginInProgress by remember { mutableStateOf(false) }
    // 进页面时已存在的 MUSIC_U（null = 还没读到）。轮询只在它变化后才认作「新登录」，
    // 否则已登录用户一进登录页就会被自己的旧 cookie 触发完成逻辑，
    // 添加账号模式下会立刻弹回设置页、看起来像「点了没反应」。
    var initialMusicU by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            AccountAddFlow.addAccountMode = false
            // 登录页只持有一个 WebView，离开时显式销毁：AndroidView 只做 detach，
            // 不 destroy 的话每次进出登录页都会在内存里多留一个 WebView 实例。
            webView?.let { view ->
                view.loadUrl("about:blank")
                view.clearHistory()
                view.removeAllViews()
                view.destroy()
            }
            webView = null
        }
    }

    suspend fun restoreCookieProvider() = withContext(NonCancellable) {
        val storedCookie = runCatching {
            context.applicationContext.dataStore.data.first()[ncmCookieKey].orEmpty()
        }.getOrDefault(ncmCookie)
        CookieProvider.init(
            runCatching { json.decodeFromString<Map<String, String>>(storedCookie) }
                .getOrDefault(emptyMap())
        )
    }

    fun completeLogin(normalized: Map<String, String>, onSaved: () -> Unit) {
        if (loginInProgress) return
        loginInProgress = true
        coroutineScope.launch {
            try {
                // 拉资料前先使用本次登录的凭证，避免读到上一个账号。
                CookieProvider.init(normalized)
                val profile = try {
                    AccountApi.account().getOrNull()?.account?.profile
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
                val account = SavedAccount(
                    userId = profile?.userId ?: 0L,
                    nickname = profile?.nickname.orEmpty(),
                    avatarUrl = profile?.avatarUrl.orEmpty(),
                    cookie = normalized,
                    addedAt = System.currentTimeMillis(),
                )
                AccountStore.upsert(context, account)
                AccountStore.setActive(context, account)
                AccountAddFlow.addAccountMode = false
                onSaved()
                val displayName = account.nickname.ifBlank { "账号" }
                if (addingAccount) {
                    navController.popBackStack()
                    Toast.makeText(context, "已添加账号：$displayName", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "已登录：$displayName", Toast.LENGTH_SHORT).show()
                    navController.navigate(Screen.Library.route)
                }
            } catch (error: CancellationException) {
                restoreCookieProvider()
                throw error
            } catch (_: Exception) {
                restoreCookieProvider()
                loginInProgress = false
                Toast.makeText(context, "保存账号失败，请稍后重试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 网易云移动登录页是 SPA：登录成功后只改前端路由，onPageFinished 不会再触发，
    // 靠页面回调永远拿不到新写入的 MUSIC_U —— 这正是「登录成功但账号列表没新增」的原因。
    // 改成直接轮询 CookieManager，只要 MUSIC_U 与进页面时不同就认作新登录。
    LaunchedEffect(Unit) {
        initialMusicU = runCatching {
            val stored = context.applicationContext.dataStore.data.first()[ncmCookieKey].orEmpty()
            json.decodeFromString<Map<String, String>>(stored)["MUSIC_U"]
        }.getOrNull().orEmpty()
        while (true) {
            delay(1500)
            if (loginInProgress) continue
            // CookieManager 的读操作是 IO，别放在主线程每 1.5 秒做一次。
            val cookieMap = withContext(Dispatchers.IO) {
                val cookieManager = CookieManager.getInstance()
                buildMap {
                    cookieManager.getCookie("https://music.163.com")
                        ?.let { putAll(parseCookieString(it)) }
                    cookieManager.getCookie("https://y.music.163.com")
                        ?.let { putAll(parseCookieString(it)) }
                }
            }
            val musicU = cookieMap["MUSIC_U"].orEmpty()
            if (musicU.isEmpty() || musicU == initialMusicU) continue
            val normalized = NcmCookieNormalizer.normalizeForStorage(cookieMap) ?: continue
            completeLogin(normalized) { }
            break
        }
    }

    // 登录弹窗预填当前已保存 cookie（便于对照/全选复制/直接重新登录）。
    val accountCookiePreview = remember(ncmCookie) {
        runCatching {
            json.decodeFromString<Map<String, String>>(ncmCookie)
                .entries
                .joinToString("; ") { (k, v) -> "$k=$v" }
        }.getOrNull().orEmpty()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.login)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (webView?.canGoBack() == true)
                                webView?.goBack()
                            else
                                navController.navigateUp()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showQrLoginDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.QrCode2,
                            contentDescription = "扫码登录"
                        )
                    }
                    IconButton(onClick = { showCookieLoginDialog = true }) {
                        Icon(
                            imageVector = LoginIcon,
                            contentDescription = "Cookie 登录"
                        )
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Box(
            Modifier
                .padding(top = padding.calculateTopPadding())
                .fillMaxSize()
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        this.layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean {
                                val uri = request?.url ?: return false
                                val scheme = uri.scheme?.lowercase() ?: return false
                                if (scheme !in EXTERNAL_LOGIN_SCHEMES) return false
                                // QQ / 微信快捷登录走自定义 scheme：授权在客户端内完成，
                                // 回调 URL 由系统浏览器打开，登录态落在浏览器里，第三方应用拿不到
                                // （PC 端靠 localhost.ptlogin2.qq.com 本机通道，Android 没有这套机制）。
                                // 这项不维护，直接拦下，避免 WebView 弹 ERR_UNKNOWN_URL_SCHEME 报错页。
                                Toast.makeText(
                                    context,
                                    "请用手机号或扫码登录",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return true
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?,
                            ) {
                                if (request?.isForMainFrame != true) return
                                val unsupported =
                                    error?.errorCode == WebViewClient.ERROR_UNSUPPORTED_SCHEME ||
                                        error?.description?.contains("ERR_UNKNOWN_URL_SCHEME") == true
                                if (unsupported) {
                                    Toast.makeText(
                                        context,
                                        "该登录方式需要在 QQ 客户端内完成，请用手机号或扫码登录",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }

                            override fun onPageFinished(
                                view: WebView?,
                                url: String?,
                            ) {
                                // 多页跳转（含回调页）会走到这里，命中就提前完成；
                                // SPA 登录不触发本回调，剩下的情况由上面的轮询兜底。
                                val cookieManager = CookieManager.getInstance()
                                cookieManager.flush()
                                val cookieMap = buildMap {
                                    cookieManager.getCookie("https://music.163.com")
                                        ?.let { putAll(parseCookieString(it)) }
                                    cookieManager.getCookie("https://y.music.163.com")
                                        ?.let { putAll(parseCookieString(it)) }
                                    url?.let { u ->
                                        cookieManager.getCookie(u)?.let { putAll(parseCookieString(it)) }
                                    }
                                }
                                val musicU = cookieMap["MUSIC_U"].orEmpty()
                                // initialMusicU 还没读到（null）时先不动，交给轮询判定。
                                if (musicU.isEmpty() || musicU == initialMusicU) return
                                val normalized = NcmCookieNormalizer.normalizeForStorage(cookieMap)
                                    ?: return
                                completeLogin(normalized) { view?.clearCache(true) }
                            }
                        }
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            // 净化 UA：登录页按 UA 决定给什么页面，WebView 原生 UA 容易被降级
                            userAgentString = LOGIN_WEB_UA
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            cacheMode
                        }
                        // WebView 默认拒第三方 cookie，登录跳转链（含回调页写 cookie）会断
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webView = this
                        // WebView 的 cookie 是进程级共享的：不清掉的话，添加账号时会直接
                        // 命中上一个账号的登录态，页面根本没有登录入口（用户看到的就是
                        // 「跳过去又把已登录的号抓了一遍」）。
                        val target = this
                        CookieManager.getInstance().removeAllCookies {
                            CookieManager.getInstance().flush()
                            target.loadUrl(MOBILE_LOGIN_URL)
                        }
                    }
                }
            )
        }
    }

    if (showQrLoginDialog) {
        QrLoginDialog(
            onDismiss = { showQrLoginDialog = false },
            onLoginSuccess = { cookieMap ->
                val normalized = NcmCookieNormalizer.normalizeForStorage(cookieMap)
                if (normalized == null) {
                    Toast.makeText(context, "登录态还没有生效，请稍后重试", Toast.LENGTH_SHORT).show()
                } else {
                    completeLogin(normalized) { showQrLoginDialog = false }
                }
            },
        )
    }

    if (showCookieLoginDialog) {
        CookieLoginDialog(
            initialCookie = accountCookiePreview,
            onDismiss = { showCookieLoginDialog = false },
            onLogin = { raw ->
                val normalized = NcmCookieNormalizer.normalizeForStorage(raw)
                if (normalized == null) {
                    Toast.makeText(context, "未识别到 MUSIC_U，无法登录", Toast.LENGTH_SHORT).show()
                    return@CookieLoginDialog
                }
                // 登录 = 清除本地 WebView 登录缓存，按输入框内容重新登录
                CookieManager.getInstance().apply {
                    removeAllCookies(null)
                    flush()
                }
                completeLogin(normalized) { showCookieLoginDialog = false }
            }
        )
    }
}

@Composable
private fun CookieLoginDialog(
    initialCookie: String,
    onDismiss: () -> Unit,
    onLogin: (String) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = remember {
        context.getSystemService(android.content.ClipboardManager::class.java)
    }
    var text by rememberSaveable { mutableStateOf(initialCookie) }

    ResponsiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cookie 登录") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("MUSIC_U 或完整 Cookie") },
                placeholder = { Text("贴 MUSIC_U 值即可；整串 Cookie 也行") },
                minLines = 2,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onLogin(text.trim()) }) {
                Text("登录")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { text = "" }) {
                    Text("清除")
                }
                TextButton(
                    onClick = {
                        val value = text.trim()
                        if (value.isNotBlank()) {
                            clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText("ncm-cookie", value)
                            )
                            Toast.makeText(
                                context, "已复制到剪贴板", Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                ) {
                    Text("全选复制")
                }
            }
        }
    )
}


