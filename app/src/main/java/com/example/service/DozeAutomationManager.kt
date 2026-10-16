package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.example.data.repository.CommandRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DozeAutomationManager(
    private val context: Context,
    private val repository: CommandRepository
) {
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var screenReceiver: BroadcastReceiver? = null
    private var delayedIdleJob: Job? = null

    private val _isAutomatorActive = MutableStateFlow(false)
    val isAutomatorActive = _isAutomatorActive.asStateFlow()

    fun startAutomation() {
        if (_isAutomatorActive.value) return

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }

        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        // Screen turned off: wait 15 seconds to ensure user didn't just lock accidentally,
                        // then force deep idle for maximum standby battery savings
                        delayedIdleJob?.cancel()
                        delayedIdleJob = scope.launch {
                            delay(15000L)
                            repository.forceDeepIdle(true)
                        }
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        // Screen turned on: cancel pending deep idle, unforce deep idle so apps sync immediately
                        delayedIdleJob?.cancel()
                        scope.launch {
                            repository.forceDeepIdle(false)
                        }
                    }
                }
            }
        }

        context.registerReceiver(screenReceiver, filter)
        _isAutomatorActive.value = true
        scope.launch {
            repository.setState("doze_automator_enabled", "true")
        }
    }

    fun stopAutomation() {
        delayedIdleJob?.cancel()
        screenReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Throwable) {}
        }
        screenReceiver = null
        _isAutomatorActive.value = false
        scope.launch {
            repository.setState("doze_automator_enabled", "false")
        }
    }
}
