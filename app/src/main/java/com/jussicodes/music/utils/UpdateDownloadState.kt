package com.jussicodes.music.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class UpdateDownloadPhase {
    IDLE,
    DOWNLOADING,
    COMPLETED,
    FAILED,
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

    fun reset() {
        mutableState.value = UpdateDownloadSnapshot()
    }
}
