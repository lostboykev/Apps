package com.example.data.repository

import com.example.data.db.AppStateDao
import com.example.data.db.AppStateEntity
import com.example.data.db.CommandLogDao
import com.example.data.db.CommandLogEntity
import com.example.data.shell.ExecutionResult
import com.example.data.shell.ShellExecutor
import com.example.data.shell.ShellMode
import com.example.data.shell.ShizukuStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

data class ActiveJobInfo(
    val jobId: Long,
    val commandName: String,
    val commandText: String,
    val startTime: Long = System.currentTimeMillis()
)

class CommandRepository(
    private val shellExecutor: ShellExecutor,
    private val logDao: CommandLogDao,
    private val stateDao: AppStateDao
) {
    private val idGenerator = AtomicLong(System.currentTimeMillis())

    private val _activeJob = MutableStateFlow<ActiveJobInfo?>(null)
    val activeJob = _activeJob.asStateFlow()

    fun getAllLogs(): Flow<List<CommandLogEntity>> = logDao.getAllLogs()

    fun getLogsByCategory(category: String): Flow<List<CommandLogEntity>> = logDao.getLogsByCategory(category)

    fun observeState(key: String): Flow<String?> = stateDao.observeState(key)

    suspend fun getState(key: String): String? = stateDao.getState(key)

    suspend fun setState(key: String, value: String) {
        stateDao.setState(AppStateEntity(key = key, value = value))
    }

    fun getShizukuStatus(): ShizukuStatus = shellExecutor.checkShizukuStatus()

    fun requestShizukuPermission(requestCode: Int = 1001) = shellExecutor.requestShizukuPermission(requestCode)

    fun setSimulationMode(enabled: Boolean) {
        shellExecutor.forceSimulationMode = enabled
    }

    fun isSimulationMode(): Boolean = shellExecutor.forceSimulationMode

    fun cancelActiveJob() {
        val current = _activeJob.value
        if (current != null) {
            shellExecutor.cancelExecution(current.jobId)
        }
    }

    suspend fun runCommand(
        commandName: String,
        commandText: String,
        category: String,
        preferredMode: ShellMode? = null
    ): ExecutionResult {
        val jobId = idGenerator.incrementAndGet()
        val initialLog = CommandLogEntity(
            commandName = commandName,
            commandText = commandText,
            category = category,
            executionMode = (preferredMode ?: shellExecutor.checkShizukuStatus().currentMode).name,
            status = "RUNNING",
            exitCode = 0,
            output = "Executing...",
            durationMs = 0L,
            timestamp = System.currentTimeMillis()
        )
        val logId = logDao.insertLog(initialLog)

        _activeJob.value = ActiveJobInfo(
            jobId = jobId,
            commandName = commandName,
            commandText = commandText
        )

        try {
            val result = shellExecutor.executeCommand(jobId, commandText, preferredMode)

            val finalStatus = when {
                result.wasCancelled -> "CANCELLED"
                result.isSuccess -> "SUCCESS"
                else -> "FAILED"
            }

            val updatedLog = initialLog.copy(
                id = logId,
                status = finalStatus,
                exitCode = result.exitCode,
                output = result.output,
                durationMs = result.durationMs
            )
            logDao.updateLog(updatedLog)

            // Keep log database trimmed to preserve storage & battery
            logDao.trimOldLogs(300)

            return result
        } finally {
            if (_activeJob.value?.jobId == jobId) {
                _activeJob.value = null
            }
        }
    }

    suspend fun clearLogs() {
        logDao.clearAllLogs()
    }

    suspend fun deleteLog(id: Long) {
        logDao.deleteLogById(id)
    }

    // --- High-level command actions ---

    suspend fun forceDeepIdle(enable: Boolean): ExecutionResult {
        val cmd = if (enable) "dumpsys deviceidle force-idle" else "dumpsys deviceidle unforce"
        val name = if (enable) "Force Deep Idle (Doze)" else "Unforce Deep Idle"
        val res = runCommand(name, cmd, "DOZE")
        if (res.isSuccess) {
            setState("deep_idle_forced", enable.toString())
        }
        return res
    }

    suspend fun toggleMotionSensing(disableMotion: Boolean): ExecutionResult {
        val cmd = if (disableMotion) "dumpsys deviceidle disable motion" else "dumpsys deviceidle enable motion"
        val name = if (disableMotion) "Disable Motion in Doze" else "Enable Motion in Doze"
        val res = runCommand(name, cmd, "DOZE")
        if (res.isSuccess) {
            setState("motion_disabled", disableMotion.toString())
        }
        return res
    }

    suspend fun runBgDexoptJob(): ExecutionResult {
        val res = runCommand(
            commandName = "Force Speed-Profile Compilation (All Apps)",
            commandText = "cmd package bg-dexopt-job",
            category = "COMPILATION"
        )
        if (res.isSuccess) {
            setState("last_bg_dexopt_timestamp", System.currentTimeMillis().toString())
        }
        return res
    }

    suspend fun compilePackage(packageName: String, mode: String = "speed"): ExecutionResult {
        val cmd = "cmd package compile -m $mode -f $packageName"
        val name = "Compile $packageName ($mode)"
        return runCommand(name, cmd, "COMPILATION")
    }

    suspend fun resetPackageCompilation(packageName: String): ExecutionResult {
        val cmd = "cmd package compile --reset $packageName"
        val name = "Reset Compilation: $packageName"
        return runCommand(name, cmd, "COMPILATION")
    }

    suspend fun setAppStandbyBucket(packageName: String, bucket: String): ExecutionResult {
        val cmd = "am set-standby-bucket $packageName $bucket"
        val name = "Set Standby Bucket: $packageName -> $bucket"
        return runCommand(name, cmd, "STANDBY_BUCKETS")
    }

    suspend fun getAppStandbyBucket(packageName: String): ExecutionResult {
        val cmd = "am get-standby-bucket $packageName"
        val name = "Get Standby Bucket: $packageName"
        return runCommand(name, cmd, "STANDBY_BUCKETS")
    }

    suspend fun disablePackageUser0(packageName: String): ExecutionResult {
        val cmd = "pm disable-user --user 0 $packageName"
        val name = "Freeze Package: $packageName"
        val res = runCommand(name, cmd, "BLOAT_FREEZE")
        if (res.isSuccess) {
            setState("pkg_frozen_$packageName", "true")
        }
        return res
    }

    suspend fun enablePackage(packageName: String): ExecutionResult {
        val cmd = "pm enable $packageName"
        val name = "Unfreeze Package: $packageName"
        val res = runCommand(name, cmd, "BLOAT_FREEZE")
        if (res.isSuccess) {
            setState("pkg_frozen_$packageName", "false")
        }
        return res
    }

    suspend fun setAnimationScales(scale: String): ExecutionResult {
        val cmd = "settings put global window_animation_scale $scale && " +
                  "settings put global transition_animation_scale $scale && " +
                  "settings put global animator_duration_scale $scale"
        val name = "Set Animation Speeds to ${scale}x"
        val res = runCommand(name, cmd, "ANIMATIONS")
        if (res.isSuccess) {
            setState("animation_scale", scale)
        }
        return res
    }

    suspend fun trimCaches(): ExecutionResult {
        val cmd = "pm trim-caches 999G"
        val name = "Trim OS Expendable Caches (999G)"
        val res = runCommand(name, cmd, "CACHE_TRIM")
        if (res.isSuccess) {
            setState("last_cache_trim_timestamp", System.currentTimeMillis().toString())
        }
        return res
    }

    suspend fun fetchThermalService(): ExecutionResult {
        return runCommand("Inspect Thermals", "dumpsys thermalservice", "WAKELOCK_THERMAL")
    }

    suspend fun fetchCpuInfo(): ExecutionResult {
        return runCommand("Inspect Top CPU Usage", "dumpsys cpuinfo", "WAKELOCK_THERMAL")
    }

    suspend fun fetchWakelocks(): ExecutionResult {
        return runCommand("Inspect Active Wakelocks", "dumpsys power", "WAKELOCK_THERMAL")
    }
}
