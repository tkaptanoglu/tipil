package com.tipil.app.ui.scanner

import com.tipil.app.data.local.BookEntity
import com.tipil.app.data.repository.BookLookupResult
import com.tipil.app.data.repository.BookRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScannerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: BookRepository
    private lateinit var viewModel: ScannerViewModel

    private val testResult = BookLookupResult(
        isbn = "9780062316097",
        title = "Sapiens",
        subtitle = "A Brief History of Humankind",
        authors = "Yuval Noah Harari",
        publisher = "Harper",
        editor = "",
        publishedYear = "2015",
        pageCount = 464,
        isFiction = false,
        genres = listOf("History"),
        coverUrl = "https://example.com/cover.jpg",
        description = "A book about human history"
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        viewModel = ScannerViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Scanning`() {
        assertTrue(viewModel.scanState.value is ScanState.Scanning)
    }

    @Test
    fun `onBarcodeDetected transitions to Found when book exists in API`() = runTest(testDispatcher) {
        coEvery { repository.isBookInLibrary("user1", "9780062316097") } returns false
        coEvery { repository.lookupBookByIsbn("9780062316097") } returns testResult

        viewModel.onBarcodeDetected("9780062316097", "user1")
        advanceUntilIdle()

        val state = viewModel.scanState.value
        assertTrue("Expected Found, got $state", state is ScanState.Found)
        assertEquals("Sapiens", (state as ScanState.Found).result.title)
    }

    @Test
    fun `onBarcodeDetected transitions to AlreadyInLibrary`() = runTest(testDispatcher) {
        coEvery { repository.isBookInLibrary("user1", "9780062316097") } returns true

        viewModel.onBarcodeDetected("9780062316097", "user1")
        advanceUntilIdle()

        assertTrue(viewModel.scanState.value is ScanState.AlreadyInLibrary)
    }

    @Test
    fun `onBarcodeDetected transitions to NotFound when API returns null`() = runTest(testDispatcher) {
        coEvery { repository.isBookInLibrary("user1", "9780000000000") } returns false
        coEvery { repository.lookupBookByIsbn("9780000000000") } returns null

        viewModel.onBarcodeDetected("9780000000000", "user1")
        advanceUntilIdle()

        val state = viewModel.scanState.value
        assertTrue("Expected NotFound, got $state", state is ScanState.NotFound)
    }

    @Test
    fun `addToLibrary transitions to Added on success`() = runTest(testDispatcher) {
        coEvery { repository.addBook(any()) } returns 1L

        viewModel.addToLibrary("user1", testResult)
        advanceUntilIdle()

        assertTrue(viewModel.scanState.value is ScanState.Added)
    }

    @Test
    fun `addToLibrary transitions to Error on failure`() = runTest(testDispatcher) {
        coEvery { repository.addBook(any()) } throws RuntimeException("DB full")

        viewModel.addToLibrary("user1", testResult)
        advanceUntilIdle()

        val state = viewModel.scanState.value
        assertTrue("Expected Error, got $state", state is ScanState.Error)
        assertTrue((state as ScanState.Error).message.contains("DB full"))
    }

    @Test
    fun `resetScanner returns to Scanning`() = runTest(testDispatcher) {
        coEvery { repository.isBookInLibrary(any(), any()) } returns true
        viewModel.onBarcodeDetected("9780062316097", "user1")
        advanceUntilIdle()

        viewModel.resetScanner()
        assertTrue(viewModel.scanState.value is ScanState.Scanning)
    }

    @Test
    fun `addToLibrary creates BookEntity with correct fields`() = runTest(testDispatcher) {
        coEvery { repository.addBook(any()) } returns 1L

        viewModel.addToLibrary("user1", testResult)
        advanceUntilIdle()

        coVerify {
            repository.addBook(match { book ->
                book.userId == "user1" &&
                book.isbn == "9780062316097" &&
                book.title == "Sapiens" &&
                book.authors == "Yuval Noah Harari" &&
                !book.isFiction &&
                book.genres == listOf("History") &&
                book.addedAt > 0
            })
        }
    }
}
