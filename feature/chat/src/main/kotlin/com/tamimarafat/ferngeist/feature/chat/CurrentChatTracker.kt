package com.tamimarafat.ferngeist.feature.chat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Tracks which chat session (if any) the user is currently looking at. Used by
 * [com.tamimarafat.ferngeist.service.FerngeistForegroundService] to decide whether to
 * post a notification: an event for the *focused* session can be safely suppressed
 * because the user is already seeing the in-app sheet, but events for *other*
 * sessions still need a notification even when the app is in the foreground.
 *
 * The Compose layer registers via [setFocused] in a `DisposableEffect` keyed by
 * `sessionId` and pairs it with [clearFocused] in `onDispose`.
 */
object CurrentChatTracker {
    private val _focusedSessionId = MutableStateFlow<String?>(null)
    val focusedSessionId: StateFlow<String?> = _focusedSessionId.asStateFlow()

    fun setFocused(sessionId: String) {
        _focusedSessionId.value = sessionId
    }

    /** Only clears the field if the currently-focused session is exactly [sessionId]. */
    fun clearFocused(sessionId: String) {
        _focusedSessionId.update { current -> if (current == sessionId) null else current }
    }
}
