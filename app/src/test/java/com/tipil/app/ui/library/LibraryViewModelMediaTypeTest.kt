package com.tipil.app.ui.library

import com.tipil.app.data.local.BookEntity
import com.tipil.app.data.local.MediaCategory
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
        ),
        BookEntity(
            id = 8, userId = "u", isbn = "008", title = "Dark Side of the Moon",
            authors = "Pink Floyd", isFiction = false, genres = listOf("Rock"),
            mediaType = MediaType.VINYL.name, addedAt = 8000
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
    // Category tab discovery
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `availableCategories contains all categories present in library`() = runTest(testDispatcher) {
        viewModel.loadBooks("u")
        advanceUntilIdle()

        val categories = viewModel.uiState.value.availableCategories
        assertTrue(categories.contains(MediaCategory.BOOKS))
        assertTrue(categories.contains(MediaCategory.MUSIC))
        assertTrue(categories.contains(MediaCategory.DVDS))
        assertTrue(categories.contains(MediaCategory.BOARD_GAMES))
        assertTrue(categories.contains(MediaCategory.MAGAZINES))
        assertEquals(5, categories.size)
    }

    @Test
    fun `availableCategories is sorted by enum ordinal`() = runTest(testDispatcher) {
        viewModel.loadBooks("u")
        advanceUntilIdle()

        val categories = viewModel.uiState.value.availableCategories
        val ordinals = categories.map { it.ordinal }
        assertEquals(ordinals.sorted(), ordinals)
    }

    // ───────────────────────────────────────────────────────────────
    // Category filtering
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `default shows first available category`() = runTest(testDispatcher) {
        viewModel.loadBooks("u")
        advanceUntilIdle()

        // Default is the first available category (BOOKS, since it has lowest ordinal)
        assertEquals(MediaCategory.BOOKS, viewModel.uiState.value.selectedCategory)
        assertEquals(2, viewModel.uiState.value.books.size)
    }

    @Test
    fun `filter by BOOKS shows only books`() = runTest(testDispatcher) {
        viewModel.loadBooks("u")
        advanceUntilIdle()

        viewModel.setCategoryFilter(MediaCategory.BOOKS)
        val books = viewModel.uiState.value.books
        assertEquals(2, books.size)
        assertTrue(books.all { it.mediaType == MediaType.BOOK.name })
    }

    @Test
    fun `filter by MUSIC shows CDs, cassettes, and vinyl`() = runTest(testDispatcher) {
        viewModel.loadBooks("u")
        advanceUntilIdle()

        viewModel.setCategoryFilter(MediaCategory.MUSIC)
        val items = viewModel.uiState.value.books
        assertEquals(3, items.size)
        val musicTypes = setOf(MediaType.CD.name, MediaType.CASSETTE.name, MediaType.VINYL.name)
        assertTrue(items.all { it.mediaType in musicTypes })
    }

    @Test
    fun `filter by DVDS shows only DVDs`() = runTest(testDispatcher) {
        viewModel.loadBooks("u")
        advanceUntilIdle()

        viewModel.setCategoryFilter(MediaCategory.DVDS)
        assertEquals(1, viewModel.uiState.value.books.size)
        assertEquals("Blade Runner", viewModel.uiState.value.books.first().title)
    }

    @Test
    fun `filter by BOARD_GAMES shows only board games`() = runTest(testDispatcher) {
        viewModel.loadBooks("u")
        advanceUntilIdle()

        viewModel.setCategoryFilter(MediaCategory.BOARD_GAMES)
        assertEquals(1, viewModel.uiState.value.books.size)
        assertEquals("Catan", viewModel.uiState.value.books.first().title)
    }

    @Test
    fun `switching category filter changes visible items`() = runTest(testDispatcher) {
        viewModel.loadBooks("u")
        advanceUntilIdle()

        viewModel.setCategoryFilter(MediaCategory.BOOKS)
        assertEquals(2, viewModel.uiState.value.books.size)

        viewModel.setCategoryFilter(MediaCategory.MUSIC)
        assertEquals(3, viewModel.uiState.value.books.size)
    }

    // ───────────────────────────────────────────────────────────────
    // Category + other filters combined
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `category + fiction filter combined`() = runTest(testDispatcher) {
        viewModel.loadBooks("u")
        advanceUntilIdle()

        viewModel.setCategoryFilter(MediaCategory.BOOKS)
        viewModel.setTypeFilter(TypeFilter.FICTION)
        val books = viewModel.uiState.value.books
        assertEquals(2, books.size) // Dune and Foundation are both fiction books
        assertTrue(books.all { it.isFiction && it.mediaType == MediaType.BOOK.name })
    }

    @Test
    fun `category + search combined`() = runTest(testDispatcher) {
        viewModel.loadBooks("u")
        advanceUntilIdle()

        viewModel.setCategoryFilter(MediaCategory.BOOKS)
        viewModel.setSearchQuery("dune")
        assertEquals(1, viewModel.uiState.value.books.size)
        assertEquals("Dune", viewModel.uiState.value.books.first().title)
    }

    @Test
    fun `category + sort combined`() = runTest(testDispatcher) {
        viewModel.loadBooks("u")
        advanceUntilIdle()

        viewModel.setCategoryFilter(MediaCategory.BOOKS)
        viewModel.setSortOrder(SortOrder.DATE_ADDED_NEWEST)
        val titles = viewModel.uiState.value.books.map { it.title }
        assertEquals(listOf("Foundation", "Dune"), titles)
    }

    // ───────────────────────────────────────────────────────────────
    // Count and genres update with category
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `bookCount reflects selected category`() = runTest(testDispatcher) {
        viewModel.loadBooks("u")
        advanceUntilIdle()

        // Default is BOOKS
        assertEquals(2, viewModel.uiState.value.bookCount)

        viewModel.setCategoryFilter(MediaCategory.BOOKS)
        assertEquals(2, viewModel.uiState.value.bookCount)

        viewModel.setCategoryFilter(MediaCategory.MUSIC)
        assertEquals(3, viewModel.uiState.value.bookCount)

        viewModel.setCategoryFilter(MediaCategory.DVDS)
        assertEquals(1, viewModel.uiState.value.bookCount)
    }

    @Test
    fun `availableGenres reflects selected category`() = runTest(testDispatcher) {
        viewModel.loadBooks("u")
        advanceUntilIdle()

        viewModel.setCategoryFilter(MediaCategory.BOOKS)
        val bookGenres = viewModel.uiState.value.availableGenres
        assertTrue(bookGenres.contains("Sci-Fi"))
        assertFalse(bookGenres.contains("Alternative"))
        assertFalse(bookGenres.contains("Strategy"))

        viewModel.setCategoryFilter(MediaCategory.MUSIC)
        val musicGenres = viewModel.uiState.value.availableGenres
        assertTrue(musicGenres.contains("Alternative"))
        assertTrue(musicGenres.contains("Rock"))
        assertFalse(musicGenres.contains("Sci-Fi"))
    }

    // ───────────────────────────────────────────────────────────────
    // Books-only library (backward compatibility)
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `library with only books shows single category`() = runTest(testDispatcher) {
        val booksOnly = listOf(
            BookEntity(id = 1, userId = "u", isbn = "001", title = "Dune",
                authors = "Frank Herbert", mediaType = MediaType.BOOK.name, addedAt = 1000)
        )
        every { repository.getUserBooks("u") } returns flowOf(booksOnly)

        val vm = LibraryViewModel(repository)
        vm.loadBooks("u")
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.availableCategories.size)
        assertEquals(MediaCategory.BOOKS, vm.uiState.value.availableCategories.first())
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
