package com.tipil.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tipil.app.data.local.BookEntity
import com.tipil.app.data.repository.BookRepository
import com.tipil.app.util.Tier1Labels
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ReadFilter { ALL, READ, UNREAD }
enum class TypeFilter { ALL, FICTION, NON_FICTION }

data class LibraryUiState(
    val books: List<BookEntity> = emptyList(),
    val allBooks: List<BookEntity> = emptyList(),
    val readFilter: ReadFilter = ReadFilter.ALL,
    val typeFilter: TypeFilter = TypeFilter.ALL,
    val selectedGenre: String? = null,
    val availableGenres: List<String> = emptyList(),
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

    fun loadBooks(userId: String) {
        viewModelScope.launch {
            repository.getUserBooks(userId).collect { books ->
                val genres = books
                    .flatMap { it.genres }
                    .filter { it.lowercase() !in Tier1Labels.labels }
                    .distinct()
                    .sorted()
                _uiState.update {
                    it.copy(
                        allBooks = books,
                        isLoading = false,
                        bookCount = books.size,
                        availableGenres = genres
                    )
                }
                applyFilters()
            }
        }
    }

    fun setReadFilter(filter: ReadFilter) {
        _uiState.update { it.copy(readFilter = filter) }
        applyFilters()
    }

    fun setTypeFilter(filter: TypeFilter) {
        _uiState.update { it.copy(typeFilter = filter) }
        applyFilters()
    }

    fun setGenreFilter(genre: String?) {
        _uiState.update {
            it.copy(selectedGenre = if (it.selectedGenre == genre) null else genre)
        }
        applyFilters()
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilters()
    }

    fun toggleReadStatus(book: BookEntity) {
        viewModelScope.launch {
            repository.setReadStatus(book.id, book.userId, !book.isRead)
        }
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch {
            repository.deleteBook(book)
        }
    }

    private fun applyFilters() {
        _uiState.update { state ->
            var filtered = state.allBooks

            // Tier 1: Fiction / Non-Fiction
            filtered = when (state.typeFilter) {
                TypeFilter.ALL -> filtered
                TypeFilter.FICTION -> filtered.filter { it.isFiction }
                TypeFilter.NON_FICTION -> filtered.filter { !it.isFiction }
            }

            // Tier 2: Specific genre
            state.selectedGenre?.let { genre ->
                filtered = filtered.filter { book -> genre in book.genres }
            }

            // Read status filter
            filtered = when (state.readFilter) {
                ReadFilter.ALL -> filtered
                ReadFilter.READ -> filtered.filter { it.isRead }
                ReadFilter.UNREAD -> filtered.filter { !it.isRead }
            }

            // Search filter
            if (state.searchQuery.isNotBlank()) {
                val query = state.searchQuery.lowercase()
                filtered = filtered.filter { book ->
                    book.title.lowercase().contains(query) ||
                            book.authors.lowercase().contains(query) ||
                            book.genres.any { it.lowercase().contains(query) }
                }
            }

            state.copy(books = filtered)
        }
    }
}
