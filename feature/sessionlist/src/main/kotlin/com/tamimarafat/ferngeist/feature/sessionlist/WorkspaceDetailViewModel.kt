package com.tamimarafat.ferngeist.feature.sessionlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tamimarafat.ferngeist.acp.bridge.connection.AcpConnectionRegistry
import com.tamimarafat.ferngeist.acp.bridge.connection.AcpConnectionState
import com.tamimarafat.ferngeist.core.model.LaunchableTarget
import com.tamimarafat.ferngeist.core.model.SessionSummary
import com.tamimarafat.ferngeist.core.model.Workspace
import com.tamimarafat.ferngeist.core.model.WorkspaceIds
import com.tamimarafat.ferngeist.core.model.repository.LaunchableTargetRepository
import com.tamimarafat.ferngeist.core.model.repository.SessionRepository
import com.tamimarafat.ferngeist.core.model.repository.WorkspaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Loads a single Workspace and shows the threads belonging to it across all agents on
 * its helper. Uses [SavedStateHandle] to receive `workspaceId` from the navigation graph.
 */
@HiltViewModel
class WorkspaceDetailViewModel @Inject constructor(
    private val workspaceRepository: WorkspaceRepository,
    private val launchableTargetRepository: LaunchableTargetRepository,
    private val sessionRepository: SessionRepository,
    private val connectionRegistry: AcpConnectionRegistry,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val workspaceId: String = savedStateHandle["workspaceId"] ?: error("workspaceId is required")

    private val _workspace = MutableStateFlow<Workspace?>(null)
    val workspace: StateFlow<Workspace?> = _workspace.asStateFlow()

    val threads: StateFlow<List<WorkspaceThread>> = sessionRepository
        .getSessionsForWorkspace(workspaceId)
        .combine(launchableTargetRepository.getTargets()) { sessions, targets ->
            sessions.map { session -> session.toThread(targets) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val archivedThreads: StateFlow<List<WorkspaceThread>> = sessionRepository
        .getArchivedSessionsForWorkspace(workspaceId)
        .combine(launchableTargetRepository.getTargets()) { sessions, targets ->
            sessions.map { session -> session.toThread(targets) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Agents that can serve this workspace (i.e. live on the workspace's helperKey).
     * Used when the user starts a new thread.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val availableAgents: StateFlow<List<LaunchableTarget>> = workspace
        .flatMapLatest { ws ->
            if (ws == null) flowOf(emptyList())
            else launchableTargetRepository.getTargets().map { all ->
                all.filter { target -> target.helperKey() == ws.helperKey }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Per-target connection state, used for the per-row dot. */
    val connectionStates: StateFlow<Map<String, AcpConnectionState>> =
        connectionRegistry.connectionStates
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** serverIds we've already pulled live sessions for in this VM's lifetime. */
    private val refreshedServerIds = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            _workspace.value = workspaceRepository.getById(workspaceId)
        }
        // C5: when an agent on this workspace's helper is connected, pull its server-side
        // session list once and upsert into the local DB so threads created on another
        // device (or via Zed's CLI directly) appear here. Idempotent — refreshedServerIds
        // tracks which servers we've already pulled in this VM lifetime.
        viewModelScope.launch {
            availableAgents.collect { targets ->
                val ws = _workspace.value ?: return@collect
                targets.forEach { target ->
                    if (target.id in refreshedServerIds) return@forEach
                    val mgr = connectionRegistry.existingConnectionFor(target.id) ?: return@forEach
                    if (mgr.connectionState.value !is AcpConnectionState.Connected) return@forEach
                    refreshedServerIds += target.id
                    runCatching {
                        @Suppress("NewApi")
                        mgr.listSessions(cwd = ws.cwd)
                    }.onSuccess { remote ->
                        remote.forEach { summary ->
                            sessionRepository.upsertSession(
                                serverId = target.id,
                                workspaceId = workspaceId,
                                summary = summary.copy(serverId = target.id),
                            )
                        }
                    }
                }
            }
        }
    }

    fun rename(displayName: String?) {
        viewModelScope.launch {
            workspaceRepository.rename(workspaceId, displayName?.takeIf { it.isNotBlank() })
            _workspace.value = workspaceRepository.getById(workspaceId)
        }
    }

    fun delete() {
        viewModelScope.launch {
            workspaceRepository.delete(workspaceId, cascadeSessions = true)
        }
    }

    fun deleteThread(serverId: String, sessionId: String) {
        viewModelScope.launch {
            sessionRepository.deleteSession(serverId, sessionId)
        }
    }

    fun archiveThread(sessionId: String) {
        viewModelScope.launch { sessionRepository.archiveSession(sessionId) }
    }

    fun unarchiveThread(sessionId: String) {
        viewModelScope.launch { sessionRepository.unarchiveSession(sessionId) }
    }

    private fun SessionSummary.toThread(targets: List<LaunchableTarget>): WorkspaceThread {
        val target = serverId?.let { id -> targets.firstOrNull { it.id == id } }
        return WorkspaceThread(
            sessionId = id,
            serverId = serverId,
            title = title,
            cwd = cwd,
            updatedAt = updatedAt,
            agentName = target?.name,
            agentId = target?.id,
        )
    }

    private fun LaunchableTarget.helperKey(): String = when (this) {
        is LaunchableTarget.HelperAgent -> helperSource.id
        is LaunchableTarget.Manual -> WorkspaceIds.helperKeyForManual(server.scheme, server.host)
    }
}

data class WorkspaceThread(
    val sessionId: String,
    val serverId: String?,
    val title: String?,
    val cwd: String?,
    val updatedAt: Long?,
    val agentName: String?,
    val agentId: String?,
)
