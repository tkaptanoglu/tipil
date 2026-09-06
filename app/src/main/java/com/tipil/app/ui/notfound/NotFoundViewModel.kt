package com.tipil.app.ui.notfound

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tipil.app.data.local.BookEntity
import com.tipil.app.data.local.NotFoundScanEntity
import com.tipil.app.data.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotFoundUiState(
    val scans: List<NotFoundScanEntity> = emptyList(),
    val retryingId: Long? = null,
    val isLoading: Boolean = true,
    /** One-shot outcome of a retry, shown as a snackbar then cleared. */
    val retryMessage: String? = null
)

@HiltViewModel
class NotFoundViewModel @Inject constructor(
    private val repository: BookRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotFoundUiState())
    val uiState: StateFlow<NotFoundUiState> = _uiState.asStateFlow()

    fun loadScans(userId: String) {
        viewModelScope.launch {
            repository.getNotFoundScans(userId).collect { scans ->
                _uiState.update { it.copy(scans = scans, isLoading = false) }
            }
        }
    }

    fun retry(scan: NotFoundScanEntity, userId: String) {
        if (_uiState.value.retryingId != null) return

        _uiState.update { it.copy(retryingId = scan.id, retryMessage = null) }

        viewModelScope.launch {
            // Auto-detect: try both book and music APIs in parallel
            val result = try {
                repository.lookupByBarcode(scan.barcode)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(retryingId = null, retryMessage = "Lookup failed: ${e.message}")
                }
                return@launch
            }

            val message = if (result != null) {
                try {
                    val book = BookEntity(
                        userId = userId,
                        isbn = result.isbn,
                        title = result.title,
                        subtitle = result.subtitle,
                        authors = result.authors,
                        publisher = result.publisher,
                        editor = result.editor,
                        publishedYear = result.publishedYear,
                        pageCount = result.pageCount,
                        isFiction = result.isFiction,
                        genres = result.genres,
                        coverUrl = result.coverUrl,
                        description = result.description,
                        addedAt = System.currentTimeMillis(),
                        mediaType = result.mediaType.name
                    )
                    repository.addBook(book)
                    repository.removeNotFoundScan(scan.id)
                    "Added \"${result.title}\""
                } catch (_: Exception) {
                    // Duplicate insert — the item is already in the library, so
                    // the queue entry is stale either way.
                    repository.removeNotFoundScan(scan.id)
                    "Already in your library"
                }
            } else {
                "Still not found in any source"
            }

            _uiState.update { it.copy(retryingId = null, retryMessage = message) }
        }
    }

    fun clearRetryMessage() {
        _uiState.update { it.copy(retryMessage = null) }
    }

    fun delete(scan: NotFoundScanEntity) {
        viewModelScope.launch {
            repository.removeNotFoundScan(scan.id)
        }
    }

    /**
     * Empties the queue. The caller is expected to have confirmed with the
     * user first — this discards every entry and cannot be undone.
     */
    fun clearAll(userId: String) {
        val count = _uiState.value.scans.size
        if (count == 0) return

        viewModelScope.launch {
            val message = try {
                repository.clearNotFoundScans(userId)
                if (count == 1) "Removed 1 item" else "Removed $count items"
            } catch (e: Exception) {
                "Couldn't clear the list: ${e.message}"
            }
            _uiState.update { it.copy(retryMessage = message) }
        }
    }
}
