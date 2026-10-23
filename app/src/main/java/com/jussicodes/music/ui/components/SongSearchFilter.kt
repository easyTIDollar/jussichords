package com.jussicodes.music.ui.components

import android.icu.text.Transliterator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.rcmiku.ncmapi.model.Song
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 列表内搜索（歌单页 / 专辑页共用）。
 *
 * 支持歌名、歌手名与拼音首字母（Han-Latin/Names 转写）。
 */
@Composable
fun rememberFilteredSongs(tracks: List<Song>, query: String): List<Song> {
    val normalizedQuery = remember(query) { query.toSearchText() }
    var index by remember(tracks) { mutableStateOf<SongSearchIndex?>(null) }
    LaunchedEffect(tracks) {
        index = withContext(Dispatchers.Default) { tracks.toSearchIndex() }
    }
    return when {
        normalizedQuery.isBlank() -> tracks
        index == null -> tracks
        else -> index!!.filter(normalizedQuery)
    }
}

private class SongSearchEntry(song: Song) {
    val song = song
    private val title = song.name.toSearchText()
    private val artists = song.ar.joinToString(" ") { it.name }.toSearchText()
    private val initials = buildString {
        append(song.name.toInitials())
        append(' ')
        song.ar.forEach { artist ->
            append(artist.name.toInitials())
            append(' ')
        }
    }

    fun matches(normalizedQuery: String): Boolean {
        if (normalizedQuery.isBlank()) return true
        return title.contains(normalizedQuery) ||
            artists.contains(normalizedQuery) ||
            initials.contains(normalizedQuery)
    }
}

private fun String.toSearchText(): String =
    lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

private fun String.toInitials(): String =
    PinyinInitialTransliterator.transliterate(this)
        .split(' ', '-', '_', '/', '\\', '.', ',', '(', ')', '[', ']', '·')
        .mapNotNull { word -> word.firstOrNull { it.isLetterOrDigit() } }
        .joinToString("")
        .lowercase(Locale.ROOT)

private fun List<Song>.toSearchIndex(): SongSearchIndex =
    SongSearchIndex(map(::SongSearchEntry))

private class SongSearchIndex(private val entries: List<SongSearchEntry>) {
    fun filter(normalizedQuery: String): List<Song> =
        entries.asSequence()
            .filter { it.matches(normalizedQuery) }
            .map { it.song }
            .toList()
}

private object PinyinInitialTransliterator {
    private val transliterator by lazy {
        Transliterator.getInstance("Han-Latin/Names; Latin-ASCII")
    }

    fun transliterate(value: String): String = transliterator.transliterate(value)
}
