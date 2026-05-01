package com.tamimarafat.ferngeist.feature.sessionlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tamimarafat.ferngeist.acp.bridge.connection.AcpConnectionRegistry
import com.tamimarafat.ferngeist.acp.bridge.connection.AcpConnectionState
import com.tamimarafat.ferngeist.core.model.LaunchableTarget
import com.tamimarafat.ferngeist.core.model.Workspace
import com.tamimarafat.ferngeist.core.model.WorkspaceIds
import com.tamimarafat.ferngeist.core.model.repository.LaunchableTargetRepository
import com.tamimarafat.ferngeist.core.model.repository.SessionRepository
import com.tamimarafat.ferngeist.core.model.repository.WorkspaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Top-level workspace list. A "workspace" is a (helperKey, cwd) pair — the same group of
 * threads under Zed's threads sidebar. Threads from any agent that lives on the same helper
 * coexist within one workspace.
 */
@HiltViewModel
class WorkspaceListViewModel @Inject constructor(
    private val workspaceRepository: WorkspaceRepository,
    private val launchableTargetRepository: LaunchableTargetRepository,
    private val sessionRepository: SessionRepository,
    connectionRegistry: AcpConnectionRegistry,
) : ViewModel() {

    /**
     * Map of (workspace.helperKey -> true) when *any* agent on that helper is currently
     * Connected. Mirrors Zed's per-row status indicator in the threads sidebar — each
     * workspace card can show a small dot.
     */
    val helperConnectivity: StateFlow<Map<String, Boolean>> = combine(
        connectionRegistry.connectionStates,
        launchableTargetRepository.getTargets(),
    ) { states, targets ->
        // For each helperKey in use by any target, true if its serverId is Connected.
        targets.associate { target ->
            val helperKey = when (target) {
                is LaunchableTarget.HelperAgent -> target.helperSource.id
                is LaunchableTarget.Manual -> WorkspaceIds.helperKeyForManual(
                    target.server.scheme,
                    target.server.host,
                )
            }
            helperKey to (states[target.id] is AcpConnectionState.Connected)
        }
            .toList()
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, list) -> list.any { it } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    val workspaces: StateFlow<List<WorkspaceListItem>> = workspaceRepository.getAllWorkspaces()
        .flatMapLatest { list ->
            if (list.isEmpty()) {
                flowOf(emptyList())
            } else {
                val perWorkspaceCounts = list.map { workspace ->
                    sessionRepository.getSessionsForWorkspace(workspace.id).map { sessions ->
                        workspace to sessions.size
                    }
                }
                combine(perWorkspaceCounts) { pairs ->
                    pairs.map { (ws, count) ->
                        WorkspaceListItem(workspace = ws, threadCount = count)
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** All registered launchable targets — used as the "where does your project live?" picker. */
    val launchableTargets: StateFlow<List<LaunchableTarget>> = launchableTargetRepository.getTargets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiEffect = MutableStateFlow<WorkspaceListEffect?>(null)
    val uiEffect: StateFlow<WorkspaceListEffect?> = _uiEffect.asStateFlow()

    fun consumeEffect() { _uiEffect.value = null }

    /**
     * Create (or surface, if it already exists) a workspace at the given target's helper +
     * cwd, with optional override [displayName]. Emits [WorkspaceListEffect.WorkspaceReady]
     * with the resulting id so the UI can navigate to the detail screen.
     */
    fun createWorkspace(target: LaunchableTarget, cwd: String, displayName: String?) {
        val trimmed = cwd.trim().ifBlank { "/" }
        val helperKey = when (target) {
            is LaunchableTarget.HelperAgent -> target.helperSource.id
            is LaunchableTarget.Manual -> WorkspaceIds.helperKeyForManual(target.server.scheme, target.server.host)
        }
        viewModelScope.launch {
            val existing = workspaceRepository.findByKey(helperKey, trimmed)
            val workspace = existing ?: workspaceRepository.findOrCreate(helperKey, trimmed)
            val finalName = displayName?.takeIf { it.isNotBlank() }
            if (finalName != null && workspace.displayName != finalName) {
                workspaceRepository.rename(workspace.id, finalName)
            }
            _uiEffect.value = WorkspaceListEffect.WorkspaceReady(workspace.id)
        }
    }

    fun renameWorkspace(workspaceId: String, displayName: String?) {
        viewModelScope.launch {
            workspaceRepository.rename(workspaceId, displayName?.takeIf { it.isNotBlank() })
        }
    }

    fun deleteWorkspace(workspaceId: String) {
        viewModelScope.launch {
            workspaceRepository.delete(workspaceId, cascadeSessions = true)
        }
    }
}

data class WorkspaceListItem(
    val workspace: Workspace,
    val threadCount: Int,
)

sealed interface WorkspaceListEffect {
    data class WorkspaceReady(val workspaceId: String) : WorkspaceListEffect
}
