package com.tipil.app.ui.notfound

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tipil.app.data.local.NotFoundScanEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotFoundScreen(
    viewModel: NotFoundViewModel,
    userId: String,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var showClearAllDialog by remember { mutableStateOf(false) }

    LaunchedEffect(userId) {
        viewModel.loadScans(userId)
    }

    LaunchedEffect(uiState.retryMessage) {
        uiState.retryMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearRetryMessage()
        }
    }

    if (showClearAllDialog) {
        val count = uiState.scans.size
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear the list") },
            text = {
                Text(
                    if (count == 1) {
                        "Remove the 1 unidentified barcode? This can't be undone."
                    } else {
                        "Remove all $count unidentified barcodes? This can't be undone."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAll(userId)
                        showClearAllDialog = false
                    }
                ) {
                    Text("Remove all", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Not Found") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // A labelled button rather than a second trash icon, which
                    // would be easy to mistake for the per-row delete.
                    TextButton(
                        onClick = { showClearAllDialog = true },
                        enabled = uiState.scans.isNotEmpty()
                    ) {
                        Text(
                            "Clear all",
                            color = if (uiState.scans.isNotEmpty()) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (uiState.scans.isEmpty() && !uiState.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No missing items",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Barcodes that couldn't be identified will appear here so you can retry later.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScrollbar(
                        state = listState,
                        thumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    ),
                // Right padding leaves a lane for the scrollbar so the cards
                // never sit underneath it.
                contentPadding = PaddingValues(start = 16.dp, end = 22.dp, top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = uiState.scans,
                    key = { it.id }
                ) { scan ->
                    NotFoundCard(
                        scan = scan,
                        isRetrying = uiState.retryingId == scan.id,
                        onRetry = { viewModel.retry(scan, userId) },
                        onDelete = { viewModel.delete(scan) }
                    )
                }
            }
        }
    }
}

/**
 * Draws a scrollbar down the right edge of a lazy list.
 *
 * Compose has no built-in scrollbar for [LazyColumn], so the thumb is drawn
 * over the content: its height is the visible fraction of the list, its
 * position the scroll progress. Nothing is drawn when the whole list already
 * fits on screen.
 *
 * Position is derived from the first visible item's index plus how far it has
 * been scrolled, which tracks the content accurately while item heights are
 * near-uniform, as they are here.
 */
private fun Modifier.verticalScrollbar(
    state: LazyListState,
    thumbColor: Color,
    width: Dp = 4.dp,
    minThumbHeight: Dp = 24.dp
): Modifier = drawWithContent {
    drawContent()

    val info = state.layoutInfo
    val visible = info.visibleItemsInfo
    val totalItems = info.totalItemsCount

    // Nothing to scroll, or nothing laid out yet.
    if (totalItems == 0 || visible.isEmpty() || visible.size >= totalItems) {
        return@drawWithContent
    }

    val widthPx = width.toPx()
    val trackHeight = size.height

    val thumbHeight = (trackHeight * visible.size / totalItems)
        .coerceAtLeast(minThumbHeight.toPx())

    val firstVisible = visible.first()
    val itemHeight = firstVisible.size.toFloat().coerceAtLeast(1f)
    // offset is negative once the item is partly scrolled off the top.
    val scrolledItems = firstVisible.index + (-firstVisible.offset / itemHeight)
    val maxScroll = (totalItems - visible.size).toFloat().coerceAtLeast(1f)
    val progress = (scrolledItems / maxScroll).coerceIn(0f, 1f)

    drawRoundRect(
        color = thumbColor,
        topLeft = Offset(size.width - widthPx, (trackHeight - thumbHeight) * progress),
        size = Size(widthPx, thumbHeight),
        cornerRadius = CornerRadius(widthPx / 2)
    )
}

@Composable
private fun NotFoundCard(
    scan: NotFoundScanEntity,
    isRetrying: Boolean,
    onRetry: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    val dateStr = dateFormat.format(Date(scan.scannedAt))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    scan.barcode,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                // No media type is shown: an unidentified barcode has no known
                // type, and labelling every entry "Book" was simply wrong.
                Text(
                    "Scanned $dateStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isRetrying) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                OutlinedButton(onClick = onRetry) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Search again",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Retry")
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
