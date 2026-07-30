package com.jussicodes.music.ui.screen

import android.annotation.SuppressLint
import android.os.Build
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
import com.jussicodes.music.utils.getDeviceID
import com.jussicodes.music.utils.rememberPreference
import com.rcmiku.ncmapi.utils.CookieKeys
import com.rcmiku.ncmapi.utils.CookieProvider
import com.rcmiku.ncmapi.utils.json
import com.rcmiku.ncmapi.utils.parseCookieString
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
                                    }.toMutableMap()
                                    if (!cookieMap.containsKey(CookieKeys.MUSIC_U)) {
                                        Toast.makeText(
                                            context,
                                            "登录态还没有生效，请完成登录后稍等",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return
                                    }
                                    ncmCookie = cookieMap.withDeviceInfo().toStoredCookie()
                                    CookieProvider.init(cookieMap.withDeviceInfo())
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
            onDismiss = { showCookieLoginDialog = false },
            onConfirm = { rawCookie ->
                val cookieMap = parseCookieString(rawCookie).withDeviceInfo()
                if (!cookieMap.containsKey(CookieKeys.MUSIC_U)) {
                    Toast.makeText(context, "Cookie 缺少 MUSIC_U，无法登录", Toast.LENGTH_SHORT).show()
                    return@CookieLoginDialog
                }
                ncmCookie = cookieMap.toStoredCookie()
                CookieProvider.init(cookieMap)
                showCookieLoginDialog = false
                navController.navigate(Screen.Library.route)
            }
        )
    }
}

@Composable
private fun CookieLoginDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var cookie by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cookie 登录") },
        text = {
            OutlinedTextField(
                value = cookie,
                onValueChange = { cookie = it },
                label = { Text("完整 Cookie") },
                minLines = 6,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(cookie.trim()) }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { cookie = "" }) {
                    Text("清空")
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}

private fun Map<String, String>.withDeviceInfo(): MutableMap<String, String> = toMutableMap().apply {
    this[CookieKeys.DEVICE_ID] = getDeviceID()
    this[CookieKeys.OS_VER] = Build.VERSION.RELEASE
    this[CookieKeys.MOBILE_NAME] = Build.MODEL
}

private fun Map<String, String>.toStoredCookie(): String = json.encodeToString(this)


