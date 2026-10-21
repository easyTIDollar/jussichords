package com.rcmiku.ncmapi.api.search

enum class SearchType(val type: Int) {
    /** 综合搜索：网易云 /cloudsearch type=101，单次返回单曲/专辑/歌手/歌单/用户/播客混合列表 */
    All(101),
    Song(1),
    Album(10),
    Artist(100),
    Playlist(1000),
    User(1002),
    Radio(1009),
    VoiceList(1009)
}
