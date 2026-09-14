package com.jussicodes.music.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.util.concurrent.atomic.AtomicLong

/**
 * 当已配置的 API 服务器请求失败（地址填错或服务器失效）时，弹一个节流 toast
 * 提醒用户去「设置 → API 服务器」更换后端。
 *
 * 跨 explore（启动预热）/搜索等多处调用，5 秒内只弹一次，避免开屏时并发失败刷屏。
 * 统一走主线程 [Toast]。
 */
object ApiServerErrorNotifier {

    /** 去抖窗口：窗口内重复失败只提示一次。 */
    private const val THROTTLE_MS = 5_000L

    private val main = Handler(Looper.getMainLooper())
    private val lastShownAt = AtomicLong(0L)

    /**
     * 提示用户更换 API 服务器。[context] 仅取 applicationContext，可安全跨线程调用。
     * 5 秒内重复调用会被节流掉，不重复弹 toast。
     */
    fun notifyServerUnreachable(context: Context) {
        val now = System.currentTimeMillis()
        val prev = lastShownAt.get()
        // 用 CAS 抢占时间戳，避免并发失败时多弹；窗口内直接返回。
        if (now - prev < THROTTLE_MS) return
        if (!lastShownAt.compareAndSet(prev, now)) return
        main.post {
            Toast.makeText(
                context.applicationContext,
                "API 服务器不可用，请到「设置 → API 服务器」更换服务器",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
