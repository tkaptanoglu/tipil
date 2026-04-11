package com.tipil.app.ui.library

import com.tipil.app.data.local.BookEntity
import com.tipil.app.data.repository.BookRepository
import com.tipil.app.util.Tier1Labels
import io.mockk.every
import io.mockk.mockk
import io.mockk.coVerify
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

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: BookRepository
    private lateinit var viewModel: LibraryViewModel

    private val testBooks = listOf(
        BookEntity(
            id = 1, userId = "user1", isbn = "9780000000001", title = "Dune",
            authors = "Frank Herbert", isFiction = true, genres = listOf("Sci-Fi"),
            isRead = true, addedAt = 1000
        ),
        BookEntity(
            id = 2, userId = "user1", isbn = "9780000000002", title = "Sapiens",
            authors = "Yuval Noah Harari", isFiction = false, genres = listOf("History"),
            isRead = false, addedAt = 2000
        ),
        BookEntity(
            id = 3, userId = "user1", isbn = "9780000000003", title = "Foundation",
            authors = "Isaac Asimov", isFiction = true, genres = listOf("Sci-Fi"),
            isRead = false, addedAt = 3000
        ),
        BookEntity(
            id = 4, userId = "user1", isbn = "9780000000004", title = "The Great Gatsby",
            authors = "F. Scott Fitzgerald", isFiction = true, genres = listOf("Drama"),
            isRead = true, addedAt = 500
        )
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        every { repository.getUserBooks("user1") } returns flowOf(testBooks)
        viewModel = LibraryViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun loadAndIdle() = runTest(testDispatcher) {
        viewModel.loadBooks("user1")
        advanceUntilIdle()
    }

    // ───────────────────────────────────────────────────────────────
    // Loading
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `initial state is loading`() {
        assertTrue(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `loadBooks populates allBooks and books`() = runTest(testDispatcher) {
        viewModel.loadBooks("user1")
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(4, state.bookCount)
        assertEquals(4, state.books.size)
    }

    // ───────────────────────────────────────────────────────────────
    // Sorting
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `default sort is author A-Z by last name`() = runTest(testDispatcher) {
        viewModel.loadBooks("user1")
        advanceUntilIdle()
        val titles = viewModel.uiState.value.books.map { it.title }
        // Asimov, Fitzgerald, Harari, Herbert
        assertEquals(listOf("Foundation", "The Great Gatsby", "Sapiens", "Dune"), titles)
    }

    @Test
    fun `sort by date added newest first`() = runTest(testDispatcher) {
        viewModel.loadBooks("user1")
        advanceUntilIdle()
        viewModel.setSortOrder(SortOrder.DATE_ADDED_NEWEST)
        val titles = viewModel.uiState.value.books.map { it.title }
        assertEquals(listOf("Foundation", "Sapiens", "Dune", "The Great Gatsby"), titles)
    }

    @Test
    fun `sort by date added oldest first`() = runTest(testDispatcher) {
        viewModel.loadBooks("user1")
        advanceUntilIdle()
        viewModel.setSortOrder(SortOrder.DATE_ADDED_OLDEST)
        val titles = viewModel.uiState.value.books.map { it.title }
        assertEquals(listOf("The Great Gatsby", "Dune", "Sapiens", "Foundation"), titles)
    }

    // ───────────────────────────────────────────────────────────────
    // Read filter
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `read filter shows only read books`() = runTest(testDispatcher) {
        viewModel.loadBooks("user1")
        advanceUntilIdle()
        viewModel.setReadFilter(ReadFilter.READ)
        val books = viewModel.uiState.value.books
        assertEquals(2, books.size)
        assertTrue(books.all { it.isRead })
    }

    @Test
    fun `unread filter shows only unread books`() = runTest(testDispatcher) {
        viewModel.loadBooks("user1")
        advanceUntilIdle()
        viewModel.setReadFilter(ReadFilter.UNREAD)
        val books = viewModel.uiState.value.books
        assertEquals(2, books.size)
        assertTrue(books.none { it.isRead })
    }

    // ───────────────────────────────────────────────────────────────
    // Type filter
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `fiction filter shows only fiction books`() = runTest(testDispatcher) {
        viewModel.loadBooks("user1")
        advanceUntilIdle()
        viewModel.setTypeFilter(TypeFilter.FICTION)
        val books = viewModel.uiState.value.books
        assertEquals(3, books.size)
        assertTrue(books.all { it.isFiction })
    }

    @Test
    fun `non-fiction filter shows only non-fiction books`() = runTest(testDispatcher) {
        viewModel.loadBooks("user1")
        advanceUntilIdle()
        viewModel.setTypeFilter(TypeFilter.NON_FICTION)
        val books = viewModel.uiState.value.books
        assertEquals(1, books.size)
        assertFalse(books.first().isFiction)
    }

    // ───────────────────────────────────────────────────────────────
    // Genre filter
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `genre filter shows only matching books`() = runTest(testDispatcher) {
        viewModel.loadBooks("user1")
        advanceUntilIdle()
        viewModel.setGenreFilter("Sci-Fi")
        val books = viewModel.uiState.value.books
        assertEquals(2, books.size)
        assertTrue(books.all { "Sci-Fi" in it.genres })
    }

    @Test
    fun `toggle genre filter off deselects it`() = runTest(testDispatcher) {
        viewModel.loadBooks("user1")
        advanceUntilIdle()
        viewModel.setGenreFilter("Sci-Fi")
        viewModel.setGenreFilter("Sci-Fi") // toggle off
        assertNull(viewModel.uiState.value.selectedGenre)
        assertEquals(4, viewModel.uiState.value.books.size)
    }

    // ───────────────────────────────────────────────────────────────
    // Search
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `search by title filters correctly`() = runTest(testDispatcher) {
        viewModel.loadBooks("user1")
        advanceUntilIdle()
        viewModel.setSearchQuery("dune")
        val books = viewModel.uiState.value.books
        assertEquals(1, books.size)
        assertEquals("Dune", books.first().title)
    }

    @Test
    fun `search by author filters correctly`() = runTest(testDispatcher) {
        viewModel.loadBooks("user1")
        advanceUntilIdle()
        viewModel.setSearchQuery("asimov")
        val books = viewModel.uiState.value.books
        assertEquals(1, books.size)
        assertEquals("Foundation", books.first().title)
    }

    @Test
    fun `search by genre filters correctly`() = runTest(testDispatcher) {
        viewModel.loadBooks("user1")
        advanceUntilIdle()
        viewModel.setSearchQuery("sci-fi")
        val books = viewModel.uiState.value.books
        assertEquals(2, books.size)
    }

    @Test
    fun `blank search shows all books`() = runTest(testDispatcher) {
        viewModel.loadBooks("user1")
        advanceUntilIdle()
        viewModel.setSearchQuery("dune")
        viewModel.setSearchQuery("")
        assertEquals(4, viewModel.uiState.value.books.size)
    }

    // ───────────────────────────────────────────────────────────────
    // Combined filters
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `fiction + read filter combined`() = runTest(testDispatcher) {
        viewModel.loadBooks("user1")
        advanceUntilIdle()
        viewModel.setTypeFilter(TypeFilter.FICTION)
        viewModel.setReadFilter(ReadFilter.READ)
        val books = viewModel.uiState.value.books
        assertEquals(2, books.size) // Dune (read, fiction) and Gatsby (read, fiction)
        assertTrue(books.all { it.isFiction && it.isRead })
    }

    // ───────────────────────────────────────────────────────────────
    // Available genres
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `availableGenres excludes tier-1 labels`() = runTest(testDispatcher) {
        viewModel.loadBooks("user1")
        advanceUntilIdle()
        val genres = viewModel.uiState.value.availableGenres
        genres.forEach { genre ->
            assertFalse("Tier-1 label leaked: $genre", genre.lowercase() in Tier1Labels.labels)
        }
    }

    // ───────────────────────────────────────────────────────────────
    // Toggle read status / delete
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `toggleReadStatus calls repository`() = runTest(testDispatcher) {
        val book = testBooks[0]
        viewModel.toggleReadStatus(book)
        advanceUntilIdle()
        coVerify { repository.setReadStatus(book.id, book.userId, !book.isRead) }
    }

    @Test
    fun `deleteBook calls repository`() = runTest(testDispatcher) {
        val book = testBooks[0]
        viewModel.deleteBook(book)
        advanceUntilIdle()
        coVerify { repository.deleteBook(book) }
    }
}
