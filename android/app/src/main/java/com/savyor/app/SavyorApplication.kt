package com.savyor.app

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.savyor.app.data.local.EncryptedSettingsManager
import com.savyor.app.data.local.SavyorDatabase
import com.savyor.app.data.local.SnapshotCacheManager
import com.savyor.app.data.remote.AppUpdateManager
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

                // Sweep orphaned temp videos, old update APKs, and enforce 100 MB snapshot LRU cap
                SnapshotCacheManager.cleanupOrphanedTempVideos(cacheDir)
                AppUpdateManager.cleanupCachedUpdates(cacheDir)
                SnapshotCacheManager.enforceSnapshotLruCap(filesDir)

                val settings = EncryptedSettingsManager(this@SavyorApplication)
                val engine = YtDlpEngine(this@SavyorApplication)

                // Check yt-dlp update status on cold start (6h throttled, silent offline)
                engine.refreshUpdateStatusIfNeeded(force = false)

                val sevenDaysMs = 7L * 24L * 3600L * 1000L
                val isUpdateStale = !settings.isFirstRunDone ||
                    (System.currentTimeMillis() - settings.lastYtDlpUpdateCheckMs > sevenDaysMs)

                if (settings.autoUpdateYtDlp && isUpdateStale && isOnUnmeteredWifi(this@SavyorApplication)) {
                    val (status, version) = engine.updateEngine()
                    Log.i("SavyorApp", "Smart Wi-Fi yt-dlp update complete: status=$status version=$version")
                    settings.isFirstRunDone = true
                }
            } catch (e: Exception) {
                Log.w("SavyorApp", "Background startup tasks non-fatal error: ${e.message}")
            }
        }
    }

    private fun isOnUnmeteredWifi(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
