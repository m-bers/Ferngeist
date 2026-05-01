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
import androidx.core.app.RemoteInput
import com.tamimarafat.ferngeist.acp.bridge.connection.AcpConnectionRegistry
import com.tamimarafat.ferngeist.acp.bridge.connection.AcpConnectionState
import com.tamimarafat.ferngeist.acp.bridge.connection.PermissionFlowEvent
import com.tamimarafat.ferngeist.acp.bridge.connection.TaggedPermissionEvent
import com.tamimarafat.ferngeist.acp.bridge.connection.TaggedTurnCompleteEvent
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
        /** High-importance channel used for input-required notifications. */
        const val INPUT_REQUIRED_CHANNEL_ID = "ferngeist_input_required"
        /** Default-importance channel for "agent is done, reply?" notifications. */
        const val TURN_COMPLETE_CHANNEL_ID = "ferngeist_turn_complete"
        const val NOTIFICATION_ID = 1
        /**
         * Permission notifications get an id derived from `toolCallId.hashCode()` so
         * we can cancel/replace them by the same id when the request is resolved.
         * Reserved range starts above [NOTIFICATION_ID] to avoid collision with the
         * connection-status notification.
         */
        const val PERMISSION_NOTIFICATION_ID_BASE = 1000
        /**
         * Turn-complete notifications get an id derived from `sessionId.hashCode()`
         * (offset to avoid collision with [PERMISSION_NOTIFICATION_ID_BASE]).
         */
        const val TURN_COMPLETE_NOTIFICATION_ID_BASE = 100_000

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
        createInputRequiredChannel()
        createTurnCompleteChannel()
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
            launch {
                connectionRegistry.connectionStates
                    .collect { states ->
                        if (!isStarted) return@collect
                        updateNotification(states)
                        if (states.isNotEmpty() && states.values.all { it is AcpConnectionState.Disconnected }) {
                            stopSelf()
                        }
                    }
            }
            launch {
                connectionRegistry.permissionEvents.collect { tagged ->
                    handlePermissionEvent(tagged)
                }
            }
            launch {
                connectionRegistry.turnCompleteEvents.collect { tagged ->
                    handleTurnCompleteEvent(tagged)
                }
            }
        }
    }

    private fun handleTurnCompleteEvent(tagged: TaggedTurnCompleteEvent) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val notificationId = TURN_COMPLETE_NOTIFICATION_ID_BASE + tagged.event.sessionId.hashCode().and(0x7FFF_FFFF)

        val agentName = connectionRegistry.existingConnectionFor(tagged.serverId)
            ?.let { it.currentConnectionConfig()?.serverDisplayName ?: it.agentInfo.value?.name }
            ?: "Agent"

        val replyIntent = Intent(this, PermissionActionReceiver::class.java).apply {
            action = PermissionActionReceiver.ACTION_REMOTE_REPLY
            putExtra(PermissionActionReceiver.EXTRA_SERVER_ID, tagged.serverId)
            putExtra(PermissionActionReceiver.EXTRA_SESSION_ID, tagged.event.sessionId)
            putExtra(PermissionActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val replyPi = PendingIntent.getBroadcast(
            this,
            notificationId,
            replyIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val remoteInput = RemoteInput.Builder(PermissionActionReceiver.KEY_REMOTE_INPUT_TEXT)
            .setLabel(getString(R.string.notification_turn_complete_reply_hint))
            .build()
        val replyAction = NotificationCompat.Action.Builder(
            0,
            getString(R.string.notification_turn_complete_reply_action),
            replyPi,
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()

        val contentIntent = PendingIntent.getActivity(
            this,
            notificationId,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, TURN_COMPLETE_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_turn_complete_title, agentName))
            .setContentText(getString(R.string.notification_turn_complete_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(replyAction)
            .build()

        nm.notify(notificationId, notification)
    }

    private fun handlePermissionEvent(tagged: TaggedPermissionEvent) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val notificationId = PERMISSION_NOTIFICATION_ID_BASE + tagged.event.toolCallId.hashCode()
        when (val event = tagged.event) {
            is PermissionFlowEvent.Requested -> {
                val notification = buildPermissionNotification(
                    serverId = tagged.serverId,
                    event = event,
                    notificationId = notificationId,
                )
                nm.notify(notificationId, notification)
            }
            is PermissionFlowEvent.Resolved -> {
                nm.cancel(notificationId)
            }
        }
    }

    private fun buildPermissionNotification(
        serverId: String,
        event: PermissionFlowEvent.Requested,
        notificationId: Int,
    ): Notification {
        val agentName = connectionRegistry.existingConnectionFor(serverId)
            ?.let { it.currentConnectionConfig()?.serverDisplayName ?: it.agentInfo.value?.name }
            ?: "Agent"

        val contentIntent = PendingIntent.getActivity(
            this,
            notificationId,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(this, INPUT_REQUIRED_CHANNEL_ID)
            .setContentTitle("$agentName needs input")
            .setContentText(event.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(event.title))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)

        // Up to 3 inline action buttons: lock-screen / collapsed shows these. Beyond 3,
        // the rest are still reachable by expanding the notification (BigText) — and we
        // always include a Deny fallback that's distinct from the option list.
        val maxInlineOptions = 3
        event.options.take(maxInlineOptions).forEach { option ->
            val intent = Intent(this, PermissionActionReceiver::class.java).apply {
                action = PermissionActionReceiver.ACTION_GRANT
                putExtra(PermissionActionReceiver.EXTRA_SERVER_ID, serverId)
                putExtra(PermissionActionReceiver.EXTRA_SESSION_ID, event.sessionId)
                putExtra(PermissionActionReceiver.EXTRA_TOOL_CALL_ID, event.toolCallId)
                putExtra(PermissionActionReceiver.EXTRA_OPTION_ID, option.id)
                putExtra(PermissionActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            val pi = PendingIntent.getBroadcast(
                this,
                notificationId * 16 + option.id.hashCode().and(0x0F),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, option.label, pi)
        }

        // Deny is always present, last.
        val denyIntent = Intent(this, PermissionActionReceiver::class.java).apply {
            action = PermissionActionReceiver.ACTION_DENY
            putExtra(PermissionActionReceiver.EXTRA_SERVER_ID, serverId)
            putExtra(PermissionActionReceiver.EXTRA_SESSION_ID, event.sessionId)
            putExtra(PermissionActionReceiver.EXTRA_TOOL_CALL_ID, event.toolCallId)
            putExtra(PermissionActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val denyPi = PendingIntent.getBroadcast(
            this,
            notificationId * 16 + 15,
            denyIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        builder.addAction(0, "Deny", denyPi)

        return builder.build()
    }

    private fun createInputRequiredChannel() {
        val channel = NotificationChannel(
            INPUT_REQUIRED_CHANNEL_ID,
            getString(R.string.notification_channel_input_required_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.notification_channel_input_required_description)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun createTurnCompleteChannel() {
        val channel = NotificationChannel(
            TURN_COMPLETE_CHANNEL_ID,
            getString(R.string.notification_channel_turn_complete_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = getString(R.string.notification_channel_turn_complete_description)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
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
