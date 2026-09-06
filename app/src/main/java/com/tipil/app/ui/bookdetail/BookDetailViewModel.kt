package com.tipil.app.ui.bookdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tipil.app.data.local.BookEntity
import com.tipil.app.data.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookDetailUiState(
    val book: BookEntity? = null,
    val isLoading: Boolean = true,
    val isDeleted: Boolean = false,
    val isRefreshing: Boolean = false,
    /** One-shot result of a refresh, shown as a snackbar then cleared. */
    val refreshMessage: String? = null
)

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    private val repository: BookRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookDetailUiState())
    val uiState: StateFlow<BookDetailUiState> = _uiState.asStateFlow()

    private var currentUserId: String = ""

    fun loadBook(bookId: Long, userId: String) {
        currentUserId = userId
        viewModelScope.launch {
            val book = repository.getBookById(bookId, userId)
            _uiState.update { BookDetailUiState(book = book, isLoading = false) }
        }
    }

    fun toggleReadStatus() {
        val book = _uiState.value.book ?: return
        viewModelScope.launch {
            repository.setReadStatus(book.id, currentUserId, !book.isRead)
            _uiState.update { it.copy(book = book.copy(isRead = !book.isRead)) }
        }
    }

    /**
     * Re-runs the metadata lookup and overwrites the stored description of
     * this item. The read/listened flag and the date it was added survive.
     *
     * A failed lookup leaves the existing record alone.
     */
    fun refresh() {
        val book = _uiState.value.book ?: return
        if (_uiState.value.isRefreshing) return

        _uiState.update { it.copy(isRefreshing = true, refreshMessage = null) }

        viewModelScope.launch {
            val message = try {
                val updated = repository.refreshItem(book)
                if (updated != null) {
                    _uiState.update { it.copy(book = updated) }
                    "Details refreshed"
                } else {
                    "No updated details found"
                }
            } catch (e: Exception) {
                "Refresh failed: ${e.message}"
            }
            _uiState.update { it.copy(isRefreshing = false, refreshMessage = message) }
        }
    }

    fun clearRefreshMessage() {
        _uiState.update { it.copy(refreshMessage = null) }
    }

    fun deleteBook() {
        val book = _uiState.value.book ?: return
        viewModelScope.launch {
            repository.deleteBook(book)
            _uiState.update { it.copy(isDeleted = true) }
        }
    }
}
