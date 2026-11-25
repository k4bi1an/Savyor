package com.savyor.app.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.savyor.app.data.local.BackupRestoreManager
import com.savyor.app.data.local.BackupStats
import com.savyor.app.data.local.EncryptedSettingsManager
import com.savyor.app.data.local.RestoreStats
import com.savyor.app.data.remote.GeminiFilesClient
import com.savyor.app.data.remote.YtDlpEngine
import kotlinx.coroutines.Dispatchers
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

    private val _maskedApiKey = MutableStateFlow(settings.getMaskedApiKey())
    val maskedApiKey: StateFlow<String> = _maskedApiKey.asStateFlow()

    private val _isWifiOnly = MutableStateFlow(settings.isWifiOnly)
    val isWifiOnly: StateFlow<Boolean> = _isWifiOnly.asStateFlow()

    private val _autoUpdateYtDlp = MutableStateFlow(settings.autoUpdateYtDlp)
    val autoUpdateYtDlp: StateFlow<Boolean> = _autoUpdateYtDlp.asStateFlow()

    private val _ytDlpVersion = MutableStateFlow("Checking...")
    val ytDlpVersion: StateFlow<String> = _ytDlpVersion.asStateFlow()

    private val _isUpdatingEngine = MutableStateFlow(false)
    val isUpdatingEngine: StateFlow<Boolean> = _isUpdatingEngine.asStateFlow()

    private val _connectionTestStatus = MutableStateFlow<String?>(null)
    val connectionTestStatus: StateFlow<String?> = _connectionTestStatus.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

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

    init {
        refreshEngineVersion()
        calculateStorage()
    }

    fun saveApiKey(key: String) {
        settings.geminiApiKey = key
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

    fun refreshEngineVersion() {
        viewModelScope.launch {
            _ytDlpVersion.value = ytEngine.getEngineVersion()
        }
    }

    fun updateYtDlpEngine() {
        if (_isUpdatingEngine.value) return
        _isUpdatingEngine.value = true
        viewModelScope.launch {
            try {
                val (status, version) = ytEngine.updateEngine()
                _ytDlpVersion.value = "$version ($status)"
            } catch (e: Exception) {
                _ytDlpVersion.value = "Error: ${e.message}"
            } finally {
                _isUpdatingEngine.value = false
            }
        }
    }

    fun testGeminiConnection() {
        val key = settings.geminiApiKey
        if (key.isBlank()) {
            _connectionTestStatus.value = "Error: API key is blank"
            return
        }

        _isTestingConnection.value = true
        _connectionTestStatus.value = "Testing connection..."
        viewModelScope.launch {
            val res = geminiClient.testConnection(key)
            _isTestingConnection.value = false
            _connectionTestStatus.value = res.fold(
                onSuccess = { "Success: $it" },
                onFailure = { "Failed: ${it.message}" }
            )
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
