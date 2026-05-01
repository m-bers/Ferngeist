package com.tamimarafat.ferngeist.feature.sessionlist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tamimarafat.ferngeist.acp.bridge.connection.AcpConnectionState
import com.tamimarafat.ferngeist.core.model.LaunchableTarget
import com.tamimarafat.ferngeist.feature.sessionlist.WorkspaceDetailViewModel
import com.tamimarafat.ferngeist.feature.sessionlist.WorkspaceThread
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceDetailScreen(
    viewModel: WorkspaceDetailViewModel,
    onNavigateBack: () -> Unit,
    onOpenChat: (serverId: String, sessionId: String, cwd: String, workspaceId: String, title: String?) -> Unit,
) {
    val workspace by viewModel.workspace.collectAsState()
    val threads by viewModel.threads.collectAsState()
    val archivedThreads by viewModel.archivedThreads.collectAsState()
    val agents by viewModel.availableAgents.collectAsState()
    val connectionStates by viewModel.connectionStates.collectAsState()

    var showAgentPicker by rememberSaveable { mutableStateOf(false) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showArchived by rememberSaveable { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val ws = workspace

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            ws?.name ?: "Workspace",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (ws?.displayName != null) {
                            Text(
                                ws.cwd,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(if (showArchived) "Show active" else "Show archived (${archivedThreads.size})")
                                },
                                onClick = {
                                    menuExpanded = false
                                    showArchived = !showArchived
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                onClick = {
                                    menuExpanded = false
                                    showRenameDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = {
                                    menuExpanded = false
                                    showDeleteDialog = true
                                },
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAgentPicker = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New thread")
            }
        },
    ) { padding ->
        val displayThreads = if (showArchived) archivedThreads else threads
        if (displayThreads.isEmpty()) {
            EmptyThreadsState(
                hasAgents = agents.isNotEmpty(),
                isArchivedView = showArchived,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 24.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(displayThreads, key = { it.sessionId }) { thread ->
                    ThreadCard(
                        thread = thread,
                        connectionState = thread.serverId?.let { connectionStates[it] },
                        isArchived = showArchived,
                        onClick = {
                            val serverId = thread.serverId ?: return@ThreadCard
                            val cwd = thread.cwd ?: ws?.cwd ?: "/"
                            onOpenChat(serverId, thread.sessionId, cwd, viewModel.workspaceId, thread.title)
                        },
                        onArchive = { viewModel.archiveThread(thread.sessionId) },
                        onUnarchive = { viewModel.unarchiveThread(thread.sessionId) },
                    )
                }
            }
        }
    }

    if (showAgentPicker) {
        AgentPickerDialog(
            agents = agents,
            onDismiss = { showAgentPicker = false },
            onPick = { target ->
                val cwd = ws?.cwd ?: "/"
                val newSessionId = UUID.randomUUID().toString()
                showAgentPicker = false
                onOpenChat(target.id, newSessionId, cwd, viewModel.workspaceId, null)
            },
        )
    }

    if (showRenameDialog) {
        RenameWorkspaceDialog(
            initial = ws?.displayName.orEmpty(),
            onDismiss = { showRenameDialog = false },
            onConfirm = { name ->
                viewModel.rename(name.takeIf { it.isNotBlank() })
                showRenameDialog = false
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete workspace?") },
            text = { Text("This deletes the workspace and all threads inside it.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete()
                    showDeleteDialog = false
                    onNavigateBack()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ThreadCard(
    thread: WorkspaceThread,
    connectionState: AcpConnectionState?,
    isArchived: Boolean,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
) {
    var rowMenuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = thread.title ?: "Untitled thread",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isArchived) {
                        ConnectionDot(state = connectionState)
                        Spacer(modifier = Modifier.size(8.dp))
                    }
                    AgentBadge(name = thread.agentName ?: "Agent")
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = thread.cwd ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box {
                IconButton(onClick = { rowMenuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Thread menu")
                }
                DropdownMenu(
                    expanded = rowMenuOpen,
                    onDismissRequest = { rowMenuOpen = false },
                ) {
                    if (isArchived) {
                        DropdownMenuItem(
                            text = { Text("Restore") },
                            onClick = {
                                rowMenuOpen = false
                                onUnarchive()
                            },
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Archive") },
                            onClick = {
                                rowMenuOpen = false
                                onArchive()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionDot(state: AcpConnectionState?) {
    val color = when (state) {
        is AcpConnectionState.Connected -> Color(0xFF4CAF50)
        is AcpConnectionState.Connecting -> MaterialTheme.colorScheme.tertiary
        is AcpConnectionState.Failed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(color = color, shape = CircleShape),
    )
}

@Composable
private fun AgentBadge(name: String) {
    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun EmptyThreadsState(
    hasAgents: Boolean,
    isArchivedView: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (isArchivedView) "No archived threads" else "No threads yet",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = when {
                isArchivedView -> "Threads you archive show up here."
                hasAgents -> "Tap + to start a new thread."
                else -> "There are no agents available for this workspace's helper."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AgentPickerDialog(
    agents: List<LaunchableTarget>,
    onDismiss: () -> Unit,
    onPick: (LaunchableTarget) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick an agent") },
        text = {
            if (agents.isEmpty()) {
                Text(
                    "No agents on this workspace's helper. Add one in Settings first.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    agents.forEach { target ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onPick(target) },
                        ) {
                            Text(target.name)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun RenameWorkspaceDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename workspace") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Display name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
