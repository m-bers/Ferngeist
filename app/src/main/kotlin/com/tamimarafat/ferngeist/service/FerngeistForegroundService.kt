package com.tamimarafat.ferngeist.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.tamimarafat.ferngeist.MainActivity
import com.tamimarafat.ferngeist.R
import com.tamimarafat.ferngeist.acp.bridge.connection.AcpConnectionRegistry
import com.tamimarafat.ferngeist.acp.bridge.connection.AcpConnectionState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FerngeistForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "ferngeist_connection"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.tamimarafat.ferngeist.ACTION_START_FOREGROUND"
        const val ACTION_STOP = "com.tamimarafat.ferngeist.ACTION_STOP_FOREGROUND"
        const val ACTION_DISCONNECT = "com.tamimarafat.ferngeist.ACTION_DISCONNECT"
    }

    @Inject
    lateinit var connectionRegistry: AcpConnectionRegistry

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observationJob: Job? = null
    private var isStarted = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                observationJob?.cancel()
                scope.launch {
                    connectionRegistry.disconnectAll()
                    stopSelf()
                }
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        if (!isStarted) {
            isStarted = true
            val notification = buildNotification(connectionRegistry.connectionStates.value)
            startForeground(NOTIFICATION_ID, notification)
            observeConnectionState()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        observationJob?.cancel()
        scope.cancel()
        isStarted = false
        super.onDestroy()
    }

    private fun observeConnectionState() {
        observationJob?.cancel()
        observationJob = scope.launch {
            connectionRegistry.connectionStates
                .collect { states ->
                    if (!isStarted) return@collect
                    updateNotification(states)
                    if (states.isNotEmpty() && states.values.all { it is AcpConnectionState.Disconnected }) {
                        stopSelf()
                    }
                }
        }
    }

    private fun updateNotification(states: Map<String, AcpConnectionState>) {
        if (!isStarted) return
        val notification = buildNotification(states)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(states: Map<String, AcpConnectionState>): Notification {
        val connectedServerIds = states.entries
            .filter { it.value is AcpConnectionState.Connected }
            .map { it.key }
        val connecting = states.values.any { it is AcpConnectionState.Connecting }
        val failed = states.values.firstOrNull { it is AcpConnectionState.Failed } as? AcpConnectionState.Failed

        val (title, text) = when {
            connectedServerIds.isNotEmpty() -> {
                val displayNames = connectedServerIds.mapNotNull { id ->
                    val mgr = connectionRegistry.existingConnectionFor(id)
                    mgr?.currentConnectionConfig()?.serverDisplayName ?: mgr?.agentInfo?.value?.name
                }
                val label = when (displayNames.size) {
                    0 -> "agent"
                    1 -> displayNames[0]
                    else -> "${displayNames.size} agents"
                }
                getString(R.string.notification_connected_title) to
                    getString(R.string.notification_connected_text, label)
            }
            connecting -> {
                getString(R.string.notification_connecting_title) to
                    getString(R.string.notification_connecting_text)
            }
            failed != null -> {
                getString(R.string.notification_failed_title) to
                    (failed.error.message ?: getString(R.string.notification_failed_text))
            }
            else -> {
                getString(R.string.notification_disconnected_title) to
                    getString(R.string.notification_disconnected_text)
            }
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val disconnectIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, FerngeistForegroundService::class.java).apply {
                action = ACTION_DISCONNECT
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(
                R.drawable.ic_stop_solid,
                getString(R.string.notification_action_disconnect),
                disconnectIntent,
            )
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
