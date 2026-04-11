package com.tipil.app.ui.recommendations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tipil.app.data.repository.BookRecommendation
import com.tipil.app.data.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TypeBrowse { ALL, FICTION, NON_FICTION }

data class RecommendationsUiState(
    val allRecommendations: List<BookRecommendation> = emptyList(),
    val globalRecommendations: List<BookRecommendation> = emptyList(),
    val genreRecommendations: Map<String, List<BookRecommendation>> = emptyMap(),
    val userGenres: List<String> = emptyList(),
    val typeBrowse: TypeBrowse = TypeBrowse.ALL,
    val selectedGenre: String? = null,
    val hasFiction: Boolean = false,
    val hasNonFiction: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingGenre: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class RecommendationsViewModel @Inject constructor(
    private val repository: BookRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecommendationsUiState())
    val uiState: StateFlow<RecommendationsUiState> = _uiState.asStateFlow()

    // Tier-1 labels that should never appear as genre tags
    private val tier1Labels = setOf(
        "fiction", "non-fiction", "nonfiction", "non fiction",
        "literary fiction", "general fiction", "juvenile fiction",
        "juvenile nonfiction", "juvenile non-fiction"
    )

    fun loadRecommendations(userId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val recommendations = repository.getRecommendations(userId)

                val books = repository.getUserBooks(userId).first()

                // Tier 1: Check what types the user has
                val hasFiction = books.any { it.isFiction }
                val hasNonFiction = books.any { !it.isFiction }

                // Tier 2: Collect genre tags, stripping any tier-1 labels
                val genres = books
                    .flatMap { it.genres }
                    .filter { it.lowercase() !in tier1Labels }
                    .groupBy { it }
                    .entries
                    .sortedByDescending { it.value.size }
                    .map { it.key }
                    .distinct()

                _uiState.value = _uiState.value.copy(
                    allRecommendations = recommendations,
                    globalRecommendations = recommendations,
                    userGenres = genres,
                    hasFiction = hasFiction,
                    hasNonFiction = hasNonFiction,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load recommendations"
                )
            }
        }
    }

    fun setTypeBrowse(type: TypeBrowse) {
        val all = _uiState.value.allRecommendations
        val filtered = when (type) {
            TypeBrowse.ALL -> all
            TypeBrowse.FICTION -> all.filter { it.isFiction }
            TypeBrowse.NON_FICTION -> all.filter { !it.isFiction }
        }
        _uiState.value = _uiState.value.copy(
            typeBrowse = type,
            globalRecommendations = filtered
        )
    }

    fun selectGenre(userId: String, genre: String) {
        if (_uiState.value.selectedGenre == genre) {
            _uiState.value = _uiState.value.copy(selectedGenre = null)
            return
        }

        _uiState.value = _uiState.value.copy(selectedGenre = genre)

        if (_uiState.value.genreRecommendations.containsKey(genre)) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingGenre = true)

            try {
                val recommendations = repository.getRecommendationsByGenre(userId, genre)
                val updatedMap = _uiState.value.genreRecommendations.toMutableMap()
                updatedMap[genre] = recommendations

                _uiState.value = _uiState.value.copy(
                    genreRecommendations = updatedMap,
                    isLoadingGenre = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingGenre = false)
            }
        }
    }
}
