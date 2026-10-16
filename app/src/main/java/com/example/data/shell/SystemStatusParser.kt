package com.example.data.shell

data class ThermalInfo(
    val statusLevel: Int = 0, // 0=Cool, 1=Light, 2=Moderate, 3=Severe, -1=Unknown
    val statusText: String = "Cool / Normal",
    val temperatures: List<String> = emptyList(),
    val rawOutput: String = ""
)

data class ProcessCpuInfo(
    val pid: String,
    val name: String,
    val totalPercentage: String,
    val userPercentage: String,
    val kernelPercentage: String
)

data class WakeLockItem(
    val tag: String,
    val type: String,
    val uid: String,
    val pid: String
)

object SystemStatusParser {

    fun parseThermalService(output: String): ThermalInfo {
        if (output.isBlank()) return ThermalInfo()

        var level = 0
        var statusText = "Cool / Normal"
        val temps = mutableListOf<String>()

        output.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.contains("Current thermal status:", ignoreCase = true)) {
                val match = Regex("Current thermal status:\\s*(\\d+)").find(trimmed)
                if (match != null) {
                    level = match.groupValues[1].toIntOrNull() ?: 0
                    statusText = when (level) {
                        0 -> "Cool / Normal (No Throttling)"
                        1 -> "Light Throttling"
                        2 -> "Moderate Throttling"
                        3 -> "Severe Throttling"
                        else -> "Critical / Overheating ($level)"
                    }
                }
            } else if (trimmed.contains("temperature", ignoreCase = true) || trimmed.contains("°C")) {
                temps.add(trimmed)
            }
        }

        return ThermalInfo(
            statusLevel = level,
            statusText = statusText,
            temperatures = temps.take(6),
            rawOutput = output
        )
    }

    fun parseCpuInfo(output: String): List<ProcessCpuInfo> {
        val list = mutableListOf<ProcessCpuInfo>()
        if (output.isBlank()) return list

        // Example line:
        //  12.4% 1842/system_server: 7.8% user + 4.6% kernel
        val regex = Regex("^\\s*([\\d.]+)%\\s+(\\d+)/([^:]+):\\s*([\\d.]+)%\\s*user\\s*\\+\\s*([\\d.]+)%\\s*kernel", RegexOption.MULTILINE)

        output.lines().forEach { line ->
            val match = regex.find(line)
            if (match != null) {
                val (total, pid, name, user, kernel) = match.destructured
                list.add(
                    ProcessCpuInfo(
                        pid = pid,
                        name = name.trim(),
                        totalPercentage = "$total%",
                        userPercentage = "$user%",
                        kernelPercentage = "$kernel%"
                    )
                )
            }
        }

        return list.take(15)
    }

    fun parseWakeLocks(output: String): List<WakeLockItem> {
        val list = mutableListOf<WakeLockItem>()
        if (output.isBlank()) return list

        val lines = output.lines()
        var inWakelocksSection = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Wake Locks:", ignoreCase = true)) {
                inWakelocksSection = true
                continue
            }
            if (inWakelocksSection && trimmed.startsWith("Suspend Blockers:", ignoreCase = true)) {
                break
            }
            if (inWakelocksSection && trimmed.isNotEmpty()) {
                // e.g., PARTIAL_WAKE_LOCK 'AudioMix' (uid=1041, pid=1842)
                val type = when {
                    trimmed.contains("PARTIAL_WAKE_LOCK") -> "PARTIAL"
                    trimmed.contains("SCREEN_BRIGHT_WAKE_LOCK") -> "SCREEN_BRIGHT"
                    trimmed.contains("SCREEN_DIM_WAKE_LOCK") -> "SCREEN_DIM"
                    trimmed.contains("FULL_WAKE_LOCK") -> "FULL"
                    trimmed.contains("PROXIMITY_SCREEN_OFF_WAKE_LOCK") -> "PROXIMITY"
                    else -> "WAKELOCK"
                }

                val tagMatch = Regex("'([^']+)'").find(trimmed)
                val uidMatch = Regex("uid=(\\d+)").find(trimmed)
                val pidMatch = Regex("pid=(\\d+)").find(trimmed)

                val tag = tagMatch?.groupValues?.get(1) ?: trimmed
                val uid = uidMatch?.groupValues?.get(1) ?: "?"
                val pid = pidMatch?.groupValues?.get(1) ?: "?"

                if (tag.isNotEmpty()) {
                    list.add(WakeLockItem(tag = tag, type = type, uid = uid, pid = pid))
                }
            }
        }

        return list
    }

    fun parseStandbyBucket(output: String): String {
        val trimmed = output.trim()
        val num = trimmed.filter { it.isDigit() }.toIntOrNull()
        return when (num) {
            10 -> "Active (10)"
            20 -> "Working Set (20)"
            30 -> "Frequent (30)"
            40 -> "Rare (40)"
            45 -> "Restricted (45)"
            else -> when {
                trimmed.contains("active", ignoreCase = true) -> "Active (10)"
                trimmed.contains("working_set", ignoreCase = true) -> "Working Set (20)"
                trimmed.contains("frequent", ignoreCase = true) -> "Frequent (30)"
                trimmed.contains("rare", ignoreCase = true) -> "Rare (40)"
                trimmed.contains("restricted", ignoreCase = true) -> "Restricted (45)"
                else -> trimmed.ifBlank { "Unknown" }
            }
        }
    }
}
