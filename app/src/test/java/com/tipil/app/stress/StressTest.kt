package com.tipil.app.stress

import com.tipil.app.data.local.BookEntity
import com.tipil.app.data.local.StringListConverter
import com.tipil.app.data.remote.VolumeInfo
import com.tipil.app.data.repository.BookRepository
import com.tipil.app.ui.library.LibraryViewModel
import com.tipil.app.ui.library.ReadFilter
import com.tipil.app.ui.library.SortOrder
import com.tipil.app.ui.library.TypeFilter
import com.tipil.app.util.GenreClassifier
import com.tipil.app.util.IsbnValidator
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
 * Stress tests: large data volumes, rapid operations, boundary conditions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StressTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ───────────────────────────────────────────────────────────────
    // Large library stress
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `library with 10000 books loads and filters correctly`() = runTest(testDispatcher) {
        val largeBookList = (1..10_000).map { i ->
            BookEntity(
                id = i.toLong(),
                userId = "user1",
                isbn = "978%010d".format(i),
                title = "Book $i",
                authors = "Author ${i % 500}",
                isFiction = i % 2 == 0,
                genres = listOf(if (i % 3 == 0) "Sci-Fi" else "History"),
                isRead = i % 4 == 0,
                addedAt = i.toLong()
            )
        }
        val repository: BookRepository = mockk(relaxed = true)
        every { repository.getUserBooks("user1") } returns flowOf(largeBookList)

        val vm = LibraryViewModel(repository)
        vm.loadBooks("user1")
        advanceUntilIdle()

        assertEquals(10_000, vm.uiState.value.bookCount)
        assertEquals(10_000, vm.uiState.value.books.size)

        // Filter fiction
        vm.setTypeFilter(TypeFilter.FICTION)
        assertEquals(5_000, vm.uiState.value.books.size)

        // Filter read
        vm.setReadFilter(ReadFilter.READ)
        assertEquals(2_500, vm.uiState.value.books.size)

        // Sort by date
        vm.setSortOrder(SortOrder.DATE_ADDED_NEWEST)
        assertTrue(vm.uiState.value.books.first().addedAt > vm.uiState.value.books.last().addedAt)
    }

    // ───────────────────────────────────────────────────────────────
    // Rapid filter toggling
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `rapid filter toggling does not crash`() = runTest(testDispatcher) {
        val books = (1..100).map { i ->
            BookEntity(
                id = i.toLong(), userId = "u", isbn = "$i", title = "Book $i",
                authors = "Author $i", isFiction = i % 2 == 0,
                genres = listOf("Genre${i % 5}"), addedAt = i.toLong()
            )
        }
        val repository: BookRepository = mockk(relaxed = true)
        every { repository.getUserBooks("u") } returns flowOf(books)

        val vm = LibraryViewModel(repository)
        vm.loadBooks("u")
        advanceUntilIdle()

        // Rapidly toggle filters 1000 times
        repeat(1_000) { i ->
            when (i % 6) {
                0 -> vm.setTypeFilter(TypeFilter.FICTION)
                1 -> vm.setTypeFilter(TypeFilter.NON_FICTION)
                2 -> vm.setTypeFilter(TypeFilter.ALL)
                3 -> vm.setReadFilter(ReadFilter.READ)
                4 -> vm.setReadFilter(ReadFilter.UNREAD)
                5 -> vm.setReadFilter(ReadFilter.ALL)
            }
        }

        // Should settle without crash
        val state = vm.uiState.value
        assertNotNull(state.books)
    }

    // ───────────────────────────────────────────────────────────────
    // Genre classifier throughput
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `GenreClassifier handles 10000 classifications without error`() {
        val classifier = GenreClassifier()
        val categories = listOf(
            "Fiction", "Science Fiction", "Mystery", "Thriller", "Romance",
            "Biography", "History", "Science", "Computers", "Philosophy",
            "Psychology", "Horror", "Fantasy", "Art", "Music"
        )
        val random = java.util.Random(42)

        repeat(10_000) {
            val numCats = random.nextInt(3) + 1
            val cats = (1..numCats).map { categories[random.nextInt(categories.size)] }
            val info = VolumeInfo(categories = cats)
            classifier.classify(info)
            classifier.isFiction(info)
        }
    }

    // ───────────────────────────────────────────────────────────────
    // ISBN validator throughput
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `IsbnValidator handles 100000 validations`() {
        val random = java.util.Random(42)
        repeat(100_000) {
            val len = if (random.nextBoolean()) 13 else 10
            val isbn = (1..len).map { random.nextInt(10) }.joinToString("")
            IsbnValidator.isValid(isbn) // should not throw
        }
    }

    // ───────────────────────────────────────────────────────────────
    // StringListConverter with large lists
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `StringListConverter handles 1000-element list`() {
        val converter = StringListConverter()
        val list = (1..1_000).map { "Genre_$it" }
        val serialized = converter.fromList(list)
        val deserialized = converter.toList(serialized)
        assertEquals(list, deserialized)
    }

    // ───────────────────────────────────────────────────────────────
    // Search with long query
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `search with very long query string does not crash`() = runTest(testDispatcher) {
        val books = listOf(
            BookEntity(id = 1, userId = "u", isbn = "001", title = "Dune",
                authors = "Frank Herbert", addedAt = 1000)
        )
        val repository: BookRepository = mockk(relaxed = true)
        every { repository.getUserBooks("u") } returns flowOf(books)

        val vm = LibraryViewModel(repository)
        vm.loadBooks("u")
        advanceUntilIdle()

        val longQuery = "a".repeat(10_000)
        vm.setSearchQuery(longQuery)
        assertEquals(0, vm.uiState.value.books.size) // no match, but no crash
    }
}
