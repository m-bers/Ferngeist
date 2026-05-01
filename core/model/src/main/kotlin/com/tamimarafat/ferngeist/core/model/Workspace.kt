package com.tamimarafat.ferngeist.core.model

/**
 * A Workspace groups threads (sessions) by their working directory on a specific helper /
 * server filesystem. The same `cwd` on the same helper hosts threads from any number of
 * agents — Claude, Gemini, etc. all coexist under one Workspace, matching Zed's threads-
 * sidebar model.
 *
 * Identity is `(helperKey, cwd)`, encoded as the literal string `"$helperKey|$cwd"` so the
 * lookup is deterministic without requiring UUIDs. `helperKey` is either:
 * - the desktop-helper-source id (for HelperAgent-backed servers), so multiple agents on
 *   the same helper share workspaces, or
 * - `"manual:<scheme>://<host>"` for manual servers, scoped per host.
 */
data class Workspace(
    val id: String,
    val helperKey: String,
    val cwd: String,
    val displayName: String?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    /** Human-friendly name: user override, else the cwd. */
    val name: String get() = displayName?.takeIf { it.isNotBlank() } ?: cwd
}

object WorkspaceIds {
    /** Compose the deterministic workspace id from its key parts. */
    fun encode(helperKey: String, cwd: String): String = "$helperKey|$cwd"

    fun helperKeyForManual(scheme: String, host: String): String = "manual:$scheme://$host"
}
