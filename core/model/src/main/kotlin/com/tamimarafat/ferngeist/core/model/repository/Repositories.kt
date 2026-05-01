package com.tamimarafat.ferngeist.core.model.repository

import com.tamimarafat.ferngeist.core.model.DesktopHelperSource
import com.tamimarafat.ferngeist.core.model.HelperAgentBinding
import com.tamimarafat.ferngeist.core.model.LaunchableTarget
import com.tamimarafat.ferngeist.core.model.LaunchableTargetSessionSettings
import com.tamimarafat.ferngeist.core.model.ServerConfig
import com.tamimarafat.ferngeist.core.model.SessionSummary
import com.tamimarafat.ferngeist.core.model.Workspace
import kotlinx.coroutines.flow.Flow

interface ServerRepository {
    fun getServers(): Flow<List<ServerConfig>>
    suspend fun addServer(config: ServerConfig)
    suspend fun updateServer(config: ServerConfig)
    suspend fun deleteServer(id: String)
    suspend fun getServer(id: String): ServerConfig?
}

interface DesktopHelperSourceRepository {
    fun getHelpers(): Flow<List<DesktopHelperSource>>
    suspend fun addHelper(helper: DesktopHelperSource)
    suspend fun updateHelper(helper: DesktopHelperSource)
    suspend fun deleteHelper(id: String)
    suspend fun getHelper(id: String): DesktopHelperSource?
}

interface HelperAgentBindingRepository {
    fun getBindings(): Flow<List<HelperAgentBinding>>
    suspend fun addBinding(binding: HelperAgentBinding)
    suspend fun updateBinding(binding: HelperAgentBinding)
    suspend fun deleteBinding(id: String)
    suspend fun getBinding(id: String): HelperAgentBinding?
    suspend fun getBindingsForHelper(helperId: String): List<HelperAgentBinding>
}

interface LaunchableTargetRepository {
    fun getTargets(): Flow<List<LaunchableTarget>>
    suspend fun getTarget(id: String): LaunchableTarget?
    suspend fun updatePreferredAuthMethod(targetId: String, methodId: String)
    suspend fun deleteTarget(id: String)
}

interface SessionRepository {
    fun getSessions(serverId: String): Flow<List<SessionSummary>>
    fun getSessionsForWorkspace(workspaceId: String): Flow<List<SessionSummary>>
    suspend fun upsertSession(serverId: String, summary: SessionSummary)
    suspend fun upsertSession(serverId: String, workspaceId: String?, summary: SessionSummary)
    suspend fun deleteSession(serverId: String, sessionId: String)
    suspend fun clearSessions(serverId: String)
}

interface WorkspaceRepository {
    /** All workspaces, most-recently-updated first. */
    fun getAllWorkspaces(): Flow<List<Workspace>>

    /** Existing workspace for the given key, or null. */
    suspend fun findByKey(helperKey: String, cwd: String): Workspace?

    /** Find an existing workspace by id. */
    suspend fun getById(id: String): Workspace?

    /**
     * Find an existing workspace for [helperKey]+[cwd], or create one if absent. Returns
     * the workspace's id, suitable for setting `Session.workspaceId`.
     */
    suspend fun findOrCreate(helperKey: String, cwd: String): Workspace

    /** Resolve a server id to its helper key (used when creating workspaces from session context). */
    suspend fun helperKeyForServer(serverId: String): String?

    /** Convenience: resolve helperKey for the server, then find-or-create the workspace. */
    suspend fun findOrCreateForServer(serverId: String, cwd: String): Workspace?

    /** Rename a workspace (sets displayName). null clears the override. */
    suspend fun rename(workspaceId: String, displayName: String?)

    /** Mark a workspace as recently used. */
    suspend fun touch(workspaceId: String)

    /** Delete a workspace and (optionally) cascade-delete its sessions. */
    suspend fun delete(workspaceId: String, cascadeSessions: Boolean = true)
}

interface LaunchableTargetSessionSettingsRepository {
    fun getSettings(targetId: String): Flow<LaunchableTargetSessionSettings>
    suspend fun getSettingsBlocking(targetId: String): LaunchableTargetSessionSettings?
    suspend fun updateCwd(targetId: String, cwd: String)
    suspend fun deleteSettings(targetId: String)
}
