package com.jussicodes.music.ui.screen

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.jussicodes.music.R
import com.jussicodes.music.constants.ncmCookieKey
import com.jussicodes.music.ui.icons.Login as LoginIcon
import com.jussicodes.music.ui.navigation.Screen
import com.jussicodes.music.utils.NcmCookieNormalizer
import com.jussicodes.music.utils.rememberPreference
import com.rcmiku.ncmapi.utils.CookieProvider
import com.rcmiku.ncmapi.utils.json
import com.rcmiku.ncmapi.utils.parseCookieString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString


@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    navController: NavController,
) {

    val context = LocalContext.current
    var ncmCookie by rememberPreference(ncmCookieKey, "")
    var showCookieLoginDialog by rememberSaveable { mutableStateOf(false) }
    var webView: WebView? = null

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
                            override fun onPageFinished(
                                view: WebView?,
                                url: String?,
                            ) {
                                if (url?.startsWith("https://y.music.163.com/m") == true) {
                                    val cookieManager = CookieManager.getInstance()
                                    cookieManager.flush()
                                    val cookieMap = buildMap {
                                        cookieManager.getCookie("https://music.163.com")
                                            ?.let { putAll(parseCookieString(it)) }
                                        cookieManager.getCookie("https://y.music.163.com")
                                            ?.let { putAll(parseCookieString(it)) }
                                        cookieManager.getCookie(url)
                                            ?.let { putAll(parseCookieString(it)) }
                                    }
                                    val normalized = NcmCookieNormalizer.normalizeForStorage(cookieMap)
                                    if (normalized == null) {
                                        Toast.makeText(
                                            context,
                                            "登录态还没有生效，请完成登录后稍等",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return
                                    }
                                    ncmCookie = json.encodeToString(normalized)
                                    CookieProvider.init(normalized)
                                    webView?.clearCache(true)
                                    navController.navigate(Screen.Library.route)
                                }
                            }
                        }
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            cacheMode
                        }
                        webView = this
                        loadUrl("https://music.163.com/m/login")
                    }
                }
            )
        }
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
                ncmCookie = json.encodeToString(normalized)
                CookieProvider.init(normalized)
                showCookieLoginDialog = false
                navController.navigate(Screen.Library.route)
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

    AlertDialog(
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


