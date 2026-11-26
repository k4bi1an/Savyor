package com.savyor.app.ui.screens.settings

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.GsonBuilder
import com.savyor.app.BuildConfig
import com.savyor.app.data.local.BackupRestoreManager
import com.savyor.app.data.local.EncryptedSettingsManager
import com.savyor.app.data.local.SavyorDatabase
import com.savyor.app.data.remote.AppInstallLaunchResult
import com.savyor.app.data.remote.AppUpdateCheckResult
import com.savyor.app.data.remote.AppUpdateInfo
import com.savyor.app.data.remote.AppUpdateManager
import com.savyor.app.data.remote.GeminiFilesClient
import com.savyor.app.data.remote.GeminiModelOption
import com.savyor.app.data.remote.YtDlpEngine
import com.savyor.app.ui.theme.SavyorThemeMode
import com.savyor.app.ui.theme.SavyorThemeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class StorageStats(
    val dbSizeBytes: Long = 0L,
    val snapshotsSizeBytes: Long = 0L,
    val videoSizeBytes: Long = 0L // Guaranteed 0 MB
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = EncryptedSettingsManager(application)
    private val geminiClient = GeminiFilesClient()
    private val ytEngine = YtDlpEngine(application)
    private val appUpdateManager = AppUpdateManager()

    private val _maskedApiKey = MutableStateFlow(settings.getMaskedApiKey())
    val maskedApiKey: StateFlow<String> = _maskedApiKey.asStateFlow()

    private val _isWifiOnly = MutableStateFlow(settings.isWifiOnly)
    val isWifiOnly: StateFlow<Boolean> = _isWifiOnly.asStateFlow()

    private val _autoUpdateYtDlp = MutableStateFlow(settings.autoUpdateYtDlp)
    val autoUpdateYtDlp: StateFlow<Boolean> = _autoUpdateYtDlp.asStateFlow()

    private val _ytDlpVersion = MutableStateFlow(settings.ytDlpInstalledVersion.ifBlank { "Checking..." })
    val ytDlpVersion: StateFlow<String> = _ytDlpVersion.asStateFlow()

    private val _ytDlpLatestVersion = MutableStateFlow(settings.ytDlpLatestVersion)
    val ytDlpLatestVersion: StateFlow<String> = _ytDlpLatestVersion.asStateFlow()

    private val _ytDlpUpdateStatus = MutableStateFlow(settings.ytDlpUpdateStatus)
    val ytDlpUpdateStatus: StateFlow<String> = _ytDlpUpdateStatus.asStateFlow()

    private val _ytDlpLastCheckedMs = MutableStateFlow(settings.ytDlpLastCheckedEpochMs)
    val ytDlpLastCheckedMs: StateFlow<Long> = _ytDlpLastCheckedMs.asStateFlow()

    private val _isUpdatingEngine = MutableStateFlow(false)
    val isUpdatingEngine: StateFlow<Boolean> = _isUpdatingEngine.asStateFlow()

    private val _isExportingTelemetry = MutableStateFlow(false)
    val isExportingTelemetry: StateFlow<Boolean> = _isExportingTelemetry.asStateFlow()

    private val _connectionTestStatus = MutableStateFlow<String?>(null)
    val connectionTestStatus: StateFlow<String?> = _connectionTestStatus.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private var connectionTestJob: Job? = null

    private val _storageStats = MutableStateFlow(StorageStats())
    val storageStats: StateFlow<StorageStats> = _storageStats.asStateFlow()

    private val _isBackingUp = MutableStateFlow(false)
    val isBackingUp: StateFlow<Boolean> = _isBackingUp.asStateFlow()

    private val _isRestoring = MutableStateFlow(false)
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()

    private val _backupRestoreMessage = MutableSharedFlow<String>()
    val backupRestoreMessage: SharedFlow<String> = _backupRestoreMessage.asSharedFlow()

    private val _lastBackupMs = MutableStateFlow(settings.lastBackupMs)
    val lastBackupMs: StateFlow<Long> = _lastBackupMs.asStateFlow()

    private val _themeMode = MutableStateFlow(SavyorThemeMode.fromString(settings.themeMode))
    val themeMode: StateFlow<SavyorThemeMode> = _themeMode.asStateFlow()

    private val _geminiModel = MutableStateFlow(GeminiModelOption.fromModeKey(settings.geminiModelMode))
    val geminiModel: StateFlow<GeminiModelOption> = _geminiModel.asStateFlow()

    private val _availableAppUpdate = MutableStateFlow<AppUpdateInfo?>(null)
    val availableAppUpdate: StateFlow<AppUpdateInfo?> = _availableAppUpdate.asStateFlow()

    private val _appUpdateStatus = MutableStateFlow("IDLE")
    val appUpdateStatus: StateFlow<String> = _appUpdateStatus.asStateFlow()

    private val _appUpdateDownloadProgress = MutableStateFlow(0)
    val appUpdateDownloadProgress: StateFlow<Int> = _appUpdateDownloadProgress.asStateFlow()

    private val _downloadedApkFile = MutableStateFlow<File?>(null)
    val downloadedApkFile: StateFlow<File?> = _downloadedApkFile.asStateFlow()

    init {
        refreshEngineVersion()
        calculateStorage()
        checkForAppUpdate(force = false)
    }

    fun checkForAppUpdate(force: Boolean = true) {
        if (_appUpdateStatus.value == "CHECKING" || _appUpdateStatus.value == "DOWNLOADING") return
        val twelveHoursMs = 12L * 3600L * 1000L
        val now = System.currentTimeMillis()
        if (!force && (now - settings.lastAppUpdateCheckMs) < twelveHoursMs && _appUpdateStatus.value != "IDLE") {
            return
        }
        _appUpdateStatus.value = "CHECKING"
        viewModelScope.launch {
            when (val res = appUpdateManager.checkForUpdate()) {
                is AppUpdateCheckResult.UpdateAvailable -> {
                    settings.lastAppUpdateCheckMs = System.currentTimeMillis()
                    _availableAppUpdate.value = res.info
                    _appUpdateStatus.value = "UPDATE_AVAILABLE"
                }
                is AppUpdateCheckResult.UpToDate -> {
                    settings.lastAppUpdateCheckMs = System.currentTimeMillis()
                    _availableAppUpdate.value = null
                    _appUpdateStatus.value = "UP_TO_DATE"
                    if (force) {
                        _backupRestoreMessage.emit("Savyor v${BuildConfig.VERSION_NAME} is up to date")
                    }
                }
                is AppUpdateCheckResult.Error -> {
                    _appUpdateStatus.value = "ERROR"
                    if (force) {
                        _backupRestoreMessage.emit("Update check failed: ${res.message}")
                    }
                }
            }
        }
    }

    fun downloadAndInstallAppUpdate() {
        val info = _availableAppUpdate.value ?: return
        val existing = _downloadedApkFile.value
        val app = getApplication<Application>()
        if (existing != null && existing.exists()) {
            viewModelScope.launch {
                handleInstallLaunch(app, existing)
            }
            return
        }
        if (_appUpdateStatus.value == "DOWNLOADING") return

        _appUpdateStatus.value = "DOWNLOADING"
        _appUpdateDownloadProgress.value = 0
        viewModelScope.launch {
            val res = appUpdateManager.downloadApk(app, info) { pct, _, _ ->
                _appUpdateDownloadProgress.value = pct
            }
            res.fold(
                onSuccess = { apkFile ->
                    _downloadedApkFile.value = apkFile
                    _appUpdateStatus.value = "READY_TO_INSTALL"
                    handleInstallLaunch(app, apkFile)
                },
                onFailure = { err ->
                    _appUpdateStatus.value = "UPDATE_AVAILABLE"
                    _backupRestoreMessage.emit("Download failed: ${err.message}")
                }
            )
        }
    }

    private suspend fun handleInstallLaunch(app: Application, apkFile: File) {
        when (val launch = appUpdateManager.launchInstaller(app, apkFile)) {
            is AppInstallLaunchResult.LaunchedInstaller -> Unit
            is AppInstallLaunchResult.NeedsInstallPermission -> {
                _backupRestoreMessage.emit("Enable 'Allow from this source' for Savyor, then tap Install")
            }
            is AppInstallLaunchResult.Error -> {
                _backupRestoreMessage.emit("Install failed: ${launch.message}")
            }
        }
    }

    fun getEditableApiKey(): String = settings.geminiApiKey

    fun saveApiKey(key: String) {
        val sanitized = key.trim().replace(Regex("\\s+"), "")
        connectionTestJob?.cancel()
        _isTestingConnection.value = false
        settings.geminiApiKey = sanitized
        _maskedApiKey.value = settings.getMaskedApiKey()
        _connectionTestStatus.value = null
        if (sanitized.isNotBlank()) {
            testGeminiConnection()
        }
    }

    fun clearApiKey() {
        connectionTestJob?.cancel()
        _isTestingConnection.value = false
        settings.geminiApiKey = ""
        _maskedApiKey.value = settings.getMaskedApiKey()
        _connectionTestStatus.value = null
    }

    fun setWifiOnly(enabled: Boolean) {
        settings.isWifiOnly = enabled
        _isWifiOnly.value = enabled
    }

    fun setAutoUpdateYtDlp(enabled: Boolean) {
        settings.autoUpdateYtDlp = enabled
        _autoUpdateYtDlp.value = enabled
    }

    fun setThemeMode(mode: SavyorThemeMode) {
        settings.themeMode = mode.name
        _themeMode.value = mode
        SavyorThemeState.setThemeMode(mode)
    }

    fun setGeminiModel(option: GeminiModelOption) {
        connectionTestJob?.cancel()
        _isTestingConnection.value = false
        settings.geminiModelMode = option.modeKey
        _geminiModel.value = option
        _connectionTestStatus.value = null
    }

    fun refreshEngineVersion() {
        viewModelScope.launch {
            val v = ytEngine.getEngineVersion()
            _ytDlpVersion.value = v
            if (_ytDlpUpdateStatus.value == "UNKNOWN") {
                _ytDlpUpdateStatus.value = "CHECKING"
            }
            ytEngine.refreshUpdateStatusIfNeeded(force = false)
            _ytDlpVersion.value = settings.ytDlpInstalledVersion.ifBlank { v }
            _ytDlpLatestVersion.value = settings.ytDlpLatestVersion
            _ytDlpUpdateStatus.value = settings.ytDlpUpdateStatus
            _ytDlpLastCheckedMs.value = settings.ytDlpLastCheckedEpochMs
        }
    }

    fun updateYtDlpEngine() {
        if (_isUpdatingEngine.value) return
        _isUpdatingEngine.value = true
        _ytDlpUpdateStatus.value = "CHECKING"
        viewModelScope.launch {
            try {
                val (status, version) = ytEngine.updateEngine()
                _ytDlpVersion.value = version
                _ytDlpLatestVersion.value = settings.ytDlpLatestVersion
                _ytDlpUpdateStatus.value = settings.ytDlpUpdateStatus
                _ytDlpLastCheckedMs.value = settings.ytDlpLastCheckedEpochMs
                if (status.startsWith("ERROR")) {
                    _backupRestoreMessage.emit("yt-dlp update error: $status")
                } else {
                    _backupRestoreMessage.emit("yt-dlp $version ($status)")
                }
            } catch (e: Exception) {
                _ytDlpVersion.value = "Error: ${e.message}"
                _ytDlpUpdateStatus.value = "UNKNOWN"
            } finally {
                _isUpdatingEngine.value = false
            }
        }
    }

    fun exportTelemetry(uri: Uri) {
        if (_isExportingTelemetry.value) return
        _isExportingTelemetry.value = true
        viewModelScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    val app = getApplication<Application>()
                    val reels = SavyorDatabase.getInstance(app).reelDao().getAllReelsOnce()
                    val timedReels = reels.filter { it.totalProcessingDurationMs != null }

                    val avgTotalMs = timedReels.mapNotNull { it.totalProcessingDurationMs }.average().takeIf { !it.isNaN() }?.toLong() ?: 0L
                    val avgYtDlpMs = timedReels.mapNotNull { it.ytDlpDurationMs }.average().takeIf { !it.isNaN() }?.toLong() ?: 0L
                    val avgAiMs = timedReels.mapNotNull { it.aiAnalysisDurationMs }.average().takeIf { !it.isNaN() }?.toLong() ?: 0L
                    val avgCoverKb = timedReels.mapNotNull { it.coverSizeBytes }.average().takeIf { !it.isNaN() }?.div(1024.0) ?: 0.0
                    val totalFrames = reels.sumOf { it.framesExtractedCount ?: 0 }

                    val report = mapOf(
                        "exportedAtEpochMs" to System.currentTimeMillis(),
                        "device" to mapOf(
                            "manufacturer" to Build.MANUFACTURER,
                            "model" to Build.MODEL,
                            "sdkInt" to Build.VERSION.SDK_INT,
                            "supportedAbis" to Build.SUPPORTED_ABIS.toList(),
                            "appVersion" to BuildConfig.VERSION_NAME,
                            "ytDlpVersion" to settings.ytDlpInstalledVersion.ifBlank { _ytDlpVersion.value }
                        ),
                        "aggregate" to mapOf(
                            "totalReels" to reels.size,
                            "timedReelsCount" to timedReels.size,
                            "avgTotalProcessingMs" to avgTotalMs,
                            "avgYtDlpMs" to avgYtDlpMs,
                            "avgGeminiMs" to avgAiMs,
                            "avgCoverKb" to String.format("%.1f", avgCoverKb).toDouble(),
                            "totalFramesExtracted" to totalFrames
                        ),
                        "reels" to reels.map { r ->
                            mapOf(
                                "shortcode" to r.shortcode,
                                "title" to r.title,
                                "status" to r.status,
                                "processingState" to r.processingState,
                                "ytDlpDurationMs" to r.ytDlpDurationMs,
                                "frameExtractDurationMs" to r.frameExtractDurationMs,
                                "aiAnalysisDurationMs" to r.aiAnalysisDurationMs,
                                "totalProcessingDurationMs" to r.totalProcessingDurationMs,
                                "videoDurationSec" to r.videoDurationSec,
                                "downloadedVideoBytes" to r.downloadedVideoBytes,
                                "coverSizeBytes" to r.coverSizeBytes,
                                "framesExtractedCount" to r.framesExtractedCount,
                                "linksExtractedCount" to r.linksExtractedCount,
                                "aiModelUsed" to r.aiModelUsed,
                                "processingError" to r.processingError
                            )
                        }
                    )

                    val json = GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(report)
                    app.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                        out.flush()
                    } ?: throw Exception("Could not open output stream for telemetry export")
                    reels.size
                }
                _backupRestoreMessage.emit("Exported telemetry for $count reels")
            } catch (e: Exception) {
                _backupRestoreMessage.emit("Telemetry export failed: ${e.message}")
            } finally {
                _isExportingTelemetry.value = false
            }
        }
    }

    fun testGeminiConnection() {
        val key = settings.geminiApiKey.trim()
        if (key.isBlank()) {
            _connectionTestStatus.value = "Error: API key is blank"
            return
        }

        connectionTestJob?.cancel()
        _isTestingConnection.value = true
        _connectionTestStatus.value = "Testing connection..."
        val modelMode = _geminiModel.value.modeKey
        connectionTestJob = viewModelScope.launch {
            try {
                val res = geminiClient.testConnection(key, modelMode)
                _connectionTestStatus.value = res.fold(
                    onSuccess = { it },
                    onFailure = { "Failed: ${it.message}" }
                )
            } finally {
                _isTestingConnection.value = false
            }
        }
    }

    fun calculateStorage() {
        viewModelScope.launch {
            val stats = withContext(Dispatchers.IO) {
                val app = getApplication<Application>()
                val dbFile = app.getDatabasePath("savyor.db")
                val dbSize = if (dbFile.exists()) dbFile.length() else 0L

                val snapshotsDir = File(app.filesDir, "snapshots")
                var snapshotsSize = 0L
                if (snapshotsDir.exists()) {
                    snapshotsDir.walkTopDown().forEach { if (it.isFile) snapshotsSize += it.length() }
                }

                // Check cache reels temp (should always be 0 bytes)
                val tempDir = File(app.cacheDir, "reels_temp")
                var tempSize = 0L
                if (tempDir.exists()) {
                    tempDir.walkTopDown().forEach { if (it.isFile) tempSize += it.length() }
                }

                StorageStats(
                    dbSizeBytes = dbSize,
                    snapshotsSizeBytes = snapshotsSize,
                    videoSizeBytes = tempSize
                )
            }
            _storageStats.value = stats
        }
    }

    fun clearSnapshots() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val snapshotsDir = File(getApplication<Application>().filesDir, "snapshots")
                if (snapshotsDir.exists()) {
                    snapshotsDir.deleteRecursively()
                    snapshotsDir.mkdirs()
                }
            }
            calculateStorage()
        }
    }

    fun exportBackup(uri: Uri) {
        if (_isBackingUp.value) return
        _isBackingUp.value = true
        viewModelScope.launch {
            val res = BackupRestoreManager.exportBackup(getApplication(), uri)
            _isBackingUp.value = false
            res.fold(
                onSuccess = { stats ->
                    val now = System.currentTimeMillis()
                    settings.lastBackupMs = now
                    _lastBackupMs.value = now
                    val mb = "%.2f".format(stats.fileSizeBytes.toDouble() / (1024 * 1024))
                    _backupRestoreMessage.emit("Backup created ($mb MB, ${stats.snapshotCount} snapshots)")
                },
                onFailure = { err ->
                    _backupRestoreMessage.emit("Backup failed: ${err.message}")
                }
            )
        }
    }

    fun importBackup(uri: Uri) {
        if (_isRestoring.value) return
        _isRestoring.value = true
        viewModelScope.launch {
            val res = BackupRestoreManager.importBackup(getApplication(), uri)
            _isRestoring.value = false
            res.fold(
                onSuccess = { stats ->
                    calculateStorage()
                    _backupRestoreMessage.emit("Restored ${stats.reelsRestored} reels (${stats.snapshotCount} snapshots)")
                },
                onFailure = { err ->
                    _backupRestoreMessage.emit("Restore failed: ${err.message}")
                }
            )
        }
    }
}
