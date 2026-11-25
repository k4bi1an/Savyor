package com.savyor.app

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.savyor.app.data.local.BackupRestoreManager
import com.savyor.app.data.local.SavyorDatabase
import com.savyor.app.data.local.entity.CategoryEntity
import com.savyor.app.data.local.entity.ReelEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class Phase2BAndroidSuiteTest {

    private lateinit var context: Context
    private lateinit var testDb: SavyorDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        testDb = Room.inMemoryDatabaseBuilder(context, SavyorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        testDb.close()
    }

    @Test
    fun testRoomMigration1To2PreservesReelsAndIndexesUserNoteInFts() {
        context.deleteDatabase("test_migration.db")

        // 1. Create a version 1 database schema directly
        val helperFactory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("test_migration.db")
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
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

                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val openHelper = helperFactory.create(config)
        val v1Db = openHelper.writableDatabase

        // Insert a reel in version 1
        v1Db.execSQL("""
            INSERT INTO `reels` (
                `shortcode`, `original_url`, `clean_url`, `creator`, `source_language`,
                `title`, `summary`, `category`, `tags`, `transcript`, `key_takeaways`,
                `key_moments`, `structured_content`, `extracted_resources`, `caveats`,
                `use_when`, `status`, `processing_state`, `created_at`, `updated_at`
            ) VALUES (
                'V1_TEST_SHORTCODE', 'https://instagram.com/reel/V1_TEST/', 'https://instagram.com/reel/V1_TEST/', 'creator1', 'English',
                'Advanced Motion Graphics in Blender', 'Comprehensive guide to simulation nodes', 'Design & UI', '[]', 'transcript text', '[]',
                '[]', '{}', '[]', '[]', 'Designing physics simulations', 'UNREAD', 'COMPLETED', 1700000000000, 1700000000000
            )
        """.trimIndent())

        // Insert a default category in version 1
        v1Db.execSQL("INSERT INTO `categories` (`name`, `is_default`, `display_order`) VALUES ('Design & UI', 1, 4)")

        // Verify reel exists before migration
        val cursorBefore = v1Db.query("SELECT title FROM reels WHERE shortcode = 'V1_TEST_SHORTCODE'")
        assertTrue(cursorBefore.moveToFirst())
        assertEquals("Advanced Motion Graphics in Blender", cursorBefore.getString(0))
        cursorBefore.close()

        // 2. Execute Migration from 1 to 2
        SavyorDatabase.MIGRATION_1_2.migrate(v1Db)

        // 3. Verify user_note column exists on reels and default is empty string
        val cursorAfter = v1Db.query("SELECT shortcode, title, user_note FROM reels WHERE shortcode = 'V1_TEST_SHORTCODE'")
        assertTrue("Migrated reel MUST exist in reels table", cursorAfter.moveToFirst())
        assertEquals("V1_TEST_SHORTCODE", cursorAfter.getString(0))
        assertEquals("Advanced Motion Graphics in Blender", cursorAfter.getString(1))
        assertEquals("", cursorAfter.getString(2)) // default user_note
        cursorAfter.close()

        // 4. Verify color_hex column exists on categories
        val catCursor = v1Db.query("SELECT name, color_hex FROM categories WHERE name = 'Design & UI'")
        assertTrue(catCursor.moveToFirst())
        assertEquals("#2F5D50", catCursor.getString(1)) // default migration color
        catCursor.close()

        // 5. Update user_note and verify it is indexed in reels_fts
        v1Db.execSQL("UPDATE reels SET user_note = 'My secret modifier stack: remesh + smooth' WHERE shortcode = 'V1_TEST_SHORTCODE'")

        // Update FTS index directly or via sync
        v1Db.execSQL("DELETE FROM reels_fts")
        v1Db.execSQL("""
            INSERT INTO reels_fts (docid, title, summary, transcript, original_transcript, tags, creator, structured_content, user_note)
            SELECT id, title, summary, transcript, original_transcript, tags, creator, structured_content, user_note FROM reels
        """.trimIndent())

        val ftsCursor = v1Db.query("SELECT docid FROM reels_fts WHERE reels_fts MATCH '\"secret modifier stack\"*'")
        assertTrue("FTS query matching user_note MUST return the document", ftsCursor.moveToFirst())
        ftsCursor.close()

        v1Db.close()
        context.deleteDatabase("test_migration.db")
    }

    @Test
    fun testCategoryCrudAndSafeReassignmentToOther(): Unit = runBlocking {
        val categoryDao = testDb.categoryDao()
        val reelDao = testDb.reelDao()

        // Initialize default categories
        categoryDao.insertAll(SavyorDatabase.DEFAULT_CATEGORIES)

        // 1. Add custom category
        val customCat = CategoryEntity(name = "Woodworking", isDefault = false, displayOrder = 99, colorHex = "#B35446")
        categoryDao.insert(customCat)
        assertNotNull(categoryDao.getCategoryByName("Woodworking"))

        // 2. Insert 2 reels under "Woodworking"
        reelDao.insert(
            ReelEntity(
                shortcode = "WOOD1",
                originalUrl = "https://instagram.com/reel/WOOD1/",
                cleanUrl = "https://instagram.com/reel/WOOD1/",
                title = "Dovetail joints tutorial",
                category = "Woodworking"
            )
        )
        reelDao.insert(
            ReelEntity(
                shortcode = "WOOD2",
                originalUrl = "https://instagram.com/reel/WOOD2/",
                cleanUrl = "https://instagram.com/reel/WOOD2/",
                title = "Japanese pull saw comparison",
                category = "Woodworking"
            )
        )

        assertEquals(2, reelDao.countByCategory("Woodworking"))

        // 3. Rename category "Woodworking" -> "Carpentry"
        categoryDao.updateCategoryName("Woodworking", "Carpentry")
        reelDao.reassignCategory(fromCategory = "Woodworking", toCategory = "Carpentry")

        assertEquals(0, reelDao.countByCategory("Woodworking"))
        assertEquals(2, reelDao.countByCategory("Carpentry"))

        // 4. Update color
        categoryDao.updateCategoryColor("Carpentry", "#80632E")
        val updatedCat = categoryDao.getCategoryByName("Carpentry")
        assertEquals("#80632E", updatedCat?.colorHex)

        // 5. Delete category "Carpentry" -> Must safely reassign all reels to "Other"
        reelDao.reassignCategory(fromCategory = "Carpentry", toCategory = "Other")
        categoryDao.deleteCustomCategory("Carpentry")

        assertNull(categoryDao.getCategoryByName("Carpentry"))
        assertEquals(0, reelDao.countByCategory("Carpentry"))
        assertEquals(2, reelDao.countByCategory("Other"))

        // 6. Verify "Other" cannot be deleted
        categoryDao.deleteCustomCategory("Other") // Query has is_default = 0
        assertNotNull("System category 'Other' must NEVER be deleted", categoryDao.getCategoryByName("Other"))
    }

    @Test
    fun testSafBackupAndRestoreRoundTrip(): Unit = runBlocking {
        // Setup a live database with a reel and snapshot
        val appDb = SavyorDatabase.getInstance(context)
        val testShortcode = "BACKUP_TEST_1"
        appDb.reelDao().deleteByShortcode(testShortcode)

        val testSnapshotDir = File(context.filesDir, "snapshots/$testShortcode").apply { mkdirs() }
        val testThumb = File(testSnapshotDir, "thumb.webp").apply { writeBytes("SAMPLE_WEBP_IMAGE_DATA".toByteArray()) }

        appDb.reelDao().insert(
            ReelEntity(
                shortcode = testShortcode,
                originalUrl = "https://instagram.com/reel/$testShortcode/",
                cleanUrl = "https://instagram.com/reel/$testShortcode/",
                title = "Backup Test Reel",
                userNote = "Crucial test observation",
                thumbnailPath = testThumb.absolutePath,
                category = "Tech & Code",
                processingState = "COMPLETED"
            )
        )

        val backupFile = File(context.cacheDir, "test_backup_${System.currentTimeMillis()}.zip")
        val backupUri = Uri.fromFile(backupFile)

        // 1. Export backup
        val exportResult = BackupRestoreManager.exportBackup(context, backupUri)
        assertTrue("Export backup must succeed", exportResult.isSuccess)
        val backupStats = exportResult.getOrThrow()
        assertTrue("Backup file must contain bytes", backupStats.fileSizeBytes > 0)
        assertTrue("Backup must have archived at least 1 snapshot", backupStats.snapshotCount >= 1)
        assertTrue(backupFile.exists())

        // 2. Simulate data loss / corruption
        appDb.reelDao().deleteByShortcode(testShortcode)
        testThumb.delete()
        assertNull(appDb.reelDao().getReelByShortcode(testShortcode))
        assertFalse(testThumb.exists())

        // 3. Restore from backup
        val importResult = BackupRestoreManager.importBackup(context, backupUri)
        assertTrue("Restore backup must succeed: ${importResult.exceptionOrNull()?.message}", importResult.isSuccess)
        val restoreStats = importResult.getOrThrow()
        assertTrue("Restored count must be >= 1", restoreStats.reelsRestored >= 1)

        // 4. Verify restored data
        val restoredDb = SavyorDatabase.getInstance(context)
        val restoredReel = restoredDb.reelDao().getReelByShortcode(testShortcode)
        assertNotNull("Restored reel must exist in DB", restoredReel)
        assertEquals("Backup Test Reel", restoredReel?.title)
        assertEquals("Crucial test observation", restoredReel?.userNote)
        assertTrue("Snapshot file must be restored", testThumb.exists())

        // Cleanup
        restoredDb.reelDao().deleteByShortcode(testShortcode)
        testSnapshotDir.deleteRecursively()
        backupFile.delete()
    }
}
