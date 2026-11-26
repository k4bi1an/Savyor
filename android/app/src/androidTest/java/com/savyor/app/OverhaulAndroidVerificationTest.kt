package com.savyor.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.savyor.app.data.local.SavyorDatabase
import com.savyor.app.data.remote.ImageCompressor
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class OverhaulAndroidVerificationTest {

    @Test
    fun webpImageCompressorDownscalesAndStaysUnder80Kb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(18, 22, 28))
        val paint = Paint().apply {
            color = Color.rgb(96, 165, 250)
            strokeWidth = 12f
        }
        for (i in 0..20) {
            canvas.drawLine(0f, i * 90f, 1080f, i * 90f + 40f, paint)
        }

        val coverFile = File(context.cacheDir, "test_cover_540.webp")
        val momentFile = File(context.cacheDir, "test_moment_720.webp")
        try {
            val coverBytes = ImageCompressor.saveOptimizedWebp(bitmap, coverFile, maxWidthPx = 540, quality = 78)
            val momentBytes = ImageCompressor.saveOptimizedWebp(bitmap, momentFile, maxWidthPx = 720, quality = 80)

            val coverOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(coverFile.absolutePath, coverOpts)
            val momentOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(momentFile.absolutePath, momentOpts)

            assertTrue("Cover width ${coverOpts.outWidth} must be <= 540", coverOpts.outWidth in 1..540)
            assertTrue("Moment width ${momentOpts.outWidth} must be <= 720", momentOpts.outWidth in 1..720)
            assertTrue("Cover size $coverBytes bytes must be < 80KB", coverBytes in 1 until 80 * 1024)
            assertTrue("Moment size $momentBytes bytes must be < 80KB", momentBytes in 1 until 80 * 1024)
        } finally {
            bitmap.recycle()
            coverFile.delete()
            momentFile.delete()
        }
    }

    @Test
    fun roomMigration3To4RunsCleanlyWithTelemetryColumns() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbName = "test_migration_3_4.db"
        context.deleteDatabase(dbName)

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
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
                            `user_note` TEXT NOT NULL,
                            `visual_memory` TEXT NOT NULL,
                            `is_starred` INTEGER NOT NULL,
                            `status` TEXT NOT NULL,
                            `processing_state` TEXT NOT NULL,
                            `error_message` TEXT,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL("INSERT INTO `reels` (`shortcode`,`original_url`,`clean_url`,`creator`,`source_language`,`title`,`summary`,`category`,`tags`,`transcript`,`key_takeaways`,`key_moments`,`structured_content`,`extracted_resources`,`caveats`,`use_when`,`user_note`,`visual_memory`,`is_starred`,`status`,`processing_state`,`created_at`,`updated_at`) VALUES ('abc123','https://instagram.com/reel/abc123/','https://www.instagram.com/reel/abc123/','@test','English','Test Reel','Summary','Other','','','','[]','{}','[]','[]','','','',0,'UNREAD','COMPLETED',1000,1000)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase
        SavyorDatabase.MIGRATION_3_4.migrate(db)

        val cursor = db.query("SELECT shortcode, yt_dlp_duration_ms, ai_model_used FROM reels WHERE shortcode = 'abc123'")
        assertTrue(cursor.moveToFirst())
        assertTrue(cursor.getString(0) == "abc123")
        assertTrue(cursor.isNull(1))
        assertTrue(cursor.isNull(2))
        cursor.close()
        db.close()
        context.deleteDatabase(dbName)
    }
}
