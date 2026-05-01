package com.tamimarafat.ferngeist.feature.sessionlist.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tamimarafat.ferngeist.core.model.LaunchableTarget
import com.tamimarafat.ferngeist.feature.sessionlist.WorkspaceListEffect
import com.tamimarafat.ferngeist.feature.sessionlist.WorkspaceListItem
import com.tamimarafat.ferngeist.feature.sessionlist.WorkspaceListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceListScreen(
    viewModel: WorkspaceListViewModel,
    onOpenWorkspace: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val workspaces by viewModel.workspaces.collectAsState()
    val launchableTargets by viewModel.launchableTargets.collectAsState()
    val effect by viewModel.uiEffect.collectAsState()

    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(effect) {
        when (val e = effect) {
            is WorkspaceListEffect.WorkspaceReady -> {
                showAddDialog = false
                viewModel.consumeEffect()
                onOpenWorkspace(e.workspaceId)
            }
            null -> Unit
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Workspaces") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add workspace")
            }
        },
    ) { padding ->
        if (workspaces.isEmpty()) {
            EmptyWorkspacesState(
                hasAgents = launchableTargets.isNotEmpty(),
                onOpenSettings = onOpenSettings,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 24.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(workspaces, key = { it.workspace.id }) { item ->
                    WorkspaceCard(
                        item = item,
                        onClick = { onOpenWorkspace(item.workspace.id) },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddWorkspaceDialog(
            launchableTargets = launchableTargets,
            onDismiss = { showAddDialog = false },
            onCreate = { target, cwd, displayName ->
                viewModel.createWorkspace(target, cwd, displayName)
            },
        )
    }
}

@Composable
private fun WorkspaceCard(
    item: WorkspaceListItem,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.workspace.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.workspace.cwd,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (item.threadCount == 1) "1 thread" else "${item.threadCount} threads",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyWorkspacesState(
    hasAgents: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No workspaces yet",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (hasAgents) {
                "Tap + to add your first workspace."
            } else {
                "Add an agent in Settings, then create a workspace."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!hasAgents) {
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onOpenSettings) { Text("Open Settings") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddWorkspaceDialog(
    launchableTargets: List<LaunchableTarget>,
    onDismiss: () -> Unit,
    onCreate: (target: LaunchableTarget, cwd: String, displayName: String?) -> Unit,
) {
    var selectedTarget by remember(launchableTargets) {
        mutableStateOf(launchableTargets.firstOrNull())
    }
    var cwd by rememberSaveable { mutableStateOf("/") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New workspace") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (launchableTargets.isEmpty()) {
                    Text(
                        "You need to add an agent first. Open Settings to pair a desktop helper or add a manual server.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = menuExpanded,
                        onExpandedChange = { menuExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = selectedTarget?.name ?: "Select agent",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Agent / helper") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                        )
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            launchableTargets.forEach { target ->
                                DropdownMenuItem(
                                    text = { Text(target.name) },
                                    onClick = {
                                        selectedTarget = target
                                        menuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = cwd,
                        onValueChange = { cwd = it },
                        label = { Text("Working directory") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Display name (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val target = selectedTarget ?: return@TextButton
                    onCreate(target, cwd, displayName.takeIf { it.isNotBlank() })
                },
                enabled = selectedTarget != null && cwd.isNotBlank(),
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
