package com.savyor.app.data.local

import androidx.room.*
import com.savyor.app.data.local.entity.ReelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReelDao {

    @Query("SELECT * FROM reels ORDER BY created_at DESC")
    fun getAllReels(): Flow<List<ReelEntity>>

    @Query("SELECT * FROM reels WHERE category = :category ORDER BY created_at DESC")
    fun getReelsByCategory(category: String): Flow<List<ReelEntity>>

    @Query("SELECT * FROM reels WHERE status = :status ORDER BY created_at DESC")
    fun getReelsByStatus(status: String): Flow<List<ReelEntity>>

    @Query("SELECT * FROM reels WHERE category = :category AND status = :status ORDER BY created_at DESC")
    fun getReelsByCategoryAndStatus(category: String, status: String): Flow<List<ReelEntity>>

    @Query("SELECT * FROM reels WHERE processing_state IN ('QUEUED', 'DOWNLOADING', 'UPLOADING', 'ANALYZING', 'FRAMES') ORDER BY created_at ASC")
    fun getActiveProcessingReels(): Flow<List<ReelEntity>>

    @Query("SELECT * FROM reels WHERE processing_state IN ('QUEUED', 'DOWNLOADING', 'UPLOADING', 'ANALYZING', 'FRAMES')")
    suspend fun getActiveProcessingReelsOnce(): List<ReelEntity>

    @Query("SELECT * FROM reels ORDER BY created_at DESC")
    suspend fun getAllReelsOnce(): List<ReelEntity>

    @Query("SELECT * FROM reels WHERE shortcode = :shortcode LIMIT 1")
    suspend fun getReelByShortcode(shortcode: String): ReelEntity?

    @Query("SELECT * FROM reels WHERE shortcode = :shortcode LIMIT 1")
    fun getReelByShortcodeFlow(shortcode: String): Flow<ReelEntity?>

    @Query(
        """
        SELECT reels.* FROM reels
        JOIN reels_fts ON reels.id = reels_fts.docid
        WHERE reels_fts MATCH :query
        ORDER BY reels.created_at DESC
        """
    )
    fun searchReelsFts(query: String): Flow<List<ReelEntity>>

    @Query(
        """
        SELECT * FROM reels
        WHERE title LIKE '%' || :query || '%'
           OR summary LIKE '%' || :query || '%'
           OR transcript LIKE '%' || :query || '%'
           OR original_transcript LIKE '%' || :query || '%'
           OR creator LIKE '%' || :query || '%'
           OR tags LIKE '%' || :query || '%'
           OR structured_content LIKE '%' || :query || '%'
           OR user_note LIKE '%' || :query || '%'
           OR visual_memory LIKE '%' || :query || '%'
        ORDER BY created_at DESC
        """
    )
    fun searchReelsFallback(query: String): Flow<List<ReelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reel: ReelEntity): Long

    @Update
    suspend fun update(reel: ReelEntity)

    @Query("UPDATE reels SET status = :status, updated_at = :updatedAt WHERE shortcode = :shortcode")
    suspend fun updateStatus(shortcode: String, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE reels SET category = :category, updated_at = :updatedAt WHERE shortcode = :shortcode")
    suspend fun updateCategory(shortcode: String, category: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE reels SET user_note = :userNote, updated_at = :updatedAt WHERE shortcode = :shortcode")
    suspend fun updateUserNote(shortcode: String, userNote: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE reels SET is_starred = :isStarred, updated_at = :updatedAt WHERE shortcode = :shortcode")
    suspend fun updateStarred(shortcode: String, isStarred: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE reels SET category = :toCategory, updated_at = :updatedAt WHERE category = :fromCategory")
    suspend fun reassignCategory(fromCategory: String, toCategory: String, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM reels WHERE category = :category")
    suspend fun countByCategory(category: String): Int

    @Query("UPDATE reels SET processing_state = :state, error_message = :errorMessage, updated_at = :updatedAt WHERE shortcode = :shortcode")
    suspend fun updateProcessingState(
        shortcode: String,
        state: String,
        errorMessage: String? = null,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE reels SET processing_state = :state, status = :status, error_message = :errorMessage, processing_error = :errorMessage, processing_progress = :progress, processing_detail = :detail, updated_at = :updatedAt WHERE shortcode = :shortcode")
    suspend fun updateProcessingAndStatus(
        shortcode: String,
        state: String,
        status: String,
        errorMessage: String? = null,
        progress: Int = 0,
        detail: String? = null,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE reels SET processing_state = :state, processing_progress = :progress, processing_detail = :detail, updated_at = :updatedAt WHERE shortcode = :shortcode")
    suspend fun updateProcessingProgress(
        shortcode: String,
        state: String,
        progress: Int,
        detail: String? = null,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE reels SET title = '' WHERE shortcode = :shortcode AND title LIKE 'Processing reel @%'")
    suspend fun clearLegacyPlaceholderTitle(shortcode: String)

    @Query("DELETE FROM reels WHERE shortcode = :shortcode")
    suspend fun deleteByShortcode(shortcode: String)

    @Delete
    suspend fun delete(reel: ReelEntity)

    @Query("SELECT COUNT(*) FROM reels")
    suspend fun countAll(): Int
}
