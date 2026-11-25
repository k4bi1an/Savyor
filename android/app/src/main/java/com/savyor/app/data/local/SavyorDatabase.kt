package com.savyor.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.savyor.app.data.local.entity.CategoryCorrectionEntity
import com.savyor.app.data.local.entity.CategoryEntity
import com.savyor.app.data.local.entity.ReelEntity
import com.savyor.app.data.local.entity.ReelFtsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ReelEntity::class,
        ReelFtsEntity::class,
        CategoryEntity::class,
        CategoryCorrectionEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class SavyorDatabase : RoomDatabase() {

    abstract fun reelDao(): ReelDao
    abstract fun categoryDao(): CategoryDao
    abstract fun categoryCorrectionDao(): CategoryCorrectionDao

    companion object {
        @Volatile
        private var INSTANCE: SavyorDatabase? = null

        val DEFAULT_CATEGORIES = listOf(
            CategoryEntity(name = "Tech & Code", isDefault = true, displayOrder = 1, colorHex = "#4A607A"),
            CategoryEntity(name = "Recipes & Cooking", isDefault = true, displayOrder = 2, colorHex = "#B35446"),
            CategoryEntity(name = "Health & Fitness", isDefault = true, displayOrder = 3, colorHex = "#2F5D50"),
            CategoryEntity(name = "Design & UI", isDefault = true, displayOrder = 4, colorHex = "#74B9A2"),
            CategoryEntity(name = "Other", isDefault = true, displayOrder = 5, colorHex = "#6E6758")
        )

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add user_note column to reels table
                db.execSQL("ALTER TABLE `reels` ADD COLUMN `user_note` TEXT NOT NULL DEFAULT ''")

                // 2. Add color_hex column to categories table
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `color_hex` TEXT NOT NULL DEFAULT '#2F5D50'")

                // 3. Drop existing FTS table and recreate with user_note included
                db.execSQL("DROP TABLE IF EXISTS `reels_fts`")
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `reels_fts` USING FTS4(`title` TEXT NOT NULL, `summary` TEXT NOT NULL, `transcript` TEXT NOT NULL, `original_transcript` TEXT, `tags` TEXT NOT NULL, `creator` TEXT NOT NULL, `structured_content` TEXT NOT NULL, `user_note` TEXT NOT NULL, content=`reels`)")

                // 4. Populate reels_fts from existing reels
                db.execSQL("""
                    INSERT INTO `reels_fts` (`docid`, `title`, `summary`, `transcript`, `original_transcript`, `tags`, `creator`, `structured_content`, `user_note`)
                    SELECT `id`, `title`, `summary`, `transcript`, `original_transcript`, `tags`, `creator`, `structured_content`, `user_note` FROM `reels`
                """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add visual_memory and is_starred columns to reels
                db.execSQL("ALTER TABLE `reels` ADD COLUMN `visual_memory` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `reels` ADD COLUMN `is_starred` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reels_is_starred` ON `reels` (`is_starred`)")

                // 2. Recreate reels_fts with visual_memory included
                db.execSQL("DROP TABLE IF EXISTS `reels_fts`")
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `reels_fts` USING FTS4(`title` TEXT NOT NULL, `summary` TEXT NOT NULL, `transcript` TEXT NOT NULL, `original_transcript` TEXT, `tags` TEXT NOT NULL, `creator` TEXT NOT NULL, `structured_content` TEXT NOT NULL, `user_note` TEXT NOT NULL, `visual_memory` TEXT NOT NULL, content=`reels`)")

                // 3. Repopulate reels_fts from existing reels
                db.execSQL("""
                    INSERT INTO `reels_fts` (`docid`, `title`, `summary`, `transcript`, `original_transcript`, `tags`, `creator`, `structured_content`, `user_note`, `visual_memory`)
                    SELECT `id`, `title`, `summary`, `transcript`, `original_transcript`, `tags`, `creator`, `structured_content`, `user_note`, `visual_memory` FROM `reels`
                """.trimIndent())
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `reels` ADD COLUMN `yt_dlp_duration_ms` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `reels` ADD COLUMN `frame_extract_duration_ms` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `reels` ADD COLUMN `ai_analysis_duration_ms` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `reels` ADD COLUMN `total_processing_duration_ms` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `reels` ADD COLUMN `video_duration_sec` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `reels` ADD COLUMN `downloaded_video_bytes` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `reels` ADD COLUMN `cover_size_bytes` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `reels` ADD COLUMN `frames_extracted_count` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `reels` ADD COLUMN `links_extracted_count` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `reels` ADD COLUMN `ai_model_used` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `reels` ADD COLUMN `processing_error` TEXT DEFAULT NULL")
            }
        }

        fun getInstance(context: Context): SavyorDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SavyorDatabase::class.java,
                    "savyor.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                getInstance(context).categoryDao().insertAll(DEFAULT_CATEGORIES)
                            }
                        }
                    })
                    // Note: Never use fallbackToDestructiveMigration() so existing saved reels are preserved
                    .build()
                INSTANCE = instance
                instance
            }
        }

        @Synchronized
        fun closeAndResetInstance() {
            INSTANCE?.let {
                if (it.isOpen) {
                    it.close()
                }
            }
            INSTANCE = null
        }
    }
}
