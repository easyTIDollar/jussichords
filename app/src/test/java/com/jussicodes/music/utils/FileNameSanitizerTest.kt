package com.jussicodes.music.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNameSanitizerTest {
    @Test
    fun keepsNormalName() {
        assertEquals("周杰伦 - 晴天", sanitizeFileName("周杰伦 - 晴天"))
    }

    @Test
    fun replacesEveryInvalidWindowsCharacter() {
        assertEquals("a_b_c_d_e_f_g_h_i_j", sanitizeFileName("a/b\\c:d*e?f\"g<h>i|j"))
    }

    @Test
    fun keepsEmptyNameEmpty() {
        assertEquals("", sanitizeFileName(""))
    }

    @Test
    fun trimsWhitespaceOnlyNameToEmpty() {
        assertEquals("", sanitizeFileName(" \t\n "))
    }

    @Test
    fun trimsLeadingAndTrailingSpaces() {
        assertEquals("name", sanitizeFileName("  name  "))
    }

    @Test
    fun replacesNameMadeOnlyOfInvalidCharacters() {
        assertEquals("___", sanitizeFileName("///"))
    }

    @Test
    fun keepsChineseAndEmoji() {
        assertEquals("🎵歌", sanitizeFileName("🎵歌"))
    }
}
