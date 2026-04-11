package com.tipil.app.ui.bookdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tipil.app.data.local.MediaType
import com.tipil.app.ui.theme.LocalExtraColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookDetailScreen(
    viewModel: BookDetailViewModel,
    bookId: Long,
    userId: String,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val extra = LocalExtraColors.current

    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId, userId)
    }

    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) onNavigateBack()
    }

    if (showDeleteDialog) {
        val itemTypeName = uiState.book?.let { MediaType.fromName(it.mediaType).label } ?: "Item"
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Remove $itemTypeName") },
            text = { Text("Remove \"${uiState.book?.title}\" from your library?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBook()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            val detailTitle = uiState.book?.let {
                "${MediaType.fromName(it.mediaType).label} Details"
            } ?: "Details"
            TopAppBar(
                title = { Text(detailTitle) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val book = uiState.book ?: return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Cover and basic info
            Row(modifier = Modifier.fillMaxWidth()) {
                if (book.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = book.coverUrl,
                        contentDescription = "Cover of ${book.title}",
                        modifier = Modifier
                            .size(width = 120.dp, height = 180.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(width = 120.dp, height = 180.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        book.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (book.subtitle.isNotBlank()) {
                        Text(
                            book.subtitle,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        book.authors,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Consumed status toggle — label depends on media type
            val consumedLabel = when (MediaType.fromName(book.mediaType)) {
                MediaType.BOOK, MediaType.MAGAZINE -> if (book.isRead) "Marked as Read" else "Mark as Read"
                MediaType.CD, MediaType.CASSETTE -> if (book.isRead) "Marked as Listened" else "Mark as Listened"
                MediaType.DVD -> if (book.isRead) "Marked as Watched" else "Mark as Watched"
                MediaType.BOARD_GAME -> if (book.isRead) "Marked as Played" else "Mark as Played"
            }
            Button(
                onClick = { viewModel.toggleReadStatus() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (book.isRead) extra.readIndicator else extra.unreadIndicator
                )
            ) {
                Icon(
                    if (book.isRead) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(consumedLabel)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Details card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (book.isbn.isNotBlank()) DetailItem("ISBN", book.isbn)
                    if (book.publisher.isNotBlank()) DetailItem("Publisher", book.publisher)
                    if (book.editor.isNotBlank()) DetailItem("Editor", book.editor)
                    if (book.publishedYear.isNotBlank()) DetailItem("Year Published", book.publishedYear)
                    if (book.pageCount > 0) DetailItem("Pages", book.pageCount.toString())
                }
            }

            // Two-tier tagging
            Spacer(modifier = Modifier.height(16.dp))

            // Tier 1: Fiction / Non-Fiction
            Text(
                "Classification",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            AssistChip(
                onClick = { },
                label = {
                    Text(
                        if (book.isFiction) "FICTION" else "NON-FICTION",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            )

            // Tier 2: Genre tags
            if (book.genres.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Genres",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    book.genres.forEach { genre ->
                        AssistChip(
                            onClick = { },
                            label = { Text(genre) }
                        )
                    }
                }
            }

            // Description
            if (book.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Description",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    book.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Remove button at bottom
            OutlinedButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Remove from Library")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(120.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
