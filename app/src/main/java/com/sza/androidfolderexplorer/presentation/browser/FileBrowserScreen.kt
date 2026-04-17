package com.sza.androidfolderexplorer.presentation.browser

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sza.androidfolderexplorer.data.source.ShizukuFileAccess
import com.sza.androidfolderexplorer.domain.model.FileItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    viewModel: FileBrowserViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    // SAF tree picker — launched when AccessPromptState.SafRequired is shown
    val safLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) viewModel.onSafPermissionGranted(uri)
    }

    // When user returns from Shizuku/Settings, auto-retry the pending restricted path
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val prompt = state.accessPrompt
        if (prompt is AccessPromptState.ShizukuNotRunning ||
            prompt is AccessPromptState.ShizukuPermissionRequired ||
            prompt is AccessPromptState.ManageStorageRequired) {
            viewModel.retryPendingPath()
        }
    }

    BackHandler(enabled = state.currentPath != FileBrowserUiState.DEFAULT_PATH) {
        viewModel.navigateUp()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    BreadcrumbBar(
                        segments = state.pathSegments,
                        onSegmentClick = { viewModel.navigateTo(it.fullPath) }
                    )
                },
                navigationIcon = {
                    if (state.currentPath != FileBrowserUiState.DEFAULT_PATH) {
                        IconButton(onClick = { viewModel.navigateUp() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleSelectAll() }) {
                        Icon(
                            if (state.allSelected) Icons.Default.RemoveDone
                            else Icons.Default.DoneAll,
                            contentDescription = "Select all"
                        )
                    }
                }
            )
        },
        bottomBar = {
            AnimatedVisibility(visible = state.hasSelection) {
                BottomActionBar(
                    selectedCount = state.selectedPaths.size,
                    onCopy = { viewModel.copyToDownloads() },
                    onMove = { viewModel.moveToDownloads() },
                    onDelete = { viewModel.requestDelete() }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.error != null -> {
                    ErrorContent(
                        message = state.error!!,
                        onRetry = { viewModel.navigateTo(state.currentPath) },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                state.items.isEmpty() -> {
                    Text(
                        text = "Empty directory",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            items(state.items, key = { it.path }) { item ->
                                FileItemRow(
                                    item = item,
                                    isSelected = item.path in state.selectedPaths,
                                    onItemClick = {
                                        if (item.isDirectory) viewModel.navigateTo(item.path)
                                    },
                                    onSelectionToggle = { viewModel.toggleSelection(item.path) }
                                )
                            }
                        }
                        ScrollbarIndicator(
                            state = listState,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(6.dp)
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (state.pendingDelete) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            icon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete ${state.selectedPaths.size} item(s)?") },
            text = { Text("This action is permanent and cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmDelete() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) { Text("Cancel") }
            }
        )
    }

    // Operation dialog
    state.operation?.let { operationState ->
        OperationDialog(
            state = operationState,
            onCancel = { viewModel.cancelOperation() },
            onDismiss = { viewModel.dismissOperation() }
        )
    }

    // Access permission dialog (SAF / Shizuku / Manage Storage)
    state.accessPrompt?.let { prompt ->
        AccessPromptDialog(
            state = prompt,
            onDismiss = { viewModel.dismissAccessPrompt() },
            onGrantSaf = { safLauncher.launch(viewModel.buildSafInitialUri(prompt.path)) },
            onGrantManageStorage = {
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                } else {
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                }
                context.startActivity(intent)
            },
            onGrantShizuku = { viewModel.requestShizukuPermission() },
            onOpenShizukuApp = {
                val intent = context.packageManager
                    .getLaunchIntentForPackage(ShizukuFileAccess.SHIZUKU_PACKAGE)
                if (intent != null) context.startActivity(intent)
            },
            onOpenPlayStore = {
                val market = Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=${ShizukuFileAccess.SHIZUKU_PACKAGE}"))
                val web = Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=${ShizukuFileAccess.SHIZUKU_PACKAGE}"))
                runCatching { context.startActivity(market) }
                    .onFailure { context.startActivity(web) }
            },
            onOpenFDroid = {
                context.startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://f-droid.org/packages/${ShizukuFileAccess.SHIZUKU_PACKAGE}/")))
            },
            onOpenWebsite = {
                context.startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://shizuku.rikka.app/")))
            },
            onRetry = { viewModel.retryPendingPath() }
        )
    }
}

@Composable
private fun ScrollbarIndicator(state: LazyListState, modifier: Modifier = Modifier) {
    val totalItems = state.layoutInfo.totalItemsCount
    val visibleCount = state.layoutInfo.visibleItemsInfo.size
    if (totalItems == 0 || visibleCount == 0 || visibleCount >= totalItems) return
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val firstVisibleIndex = state.firstVisibleItemIndex
    Canvas(modifier = modifier) {
        val thumbHeight = (visibleCount.toFloat() / totalItems * size.height)
            .coerceAtLeast(48.dp.toPx())
        val availableTravel = size.height - thumbHeight
        val maxFirst = totalItems - visibleCount
        val fraction = if (maxFirst > 0) firstVisibleIndex.toFloat() / maxFirst else 0f
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, fraction * availableTravel),
            size = Size(size.width, thumbHeight),
            cornerRadius = CornerRadius(size.width / 2)
        )
    }
}

@Composable
private fun BreadcrumbBar(
    segments: List<PathSegment>,
    onSegmentClick: (PathSegment) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        segments.forEachIndexed { index, segment ->
            if (index > 0) {
                Text(" / ", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(
                onClick = { onSegmentClick(segment) },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
            ) {
                Text(
                    text = segment.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun FileItemRow(
    item: FileItem,
    isSelected: Boolean,
    onItemClick: () -> Unit,
    onSelectionToggle: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            if (item.isDirectory) {
                Text("${item.childCount ?: "?"} items")
            } else {
                Text(formatFileSize(item.size))
            }
        },
        leadingContent = {
            Icon(
                imageVector = if (item.isDirectory) Icons.Default.Folder
                else Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = if (item.isDirectory) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onSelectionToggle() }
            )
        },
        modifier = Modifier.clickable { onItemClick() }
    )
}

@Composable
private fun BottomActionBar(
    selectedCount: Int,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$selectedCount selected",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = onCopy) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Copy")
            }
            Button(onClick = onMove) {
                Icon(
                    Icons.Default.MoveToInbox,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Move")
            }
            Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Delete")
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
        OutlinedButton(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
private fun OperationDialog(
    state: OperationState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (state !is OperationState.InProgress) onDismiss()
        },
        title = {
            Text(
                when (state) {
                    is OperationState.InProgress -> "Processing..."
                    is OperationState.Completed -> "Done"
                    is OperationState.Failed -> "Error"
                }
            )
        },
        text = {
            when (state) {
                is OperationState.InProgress -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(
                            progress = { state.progress.fraction },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "${state.progress.processedFiles} / ${state.progress.totalFiles} files",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (state.progress.currentFileName.isNotEmpty()) {
                            Text(
                                state.progress.currentFileName,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                is OperationState.Completed -> Text(state.message)
                is OperationState.Failed -> Text(state.error)
            }
        },
        confirmButton = {
            when (state) {
                is OperationState.InProgress -> {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
                else -> {
                    TextButton(onClick = onDismiss) { Text("OK") }
                }
            }
        }
    )
}

@Composable
private fun AccessPromptDialog(
    state: AccessPromptState,
    onDismiss: () -> Unit,
    onGrantSaf: () -> Unit,
    onGrantManageStorage: () -> Unit,
    onGrantShizuku: () -> Unit,
    onOpenShizukuApp: () -> Unit,
    onOpenPlayStore: () -> Unit,
    onOpenFDroid: () -> Unit,
    onOpenWebsite: () -> Unit,
    onRetry: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                when (state) {
                    is AccessPromptState.ManageStorageRequired -> "Grant All Files Access"
                    is AccessPromptState.SafRequired -> "Grant Folder Access"
                    is AccessPromptState.ShizukuPermissionRequired -> "Allow Shizuku Access"
                    is AccessPromptState.ShizukuNotRunning -> "Start Shizuku Service"
                    is AccessPromptState.ShizukuNotInstalled -> "Install Shizuku"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (state) {
                    is AccessPromptState.ManageStorageRequired -> {
                        Text(
                            "Android 11–13 requires \"All files access\" to browse Android/data and Android/obb.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Tap \"Open Settings\", enable All files access for this app, then return here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    is AccessPromptState.SafRequired -> {
                        Text(
                            "This folder requires special access.\n\n" +
                            "Tap \"Grant Access\", then select the folder:\n" +
                            state.path.substringAfter("/storage/emulated/0/"),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Confirm \"Allow\" in the system picker.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    is AccessPromptState.ShizukuPermissionRequired -> {
                        Text(
                            "Shizuku is running. Tap \"Allow\" to grant shell-level " +
                            "file access to Android/data and Android/obb.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "Shizuku service: running",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }

                    is AccessPromptState.ShizukuNotRunning -> {
                        Text(
                            "Shizuku is installed but the service is not running.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Option 1 — Open Shizuku app and tap Start:",
                            style = MaterialTheme.typography.labelMedium
                        )
                        FilledTonalButton(
                            onClick = onOpenShizukuApp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Open Shizuku")
                        }
                        HorizontalDivider()
                        Text(
                            "Option 2 — Start via ADB (one-time):",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "adb shell \$(pm path moe.shizuku.privileged.api |" +
                                    " sed 's/package://;s/base.apk/lib/arm64/libshizuku.so/')",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                        Text(
                            "After starting Shizuku, tap Retry.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    is AccessPromptState.ShizukuNotInstalled -> {
                        Text(
                            "Android 14+ blocks all standard access to Android/data and Android/obb.\n\n" +
                            "Shizuku provides privileged shell access. Install it, start the service " +
                            "once via ADB, then grant permission — no root required.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Install from:", style = MaterialTheme.typography.labelMedium)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilledTonalButton(
                                onClick = onOpenPlayStore,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Google Play Store")
                            }
                            OutlinedButton(
                                onClick = onOpenFDroid,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("F-Droid (open source)")
                            }
                            TextButton(
                                onClick = onOpenWebsite,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("shizuku.rikka.app — APK & instructions")
                            }
                        }
                        HorizontalDivider()
                        Text(
                            "After install, start the service once:",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "adb shell \$(pm path moe.shizuku.privileged.api |" +
                                    " sed 's/package://;s/base.apk/lib/arm64/libshizuku.so/')",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                        Text(
                            "Wireless ADB: Developer Options → Wireless debugging\n" +
                            "(no PC needed after pairing once)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                is AccessPromptState.ManageStorageRequired ->
                    Button(onClick = onGrantManageStorage) { Text("Open Settings") }
                is AccessPromptState.SafRequired ->
                    Button(onClick = onGrantSaf) { Text("Grant Access") }
                is AccessPromptState.ShizukuPermissionRequired ->
                    Button(onClick = onGrantShizuku) { Text("Allow") }
                is AccessPromptState.ShizukuNotRunning ->
                    Button(onClick = onRetry) { Text("Retry") }
                is AccessPromptState.ShizukuNotInstalled ->
                    TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
        dismissButton = {
            when (state) {
                is AccessPromptState.ManageStorageRequired ->
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                is AccessPromptState.ShizukuNotInstalled ->
                    // Fallback: SAF may partially work on some ROMs even on Android 14
                    TextButton(onClick = onGrantSaf) { Text("Try SAF (limited)") }
                is AccessPromptState.ShizukuNotRunning ->
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                else ->
                    TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
}
