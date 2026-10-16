package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "command_logs")
data class CommandLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val commandName: String,
    val commandText: String,
    val category: String, // COMPILATION, DOZE, WAKELOCK_THERMAL, STANDBY_BUCKETS, BLOAT_FREEZE, ANIMATIONS, CACHE_TRIM, CUSTOM
    val executionMode: String, // SHIZUKU, ROOT, LOCAL_SHELL, SIMULATED
    val status: String, // RUNNING, SUCCESS, FAILED, CANCELLED
    val exitCode: Int = 0,
    val output: String = "",
    val durationMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)
