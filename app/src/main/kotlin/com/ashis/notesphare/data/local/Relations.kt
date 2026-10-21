package com.ashis.notesphare.data.local

import androidx.room.Embedded

data class FolderWithCount(
    @Embedded val folder: FolderEntity,
    val noteCount: Int
)

data class TagWithCount(
    @Embedded val tag: TagEntity,
    val noteCount: Int
)
