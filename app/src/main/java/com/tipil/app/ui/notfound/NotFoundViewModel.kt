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
    val isLoading: Boolean = true
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

        _uiState.update { it.copy(retryingId = scan.id) }

        viewModelScope.launch {
            // Auto-detect: try both book and music APIs in parallel
            val result = repository.lookupByBarcode(scan.barcode)

            if (result != null) {
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
                } catch (_: Exception) {
                    // Duplicate or other insert error — just remove from not-found list
                    repository.removeNotFoundScan(scan.id)
                }
            }

            _uiState.update { it.copy(retryingId = null) }
        }
    }

    fun delete(scan: NotFoundScanEntity) {
        viewModelScope.launch {
            repository.removeNotFoundScan(scan.id)
        }
    }
}
