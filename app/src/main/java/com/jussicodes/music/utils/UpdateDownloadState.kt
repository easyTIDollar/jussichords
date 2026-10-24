package com.jussicodes.music.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class UpdateDownloadPhase {
    IDLE,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

data class UpdateDownloadSnapshot(
    val phase: UpdateDownloadPhase = UpdateDownloadPhase.IDLE,
    val updateInfo: UpdateInfo? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val message: String? = null,
    val apkPath: String? = null,
)

object UpdateDownloadStateStore {
    private val mutableState = MutableStateFlow(UpdateDownloadSnapshot())
    val state = mutableState.asStateFlow()

    /** 本次下载是否已经拉起过安装器（避免主界面与设置页同时收到 COMPLETED 而弹两次安装）。 */
    private var installLaunched = false

    /**
     * 尝试领取「拉起安装器」的资格：同一份下载只有第一个调用者拿到 true。
     *
     * 之前 MainActivity 和 SettingsScreen 各自 collect 同一个 store，
     * 下载完成时两边都会调 installApk → 系统弹出两个安装器窗口。
     */
    fun claimInstallLaunch(): Boolean {
        if (installLaunched) return false
        installLaunched = true
        return true
    }

    fun downloading(updateInfo: UpdateInfo, progress: DownloadProgress) {
        mutableState.value = UpdateDownloadSnapshot(
            phase = UpdateDownloadPhase.DOWNLOADING,
            updateInfo = updateInfo,
            downloadedBytes = progress.downloadedBytes,
            totalBytes = progress.totalBytes,
        )
    }

    fun completed(updateInfo: UpdateInfo, apkPath: String) {
        mutableState.value = UpdateDownloadSnapshot(
            phase = UpdateDownloadPhase.COMPLETED,
            updateInfo = updateInfo,
            downloadedBytes = updateInfo.apkSize,
            totalBytes = updateInfo.apkSize,
            apkPath = apkPath,
        )
    }

    fun failed(updateInfo: UpdateInfo, message: String) {
        mutableState.value = UpdateDownloadSnapshot(
            phase = UpdateDownloadPhase.FAILED,
            updateInfo = updateInfo,
            message = message,
        )
    }

    /** 用户主动取消：保留已下载字节数，UI 回到「可重新下载」状态。 */
    fun cancelled(updateInfo: UpdateInfo, downloadedBytes: Long = 0L) {
        mutableState.value = UpdateDownloadSnapshot(
            phase = UpdateDownloadPhase.CANCELLED,
            updateInfo = updateInfo,
            downloadedBytes = downloadedBytes,
        )
    }

    fun reset() {
        installLaunched = false
        mutableState.value = UpdateDownloadSnapshot()
    }
}
