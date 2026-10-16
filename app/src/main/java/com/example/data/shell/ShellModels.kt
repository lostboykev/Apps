package com.example.data.shell

enum class ShellMode {
    SHIZUKU,      // UID 2000 (ADB shell privileges via Shizuku)
    ROOT,         // UID 0 (Superuser / Magisk / KernelSU)
    LOCAL_SHELL,  // Normal app process shell
    SIMULATION    // Safe emulator demonstration & testing mode
}

data class ExecutionResult(
    val exitCode: Int,
    val output: String,
    val isSuccess: Boolean,
    val durationMs: Long,
    val wasCancelled: Boolean = false
)

data class ShizukuStatus(
    val isAvailable: Boolean = false,
    val isPermissionGranted: Boolean = false,
    val currentMode: ShellMode = ShellMode.SIMULATION,
    val details: String = ""
)
