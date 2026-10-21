package com.ashis.notesphare.ui.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import kotlin.math.max
import kotlin.math.min

const val BOLD_MARKER = "**"
const val ITALIC_MARKER = "*"
const val UNDERLINE_MARKER = "__"
const val STRIKE_MARKER = "~~"
const val CODE_MARKER = "`"

fun applyInlineMarker(value: TextFieldValue, marker: String): TextFieldValue {
    val text = value.text
    val start = min(value.selection.start, value.selection.end)
    val end = max(value.selection.start, value.selection.end)

    if (start == end) {
        val newText = text.substring(0, start) + marker + marker + text.substring(start)
        return TextFieldValue(
            text = newText,
            selection = TextRange(start + marker.length)
        )
    }

    val hasMarkersAround = start >= marker.length &&
        end + marker.length <= text.length &&
        text.substring(start - marker.length, start) == marker &&
        text.substring(end, end + marker.length) == marker

    if (hasMarkersAround) {
        val newText = text
            .removeRange(end, end + marker.length)
            .removeRange(start - marker.length, start)
        return TextFieldValue(
            text = newText,
            selection = TextRange(start - marker.length, end - marker.length)
        )
    }

    val selected = text.substring(start, end)
    val wrapsItself = selected.length > marker.length * 2 &&
        selected.startsWith(marker) &&
        selected.endsWith(marker)

    if (wrapsItself) {
        val newText = text
            .removeRange(end - marker.length, end)
            .removeRange(start, start + marker.length)
        return TextFieldValue(
            text = newText,
            selection = TextRange(start, end - marker.length * 2)
        )
    }

    val newText = text.substring(0, start) + marker +
        selected + marker + text.substring(end)
    return TextFieldValue(
        text = newText,
        selection = TextRange(start + marker.length, end + marker.length)
    )
}

fun insertBullet(value: TextFieldValue): TextFieldValue {
    val text = value.text
    val cursor = min(value.selection.start, value.selection.end)
    val lineStart = text.lastIndexOf('\n', cursor - 1) + 1
    val line = text.substring(lineStart)
    if (line.startsWith("- ")) {
        val newText = text.removeRange(lineStart, lineStart + 2)
        return TextFieldValue(
            text = newText,
            selection = TextRange(max(lineStart, cursor - 2))
        )
    }
    val newText = text.substring(0, lineStart) + "- " + text.substring(lineStart)
    return TextFieldValue(
        text = newText,
        selection = TextRange(cursor + 2)
    )
}

fun buildRichText(source: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    var bold = false
    var italic = false
    var underline = false
    var strike = false
    var code = false
    var atLineStart = true

    while (index < source.length) {
        if (atLineStart && (source.startsWith("- ", index) || source.startsWith("* ", index))) {
            append("\u2022 ")
            index += 2
            atLineStart = false
            continue
        }
        when {
            source.startsWith(BOLD_MARKER, index) -> {
                bold = !bold
                index += BOLD_MARKER.length
                continue
            }
            source.startsWith(UNDERLINE_MARKER, index) -> {
                underline = !underline
                index += UNDERLINE_MARKER.length
                continue
            }
            source.startsWith(STRIKE_MARKER, index) -> {
                strike = !strike
                index += STRIKE_MARKER.length
                continue
            }
            source.startsWith(ITALIC_MARKER, index) -> {
                italic = !italic
                index += ITALIC_MARKER.length
                continue
            }
            source.startsWith(CODE_MARKER, index) -> {
                code = !code
                index += CODE_MARKER.length
                continue
            }
        }

        val character = source[index]
        index++
        if (character == '\n') {
            append(character)
            atLineStart = true
            continue
        }

        val decorations = buildList {
            if (underline) add(TextDecoration.Underline)
            if (strike) add(TextDecoration.LineThrough)
        }
        val style = SpanStyle(
            fontWeight = if (bold) FontWeight.Bold else null,
            fontStyle = if (italic) FontStyle.Italic else null,
            textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
            fontFamily = if (code) FontFamily.Monospace else null
        )
        val start = length
        append(character)
        if (bold || italic || underline || strike || code) {
            addStyle(style, start, length)
        }
        atLineStart = false
    }
}
