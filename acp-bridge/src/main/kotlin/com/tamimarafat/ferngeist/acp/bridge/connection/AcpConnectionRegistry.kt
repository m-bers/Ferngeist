package com.tamimarafat.ferngeist.acp.bridge.connection

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry of per-server [AcpConnectionManager] instances. Each ACP server (e.g. Claude
 * helper, Gemini helper) gets its own manager with its own websocket transport, session
 * registry, and connection state. The user can talk to multiple agents in parallel — the
 * connection state for one agent does not affect another.
 *
 * Each manager runs under its own SupervisorJob child of the registry's parent scope, so
 * a failure in one server's coroutines does not cancel another server's work.
 */
class AcpConnectionRegistry(
    private val connectivityObserverFactory: () -> ConnectivityObserver,
    private val parentScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {

    private data class Entry(
        val manager: AcpConnectionManager,
        val scope: CoroutineScope,
    )

    private val entries = ConcurrentHashMap<String, Entry>()

    private val _activeServerIds = MutableStateFlow<Set<String>>(emptySet())
    val activeServerIds: StateFlow<Set<String>> = _activeServerIds.asStateFlow()

    /**
     * Returns (and lazily creates) the [AcpConnectionManager] for [serverId]. The first
     * call for a given server allocates a new transport + scope; subsequent calls return
     * the same instance.
     */
    fun connectionFor(serverId: String): AcpConnectionManager {
        return entries.getOrPut(serverId) {
            val parentJob = parentScope.coroutineContext[Job]
            val childScope = CoroutineScope(SupervisorJob(parentJob) + Dispatchers.Main)
            val manager = AcpConnectionManager(connectivityObserverFactory(), childScope)
            _activeServerIds.value = _activeServerIds.value + serverId
            Entry(manager = manager, scope = childScope)
        }.manager
    }

    /** Returns the existing manager without creating one. */
    fun existingConnectionFor(serverId: String): AcpConnectionManager? = entries[serverId]?.manager

    /**
     * Aggregated per-server connection state, keyed by serverId. Used by the foreground
     * service / app process to render a multi-server status surface.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val connectionStates: StateFlow<Map<String, AcpConnectionState>> =
        _activeServerIds.flatMapLatest { ids ->
            if (ids.isEmpty()) {
                flowOf(emptyMap())
            } else {
                val flows = ids.mapNotNull { id ->
                    entries[id]?.manager?.connectionState?.map { state -> id to state }
                }
                if (flows.isEmpty()) flowOf(emptyMap())
                else combine(flows) { pairs -> pairs.toMap() }
            }
        }.stateIn(parentScope, SharingStarted.Eagerly, emptyMap())

    /** True if at least one connection is in Connected/Connecting/Failed (i.e. not Disconnected). */
    val hasAnyActiveConnection: StateFlow<Boolean> =
        connectionStates.map { states ->
            states.values.any { it !is AcpConnectionState.Disconnected }
        }.stateIn(parentScope, SharingStarted.Eagerly, false)

    /** Disconnect a single server's connection. The manager and its scope are retained. */
    suspend fun disconnect(serverId: String) {
        entries[serverId]?.manager?.disconnect()
    }

    /** Disconnect every active connection. Used by the foreground service "Disconnect" action. */
    suspend fun disconnectAll() {
        entries.values.toList().forEach { it.manager.disconnect() }
    }

    /**
     * Forget a server entirely: disconnect, cancel its scope, and remove from the registry.
     * Use when the user removes a server. Subsequent [connectionFor] calls will allocate
     * a fresh manager.
     */
    suspend fun removeServer(serverId: String) {
        val removed = entries.remove(serverId) ?: return
        runCatching { removed.manager.disconnect() }
        removed.scope.cancel()
        _activeServerIds.value = _activeServerIds.value - serverId
    }
}
