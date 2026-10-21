package com.ashis.notesphare.ui.components

import androidx.compose.ui.graphics.Color

val NoteColorPalette: List<Int> = listOf(
    0xFF4C5BD4,
    0xFF00696E,
    0xFF7B4E9E,
    0xFFB3261E,
    0xFF9A6B00,
    0xFF2E7D32,
    0xFF00629E,
    0xFF6D4C41
).map { it.toInt() }

fun noteColor(value: Int, fallback: Color): Color =
    if (value == 0) fallback else Color(value)
