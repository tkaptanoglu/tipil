package com.tipil.app.ui.recommendations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tipil.app.data.local.MediaType
import com.tipil.app.data.repository.BookRecommendation
import com.tipil.app.data.repository.BookRepository
import com.tipil.app.util.Tier1Labels
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
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

    fun loadRecommendations(userId: String, mediaType: MediaType? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val recommendations = when (mediaType) {
                    MediaType.CD -> repository.getCdRecommendations(userId)
                    else -> repository.getRecommendations(userId)
                }

                val books = repository.getUserBooks(userId).first()

                // Tier 1: Check what types the user has
                val hasFiction = books.any { it.isFiction }
                val hasNonFiction = books.any { !it.isFiction }

                // Tier 2: Collect genre tags, stripping any tier-1 labels
                val genres = books
                    .flatMap { it.genres }
                    .filter { it.lowercase() !in Tier1Labels.labels }
                    .groupBy { it }
                    .entries
                    .sortedByDescending { it.value.size }
                    .map { it.key }
                    .distinct()

                _uiState.update {
                    it.copy(
                        allRecommendations = recommendations,
                        globalRecommendations = recommendations,
                        userGenres = genres,
                        hasFiction = hasFiction,
                        hasNonFiction = hasNonFiction,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to load recommendations")
                }
            }
        }
    }

    fun setTypeBrowse(type: TypeBrowse) {
        _uiState.update { state ->
            val filtered = when (type) {
                TypeBrowse.ALL -> state.allRecommendations
                TypeBrowse.FICTION -> state.allRecommendations.filter { it.isFiction }
                TypeBrowse.NON_FICTION -> state.allRecommendations.filter { !it.isFiction }
            }
            state.copy(typeBrowse = type, globalRecommendations = filtered)
        }
    }

    fun selectGenre(userId: String, genre: String, mediaType: MediaType? = null) {
        val currentGenre = _uiState.value.selectedGenre
        if (currentGenre == genre) {
            _uiState.update { it.copy(selectedGenre = null) }
            return
        }

        _uiState.update { it.copy(selectedGenre = genre) }

        if (_uiState.value.genreRecommendations.containsKey(genre)) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingGenre = true) }

            try {
                val recommendations = when (mediaType) {
                    MediaType.CD -> repository.getCdRecommendationsByGenre(userId, genre)
                    else -> repository.getRecommendationsByGenre(userId, genre)
                }
                _uiState.update { state ->
                    val updatedMap = state.genreRecommendations.toMutableMap()
                    updatedMap[genre] = recommendations
                    state.copy(genreRecommendations = updatedMap, isLoadingGenre = false)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingGenre = false) }
            }
        }
    }
}
