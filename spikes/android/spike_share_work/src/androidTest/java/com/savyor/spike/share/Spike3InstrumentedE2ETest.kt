package com.savyor.spike.share

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.*
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class Spike3InstrumentedE2ETest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @Test
    fun testMessyInstagramUrlNormalization() {
        val messyText = "Check this reel! https://www.instagram.com/reel/Df-OIaqgGZy/?igsh=MzRlODBiNWFlZA== mind blown!"
        val result = InstagramUrlCleaner.clean(messyText)

        assertTrue("Messy Instagram text should be valid", result.isValid)
        assertEquals("Df-OIaqgGZy", result.shortcode)
        assertEquals("https://www.instagram.com/reel/Df-OIaqgGZy/", result.cleanUrl)
    }

    @Test
    fun testNonInstagramUrlRejection() {
        val nonIgText = "Read this article: https://example.com/blog/article?id=456"
        val result = InstagramUrlCleaner.clean(nonIgText)

        assertFalse("Non-Instagram text should be rejected", result.isValid)
        assertNull("Clean URL should be null for rejected text", result.cleanUrl)
        assertNotNull("Rejection reason should be populated", result.rejectionReason)
    }

    @Test
    fun testWorkManagerJobExecutionWithMockPipeline() = kotlinx.coroutines.runBlocking {
        val worker = androidx.work.testing.TestListenableWorkerBuilder<ProcessReelWorker>(context)
            .setInputData(
                workDataOf(
                    "reel_url" to "https://www.instagram.com/reel/Df-OIaqgGZy/",
                    "shortcode" to "Df-OIaqgGZy",
                    "is_mock" to true
                )
            )
            .build()

        val result = worker.doWork()
        assertTrue("Worker should succeed in mock test pipeline", result is ListenableWorker.Result.Success)
        val output = (result as ListenableWorker.Result.Success).outputData
        assertEquals("processed", output.getString("status"))
        assertEquals("mock_code", output.getString("shortcode"))
    }
}
