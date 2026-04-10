package com.tipil.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tipil.app.data.local.BookEntity
import com.tipil.app.data.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryFilter { ALL, READ, UNREAD }

data class LibraryUiState(
    val books: List<BookEntity> = emptyList(),
    val filter: LibraryFilter = LibraryFilter.ALL,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val bookCount: Int = 0
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: BookRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var allBooks: List<BookEntity> = emptyList()
    private var currentUserId: String = ""

    fun loadBooks(userId: String) {
        currentUserId = userId
        viewModelScope.launch {
            repository.getUserBooks(userId).collect { books ->
                allBooks = books
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    bookCount = books.size
                )
                applyFilters()
            }
        }
    }

    fun setFilter(filter: LibraryFilter) {
        _uiState.value = _uiState.value.copy(filter = filter)
        applyFilters()
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        applyFilters()
    }

    fun toggleReadStatus(book: BookEntity) {
        viewModelScope.launch {
            repository.setReadStatus(book.id, !book.isRead)
        }
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch {
            repository.deleteBook(book)
        }
    }

    private fun applyFilters() {
        val state = _uiState.value
        var filtered = allBooks

        // Apply read/unread filter
        filtered = when (state.filter) {
            LibraryFilter.ALL -> filtered
            LibraryFilter.READ -> filtered.filter { it.isRead }
            LibraryFilter.UNREAD -> filtered.filter { !it.isRead }
        }

        // Apply search filter
        if (state.searchQuery.isNotBlank()) {
            val query = state.searchQuery.lowercase()
            filtered = filtered.filter { book ->
                book.title.lowercase().contains(query) ||
                        book.authors.lowercase().contains(query) ||
                        book.genres.any { it.lowercase().contains(query) }
            }
        }

        _uiState.value = state.copy(books = filtered)
    }
}
