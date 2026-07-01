package com.jussicodes.music.constants

import android.os.Bundle
import androidx.media3.session.SessionCommand

object MediaSessionConstants {
    const val ACTION_TOGGLE_LIKE = "TOGGLE_LIKE"
    const val ACTION_TOGGLE_SHUFFLE = "TOGGLE_SHUFFLE"
    const val ACTION_TOGGLE_DESKTOP_LYRIC = "TOGGLE_DESKTOP_LYRIC"
    const val EXTRA_SOURCE_ID = "source_id"
    const val EXTRA_SOURCE_NAME = "source_name"
    const val EXTRA_DURATION_MS = "duration_ms"
    val CommandToggleLike = SessionCommand(ACTION_TOGGLE_LIKE, Bundle.EMPTY)
    val CommandToggleShuffle = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)
    val CommandToggleDesktopLyric = SessionCommand(ACTION_TOGGLE_DESKTOP_LYRIC, Bundle.EMPTY)
}
