package com.tipil.app.ui.bookdetail

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

data class BookDetailUiState(
    val book: BookEntity? = null,
    val isLoading: Boolean = true,
    val isDeleted: Boolean = false
)

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    private val repository: BookRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookDetailUiState())
    val uiState: StateFlow<BookDetailUiState> = _uiState.asStateFlow()

    fun loadBook(bookId: Long) {
        viewModelScope.launch {
            val book = repository.getBookById(bookId)
            _uiState.value = BookDetailUiState(book = book, isLoading = false)
        }
    }

    fun toggleReadStatus() {
        val book = _uiState.value.book ?: return
        viewModelScope.launch {
            repository.setReadStatus(book.id, !book.isRead)
            _uiState.value = _uiState.value.copy(
                book = book.copy(isRead = !book.isRead)
            )
        }
    }

    fun deleteBook() {
        val book = _uiState.value.book ?: return
        viewModelScope.launch {
            repository.deleteBook(book)
            _uiState.value = _uiState.value.copy(isDeleted = true)
        }
    }

    fun updateBook(updatedBook: BookEntity) {
        viewModelScope.launch {
            repository.updateBook(updatedBook)
            _uiState.value = _uiState.value.copy(book = updatedBook)
        }
    }
}
