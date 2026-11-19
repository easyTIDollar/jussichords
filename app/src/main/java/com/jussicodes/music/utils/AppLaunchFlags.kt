package com.jussicodes.music.utils

/**
 * 本次进程的启动期标志。
 *
 * [justUpdated]：这次启动的版本名和上次运行不同 —— 说明是覆盖安装后的第一次启动。
 *
 * 更新后首启是整个生命周期里最卡的一档：dex 变了，系统此前编好的 AOT 产物全部失效，
 * 应用代码基本解释执行，同时系统后台还在为新 dex 跑 dex2oat 抢 CPU。这时候再去查更新
 * 没有任何意义（刚装的已是最新），只是白白加一次网络往返和 JSON 解析。
 */
object AppLaunchFlags {

    @Volatile
    var justUpdated: Boolean = false
        private set

    internal fun markJustUpdated() {
        justUpdated = true
    }
}
