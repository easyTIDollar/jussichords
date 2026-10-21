package com.rcmiku.ncmapi.api.search

enum class SearchType(val type: Int) {
    Song(1),
    Album(10),
    Artist(100),
    Playlist(1000),
    User(1002),
    Radio(1009),
    VoiceList(1009)
}
