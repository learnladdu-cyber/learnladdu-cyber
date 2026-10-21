package com.ashis.notesphare.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TextFormattingTest {

    @Test
    fun stripMarkupRemovesInlineMarkersAndBullets() {
        val source = "**Bold** and *italic* with `code`\n- first\n- second"
        assertEquals(
            "Bold and italic with code\nfirst\nsecond",
            stripMarkup(source)
        )
    }

    @Test
    fun noteSnippetCollapsesWhitespace() {
        assertEquals(
            "first second third",
            noteSnippet("first\n\nsecond    third")
        )
    }

    @Test
    fun noteSnippetTruncatesLongText() {
        val snippet = noteSnippet("a".repeat(200), maxLength = 20)
        assertEquals(21, snippet.length)
        assertTrue(snippet.endsWith("\u2026"))
    }

    @Test
    fun yesterdayIsFormattedAsYesterday() {
        val now = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.OCTOBER)
            set(Calendar.DAY_OF_MONTH, 20)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val yesterday = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -1)
        }.timeInMillis
        assertEquals("Yesterday", formatRelativeTime(yesterday, now))
    }

    @Test
    fun olderDateIncludesYear() {
        val now = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.OCTOBER)
            set(Calendar.DAY_OF_MONTH, 20)
        }.timeInMillis
        val old = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2020)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 5)
        }.timeInMillis
        val formatted = formatRelativeTime(old, now)
        assertTrue(formatted.contains("2020"))
    }
}
