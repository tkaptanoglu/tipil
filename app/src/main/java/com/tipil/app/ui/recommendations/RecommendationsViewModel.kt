package com.tipil.app.ui.recommendations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tipil.app.data.local.BookEntity
import com.tipil.app.data.repository.BookRecommendation
import com.tipil.app.data.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecommendationsUiState(
    val globalRecommendations: List<BookRecommendation> = emptyList(),
    val genreRecommendations: Map<String, List<BookRecommendation>> = emptyMap(),
    val userGenres: List<String> = emptyList(),
    val selectedGenre: String? = null,
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

    fun loadRecommendations(userId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // Get global recommendations
                val recommendations = repository.getRecommendations(userId)

                // Collect user's genres for the genre filter
                val books = repository.getUserBooks(userId).first()
                val genres = books
                    .flatMap { it.genres }
                    .groupBy { it }
                    .entries
                    .sortedByDescending { it.value.size }
                    .map { it.key }
                    .distinct()

                _uiState.value = _uiState.value.copy(
                    globalRecommendations = recommendations,
                    userGenres = genres,
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

    fun selectGenre(userId: String, genre: String) {
        if (_uiState.value.selectedGenre == genre) {
            _uiState.value = _uiState.value.copy(selectedGenre = null)
            return
        }

        _uiState.value = _uiState.value.copy(selectedGenre = genre)

        // Check if we already have recommendations for this genre
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
