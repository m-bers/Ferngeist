package com.tamimarafat.ferngeist.feature.chat

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

/**
 * Persists the composer's draft text per (serverId, sessionId) so the user doesn't
 * lose what they typed when navigating between threads / killing the activity.
 */
interface ChatDraftStore {
    fun restore(serverId: String, sessionId: String): String?
    fun save(serverId: String, sessionId: String, draft: String)
    fun clear(serverId: String, sessionId: String)
}

@Singleton
class SharedPreferencesChatDraftStore @Inject constructor(
    @ApplicationContext context: Context,
) : ChatDraftStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun restore(serverId: String, sessionId: String): String? {
        return prefs.getString(key(serverId, sessionId), null)?.takeIf { it.isNotEmpty() }
    }

    override fun save(serverId: String, sessionId: String, draft: String) {
        prefs.edit {
            if (draft.isEmpty()) {
                remove(key(serverId, sessionId))
            } else {
                putString(key(serverId, sessionId), draft)
            }
        }
    }

    override fun clear(serverId: String, sessionId: String) {
        prefs.edit { remove(key(serverId, sessionId)) }
    }

    private fun key(serverId: String, sessionId: String) = "draft.$serverId.$sessionId"

    private companion object {
        const val PREFS_NAME = "ferngeist_chat_drafts"
    }
}
