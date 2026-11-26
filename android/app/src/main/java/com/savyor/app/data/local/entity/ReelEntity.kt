package com.savyor.app.data.local.entity

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Keep
@Entity(
    tableName = "reels",
    indices = [
        Index(value = ["shortcode"], unique = true),
        Index(value = ["category"]),
        Index(value = ["status"]),
        Index(value = ["processing_state"]),
        Index(value = ["is_starred"])
    ]
)
data class ReelEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "shortcode")
    val shortcode: String,

    @ColumnInfo(name = "original_url")
    val originalUrl: String,

    @ColumnInfo(name = "clean_url")
    val cleanUrl: String,

    @ColumnInfo(name = "creator")
    val creator: String = "",

    @ColumnInfo(name = "creator_username")
    val creatorUsername: String = "",

    @ColumnInfo(name = "source_language")
    val sourceLanguage: String = "English",

    @ColumnInfo(name = "title")
    val title: String = "",

    @ColumnInfo(name = "summary")
    val summary: String = "",

    @ColumnInfo(name = "category")
    val category: String = "Other",

    @ColumnInfo(name = "tags")
    val tags: String = "", // Comma-separated or JSON array

    @ColumnInfo(name = "transcript")
    val transcript: String = "", // Timestamped English transcript

    @ColumnInfo(name = "original_transcript")
    val originalTranscript: String? = null, // Optional non-English transcript

    @ColumnInfo(name = "key_takeaways")
    val keyTakeaways: String = "[]", // JSON array of strings

    @ColumnInfo(name = "key_moments")
    val keyMoments: String = "[]", // JSON array of KeyMoment objects

    @ColumnInfo(name = "structured_content")
    val structuredContent: String = "{}", // JSON of StructuredContent object

    @ColumnInfo(name = "extracted_resources")
    val extractedResources: String = "[]", // JSON array of Resource objects

    @ColumnInfo(name = "caveats")
    val caveats: String = "[]", // JSON array of caveats/satire/ad notes

    @ColumnInfo(name = "use_when")
    val useWhen: String = "", // 1-line practical cue

    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String? = null,

    @ColumnInfo(name = "user_note")
    val userNote: String = "",

    @ColumnInfo(name = "visual_memory")
    val visualMemory: String = "",

    @ColumnInfo(name = "is_starred")
    val isStarred: Boolean = false,

    @ColumnInfo(name = "status")
    val status: String = "PROCESSING", // PROCESSING, UNREAD, REVIEWED, TRIED, ARCHIVED, FAILED

    @ColumnInfo(name = "processing_state")
    val processingState: String = "QUEUED", // QUEUED, DOWNLOADING, ANALYZING, COMPLETED, FAILED

    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    // Processing telemetry (Room v4)
    @ColumnInfo(name = "yt_dlp_duration_ms")
    val ytDlpDurationMs: Long? = null,

    @ColumnInfo(name = "frame_extract_duration_ms")
    val frameExtractDurationMs: Long? = null,

    @ColumnInfo(name = "ai_analysis_duration_ms")
    val aiAnalysisDurationMs: Long? = null,

    @ColumnInfo(name = "total_processing_duration_ms")
    val totalProcessingDurationMs: Long? = null,

    @ColumnInfo(name = "video_duration_sec")
    val videoDurationSec: Int? = null,

    @ColumnInfo(name = "downloaded_video_bytes")
    val downloadedVideoBytes: Long? = null,

    @ColumnInfo(name = "cover_size_bytes")
    val coverSizeBytes: Long? = null,

    @ColumnInfo(name = "frames_extracted_count")
    val framesExtractedCount: Int? = null,

    @ColumnInfo(name = "links_extracted_count")
    val linksExtractedCount: Int? = null,

    @ColumnInfo(name = "ai_model_used")
    val aiModelUsed: String? = null,

    @ColumnInfo(name = "processing_error")
    val processingError: String? = null,

    @ColumnInfo(name = "processing_progress")
    val processingProgress: Int = 0,

    @ColumnInfo(name = "processing_detail")
    val processingDetail: String? = null
)
