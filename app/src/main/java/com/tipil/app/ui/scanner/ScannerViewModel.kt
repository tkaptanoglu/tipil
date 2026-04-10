package com.tipil.app.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tipil.app.data.local.BookEntity
import com.tipil.app.data.repository.BookLookupResult
import com.tipil.app.data.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ScanState {
    data object Scanning : ScanState()
    data object Looking : ScanState()
    data class Found(val result: BookLookupResult) : ScanState()
    data class AlreadyInLibrary(val isbn: String) : ScanState()
    data class NotFound(val isbn: String) : ScanState()
    data class Error(val message: String) : ScanState()
    data object Added : ScanState()
}

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val repository: BookRepository
) : ViewModel() {

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Scanning)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private var lastScannedIsbn: String = ""

    fun onBarcodeDetected(isbn: String, userId: String) {
        if (isbn == lastScannedIsbn && _scanState.value !is ScanState.Scanning) return
        lastScannedIsbn = isbn

        viewModelScope.launch {
            _scanState.value = ScanState.Looking

            // Check if already in library
            if (repository.isBookInLibrary(userId, isbn)) {
                _scanState.value = ScanState.AlreadyInLibrary(isbn)
                return@launch
            }

            val result = repository.lookupBookByIsbn(isbn)
            _scanState.value = if (result != null) {
                ScanState.Found(result)
            } else {
                ScanState.NotFound(isbn)
            }
        }
    }

    fun addToLibrary(userId: String, result: BookLookupResult) {
        viewModelScope.launch {
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
                description = result.description
            )
            repository.addBook(book)
            _scanState.value = ScanState.Added
        }
    }

    fun resetScanner() {
        lastScannedIsbn = ""
        _scanState.value = ScanState.Scanning
    }
}
