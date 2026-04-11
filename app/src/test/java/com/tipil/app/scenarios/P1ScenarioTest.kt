package com.tipil.app.scenarios

import com.tipil.app.data.local.BookEntity
import com.tipil.app.data.repository.BookLookupResult
import com.tipil.app.data.repository.BookRepository
import com.tipil.app.ui.bookdetail.BookDetailViewModel
import com.tipil.app.ui.library.LibraryViewModel
import com.tipil.app.ui.library.ReadFilter
import com.tipil.app.ui.library.SortOrder
import com.tipil.app.ui.library.TypeFilter
import com.tipil.app.ui.scanner.ScanState
import com.tipil.app.ui.scanner.ScannerViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
 * Priority 1 end-to-end scenario tests.
 * Each test simulates a real user workflow through multiple ViewModels.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class P1ScenarioTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: BookRepository

    private val testLookup = BookLookupResult(
        isbn = "9780062316097", title = "Sapiens", subtitle = "",
        authors = "Yuval Noah Harari", publisher = "Harper", editor = "",
        publishedYear = "2015", pageCount = 464, isFiction = false,
        genres = listOf("History"), coverUrl = "https://example.com/cover.jpg",
        description = "A brief history of humankind"
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
    // Scenario 1: Scan → Find → Add → View in Library
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `scan book, add to library, verify it appears in library`() = runTest(testDispatcher) {
        // Step 1: Scan
        val scannerVM = ScannerViewModel(repository)
        coEvery { repository.isBookInLibrary("user1", "9780062316097") } returns false
        coEvery { repository.lookupBookByIsbn("9780062316097") } returns testLookup
        coEvery { repository.addBook(any()) } returns 1L

        scannerVM.onBarcodeDetected("9780062316097", "user1")
        advanceUntilIdle()
        assertTrue(scannerVM.scanState.value is ScanState.Found)

        // Step 2: Add
        scannerVM.addToLibrary("user1", testLookup)
        advanceUntilIdle()
        assertTrue(scannerVM.scanState.value is ScanState.Added)

        // Step 3: Verify in library
        val addedBook = BookEntity(
            id = 1, userId = "user1", isbn = "9780062316097", title = "Sapiens",
            authors = "Yuval Noah Harari", isFiction = false, genres = listOf("History"),
            addedAt = System.currentTimeMillis()
        )
        every { repository.getUserBooks("user1") } returns flowOf(listOf(addedBook))

        val libraryVM = LibraryViewModel(repository)
        libraryVM.loadBooks("user1")
        advanceUntilIdle()

        assertEquals(1, libraryVM.uiState.value.books.size)
        assertEquals("Sapiens", libraryVM.uiState.value.books.first().title)
    }

    // ───────────────────────────────────────────────────────────────
    // Scenario 2: Scan duplicate → reject
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `scan duplicate book shows AlreadyInLibrary`() = runTest(testDispatcher) {
        coEvery { repository.isBookInLibrary("user1", "9780062316097") } returns true

        val scannerVM = ScannerViewModel(repository)
        scannerVM.onBarcodeDetected("9780062316097", "user1")
        advanceUntilIdle()

        assertTrue(scannerVM.scanState.value is ScanState.AlreadyInLibrary)
    }

    // ───────────────────────────────────────────────────────────────
    // Scenario 3: View book detail → toggle read → delete
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `view book, mark as read, then delete`() = runTest(testDispatcher) {
        val book = BookEntity(
            id = 1, userId = "user1", isbn = "9780062316097", title = "Sapiens",
            authors = "Yuval Noah Harari", isFiction = false, genres = listOf("History"),
            isRead = false, addedAt = 1000
        )
        coEvery { repository.getBookById(1L, "user1") } returns book

        val detailVM = BookDetailViewModel(repository)
        detailVM.loadBook(1L, "user1")
        advanceUntilIdle()

        assertEquals("Sapiens", detailVM.uiState.value.book!!.title)
        assertFalse(detailVM.uiState.value.book!!.isRead)

        // Toggle read
        detailVM.toggleReadStatus()
        advanceUntilIdle()
        assertTrue(detailVM.uiState.value.book!!.isRead)

        // Delete (the book now has isRead=true after toggle)
        detailVM.deleteBook()
        advanceUntilIdle()
        assertTrue(detailVM.uiState.value.isDeleted)
        coVerify { repository.deleteBook(match { it.id == 1L && it.isRead }) }
    }

    // ───────────────────────────────────────────────────────────────
    // Scenario 4: Filter → Sort → Search combined workflow
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `filter by fiction, sort by newest, then search`() = runTest(testDispatcher) {
        val books = listOf(
            BookEntity(id = 1, userId = "u", isbn = "001", title = "Dune",
                authors = "Frank Herbert", isFiction = true, genres = listOf("Sci-Fi"),
                isRead = true, addedAt = 3000),
            BookEntity(id = 2, userId = "u", isbn = "002", title = "Sapiens",
                authors = "Yuval Noah Harari", isFiction = false, genres = listOf("History"),
                addedAt = 2000),
            BookEntity(id = 3, userId = "u", isbn = "003", title = "Foundation",
                authors = "Isaac Asimov", isFiction = true, genres = listOf("Sci-Fi"),
                addedAt = 1000)
        )
        every { repository.getUserBooks("u") } returns flowOf(books)

        val vm = LibraryViewModel(repository)
        vm.loadBooks("u")
        advanceUntilIdle()

        // Filter fiction
        vm.setTypeFilter(TypeFilter.FICTION)
        assertEquals(2, vm.uiState.value.books.size)

        // Sort by newest
        vm.setSortOrder(SortOrder.DATE_ADDED_NEWEST)
        assertEquals("Dune", vm.uiState.value.books.first().title)
        assertEquals("Foundation", vm.uiState.value.books.last().title)

        // Search within fiction
        vm.setSearchQuery("foundation")
        assertEquals(1, vm.uiState.value.books.size)
        assertEquals("Foundation", vm.uiState.value.books.first().title)
    }

    // ───────────────────────────────────────────────────────────────
    // Scenario 5: Scan → Reset → Scan again
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `scan, reset, scan different book`() = runTest(testDispatcher) {
        val result2 = testLookup.copy(isbn = "9780000000002", title = "Dune")
        coEvery { repository.isBookInLibrary(any(), any()) } returns false
        coEvery { repository.lookupBookByIsbn("9780062316097") } returns testLookup
        coEvery { repository.lookupBookByIsbn("9780000000002") } returns result2

        val vm = ScannerViewModel(repository)

        vm.onBarcodeDetected("9780062316097", "user1")
        advanceUntilIdle()
        assertEquals("Sapiens", (vm.scanState.value as ScanState.Found).result.title)

        vm.resetScanner()
        assertTrue(vm.scanState.value is ScanState.Scanning)

        vm.onBarcodeDetected("9780000000002", "user1")
        advanceUntilIdle()
        assertEquals("Dune", (vm.scanState.value as ScanState.Found).result.title)
    }

    // ───────────────────────────────────────────────────────────────
    // Scenario 6: IDOR — accessing another user's book
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `cannot access book belonging to different user`() = runTest(testDispatcher) {
        coEvery { repository.getBookById(1L, "attacker") } returns null

        val detailVM = BookDetailViewModel(repository)
        detailVM.loadBook(1L, "attacker")
        advanceUntilIdle()

        assertNull(detailVM.uiState.value.book)
    }
}
