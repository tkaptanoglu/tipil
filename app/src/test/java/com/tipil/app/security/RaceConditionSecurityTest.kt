package com.tipil.app.security

import com.tipil.app.data.repository.BookLookupResult
import com.tipil.app.data.repository.BookRepository
import com.tipil.app.ui.scanner.ScanState
import com.tipil.app.ui.scanner.ScannerViewModel
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

/**
 * Race condition and concurrency security tests.
 *
 * Verifies that rapid duplicate barcode detections don't cause
 * double-adds, state corruption, or multiple API calls.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RaceConditionSecurityTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: BookRepository

    private val testResult = BookLookupResult(
        isbn = "9780062316097", title = "Sapiens", subtitle = "",
        authors = "Yuval Noah Harari", publisher = "", editor = "",
        publishedYear = "2015", pageCount = 464, isFiction = false,
        genres = listOf("History"), coverUrl = "", description = ""
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ───────────────────────────────────────────────────────────────
    // Duplicate barcode detection
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `rapid duplicate barcodes trigger exactly one lookup`() = runTest(testDispatcher) {
        coEvery { repository.isBookInLibrary("u", "9780062316097") } returns false
        coEvery { repository.lookupBookByIsbn("9780062316097") } returns testResult

        val vm = ScannerViewModel(repository)

        // Simulate rapid-fire camera frames detecting same barcode
        repeat(50) {
            vm.onBarcodeDetected("9780062316097", "u")
        }
        advanceUntilIdle()

        // processingLock ensures only the FIRST call launches a coroutine
        coVerify(exactly = 1) { repository.lookupBookByIsbn("9780062316097") }
    }

    @Test
    fun `second different barcode while first is processing is dropped`() = runTest(testDispatcher) {
        coEvery { repository.isBookInLibrary("u", any()) } returns false
        coEvery { repository.lookupBookByIsbn("9780062316097") } returns testResult
        coEvery { repository.lookupBookByIsbn("9780000000002") } returns testResult.copy(
            isbn = "9780000000002", title = "Other Book"
        )

        val vm = ScannerViewModel(repository)

        // First barcode acquires the lock
        vm.onBarcodeDetected("9780062316097", "u")
        // Second barcode is dropped because lock is held
        vm.onBarcodeDetected("9780000000002", "u")
        advanceUntilIdle()

        // Only the first ISBN was looked up
        coVerify(exactly = 1) { repository.lookupBookByIsbn("9780062316097") }
        coVerify(exactly = 0) { repository.lookupBookByIsbn("9780000000002") }

        val state = vm.scanState.value
        assertTrue(state is ScanState.Found)
        assertEquals("Sapiens", (state as ScanState.Found).result.title)
    }

    // ───────────────────────────────────────────────────────────────
    // Double-add prevention
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `calling addToLibrary twice rapidly with same result`() = runTest(testDispatcher) {
        coEvery { repository.addBook(any()) } returns 1L

        val vm = ScannerViewModel(repository)

        // Simulate double-tap on "Add to Library"
        vm.addToLibrary("u", testResult)
        vm.addToLibrary("u", testResult)
        advanceUntilIdle()

        // Both calls go through at ViewModel level (unique constraint in DB prevents dups)
        // At minimum, verify it doesn't crash
        assertTrue(vm.scanState.value is ScanState.Added)
    }

    @Test
    fun `addToLibrary handles concurrent DB constraint violation`() = runTest(testDispatcher) {
        // First call succeeds, second throws (unique constraint)
        var callCount = 0
        coEvery { repository.addBook(any()) } answers {
            callCount++
            if (callCount == 1) 1L
            else throw android.database.sqlite.SQLiteConstraintException("UNIQUE constraint failed")
        }

        val vm = ScannerViewModel(repository)
        vm.addToLibrary("u", testResult)
        advanceUntilIdle()
        assertTrue(vm.scanState.value is ScanState.Added)

        vm.resetScanner()
        vm.addToLibrary("u", testResult)
        advanceUntilIdle()
        // Should transition to Error, not crash
        assertTrue(vm.scanState.value is ScanState.Error)
    }

    // ───────────────────────────────────────────────────────────────
    // Reset during processing
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `resetScanner during active lookup does not crash`() = runTest(testDispatcher) {
        coEvery { repository.isBookInLibrary("u", any()) } returns false
        coEvery { repository.lookupBookByIsbn(any()) } returns testResult

        val vm = ScannerViewModel(repository)
        vm.onBarcodeDetected("9780062316097", "u")

        // Reset before lookup completes
        vm.resetScanner()
        advanceUntilIdle()

        // State should be Scanning (reset) or Found (lookup completed after reset)
        // Either is acceptable; crash is not
        val state = vm.scanState.value
        assertTrue(
            "State should be Scanning or Found, was $state",
            state is ScanState.Scanning || state is ScanState.Found
        )
    }

    // ───────────────────────────────────────────────────────────────
    // Rapid reset cycles
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `rapid scan-reset cycles do not corrupt state`() = runTest(testDispatcher) {
        coEvery { repository.isBookInLibrary("u", any()) } returns false
        coEvery { repository.lookupBookByIsbn(any()) } returns testResult

        val vm = ScannerViewModel(repository)

        repeat(100) {
            vm.onBarcodeDetected("978000000000${it % 10}", "u")
            vm.resetScanner() // releases the lock
        }
        advanceUntilIdle()

        // Final state may be Scanning (reset won last) or Found (lookup completed)
        // Either is acceptable — no crash, no corruption
        val state = vm.scanState.value
        assertTrue(
            "State should be stable, was $state",
            state is ScanState.Scanning || state is ScanState.Found
        )
    }
}
