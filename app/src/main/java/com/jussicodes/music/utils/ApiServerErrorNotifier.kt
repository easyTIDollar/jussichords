package com.jussicodes.music.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.util.concurrent.atomic.AtomicLong

/**
 * 当网络或服务器请求失败时，弹一个节流 toast 提示用户。
 * 跨 explore（启动预热）/搜索等多处调用，5 秒内只弹一次。
 * 若设备处于断网/飞行模式（无活跃网络连接），静默降级，不打扰正在听本地音乐的用户。
 */
object ApiServerErrorNotifier {

    /** 去抖窗口：窗口内重复失败只提示一次。 */
    private const val THROTTLE_MS = 5_000L

    private val main = Handler(Looper.getMainLooper())
    private val lastShownAt = AtomicLong(0L)

    private fun isNetworkConnected(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(false)

    /**
     * 提示网络异常。[context] 仅取 applicationContext，可安全跨线程调用。
     * 5 秒内重复调用会被节流掉，不重复弹 toast。
     * 设备彻底断网时静默降级，不弹 Toast。
     */
    fun notifyServerUnreachable(context: Context) {
        val appContext = context.applicationContext
        // 设备彻底断网时静默降级，不打扰离线听歌体验
        if (!isNetworkConnected(appContext)) return

        val now = System.currentTimeMillis()
        val prev = lastShownAt.get()
        // 用 CAS 抢占时间戳，避免并发失败时多弹；窗口内直接返回。
        if (now - prev < THROTTLE_MS) return
        if (!lastShownAt.compareAndSet(prev, now)) return
        main.post {
            Toast.makeText(
                appContext,
                "网络连接异常，请检查网络后重试",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
