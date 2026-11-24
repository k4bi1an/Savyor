package com.savyor.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

@Entity(tableName = "reels_fts")
@Fts4(contentEntity = ReelEntity::class)
data class ReelFtsEntity(
    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "summary")
    val summary: String,

    @ColumnInfo(name = "transcript")
    val transcript: String,

    @ColumnInfo(name = "original_transcript")
    val originalTranscript: String?,

    @ColumnInfo(name = "tags")
    val tags: String,

    @ColumnInfo(name = "creator")
    val creator: String,

    @ColumnInfo(name = "structured_content")
    val structuredContent: String
)
