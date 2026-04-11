package com.tipil.app.ui.library

import com.tipil.app.data.local.BookEntity
import com.tipil.app.data.local.MediaType
import com.tipil.app.data.repository.BookRepository
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
 * Tests for the MediaType filtering in LibraryViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelMediaTypeTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: BookRepository
    private lateinit var viewModel: LibraryViewModel

    private val mixedLibrary = listOf(
        BookEntity(
            id = 1, userId = "u", isbn = "001", title = "Dune",
            authors = "Frank Herbert", isFiction = true, genres = listOf("Sci-Fi"),
            mediaType = MediaType.BOOK.name, addedAt = 1000
        ),
        BookEntity(
            id = 2, userId = "u", isbn = "002", title = "OK Computer",
            authors = "Radiohead", isFiction = false, genres = listOf("Alternative"),
            mediaType = MediaType.CD.name, addedAt = 2000
        ),
        BookEntity(
            id = 3, userId = "u", isbn = "003", title = "Blade Runner",
            authors = "Ridley Scott", isFiction = true, genres = listOf("Sci-Fi"),
            mediaType = MediaType.DVD.name, addedAt = 3000
        ),
        BookEntity(
            id = 4, userId = "u", isbn = "004", title = "Catan",
            authors = "Klaus Teuber", isFiction = false, genres = listOf("Strategy"),
            mediaType = MediaType.BOARD_GAME.name, addedAt = 4000
        ),
        BookEntity(
            id = 5, userId = "u", isbn = "005", title = "Foundation",
            authors = "Isaac Asimov", isFiction = true, genres = listOf("Sci-Fi"),
            mediaType = MediaType.BOOK.name, addedAt = 5000
        ),
        BookEntity(
            id = 6, userId = "u", isbn = "006", title = "National Geographic",
            authors = "Various", isFiction = false, genres = listOf("Nature"),
            mediaType = MediaType.MAGAZINE.name, addedAt = 6000
        ),
        BookEntity(
            id = 7, userId = "u", isbn = "007", title = "Abbey Road",
            authors = "The Beatles", isFiction = false, genres = listOf("Rock"),
            mediaType = MediaType.CASSETTE.name, addedAt = 7000
        )
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        every { repository.getUserBooks("u") } returns flowOf(mixedLibrary)
        viewModel = LibraryViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ───────────────────────────────────────────────────────────────
    // Media type tab discovery
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `availableMediaTypes contains all types present in library`() = runTest(testDispatcher) {
        viewModel.loadBooks("u")
        advanceUntilIdle()

        val types = viewModel.uiState.value.availableMediaTypes
        assertTrue(types.contains(MediaType.BOOK))
        assertTrue(types.contains(MediaType.CD))
        assertTrue(types.contains(MediaType.DVD))
        assertTrue(types.contains(MediaType.BOARD_GAME))
        assertTrue(types.contains(MediaType.MAGAZINE))
        assertTrue(types.contains(MediaType.CASSETTE))
        assertEquals(6, types.size)
    }

    @Test
    fun `availableMediaTypes is sorted by enum ordinal`() = runTest(testDispatcher) {
        viewModel.loadBooks("u")
        advanceUntilIdle()

        val types = viewModel.uiState.value.availableMediaTypes
        val ordinals = types.map { it.ordinal }
        assertEquals(ordinals.sorted(), ordinals)
    }

    // ───────────────────────────────────────────────────────────────
    // Media type filtering
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `default shows all media types`() = runTest(testDispatcher) {
        viewModel.loadBooks("u")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedMediaType)
        assertEquals(7, viewModel.uiState.value.books.size)
    }

    @Test
    fun `filter by BOOK shows only books`() = runTest(testDispatcher) {
        viewModel.loadBooks("u")
        advanceUntilIdle()

        viewModel.setMediaTypeFilter(MediaType.BOOK)
        val books = viewModel.uiState.value.books
        assertEquals(2, books.size)
        assertTrue(books.all { it.mediaType == MediaType.BOOK.name })
    }

    @Test
    fun `filter by CD shows only CDs`() = runTest(testDispatcher) {
        viewModel.loadBooks("u")
        advanceUntilIdle()

        viewModel.setMediaTypeFilter(MediaType.CD)
        val items = viewModel.uiState.value.books
        assertEquals(1, items.size)
        assertEquals("OK Computer", items.first().title)
    }

    @Test
    fun `filter by DVD shows only DVDs`() = runTest(testDispatcher) {
        viewModel.loadBooks("u")
        advanceUntilIdle()

        viewModel.setMediaTypeFilter(MediaType.DVD)
        assertEquals(1, viewModel.uiState.value.books.size)
        assertEquals("Blade Runner", viewModel.uiState.value.books.first().title)
    }

    @Test
    fun `filter by BOARD_GAME shows only board games`() = runTest(testDispatcher) {
        viewModel.loadBooks("u")
        advanceUntilIdle()

        viewModel.setMediaTypeFilter(MediaType.BOARD_GAME)
        assertEquals(1, viewModel.uiState.value.books.size)
        assertEquals("Catan", viewModel.uiState.value.books.first().title)
    }

    @Test
    fun `clearing media type filter shows all again`() = runTest(testDispatcher) {
        viewModel.loadBooks("u")
        advanceUntilIdle()

        viewModel.setMediaTypeFilter(MediaType.BOOK)
        assertEquals(2, viewModel.uiState.value.books.size)

        viewModel.setMediaTypeFilter(null)
        assertEquals(7, viewModel.uiState.value.books.size)
    }

    // ───────────────────────────────────────────────────────────────
    // Media type + other filters combined
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `media type + fiction filter combined`() = runTest(testDispatcher) {
        viewModel.loadBooks("u")
        advanceUntilIdle()

        viewModel.setMediaTypeFilter(MediaType.BOOK)
        viewModel.setTypeFilter(TypeFilter.FICTION)
        val books = viewModel.uiState.value.books
        assertEquals(2, books.size) // Dune and Foundation are both fiction books
        assertTrue(books.all { it.isFiction && it.mediaType == MediaType.BOOK.name })
    }

    @Test
    fun `media type + search combined`() = runTest(testDispatcher) {
        viewModel.loadBooks("u")
        advanceUntilIdle()

        viewModel.setMediaTypeFilter(MediaType.BOOK)
        viewModel.setSearchQuery("dune")
        assertEquals(1, viewModel.uiState.value.books.size)
        assertEquals("Dune", viewModel.uiState.value.books.first().title)
    }

    @Test
    fun `media type + sort combined`() = runTest(testDispatcher) {
        viewModel.loadBooks("u")
        advanceUntilIdle()

        viewModel.setMediaTypeFilter(MediaType.BOOK)
        viewModel.setSortOrder(SortOrder.DATE_ADDED_NEWEST)
        val titles = viewModel.uiState.value.books.map { it.title }
        assertEquals(listOf("Foundation", "Dune"), titles)
    }

    // ───────────────────────────────────────────────────────────────
    // Count and genres update with media type
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `bookCount reflects selected media type`() = runTest(testDispatcher) {
        viewModel.loadBooks("u")
        advanceUntilIdle()

        assertEquals(7, viewModel.uiState.value.bookCount)

        viewModel.setMediaTypeFilter(MediaType.BOOK)
        assertEquals(2, viewModel.uiState.value.bookCount)

        viewModel.setMediaTypeFilter(MediaType.CD)
        assertEquals(1, viewModel.uiState.value.bookCount)

        viewModel.setMediaTypeFilter(null)
        assertEquals(7, viewModel.uiState.value.bookCount)
    }

    @Test
    fun `availableGenres reflects selected media type`() = runTest(testDispatcher) {
        viewModel.loadBooks("u")
        advanceUntilIdle()

        viewModel.setMediaTypeFilter(MediaType.BOOK)
        val bookGenres = viewModel.uiState.value.availableGenres
        assertTrue(bookGenres.contains("Sci-Fi"))
        assertFalse(bookGenres.contains("Alternative"))
        assertFalse(bookGenres.contains("Strategy"))

        viewModel.setMediaTypeFilter(MediaType.CD)
        val cdGenres = viewModel.uiState.value.availableGenres
        assertTrue(cdGenres.contains("Alternative"))
        assertFalse(cdGenres.contains("Sci-Fi"))
    }

    // ───────────────────────────────────────────────────────────────
    // Books-only library (backward compatibility)
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `library with only books shows single media type`() = runTest(testDispatcher) {
        val booksOnly = listOf(
            BookEntity(id = 1, userId = "u", isbn = "001", title = "Dune",
                authors = "Frank Herbert", mediaType = MediaType.BOOK.name, addedAt = 1000)
        )
        every { repository.getUserBooks("u") } returns flowOf(booksOnly)

        val vm = LibraryViewModel(repository)
        vm.loadBooks("u")
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.availableMediaTypes.size)
        assertEquals(MediaType.BOOK, vm.uiState.value.availableMediaTypes.first())
    }

    @Test
    fun `legacy books without explicit mediaType default to BOOK`() = runTest(testDispatcher) {
        // BookEntity default mediaType is MediaType.BOOK.name
        val legacyBook = BookEntity(
            id = 1, userId = "u", isbn = "001", title = "Old Book",
            authors = "Author", addedAt = 1000
        )
        assertEquals(MediaType.BOOK.name, legacyBook.mediaType)
    }
}
