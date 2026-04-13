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
import java.util.concurrent.atomic.AtomicBoolean
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

    private val lastScannedIsbn = AtomicReference("")

    /**
     * Atomic lock that prevents duplicate coroutine launches from rapid camera frames.
     * Only one lookup can be in-flight at a time. The lock is released by [resetScanner].
     */
    private val processingLock = AtomicBoolean(false)

    fun onBarcodeDetected(isbn: String, userId: String, mediaType: MediaType = MediaType.BOOK) {
        // Atomically acquire the processing lock — only the first caller wins.
        // All subsequent calls (rapid camera frames) are silently dropped.
        if (!processingLock.compareAndSet(false, true)) return

        lastScannedIsbn.set(isbn)

        viewModelScope.launch {
            _scanState.update { ScanState.Looking }

            // Check if already in library
            if (repository.isBookInLibrary(userId, isbn)) {
                _scanState.update { ScanState.AlreadyInLibrary(isbn) }
                return@launch
            }

            val result = if (mediaType.isMusic) {
                repository.lookupMusicByBarcode(isbn, mediaType)
            } else {
                repository.lookupBookByIsbn(isbn)
            }
            if (result != null) {
                // Auto-add to library without confirmation
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
                    _scanState.update { ScanState.Error("Failed to add: ${e.message}") }
                }
            } else {
                _scanState.update { ScanState.NotFound(isbn) }
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
        processingLock.set(false)
        _scanState.update { ScanState.Scanning }
    }
}
