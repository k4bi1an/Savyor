package com.savyor.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.google.gson.Gson
import com.savyor.app.data.local.FtsQuerySanitizer
import com.savyor.app.data.local.SavyorDatabase
import com.savyor.app.data.local.entity.ReelEntity
import com.savyor.app.data.remote.*
import com.savyor.app.domain.model.GeminiReelAnalysis
import com.savyor.app.ui.screens.library.EmptyLibraryView
import com.savyor.app.ui.theme.SavyorTheme
import com.savyor.app.work.EnqueueResult
import com.savyor.app.work.PipelineDependencies
import com.savyor.app.work.ProcessReelWorker
import com.savyor.app.work.WorkManagerScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class HostileAndroidSuiteTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var inMemoryDb: SavyorDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        inMemoryDb = Room.inMemoryDatabaseBuilder(context, SavyorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        PipelineDependencies.reset()
    }

    @After
    fun tearDown() {
        inMemoryDb.close()
        PipelineDependencies.reset()
    }

    @Test
    fun testHostileFts5QueriesNeverThrowSqliteException() = runBlocking {
        val dao = inMemoryDb.reelDao()
        dao.insert(
            ReelEntity(
                shortcode = "HOSTILE1",
                originalUrl = "https://www.instagram.com/reel/HOSTILE1/",
                cleanUrl = "https://www.instagram.com/reel/HOSTILE1/",
                title = "C++ Guide by O'Reilly: 50% Faster foo-bar",
                summary = "கறி தோசை மற்றும் हाइड्रा लॉन्चर",
                processingState = "COMPLETED"
            )
        )

        val hostileQueries = listOf(
            "C++",
            "O'Reilly",
            "foo-bar",
            "50%",
            "\"unclosed quote",
            "***",
            "",
            "   ",
            "a".repeat(500),
            "கறி தோசை",
            "हाइड्रा लॉन्चर"
        )

        for (raw in hostileQueries) {
            val sanitized = FtsQuerySanitizer.sanitizeForFts5(raw)
            if (sanitized != null) {
                // Must never throw SQLiteException
                val results = dao.searchReelsFts(sanitized).first()
                assertNotNull(results)
            } else if (raw.isNotBlank()) {
                val fallback = dao.searchReelsFallback(raw.trim()).first()
                assertNotNull(fallback)
            }
        }
    }

    @Test
    fun testGeminiFailureAndTempMp4CleanupRecovery() = runBlocking {
        val appDb = SavyorDatabase.getInstance(context)
        val sc = "FAIL_TEST_1"
        appDb.reelDao().deleteByShortcode(sc)
        appDb.reelDao().insert(
            ReelEntity(
                shortcode = sc,
                originalUrl = "https://www.instagram.com/reel/$sc/",
                cleanUrl = "https://www.instagram.com/reel/$sc/",
                processingState = "QUEUED"
            )
        )

        val tempVideo = File(context.cacheDir, "reels_temp/$sc.mp4").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(256))
        }
        assertTrue(tempVideo.exists())

        PipelineDependencies.downloaderOverride = object : IReelDownloader {
            override suspend fun downloadReel(url: String, shortcode: String) =
                ReelDownloadResult(tempVideo, "tester", "caption", 5000L, 256L)
            override suspend fun updateEngine() = Pair("DONE", "2026.11.23")
            override suspend fun getEngineVersion() = "2026.11.23"
        }

        // Simulate HTTP 429 / 503 retry exhaustion
        PipelineDependencies.geminiClientOverride = object : IGeminiClient {
            override suspend fun analyzeReel(apiKey: String, videoFile: File, prompt: String): GeminiReelAnalysis {
                throw GeminiApiException(429, "HTTP 429 Too Many Requests after retries")
            }
            override suspend fun testConnection(apiKey: String) = Result.failure<String>(Exception("429"))
        }

        val worker = TestListenableWorkerBuilder<ProcessReelWorker>(context)
            .setInputData(workDataOf(ProcessReelWorker.KEY_SHORTCODE to sc))
            .build()

        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.failure(), result)

        val failedEntity = appDb.reelDao().getReelByShortcode(sc)
        assertNotNull(failedEntity)
        assertEquals("FAILED", failedEntity?.processingState)
        assertTrue(failedEntity?.errorMessage?.contains("429") == true)
        assertFalse("Temp .mp4 must be deleted even on Gemini failure!", tempVideo.exists())

        appDb.reelDao().deleteByShortcode(sc)
    }

    @Test
    fun testShareAndQueueStressTests() = runBlocking {
        val scheduler = WorkManagerScheduler(context)
        val appDb = SavyorDatabase.getInstance(context)
        val sc = "STRESS_REEL_1"
        appDb.reelDao().deleteByShortcode(sc)

        // 1. Non-Instagram URL
        val nonIg = scheduler.enqueueReel("https://youtube.com/watch?v=12345")
        assertTrue(nonIg is EnqueueResult.InvalidUrl)

        // 2. Messy text with multiple URLs
        val messy = "Check this out! https://www.instagram.com/reel/$sc/?igsh=abc and https://example.com"
        val res1 = scheduler.enqueueReel(messy)
        assertTrue(res1 is EnqueueResult.Success)
        assertTrue((res1 as EnqueueResult.Success).isNew)

        // 3. Duplicate URL
        val resDup = scheduler.enqueueReel("https://www.instagram.com/reel/$sc/")
        assertTrue(resDup is EnqueueResult.Success)
        assertFalse((resDup as EnqueueResult.Success).isNew)

        appDb.reelDao().deleteByShortcode(sc)
    }

    @Test
    fun testEmptyLibraryViewRendersCleanly() {
        composeRule.setContent {
            SavyorTheme {
                EmptyLibraryView(onAddClick = {})
            }
        }
        composeRule.onNodeWithText("Your library is calm").assertIsDisplayed()
        composeRule.onNodeWithText("Add Reel").assertIsDisplayed()
    }
}
