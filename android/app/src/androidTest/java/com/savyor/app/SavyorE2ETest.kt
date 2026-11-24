package com.savyor.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.savyor.app.data.local.SavyorDatabase
import com.savyor.app.data.local.entity.CategoryCorrectionEntity
import com.savyor.app.data.local.entity.ReelEntity
import com.savyor.app.data.remote.*
import com.savyor.app.domain.model.*
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
class SavyorE2ETest {

    private lateinit var context: Context
    private lateinit var db: SavyorDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = SavyorDatabase.getInstance(context)
        PipelineDependencies.reset()
    }

    @After
    fun tearDown() {
        PipelineDependencies.reset()
    }

    @Test
    fun testCompletePipelineE2EWithMultilingualFtsSearchAndCleanup() = runBlocking {
        val testShortcode = "DdKXX_mTXLx"
        val testUrl = "https://www.instagram.com/reel/$testShortcode/?fqnt=NTc4MTIwNjQ2YQ=="

        // Clean up prior test data if any
        db.reelDao().deleteByShortcode(testShortcode)

        // 1. Setup deterministic mocks
        val tempVideoFile = File(context.cacheDir, "reels_temp/$testShortcode.mp4").apply {
            parentFile?.mkdirs()
            writeBytes("mock video stream content".toByteArray())
        }
        assertTrue("Mock video file should exist before processing", tempVideoFile.exists())

        PipelineDependencies.downloaderOverride = object : IReelDownloader {
            override suspend fun downloadReel(url: String, shortcode: String): ReelDownloadResult {
                return ReelDownloadResult(
                    videoFile = tempVideoFile,
                    creator = "madurai_foodie",
                    caption = "Traditional Kari Dosa making video in Madurai",
                    durationMs = 12000L,
                    fileSizeBytes = tempVideoFile.length()
                )
            }
            override suspend fun updateEngine(): Pair<String, String> = Pair("DONE", "2026.11.23")
            override suspend fun getEngineVersion(): String = "2026.11.23"
        }

        val mockAnalysis = GeminiReelAnalysis(
            title = "Authentic Madurai Kari Dosa (Spicy Mutton Crepe)",
            category = "Recipes & Cooking",
            tags = listOf("madurai", "kari dosa", "street food", "mutton"),
            summary = "Direct recipe method for crispy multi-layered Madurai mutton kari dosa cooked with egg and shallot masala.",
            useWhen = "Use when preparing a hearty weekend brunch.",
            sourceLanguage = "Tamil",
            transcript = "[0:00] First prepare the mutton chuka with pepper and sombu. [0:10] Spread thick batter on iron tawa.",
            originalTranscript = "[0:00] Mudhalla nalla milagu sombu sethu kari chuka senjidunga. [0:10] Kal la ketti maavu oothunga.",
            keyTakeaways = listOf("Cook on medium flame", "Layer egg before mutton"),
            caveats = listOf("Stovetop tawa only, no oven"),
            keyMoments = listOf(
                KeyMomentAnalysis(timestampSec = 4, label = "Mutton keema texture"),
                KeyMomentAnalysis(timestampSec = 10, label = "Layering egg and meat")
            ),
            extractedResources = listOf(
                ExtractedResource(name = "Simmakkal Spice Blend", url = "", description = "Fennel and pepper powder")
            ),
            structuredContent = StructuredContent(
                noteType = "recipe",
                ingredients = listOf(
                    RecipeIngredient(item = "Mutton keema", quantity = "150g"),
                    RecipeIngredient(item = "Eggs", quantity = "2")
                ),
                steps = listOf(
                    RecipeStep(stepNumber = 1, instruction = "Pour thick batter on cast iron tawa"),
                    RecipeStep(stepNumber = 2, instruction = "Layer egg and meat, cook with ghee")
                )
            )
        )

        PipelineDependencies.geminiClientOverride = object : IGeminiClient {
            override suspend fun analyzeReel(apiKey: String, videoFile: File, prompt: String): GeminiReelAnalysis {
                return mockAnalysis
            }
            override suspend fun testConnection(apiKey: String): Result<String> {
                return Result.success("Mock connection OK")
            }
        }

        PipelineDependencies.frameExtractorOverride = object : IFrameExtractor {
            override suspend fun extractFrames(
                videoFile: File,
                shortcode: String,
                keyMoments: List<KeyMomentAnalysis>
            ): ExtractedFramesResult {
                val dir = File(context.filesDir, "snapshots/$shortcode").apply { mkdirs() }
                val thumbFile = File(dir, "thumb.webp").apply { writeBytes(ByteArray(100)) }
                val momentFiles = keyMoments.mapIndexed { i, m ->
                    val f = File(dir, "moment_$i.webp").apply { writeBytes(ByteArray(100)) }
                    KeyMomentWithImage(m.timestampSec, m.label, f.absolutePath)
                }
                return ExtractedFramesResult(
                    thumbnailPath = thumbFile.absolutePath,
                    keyMomentsWithImages = momentFiles
                )
            }
        }

        // 2. Enqueue via WorkManagerScheduler
        val scheduler = WorkManagerScheduler(context)
        val enqueueRes = scheduler.enqueueReel(testUrl)
        assertTrue("Enqueue should succeed", enqueueRes is com.savyor.app.work.EnqueueResult.Success)
        val shortcode = (enqueueRes as com.savyor.app.work.EnqueueResult.Success).shortcode
        assertEquals(testShortcode, shortcode)

        val queuedReel = db.reelDao().getReelByShortcode(shortcode)
        assertNotNull("Reel must exist in DB", queuedReel)
        assertEquals("QUEUED", queuedReel?.processingState)

        // 3. Execute Worker via TestListenableWorkerBuilder
        val worker = TestListenableWorkerBuilder<ProcessReelWorker>(context)
            .setInputData(workDataOf(ProcessReelWorker.KEY_SHORTCODE to shortcode))
            .build()

        val workerResult = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), workerResult)

        // 4. Verify Database State
        val processedReel = db.reelDao().getReelByShortcode(shortcode)
        assertNotNull("Processed reel must exist", processedReel)
        assertEquals("COMPLETED", processedReel?.processingState)
        assertEquals("Authentic Madurai Kari Dosa (Spicy Mutton Crepe)", processedReel?.title)
        assertEquals("Tamil", processedReel?.sourceLanguage)
        assertNotNull("Original transcript must be stored", processedReel?.originalTranscript)
        assertTrue("Thumbnail path must be set", !processedReel?.thumbnailPath.isNullOrBlank())

        // 5. Verify FTS5 Search (Both English and Original Language)
        // Search by English term
        val englishSearch = db.reelDao().searchReelsFallback("Madurai").first()
        assertTrue("English search for 'Madurai' should match", englishSearch.any { it.shortcode == shortcode })

        // Search by original language term from originalTranscript
        val tamilSearch = db.reelDao().searchReelsFallback("chuka").first()
        assertTrue("Original language search for 'chuka' should match", tamilSearch.any { it.shortcode == shortcode })

        // Search by creator
        val creatorSearch = db.reelDao().searchReelsFallback("madurai_foodie").first()
        assertTrue("Search by creator should match", creatorSearch.any { it.shortcode == shortcode })

        // 6. Verify Temporary Video File Cleanup (Permanent Video Storage = 0 MB)
        assertFalse("Temporary video file must be deleted after analysis", tempVideoFile.exists())

        // 7. Test Category Correction Learning
        val correctionDao = db.categoryCorrectionDao()
        val prevCorrectionsCount = correctionDao.getRecentCorrections(10).size

        // Change category from "Recipes & Cooking" to "Design & UI"
        correctionDao.insertCorrection(
            CategoryCorrectionEntity(
                reelShortcode = shortcode,
                title = processedReel!!.title,
                fromCategory = processedReel.category,
                toCategory = "Design & UI"
            )
        )
        db.reelDao().updateCategory(shortcode, "Design & UI")

        val newCorrections = correctionDao.getRecentCorrections(5)
        assertEquals(prevCorrectionsCount + 1, newCorrections.size)
        assertEquals("Design & UI", newCorrections.first().toCategory)
        assertEquals("Recipes & Cooking", newCorrections.first().fromCategory)

        // 8. Test Status Update & Delete
        db.reelDao().updateStatus(shortcode, "TRIED")
        val updatedStatusReel = db.reelDao().getReelByShortcode(shortcode)
        assertEquals("TRIED", updatedStatusReel?.status)

        // Delete reel and snapshot directory
        val snapshotsDir = File(context.filesDir, "snapshots/$shortcode")
        snapshotsDir.deleteRecursively()
        db.reelDao().deleteByShortcode(shortcode)

        val deletedReel = db.reelDao().getReelByShortcode(shortcode)
        assertNull("Reel should be null after deletion", deletedReel)
        assertFalse("Snapshots dir should be deleted", snapshotsDir.exists())
    }
}
