package com.tamimarafat.ferngeist

import android.app.Application
import android.util.Log
import com.tamimarafat.ferngeist.acp.bridge.connection.AcpConnectionRegistry
import com.tamimarafat.ferngeist.service.ForegroundServiceController
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FerngeistApplication : Application() {

    @Inject
    lateinit var connectionRegistry: AcpConnectionRegistry

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var isServiceRunning = false

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("FerngeistCrash", "Uncaught exception on thread=${thread.name}", throwable)
            previous?.uncaughtException(thread, throwable)
        }

        appScope.launch {
            connectionRegistry.hasAnyActiveConnection
                .collect { hasActive ->
                    if (hasActive) {
                        if (!isServiceRunning) {
                            isServiceRunning = true
                            ForegroundServiceController.start(this@FerngeistApplication)
                        }
                    } else {
                        if (isServiceRunning) {
                            isServiceRunning = false
                            ForegroundServiceController.stop(this@FerngeistApplication)
                        }
                    }
                }
        }
    }

    override fun onTerminate() {
        appScope.cancel()
        super.onTerminate()
    }
}