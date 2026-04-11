package com.tipil.app.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tipil.app.data.local.BookEntity
import com.tipil.app.data.local.MediaType
import com.tipil.app.data.repository.BookLookupResult
import com.tipil.app.data.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
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

    // AtomicReference prevents race conditions from the camera analyzer thread
    private val lastScannedIsbn = AtomicReference("")

    fun onBarcodeDetected(isbn: String, userId: String) {
        // Atomically check-and-set to prevent duplicate lookups from rapid camera frames
        if (!lastScannedIsbn.compareAndSet("", isbn) &&
            !(lastScannedIsbn.compareAndSet(isbn, isbn) && _scanState.value is ScanState.Scanning)
        ) {
            // Either another ISBN is being processed, or this ISBN is already handled
            if (lastScannedIsbn.get() == isbn && _scanState.value !is ScanState.Scanning) return
            if (lastScannedIsbn.get() != isbn) return
        }
        lastScannedIsbn.set(isbn)

        viewModelScope.launch {
            _scanState.update { ScanState.Looking }

            // Check if already in library
            if (repository.isBookInLibrary(userId, isbn)) {
                _scanState.update { ScanState.AlreadyInLibrary(isbn) }
                return@launch
            }

            val result = repository.lookupBookByIsbn(isbn)
            _scanState.update {
                if (result != null) ScanState.Found(result) else ScanState.NotFound(isbn)
            }
        }
    }

    fun addToLibrary(userId: String, result: BookLookupResult) {
        viewModelScope.launch {
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
                _scanState.update { ScanState.Added }
            } catch (e: Exception) {
                _scanState.update { ScanState.Error("Failed to add book: ${e.message}") }
            }
        }
    }

    fun resetScanner() {
        lastScannedIsbn.set("")
        _scanState.update { ScanState.Scanning }
    }
}
