package com.ashis.notesphare.ui.util

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RichTextTest {

    @Test
    fun collapsedSelectionInsertsMarkerPairAndPlacesCursorBetween() {
        val value = TextFieldValue(text = "hello", selection = TextRange(5))
        val result = applyInlineMarker(value, BOLD_MARKER)
        assertEquals("hello****", result.text)
        assertEquals(TextRange(7), result.selection)
    }

    @Test
    fun selectedTextIsWrappedWithMarkers() {
        val value = TextFieldValue(text = "hello", selection = TextRange(0, 5))
        val result = applyInlineMarker(value, BOLD_MARKER)
        assertEquals("**hello**", result.text)
        assertEquals(TextRange(2, 7), result.selection)
    }

    @Test
    fun applyingSameMarkerAgainUnwrapsSelection() {
        val value = TextFieldValue(text = "**hello**", selection = TextRange(2, 7))
        val result = applyInlineMarker(value, BOLD_MARKER)
        assertEquals("hello", result.text)
        assertEquals(TextRange(0, 5), result.selection)
    }

    @Test
    fun selectionIncludingMarkersIsUnwrapped() {
        val value = TextFieldValue(text = "**hello**", selection = TextRange(0, 9))
        val result = applyInlineMarker(value, BOLD_MARKER)
        assertEquals("hello", result.text)
        assertEquals(TextRange(0, 5), result.selection)
    }

    @Test
    fun bulletIsInsertedAtLineStart() {
        val value = TextFieldValue(text = "one\ntwo", selection = TextRange(7))
        val result = insertBullet(value)
        assertEquals("one\n- two", result.text)
        assertEquals(TextRange(9), result.selection)
    }

    @Test
    fun existingBulletIsRemoved() {
        val value = TextFieldValue(text = "- item", selection = TextRange(6))
        val result = insertBullet(value)
        assertEquals("item", result.text)
        assertEquals(TextRange(4), result.selection)
    }

    @Test
    fun parserRemovesMarkersAndAppliesInlineStyles() {
        val annotated = buildRichText("**bold** and *italic* and `code`")
        assertEquals("bold and italic and code", annotated.text)
        assertTrue(annotated.spanStyles.isNotEmpty())
    }

    @Test
    fun parserConvertsBulletLines() {
        val annotated = buildRichText("- one\n- two")
        assertEquals("\u2022 one\n\u2022 two", annotated.text)
    }

    @Test
    fun parserHandlesUnderlineAndStrikethrough() {
        val annotated = buildRichText("__under__ and ~~gone~~")
        assertEquals("under and gone", annotated.text)
        assertTrue(annotated.spanStyles.isNotEmpty())
    }
}
