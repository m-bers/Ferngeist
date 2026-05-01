package com.tamimarafat.ferngeist.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.tamimarafat.ferngeist.acp.bridge.connection.AcpConnectionRegistry
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives action button taps on input-required notifications and forwards them to the
 * appropriate per-server [AcpConnectionRegistry.connectionFor] for grant / deny.
 *
 * Intents must carry [EXTRA_SERVER_ID], [EXTRA_TOOL_CALL_ID], and:
 * - For [ACTION_GRANT]: [EXTRA_OPTION_ID] (the chosen `PermissionOption.id`).
 * - For [ACTION_DENY]: no further extras.
 *
 * The notification (carried via [EXTRA_NOTIFICATION_ID]) is cancelled immediately, before
 * the ACP response is sent, so the user sees instant feedback.
 */
@AndroidEntryPoint
class PermissionActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var connectionRegistry: AcpConnectionRegistry

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == ACTION_REMOTE_REPLY) {
            handleRemoteReply(context, intent)
            return
        }
        val serverId = intent.getStringExtra(EXTRA_SERVER_ID) ?: return
        val toolCallId = intent.getStringExtra(EXTRA_TOOL_CALL_ID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        if (notificationId >= 0) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm?.cancel(notificationId)
        }

        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val manager = connectionRegistry.existingConnectionFor(serverId)
                    ?: return@launch
                when (action) {
                    ACTION_GRANT -> {
                        val optionId = intent.getStringExtra(EXTRA_OPTION_ID) ?: return@launch
                        manager.respondPermissionSelected(
                            sessionId = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty(),
                            toolCallId = toolCallId,
                            optionId = optionId,
                        )
                    }
                    ACTION_DENY -> {
                        manager.respondPermissionCancelled(
                            sessionId = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty(),
                            toolCallId = toolCallId,
                        )
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Receives the RemoteInput text reply from the end-of-turn notification and dispatches it
     * as the next prompt on the relevant session, without requiring the app to be opened.
     */
    private fun handleRemoteReply(context: Context, intent: Intent) {
        val serverId = intent.getStringExtra(EXTRA_SERVER_ID) ?: return
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val results = RemoteInput.getResultsFromIntent(intent) ?: return
        val text = results.getCharSequence(KEY_REMOTE_INPUT_TEXT)?.toString()?.trim().orEmpty()
        if (text.isBlank()) return

        if (notificationId >= 0) {
            context.getSystemService(NotificationManager::class.java)?.cancel(notificationId)
        }

        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val manager = connectionRegistry.existingConnectionFor(serverId) ?: return@launch
                runCatching {
                    manager.sendSessionMessage(
                        sessionId = sessionId,
                        content = text,
                        images = emptyList(),
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_GRANT = "com.tamimarafat.ferngeist.ACTION_PERMISSION_GRANT"
        const val ACTION_DENY = "com.tamimarafat.ferngeist.ACTION_PERMISSION_DENY"
        const val ACTION_REMOTE_REPLY = "com.tamimarafat.ferngeist.ACTION_REMOTE_REPLY"

        const val EXTRA_SERVER_ID = "serverId"
        const val EXTRA_SESSION_ID = "sessionId"
        const val EXTRA_TOOL_CALL_ID = "toolCallId"
        const val EXTRA_OPTION_ID = "optionId"
        const val EXTRA_NOTIFICATION_ID = "notificationId"
        const val KEY_REMOTE_INPUT_TEXT = "key_remote_input_text"
    }
}
