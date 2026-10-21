package com.ashis.notesphare.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ashis.notesphare.ui.util.buildRichText

@Composable
fun RichTextView(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = buildRichText(text),
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge
    )
}
