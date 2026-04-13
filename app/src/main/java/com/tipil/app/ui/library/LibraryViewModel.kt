package com.tipil.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tipil.app.data.local.BookEntity
import com.tipil.app.data.local.MediaCategory
import com.tipil.app.data.local.MediaType
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

enum class SortOrder(val label: String) {
    AUTHOR_AZ("Author A–Z"),
    DATE_ADDED_NEWEST("Newest first"),
    DATE_ADDED_OLDEST("Oldest first")
}

data class LibraryUiState(
    val books: List<BookEntity> = emptyList(),
    val allBooks: List<BookEntity> = emptyList(),
    val readFilter: ReadFilter = ReadFilter.ALL,
    val typeFilter: TypeFilter = TypeFilter.ALL,
    val sortOrder: SortOrder = SortOrder.AUTHOR_AZ,
    val selectedGenre: String? = null,
    val availableGenres: List<String> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val bookCount: Int = 0,
    /** Which category tab is selected. Always non-null; defaults to BOOKS. */
    val selectedCategory: MediaCategory = MediaCategory.BOOKS,
    /** Which categories the user has items for (drives tab visibility). */
    val availableCategories: List<MediaCategory> = emptyList()
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

                // Determine which categories the user has items for
                val categories = books
                    .map { MediaType.fromName(it.mediaType).category }
                    .distinct()
                    .sortedBy { it.ordinal }

                _uiState.update {
                    // If the current selection is not in the available categories,
                    // default to the first available (or BOOKS as ultimate fallback).
                    val currentCat = it.selectedCategory
                    val effectiveCat = if (currentCat in categories) currentCat
                        else categories.firstOrNull() ?: MediaCategory.BOOKS

                    it.copy(
                        allBooks = books,
                        isLoading = false,
                        bookCount = books.size,
                        availableGenres = genres,
                        availableCategories = categories,
                        selectedCategory = effectiveCat
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

    fun setSortOrder(order: SortOrder) {
        _uiState.update { it.copy(sortOrder = order) }
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

    fun setCategoryFilter(category: MediaCategory) {
        _uiState.update { it.copy(selectedCategory = category, typeFilter = TypeFilter.ALL, selectedGenre = null) }
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

            // Category filter (Books, Music, DVDs, etc.) — always applied
            val types = state.selectedCategory.mediaTypes
            filtered = filtered.filter { MediaType.fromName(it.mediaType) in types }

            // Tier 1: Fiction / Non-Fiction (only meaningful for books, not music)
            if (state.selectedCategory == MediaCategory.BOOKS) {
                filtered = when (state.typeFilter) {
                    TypeFilter.ALL -> filtered
                    TypeFilter.FICTION -> filtered.filter { it.isFiction }
                    TypeFilter.NON_FICTION -> filtered.filter { !it.isFiction }
                }
            }

            // Tier 2: Specific genre
            state.selectedGenre?.let { genre ->
                filtered = filtered.filter { book -> genre in book.genres }
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

            // Sort
            filtered = when (state.sortOrder) {
                SortOrder.AUTHOR_AZ -> filtered.sortedBy { extractSortKey(it) }
                SortOrder.DATE_ADDED_NEWEST -> filtered.sortedByDescending { it.addedAt }
                SortOrder.DATE_ADDED_OLDEST -> filtered.sortedBy { it.addedAt }
            }

            // Recompute visible genres scoped to the active category
            val categoryItems = state.allBooks.filter { MediaType.fromName(it.mediaType) in types }

            val visibleGenres = categoryItems
                .flatMap { it.genres }
                .filter { it.lowercase() !in Tier1Labels.labels }
                .distinct()
                .sorted()

            // Count reflects the filtered category, not search/genre
            val visibleCount = categoryItems.size

            state.copy(
                books = filtered,
                availableGenres = visibleGenres,
                bookCount = visibleCount
            )
        }
    }

    /**
     * Produces a sort key for the given item, based on its media type.
     *
     * - **Books / Magazines**: last name of the first author.
     * - **Music (CD / Cassette / Vinyl)**: full artist name with leading "The " stripped.
     * - **DVDs / Board Games**: title-based sort.
     */
    internal fun extractSortKey(book: BookEntity): String {
        val mt = MediaType.fromName(book.mediaType)
        return when {
            mt.isMusic -> extractArtistSortKey(book.authors)
            mt == MediaType.DVD || mt == MediaType.BOARD_GAME -> book.title.lowercase()
            else -> extractLastName(book.authors)
        }
    }

    /**
     * Strips a leading "The " (case-insensitive) from the artist name for sorting.
     * "The Blues Brothers" → "blues brothers", "Beyoncé" → "beyoncé".
     */
    private fun extractArtistSortKey(artist: String): String {
        val trimmed = artist.trim()
        val withoutThe = if (trimmed.startsWith("The ", ignoreCase = true)) {
            trimmed.drop(4)
        } else {
            trimmed
        }
        return withoutThe.lowercase()
    }

    /**
     * Extracts the last name of the first author for sorting.
     * Handles "First Last", "First Middle Last", and "Last, First" formats.
     */
    private fun extractLastName(authors: String): String {
        val firstAuthor = authors.split(",").first().trim()
        if (firstAuthor.isBlank()) return ""
        // If the original string had "Last, First" format and we split on comma,
        // firstAuthor is already the last name
        if (authors.contains(",") && !authors.substringBefore(",").contains(" ")) {
            return firstAuthor.lowercase()
        }
        // "First Last" or "First Middle Last" — take the final word
        return firstAuthor.split(" ").last().lowercase()
    }
}
