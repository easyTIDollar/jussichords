package com.jussicodes.music.utils

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/**
 * 「一次前台会话」的标识。
 *
 * 会话起点：
 *  1. 进程启动（进程一死，标识自然就是新的）；
 *  2. 用户主动退出界面 —— Activity 以 isFinishing 销毁（按返回键 / 划掉最近任务）；
 *  3. 后台停留超过 [BACKGROUND_RESET_MS] 再回来。
 *
 * 旋转屏、弹窗、切出去看两眼再回来都不会开启新会话，
 * 所以拿它判断「用户这次是重新打开了 App，还是还在同一次使用里」是稳的。
 *
 * 用途：私人 FM 的队列按会话换批次 —— 同一次使用里点「电台」只是回到正在听的那一批；
 * 关掉 App 再进来就该是新一批，否则队列会永远停在上一批那几首。
 */
object AppSessionClock : Application.ActivityLifecycleCallbacks {

    /** 后台停留超过这个时长，再回前台就算新会话（兜底：某些 ROM 划掉任务不给 isFinishing）。 */
    private const val BACKGROUND_RESET_MS = 5 * 60_000L

    @Volatile
    var sessionId: Long = System.currentTimeMillis()
        private set

    private var resumedCount = 0
    private var leftLongEnough = false

    /** 界面被用户主动关掉过：下次回到前台直接算新会话。 */
    private var closedByUser = false

    private val handler = Handler(Looper.getMainLooper())

    private val markLeft = Runnable {
        if (resumedCount == 0) leftLongEnough = true
    }

    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }

    /** 开启新会话：只做相等比较，所以只要单调递增、不撞值即可。 */
    fun newSession() {
        sessionId = maxOf(System.currentTimeMillis(), sessionId + 1)
    }

    override fun onActivityResumed(activity: Activity) {
        val fromBackground = resumedCount == 0
        resumedCount += 1
        if (fromBackground) {
            handler.removeCallbacks(markLeft)
            if (closedByUser || leftLongEnough) {
                closedByUser = false
                leftLongEnough = false
                newSession()
            }
        }
    }

    override fun onActivityPaused(activity: Activity) {
        resumedCount = (resumedCount - 1).coerceAtLeast(0)
        if (resumedCount == 0) {
            handler.removeCallbacks(markLeft)
            handler.postDelayed(markLeft, BACKGROUND_RESET_MS)
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        // isFinishing = 用户按返回键 / 划掉最近任务；配置变更销毁不算。
        if (activity.isFinishing) closedByUser = true
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
