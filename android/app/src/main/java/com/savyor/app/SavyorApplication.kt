package com.savyor.app

import android.app.Application
import android.util.Log
import com.savyor.app.data.local.EncryptedSettingsManager
import com.savyor.app.data.local.SavyorDatabase
import com.savyor.app.data.local.SnapshotCacheManager
import com.savyor.app.data.remote.YtDlpEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SavyorApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Log.i("SavyorApp", "SavyorApplication initialized")

        // Prepopulate default categories and check yt-dlp update
        applicationScope.launch {
            try {
                val db = SavyorDatabase.getInstance(this@SavyorApplication)
                if (db.categoryDao().count() == 0) {
                    db.categoryDao().insertAll(SavyorDatabase.DEFAULT_CATEGORIES)
                }

                // Sweep orphaned temp videos and enforce 100 MB snapshot LRU cap
                SnapshotCacheManager.cleanupOrphanedTempVideos(cacheDir)
                SnapshotCacheManager.enforceSnapshotLruCap(filesDir)

                val settings = EncryptedSettingsManager(this@SavyorApplication)
                if (!settings.isFirstRunDone && settings.autoUpdateYtDlp) {
                    val engine = YtDlpEngine(this@SavyorApplication)
                    val (status, version) = engine.updateEngine()
                    Log.i("SavyorApp", "First-run yt-dlp update complete: status=$status version=$version")
                    settings.isFirstRunDone = true
                }
            } catch (e: Exception) {
                Log.w("SavyorApp", "Background startup tasks non-fatal error: ${e.message}")
            }
        }
    }
}
