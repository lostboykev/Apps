package com.example.ui.viewmodel

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.db.CommandLogEntity
import com.example.data.repository.ActiveJobInfo
import com.example.data.repository.CommandRepository
import com.example.data.shell.ProcessCpuInfo
import com.example.data.shell.ShellExecutor
import com.example.data.shell.ShizukuStatus
import com.example.data.shell.SystemStatusParser
import com.example.data.shell.ThermalInfo
import com.example.data.shell.WakeLockItem
import com.example.service.DozeAutomationManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppPackageInfo(
    val packageName: String,
    val appName: String,
    val isSystem: Boolean,
    val standbyBucket: String = "Unknown",
    val isFrozen: Boolean = false
)

class SysOptViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val shellExecutor = ShellExecutor(application)
    val repository = CommandRepository(shellExecutor, db.commandLogDao(), db.appStateDao())
    val dozeManager = DozeAutomationManager(application, repository)

    // Shell / Shizuku status
    private val _shizukuStatus = MutableStateFlow(repository.getShizukuStatus())
    val shizukuStatus: StateFlow<ShizukuStatus> = _shizukuStatus.asStateFlow()

    // Active Running Job
    val activeJob: StateFlow<ActiveJobInfo?> = repository.activeJob

    // System States
    private val _isDeepIdleForced = MutableStateFlow(false)
    val isDeepIdleForced: StateFlow<Boolean> = _isDeepIdleForced.asStateFlow()

    private val _isMotionDisabled = MutableStateFlow(false)
    val isMotionDisabled: StateFlow<Boolean> = _isMotionDisabled.asStateFlow()

    private val _animationScale = MutableStateFlow("0.75")
    val animationScale: StateFlow<String> = _animationScale.asStateFlow()

    val isDozeAutomatorActive: StateFlow<Boolean> = dozeManager.isAutomatorActive

    // Diagnostics State
    private val _thermalInfo = MutableStateFlow(ThermalInfo())
    val thermalInfo: StateFlow<ThermalInfo> = _thermalInfo.asStateFlow()

    private val _cpuProcesses = MutableStateFlow<List<ProcessCpuInfo>>(emptyList())
    val cpuProcesses: StateFlow<List<ProcessCpuInfo>> = _cpuProcesses.asStateFlow()

    private val _activeWakeLocks = MutableStateFlow<List<WakeLockItem>>(emptyList())
    val activeWakeLocks: StateFlow<List<WakeLockItem>> = _activeWakeLocks.asStateFlow()

    private val _isDiagnosticsRefreshing = MutableStateFlow(false)
    val isDiagnosticsRefreshing: StateFlow<Boolean> = _isDiagnosticsRefreshing.asStateFlow()

    // Apps & Standby
    private val _installedApps = MutableStateFlow<List<AppPackageInfo>>(emptyList())
    val installedApps: StateFlow<List<AppPackageInfo>> = _installedApps.asStateFlow()

    private val _selectedAppForAot = MutableStateFlow<AppPackageInfo?>(null)
    val selectedAppForAot: StateFlow<AppPackageInfo?> = _selectedAppForAot.asStateFlow()

    // Search and Log Filtering
    private val _logSearchQuery = MutableStateFlow("")
    val logSearchQuery: StateFlow<String> = _logSearchQuery.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow("ALL")
    val selectedCategoryFilter: StateFlow<String> = _selectedCategoryFilter.asStateFlow()

    val filteredLogs: StateFlow<List<CommandLogEntity>> = combine(
        repository.getAllLogs(),
        _logSearchQuery,
        _selectedCategoryFilter
    ) { logs, query, category ->
        logs.filter { log ->
            val matchesCategory = category == "ALL" || log.category == category
            val matchesQuery = query.isBlank() ||
                    log.commandName.contains(query, ignoreCase = true) ||
                    log.commandText.contains(query, ignoreCase = true) ||
                    log.output.contains(query, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Terminal Quick Output Preview
    private val _lastOutput = MutableStateFlow<String?>(null)
    val lastOutput: StateFlow<String?> = _lastOutput.asStateFlow()

    init {
        refreshShizukuStatus()
        loadSavedStates()
        loadInstalledApps()
        refreshDiagnostics()
    }

    fun refreshShizukuStatus() {
        _shizukuStatus.value = repository.getShizukuStatus()
    }

    fun requestShizukuPermission() {
        repository.requestShizukuPermission()
        viewModelScope.launch {
            delay(1000)
            refreshShizukuStatus()
        }
    }

    fun toggleSimulationMode(enabled: Boolean) {
        repository.setSimulationMode(enabled)
        refreshShizukuStatus()
    }

    private fun loadSavedStates() {
        viewModelScope.launch {
            _isDeepIdleForced.value = repository.getState("deep_idle_forced") == "true"
            _isMotionDisabled.value = repository.getState("motion_disabled") == "true"
            _animationScale.value = repository.getState("animation_scale") ?: "0.75"
            if (repository.getState("doze_automator_enabled") == "true") {
                dozeManager.startAutomation()
            }
        }
    }

    fun selectAppForAot(app: AppPackageInfo) {
        _selectedAppForAot.value = app
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            try {
                val pm = getApplication<Application>().packageManager
                val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
                val list = packages.map { pkg ->
                    val isSys = (pkg.applicationInfo != null && (pkg.applicationInfo!!.flags and ApplicationInfo.FLAG_SYSTEM) != 0)
                    val appLabel = pkg.applicationInfo?.loadLabel(pm)?.toString() ?: pkg.packageName
                    AppPackageInfo(
                        packageName = pkg.packageName,
                        appName = appLabel,
                        isSystem = isSys
                    )
                }.sortedWith(compareBy({ it.isSystem }, { it.appName.lowercase() }))
                _installedApps.value = list
            } catch (e: Exception) {
                // Fallback default list of popular candidates
                _installedApps.value = listOf(
                    AppPackageInfo("com.google.android.youtube", "YouTube", false),
                    AppPackageInfo("com.android.chrome", "Chrome", false),
                    AppPackageInfo("com.instagram.android", "Instagram", false),
                    AppPackageInfo("com.facebook.katana", "Facebook", false),
                    AppPackageInfo("com.zhiliaoapp.musically", "TikTok", false),
                    AppPackageInfo("com.google.android.apps.diagnosticstool", "Diagnostics Tool", true),
                    AppPackageInfo("com.carrier.diagnostic.service", "Carrier Diagnostics", true)
                )
            }
        }
    }

    // Toggle Actions

    fun toggleDeepIdle(enable: Boolean) {
        viewModelScope.launch {
            _isDeepIdleForced.value = enable
            val result = repository.forceDeepIdle(enable)
            _lastOutput.value = result.output
            if (!result.isSuccess && !result.wasCancelled) {
                _isDeepIdleForced.value = !enable
            }
        }
    }

    fun toggleMotionInDoze(disableMotion: Boolean) {
        viewModelScope.launch {
            _isMotionDisabled.value = disableMotion
            val result = repository.toggleMotionSensing(disableMotion)
            _lastOutput.value = result.output
            if (!result.isSuccess && !result.wasCancelled) {
                _isMotionDisabled.value = !disableMotion
            }
        }
    }

    fun toggleDozeAutomation(enable: Boolean) {
        if (enable) {
            dozeManager.startAutomation()
        } else {
            dozeManager.stopAutomation()
        }
    }

    fun runBgDexopt() {
        viewModelScope.launch {
            val res = repository.runBgDexoptJob()
            _lastOutput.value = res.output
        }
    }

    fun compileApp(packageName: String, mode: String = "speed") {
        viewModelScope.launch {
            val res = repository.compilePackage(packageName, mode)
            _lastOutput.value = res.output
        }
    }

    fun resetAppCompilation(packageName: String) {
        viewModelScope.launch {
            val res = repository.resetPackageCompilation(packageName)
            _lastOutput.value = res.output
        }
    }

    fun setStandbyBucket(packageName: String, bucket: String) {
        viewModelScope.launch {
            val res = repository.setAppStandbyBucket(packageName, bucket)
            _lastOutput.value = res.output
            // Update local app info
            _installedApps.value = _installedApps.value.map {
                if (it.packageName == packageName) it.copy(standbyBucket = bucket) else it
            }
        }
    }

    fun checkStandbyBucket(packageName: String) {
        viewModelScope.launch {
            val res = repository.getAppStandbyBucket(packageName)
            val parsed = SystemStatusParser.parseStandbyBucket(res.output)
            _installedApps.value = _installedApps.value.map {
                if (it.packageName == packageName) it.copy(standbyBucket = parsed) else it
            }
            _lastOutput.value = "Package $packageName is in bucket: $parsed"
        }
    }

    fun togglePackageFreeze(packageName: String, freeze: Boolean) {
        viewModelScope.launch {
            val res = if (freeze) {
                repository.disablePackageUser0(packageName)
            } else {
                repository.enablePackage(packageName)
            }
            _lastOutput.value = res.output
            _installedApps.value = _installedApps.value.map {
                if (it.packageName == packageName) it.copy(isFrozen = freeze) else it
            }
        }
    }

    fun setAnimationScale(scale: String) {
        viewModelScope.launch {
            _animationScale.value = scale
            val res = repository.setAnimationScales(scale)
            _lastOutput.value = res.output
        }
    }

    fun trimCaches() {
        viewModelScope.launch {
            val res = repository.trimCaches()
            _lastOutput.value = res.output
        }
    }

    fun cancelActiveJob() {
        repository.cancelActiveJob()
    }

    fun refreshDiagnostics() {
        viewModelScope.launch {
            _isDiagnosticsRefreshing.value = true
            try {
                // Fetch thermals
                val thermalRes = repository.fetchThermalService()
                _thermalInfo.value = SystemStatusParser.parseThermalService(thermalRes.output)

                // Fetch CPU
                val cpuRes = repository.fetchCpuInfo()
                _cpuProcesses.value = SystemStatusParser.parseCpuInfo(cpuRes.output)

                // Fetch Wakelocks
                val wakeRes = repository.fetchWakelocks()
                _activeWakeLocks.value = SystemStatusParser.parseWakeLocks(wakeRes.output)
            } finally {
                _isDiagnosticsRefreshing.value = false
            }
        }
    }

    fun setLogSearchQuery(query: String) {
        _logSearchQuery.value = query
    }

    fun setSelectedCategoryFilter(category: String) {
        _selectedCategoryFilter.value = category
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    fun deleteLog(id: Long) {
        viewModelScope.launch {
            repository.deleteLog(id)
        }
    }

    fun clearLastOutput() {
        _lastOutput.value = null
    }

    override fun onCleared() {
        super.onCleared()
        // Doze automator remains registered if user toggled background, or clean up here
    }
}
