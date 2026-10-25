package com.jussicodes.music.lyric

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 覆盖安装（每次更新）后系统会发 MY_PACKAGE_REPLACED。
 *
 * 桌面歌词是独立的悬浮窗 Service，更新把进程杀掉之后没人再拉起它，
 * 用户体感就是「每次更新都要重新开一次 / 重新给权限」。这里收到广播后
 * 按已保存的开关状态直接恢复：权限还在就立刻回来，权限被收回也会保持开关为开，
 * 授权后自动恢复，不会再静默把用户的选择清掉。
 */
class DesktopLyricRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            DesktopLyricManager.syncService(context)
        }
    }
}
