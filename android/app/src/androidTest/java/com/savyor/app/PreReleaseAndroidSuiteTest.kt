package com.savyor.app

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.savyor.app.data.local.EncryptedSettingsManager
import com.savyor.app.data.local.FtsQuerySanitizer
import com.savyor.app.data.local.SavyorDatabase
import com.savyor.app.data.local.entity.ReelEntity
import com.savyor.app.data.remote.*
import com.savyor.app.domain.model.GeminiReelAnalysis
import com.savyor.app.domain.model.StructuredContent
import com.savyor.app.ui.screens.library.SortOrder
import com.savyor.app.ui.screens.library.ViewMode
import com.savyor.app.work.PipelineDependencies
import com.savyor.app.work.ProcessReelWorker
import com.savyor.app.work.WorkManagerScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PreReleaseAndroidSuiteTest {

    private lateinit var context: Context
    private lateinit var testDb: SavyorDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        testDb = Room.inMemoryDatabaseBuilder(context, SavyorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        PipelineDependencies.reset()
    }

    @After
    fun tearDown() {
        testDb.close()
        PipelineDependencies.reset()
    }

    @Test
    fun testMigration1To2To3PreservesReelsUserNoteStarredAndVisualMemory() {
        context.deleteDatabase("test_mig_1_3.db")
        val helperFactory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("test_mig_1_3.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `reels` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `shortcode` TEXT NOT NULL,
                            `original_url` TEXT NOT NULL,
                            `clean_url` TEXT NOT NULL,
                            `creator` TEXT NOT NULL,
                            `source_language` TEXT NOT NULL,
                            `title` TEXT NOT NULL,
                            `summary` TEXT NOT NULL,
                            `category` TEXT NOT NULL,
                            `tags` TEXT NOT NULL,
                            `transcript` TEXT NOT NULL,
                            `original_transcript` TEXT,
                            `key_takeaways` TEXT NOT NULL,
                            `key_moments` TEXT NOT NULL,
                            `structured_content` TEXT NOT NULL,
                            `extracted_resources` TEXT NOT NULL,
                            `caveats` TEXT NOT NULL,
                            `use_when` TEXT NOT NULL,
                            `thumbnail_path` TEXT,
                            `status` TEXT NOT NULL,
                            `processing_state` TEXT NOT NULL,
                            `error_message` TEXT,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL
                        )
                    """.trimIndent())
                    db.execSQL("CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `is_default` INTEGER NOT NULL, `display_order` INTEGER NOT NULL)")
                    db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `reels_fts` USING FTS4(`title` TEXT NOT NULL, `summary` TEXT NOT NULL, `transcript` TEXT NOT NULL, `original_transcript` TEXT, `tags` TEXT NOT NULL, `creator` TEXT NOT NULL, `structured_content` TEXT NOT NULL, content=`reels`)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val db = helperFactory.create(config).writableDatabase
        db.execSQL("""
            INSERT INTO `reels` (
                `shortcode`, `original_url`, `clean_url`, `creator`, `source_language`,
                `title`, `summary`, `category`, `tags`, `transcript`, `key_takeaways`,
                `key_moments`, `structured_content`, `extracted_resources`, `caveats`,
                `use_when`, `status`, `processing_state`, `created_at`, `updated_at`
            ) VALUES (
                'MIG13', 'https://instagram.com/reel/MIG13/', 'https://instagram.com/reel/MIG13/', 'dev', 'English',
                'Architecture Guide', 'Clean summary', 'Tech & Code', '[]', 'transcript', '[]',
                '[]', '{}', '[]', '[]', 'Designing clean apps', 'UNREAD', 'COMPLETED', 1000, 1000
            )
        """.trimIndent())

        // Run v1 -> v2 -> v3 chain
        SavyorDatabase.MIGRATION_1_2.migrate(db)
        db.execSQL("UPDATE reels SET user_note = 'Keep this personal note' WHERE shortcode = 'MIG13'")
        SavyorDatabase.MIGRATION_2_3.migrate(db)

        val cursor = db.query("SELECT title, user_note, visual_memory, is_starred FROM reels WHERE shortcode = 'MIG13'")
        assertTrue(cursor.moveToFirst())
        assertEquals("Architecture Guide", cursor.getString(0))
        assertEquals("Keep this personal note", cursor.getString(1))
        assertEquals("", cursor.getString(2))
        assertEquals(0, cursor.getInt(3))
        cursor.close()

        // Update visual_memory and verify FTS4 matches it
        db.execSQL("UPDATE reels SET visual_memory = 'Man in navy hoodie sitting in a bedroom with One Piece posters on the wall' WHERE shortcode = 'MIG13'")
        db.execSQL("DELETE FROM reels_fts")
        db.execSQL("""
            INSERT INTO reels_fts (docid, title, summary, transcript, original_transcript, tags, creator, structured_content, user_note, visual_memory)
            SELECT id, title, summary, transcript, original_transcript, tags, creator, structured_content, user_note, visual_memory FROM reels
        """.trimIndent())

        val ftsHoodie = db.query("SELECT docid FROM reels_fts WHERE reels_fts MATCH '\"navy\"* \"hoodie\"*'")
        assertTrue("FTS must match visual_memory cues", ftsHoodie.moveToFirst())
        ftsHoodie.close()

        val ftsPoster = db.query("SELECT docid FROM reels_fts WHERE reels_fts MATCH '\"One\"* \"Piece\"*'")
        assertTrue("FTS must match visual_memory poster cue", ftsPoster.moveToFirst())
        ftsPoster.close()

        db.close()
        context.deleteDatabase("test_mig_1_3.db")
    }

    @Test
    fun testFts5SearchMatchesVisualMemoryAndStarredFiltering(): Unit = runBlocking {
        val dao = testDb.reelDao()
        dao.insert(
            ReelEntity(
                shortcode = "VIS1",
                originalUrl = "https://www.instagram.com/reel/VIS1/",
                cleanUrl = "https://www.instagram.com/reel/VIS1/",
                title = "B-Reel Motion Tutorial",
                summary = "Animation keyframes guide.",
                visualMemory = "Man in navy hoodie sitting in a bedroom with One Piece posters on the wall, showing a white canvas animation",
                isStarred = true,
                createdAt = 2000L,
                processingState = "COMPLETED"
            )
        )
        dao.insert(
            ReelEntity(
                shortcode = "VIS2",
                originalUrl = "https://www.instagram.com/reel/VIS2/",
                cleanUrl = "https://www.instagram.com/reel/VIS2/",
                title = "A-Sourdough Bread",
                summary = "Baking guide.",
                visualMemory = "Rustic wooden kitchen counter with cast iron Dutch oven",
                isStarred = false,
                createdAt = 1000L,
                processingState = "COMPLETED"
            )
        )

        val hoodieQuery = FtsQuerySanitizer.sanitizeForFts5("navy hoodie")!!
        val hoodieResults = dao.searchReelsFts(hoodieQuery).first()
        assertEquals(1, hoodieResults.size)
        assertEquals("VIS1", hoodieResults.first().shortcode)

        val onePieceQuery = FtsQuerySanitizer.sanitizeForFts5("One Piece")!!
        val onePieceResults = dao.searchReelsFts(onePieceQuery).first()
        assertEquals(1, onePieceResults.size)
        assertEquals("VIS1", onePieceResults.first().shortcode)

        // Toggle star on VIS2
        dao.updateStarred("VIS2", true)
        assertTrue(dao.getReelByShortcode("VIS2")!!.isStarred)
    }

    @Test
    fun testLayoutAndSortPersistence(): Unit = runBlocking {
        val settings = EncryptedSettingsManager(context)
        settings.libraryViewMode = ViewMode.COMPACT.name
        settings.librarySortOrder = SortOrder.TITLE_AZ.name

        val reloaded = EncryptedSettingsManager(context)
        assertEquals(ViewMode.COMPACT, ViewMode.fromString(reloaded.libraryViewMode))
        assertEquals(SortOrder.TITLE_AZ, SortOrder.fromString(reloaded.librarySortOrder))

        // Restore defaults
        settings.libraryViewMode = ViewMode.GRID.name
        settings.librarySortOrder = SortOrder.NEWEST.name
    }

    @Test
    fun testReanalyzePreservesUserNoteAndStarredState(): Unit = runBlocking {
        val appDb = SavyorDatabase.getInstance(context)
        val sc = "REANALYZE_1"
        appDb.reelDao().deleteByShortcode(sc)
        appDb.reelDao().insert(
            ReelEntity(
                shortcode = sc,
                originalUrl = "https://www.instagram.com/reel/$sc/",
                cleanUrl = "https://www.instagram.com/reel/$sc/",
                title = "Old AI Title",
                summary = "Old AI Summary",
                userNote = "My preserved personal note!",
                isStarred = true,
                processingState = "COMPLETED"
            )
        )

        // Force re-analyze via WorkManagerScheduler
        WorkManagerScheduler(context).enqueueReel("https://www.instagram.com/reel/$sc/", forceReanalyze = true)
        val queued = appDb.reelDao().getReelByShortcode(sc)
        assertEquals("QUEUED", queued?.processingState)

        val tempVideo = File(context.cacheDir, "reels_temp/$sc.mp4").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(128))
        }

        PipelineDependencies.downloaderOverride = object : IReelDownloader {
            override suspend fun downloadReel(url: String, shortcode: String) =
                ReelDownloadResult(tempVideo, "creator_new", "caption", 1000L, 128L)
            override suspend fun updateEngine() = Pair("DONE", "2026.11.25")
            override suspend fun getEngineVersion() = "2026.11.25"
        }

        PipelineDependencies.geminiClientOverride = object : IGeminiClient {
            override suspend fun analyzeReel(apiKey: String, videoFile: File, prompt: String) =
                GeminiReelAnalysis(
                    title = "Updated AI Title",
                    category = "Tech & Code",
                    summary = "Updated AI Summary",
                    useWhen = "Testing re-analysis",
                    visualMemory = "Updated studio desk with warm lamp",
                    structuredContent = StructuredContent(noteType = "general")
                )
            override suspend fun testConnection(apiKey: String) = Result.success("OK")
        }

        PipelineDependencies.frameExtractorOverride = object : IFrameExtractor {
            override suspend fun extractFrames(videoFile: File, shortcode: String, keyMoments: List<com.savyor.app.domain.model.KeyMomentAnalysis>) =
                ExtractedFramesResult(null, emptyList())
        }

        val worker = TestListenableWorkerBuilder<ProcessReelWorker>(context)
            .setInputData(workDataOf(ProcessReelWorker.KEY_SHORTCODE to sc))
            .build()

        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)

        val updated = appDb.reelDao().getReelByShortcode(sc)!!
        assertEquals("Updated AI Title", updated.title)
        assertEquals("Updated studio desk with warm lamp", updated.visualMemory)
        assertEquals("My preserved personal note!", updated.userNote)
        assertTrue("isStarred MUST be preserved after re-analyze", updated.isStarred)

        appDb.reelDao().deleteByShortcode(sc)
    }
}
