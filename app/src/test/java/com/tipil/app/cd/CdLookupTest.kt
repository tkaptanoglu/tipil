package com.tipil.app.cd

import com.tipil.app.data.local.BookEntity
import com.tipil.app.data.local.MediaType
import com.tipil.app.data.repository.BookLookupResult
import com.tipil.app.data.repository.BookRepository
import com.tipil.app.ui.library.LibraryViewModel
import com.tipil.app.ui.library.SortOrder
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
 * Tests for CD scanning, lookup, sorting, and media-type-aware behavior.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CdLookupTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: BookRepository

    private val cdResult = BookLookupResult(
        isbn = "012345678901",
        title = "Abbey Road",
        subtitle = "",
        authors = "The Beatles",
        publisher = "Apple Records",
        editor = "",
        publishedYear = "1969",
        pageCount = 17,      // track count
        isFiction = false,
        genres = listOf("Rock", "Pop"),
        coverUrl = "https://coverartarchive.org/release/abc123/front-250",
        description = "",
        mediaType = MediaType.CD
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
    // Scanner routes to correct lookup based on media type
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `onBarcodeDetected with CD mediaType calls lookupCdByBarcode`() = runTest(testDispatcher) {
        coEvery { repository.isBookInLibrary("u", "012345678901") } returns false
        coEvery { repository.lookupCdByBarcode("012345678901") } returns cdResult

        val vm = ScannerViewModel(repository)
        vm.onBarcodeDetected("012345678901", "u", MediaType.CD)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.lookupCdByBarcode("012345678901") }
        coVerify(exactly = 0) { repository.lookupBookByIsbn(any()) }

        val state = vm.scanState.value
        assertTrue(state is ScanState.Found)
        assertEquals("Abbey Road", (state as ScanState.Found).result.title)
        assertEquals(MediaType.CD, state.result.mediaType)
    }

    @Test
    fun `onBarcodeDetected with BOOK mediaType calls lookupBookByIsbn`() = runTest(testDispatcher) {
        val bookResult = cdResult.copy(isbn = "9780062316097", mediaType = MediaType.BOOK, title = "Sapiens")
        coEvery { repository.isBookInLibrary("u", "9780062316097") } returns false
        coEvery { repository.lookupBookByIsbn("9780062316097") } returns bookResult

        val vm = ScannerViewModel(repository)
        vm.onBarcodeDetected("9780062316097", "u", MediaType.BOOK)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.lookupBookByIsbn("9780062316097") }
        coVerify(exactly = 0) { repository.lookupCdByBarcode(any()) }
    }

    @Test
    fun `CD not found transitions to NotFound state`() = runTest(testDispatcher) {
        coEvery { repository.isBookInLibrary("u", "999999999999") } returns false
        coEvery { repository.lookupCdByBarcode("999999999999") } returns null

        val vm = ScannerViewModel(repository)
        vm.onBarcodeDetected("999999999999", "u", MediaType.CD)
        advanceUntilIdle()

        val state = vm.scanState.value
        assertTrue(state is ScanState.NotFound)
        assertEquals("999999999999", (state as ScanState.NotFound).isbn)
    }

    @Test
    fun `CD already in library transitions to AlreadyInLibrary state`() = runTest(testDispatcher) {
        coEvery { repository.isBookInLibrary("u", "012345678901") } returns true

        val vm = ScannerViewModel(repository)
        vm.onBarcodeDetected("012345678901", "u", MediaType.CD)
        advanceUntilIdle()

        assertTrue(vm.scanState.value is ScanState.AlreadyInLibrary)
    }

    @Test
    fun `addToLibrary stores CD with correct mediaType`() = runTest(testDispatcher) {
        coEvery { repository.addBook(any()) } returns 1L

        val vm = ScannerViewModel(repository)
        vm.addToLibrary("u", cdResult)
        advanceUntilIdle()

        coVerify {
            repository.addBook(match {
                it.mediaType == MediaType.CD.name &&
                it.title == "Abbey Road" &&
                it.authors == "The Beatles"
            })
        }
        assertTrue(vm.scanState.value is ScanState.Added)
    }

    // ───────────────────────────────────────────────────────────────
    // Sort key: "The" prefix stripping for CDs
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `CD sort strips leading The from artist name`() = runTest(testDispatcher) {
        val cds = listOf(
            BookEntity(id = 1, userId = "u", isbn = "001", title = "Abbey Road",
                authors = "The Beatles", mediaType = MediaType.CD.name, addedAt = 1000),
            BookEntity(id = 2, userId = "u", isbn = "002", title = "Thriller",
                authors = "Michael Jackson", mediaType = MediaType.CD.name, addedAt = 2000),
            BookEntity(id = 3, userId = "u", isbn = "003", title = "Briefcase Full of Blues",
                authors = "The Blues Brothers", mediaType = MediaType.CD.name, addedAt = 3000),
            BookEntity(id = 4, userId = "u", isbn = "004", title = "Lemonade",
                authors = "Beyonce", mediaType = MediaType.CD.name, addedAt = 4000)
        )
        every { repository.getUserBooks("u") } returns flowOf(cds)

        val vm = LibraryViewModel(repository)
        vm.loadBooks("u")
        advanceUntilIdle()

        vm.setMediaTypeFilter(MediaType.CD)
        vm.setSortOrder(SortOrder.AUTHOR_AZ)
        advanceUntilIdle()

        val sorted = vm.uiState.value.books
        // Expected order: Beatles (B), Beyonce (B), Blues Brothers (B), Michael Jackson (J)
        // Within B's: "beatles" < "beyonce" < "blues brothers"
        assertEquals("The Beatles", sorted[0].authors)
        assertEquals("Beyonce", sorted[1].authors)
        assertEquals("The Blues Brothers", sorted[2].authors)
        assertEquals("Michael Jackson", sorted[3].authors)
    }

    @Test
    fun `CD sort is case-insensitive for The prefix`() = runTest(testDispatcher) {
        val cds = listOf(
            BookEntity(id = 1, userId = "u", isbn = "001", title = "A",
                authors = "the rolling stones", mediaType = MediaType.CD.name, addedAt = 1000),
            BookEntity(id = 2, userId = "u", isbn = "002", title = "B",
                authors = "THE WHO", mediaType = MediaType.CD.name, addedAt = 2000),
            BookEntity(id = 3, userId = "u", isbn = "003", title = "C",
                authors = "Adele", mediaType = MediaType.CD.name, addedAt = 3000)
        )
        every { repository.getUserBooks("u") } returns flowOf(cds)

        val vm = LibraryViewModel(repository)
        vm.loadBooks("u")
        advanceUntilIdle()

        vm.setMediaTypeFilter(MediaType.CD)
        vm.setSortOrder(SortOrder.AUTHOR_AZ)
        advanceUntilIdle()

        val sorted = vm.uiState.value.books
        // Adele (a) < Rolling Stones (r) < Who (w)
        assertEquals("Adele", sorted[0].authors)
        assertEquals("the rolling stones", sorted[1].authors)
        assertEquals("THE WHO", sorted[2].authors)
    }

    @Test
    fun `book sort still uses last name extraction`() = runTest(testDispatcher) {
        val books = listOf(
            BookEntity(id = 1, userId = "u", isbn = "001", title = "A",
                authors = "J.K. Rowling", mediaType = MediaType.BOOK.name, addedAt = 1000),
            BookEntity(id = 2, userId = "u", isbn = "002", title = "B",
                authors = "Stephen King", mediaType = MediaType.BOOK.name, addedAt = 2000)
        )
        every { repository.getUserBooks("u") } returns flowOf(books)

        val vm = LibraryViewModel(repository)
        vm.loadBooks("u")
        advanceUntilIdle()

        vm.setSortOrder(SortOrder.AUTHOR_AZ)
        advanceUntilIdle()

        val sorted = vm.uiState.value.books
        // King (K) before Rowling (R)
        assertEquals("Stephen King", sorted[0].authors)
        assertEquals("J.K. Rowling", sorted[1].authors)
    }

    @Test
    fun `mixed media types sort correctly within their type`() = runTest(testDispatcher) {
        val items = listOf(
            BookEntity(id = 1, userId = "u", isbn = "001", title = "Sapiens",
                authors = "Yuval Harari", mediaType = MediaType.BOOK.name, addedAt = 1000),
            BookEntity(id = 2, userId = "u", isbn = "002", title = "Abbey Road",
                authors = "The Beatles", mediaType = MediaType.CD.name, addedAt = 2000)
        )
        every { repository.getUserBooks("u") } returns flowOf(items)

        val vm = LibraryViewModel(repository)
        vm.loadBooks("u")
        advanceUntilIdle()

        // When showing all types, each item uses its own sort key logic
        vm.setSortOrder(SortOrder.AUTHOR_AZ)
        advanceUntilIdle()

        val sorted = vm.uiState.value.books
        // "beatles" (B from CD sort) before "harari" (H from book sort)
        assertEquals("The Beatles", sorted[0].authors)
        assertEquals("Yuval Harari", sorted[1].authors)
    }

    // ───────────────────────────────────────────────────────────────
    // Media type filtering for CDs
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `filtering by CD shows only CDs`() = runTest(testDispatcher) {
        val items = listOf(
            BookEntity(id = 1, userId = "u", isbn = "001", title = "Book",
                authors = "Author", mediaType = MediaType.BOOK.name, addedAt = 1000),
            BookEntity(id = 2, userId = "u", isbn = "002", title = "CD",
                authors = "Artist", mediaType = MediaType.CD.name, addedAt = 2000)
        )
        every { repository.getUserBooks("u") } returns flowOf(items)

        val vm = LibraryViewModel(repository)
        vm.loadBooks("u")
        advanceUntilIdle()

        vm.setMediaTypeFilter(MediaType.CD)
        val state = vm.uiState.value
        assertEquals(1, state.books.size)
        assertEquals("CD", state.books[0].title)
        assertEquals(1, state.bookCount) // count reflects filtered type
    }

    // ───────────────────────────────────────────────────────────────
    // BookLookupResult carries CD-specific semantics
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `BookLookupResult for CD carries correct fields`() {
        assertEquals("012345678901", cdResult.isbn)
        assertEquals("Abbey Road", cdResult.title)
        assertEquals("The Beatles", cdResult.authors)       // artist
        assertEquals("Apple Records", cdResult.publisher)    // label
        assertEquals(17, cdResult.pageCount)                 // track count
        assertEquals(MediaType.CD, cdResult.mediaType)
        assertEquals(listOf("Rock", "Pop"), cdResult.genres)
    }

    // ───────────────────────────────────────────────────────────────
    // Edge cases for "The" stripping
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `artist name that is exactly The is not stripped`() {
        val vm = LibraryViewModel(mockk(relaxed = true))
        val entity = BookEntity(id = 1, userId = "u", isbn = "001", title = "A",
            authors = "The", mediaType = MediaType.CD.name, addedAt = 1000)
        // "The" does NOT start with "The " (needs trailing space), so it stays
        val key = vm.extractSortKey(entity)
        assertEquals("the", key)
    }

    @Test
    fun `artist name Theo does not strip The`() {
        val vm = LibraryViewModel(mockk(relaxed = true))
        val entity = BookEntity(id = 1, userId = "u", isbn = "001", title = "A",
            authors = "Theo", mediaType = MediaType.CD.name, addedAt = 1000)
        // "Theo" does NOT start with "The " (note the space)
        val key = vm.extractSortKey(entity)
        assertEquals("theo", key)
    }

    @Test
    fun `artist name Theatre Of Hate strips nothing`() {
        val vm = LibraryViewModel(mockk(relaxed = true))
        val entity = BookEntity(id = 1, userId = "u", isbn = "001", title = "A",
            authors = "Theatre Of Hate", mediaType = MediaType.CD.name, addedAt = 1000)
        val key = vm.extractSortKey(entity)
        assertEquals("theatre of hate", key)
    }

    @Test
    fun `empty artist name for CD returns empty sort key`() {
        val vm = LibraryViewModel(mockk(relaxed = true))
        val entity = BookEntity(id = 1, userId = "u", isbn = "001", title = "A",
            authors = "", mediaType = MediaType.CD.name, addedAt = 1000)
        val key = vm.extractSortKey(entity)
        assertEquals("", key)
    }
}
