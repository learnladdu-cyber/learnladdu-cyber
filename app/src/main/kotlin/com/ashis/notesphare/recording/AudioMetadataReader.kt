package com.ashis.notesphare.recording

import android.media.MediaMetadataRetriever
import java.io.File

interface AudioMetadataReader {
    fun durationMs(file: File): Long?
}

class AndroidAudioMetadataReader : AudioMetadataReader {

    override fun durationMs(file: File): Long? {
        if (!file.isFile || file.length() <= 1_024L) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val raw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = raw?.toLongOrNull() ?: return null
            duration.takeIf { it > 0L }
        } catch (throwable: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
