package com.rcmiku.ncmapi.model

import kotlinx.serialization.Serializable

/**
 * 歌曲百科与回忆坐标领域模型
 */
@Serializable
data class SongWikiData(
    val style: String = "",
    val album: String = "",
    val language: String = "",
    val publishTime: String = "",
    val bpm: String = "",
    val creators: String = "",
    val creatorRoles: List<SongWikiCreatorRole> = emptyList(),
    val entertainment: String = "",
    val awards: List<String> = emptyList(),
    val awardTotal: Int = 0,
    val musicMemory: SongMusicMemory? = null
) {
    val isEmpty: Boolean
        get() = style.isBlank() &&
                album.isBlank() &&
                language.isBlank() &&
                publishTime.isBlank() &&
                bpm.isBlank() &&
                creators.isBlank() &&
                entertainment.isBlank() &&
                awards.isEmpty() &&
                musicMemory == null
}

@Serializable
data class SongWikiCreatorRole(
    val roleName: String,
    val artistNames: List<String>
)

@Serializable
data class SongMusicMemory(
    val firstListenDate: String = "",
    val season: String = "",
    val period: String = "",
    val playCount: Int = 0,
    val playCountText: String = ""
)

// ======================= 接口 DTO 定义 =======================

@Serializable
data class SongWikiSummaryResponse(
    val code: Int = 0,
    val data: SongWikiSummaryData? = null
)

@Serializable
data class SongWikiSummaryData(
    val blocks: List<SongWikiBlock> = emptyList()
)

@Serializable
data class SongWikiBlock(
    val code: String = "",
    val showType: String = "",
    val creatives: List<SongWikiCreative> = emptyList()
)

@Serializable
data class SongWikiCreative(
    val creativeType: String = "",
    val resources: List<SongWikiResource> = emptyList(),
    val uiElement: SongWikiUiElement? = null
)

@Serializable
data class SongWikiResource(
    val resourceType: String = "",
    val resourceExt: SongWikiResourceExt? = null,
    val uiElement: SongWikiUiElement? = null
)

@Serializable
data class SongWikiUiElement(
    val mainTitle: SongWikiMainTitle? = null,
    val textLinks: List<SongWikiTextLink> = emptyList(),
    val buttons: List<SongWikiButton> = emptyList()
)

@Serializable
data class SongWikiButton(
    val text: String = ""
)

@Serializable
data class SongWikiResourceExt(
    val musicFirstListenDto: MusicFirstListenDto? = null,
    val musicTotalPlayDto: MusicTotalPlayDto? = null
)

@Serializable
data class MusicFirstListenDto(
    val date: String = "",
    val season: String = "",
    val period: String = ""
)

@Serializable
data class MusicTotalPlayDto(
    val playCount: Int = 0,
    val text: String = ""
)

@Serializable
data class SongWikiMainTitle(
    val title: String = ""
)

@Serializable
data class SongWikiTextLink(
    val text: String = ""
)

@Serializable
data class SongCreatorsResponse(
    val code: Int = 0,
    val data: SongCreatorsData? = null
)

@Serializable
data class SongCreatorsData(
    val songCreatorsRoleVos: List<SongCreatorRole> = emptyList()
)

@Serializable
data class SongCreatorRole(
    val roleName: String = "",
    val creatorMetaVOS: List<CreatorMeta> = emptyList()
)

@Serializable
data class CreatorMeta(
    val artistName: String = ""
)
