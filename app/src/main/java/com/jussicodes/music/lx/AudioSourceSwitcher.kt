package com.jussicodes.music.lx

import com.jussicodes.music.playback.PlaybackReloadBus
import com.rcmiku.ncmapi.api.AudioSourceSettings
import com.rcmiku.ncmapi.api.LocalSources
import com.rcmiku.ncmapi.api.player.PlayerApi

/**
 * 换源的唯一执行点：设置页与播放页菜单都调这里，避免两处各写一遍而漏掉某一步。
 *
 * 三件事缺一不可：
 *  1. 写 [AudioSourceSettings.SELECTED] —— PlayerApi 下一次解析读它；
 *  2. 写 [PlaybackReloadBus.sourceTag] —— cacheKey 的一部分，不写就会复用旧直链；
 *  3. 发 [PlaybackReloadBus.requestReload] —— 让 PlaybackService 重新 prepare 当前歌。
 */
object AudioSourceSwitcher {

    fun apply(audioSource: String, directSource: String) {
        AudioSourceSettings.SELECTED = audioSource
        val direct = LocalSources.fromKey(directSource)?.value ?: LocalSources.AUTO
        PlayerApi.DirectSourceSettings.SOURCE = direct
        PlaybackReloadBus.sourceTag = "$audioSource/$direct"
        PlaybackReloadBus.requestReload()
    }
}