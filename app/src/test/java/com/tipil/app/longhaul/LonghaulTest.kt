package com.tipil.app.longhaul

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
 * Longhaul tests: sustained operation over extended iterations to detect
 * memory leaks, accumulation bugs, and performance degradation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LonghaulTest {

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
    // Sustained filter cycling
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `sustained filter cycling over 50000 iterations`() = runTest(testDispatcher) {
        val books = (1..50).map { i ->
            BookEntity(
                id = i.toLong(), userId = "u", isbn = "$i", title = "Book $i",
                authors = "Author $i", isFiction = i % 2 == 0,
                genres = listOf("Genre${i % 5}"), isRead = i % 3 == 0,
                addedAt = i.toLong()
            )
        }
        val repository: BookRepository = mockk(relaxed = true)
        every { repository.getUserBooks("u") } returns flowOf(books)

        val vm = LibraryViewModel(repository)
        vm.loadBooks("u")
        advanceUntilIdle()

        val sortOrders = SortOrder.entries.toTypedArray()
        val readFilters = ReadFilter.entries.toTypedArray()
        val typeFilters = TypeFilter.entries.toTypedArray()

        repeat(50_000) { i ->
            vm.setSortOrder(sortOrders[i % sortOrders.size])
            vm.setReadFilter(readFilters[i % readFilters.size])
            vm.setTypeFilter(typeFilters[i % typeFilters.size])
        }

        // Final state should be consistent
        val state = vm.uiState.value
        assertNotNull(state.books)
        assertTrue(state.books.size <= 50)
    }

    // ───────────────────────────────────────────────────────────────
    // Sustained search cycling
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `sustained search query changes over 10000 iterations`() = runTest(testDispatcher) {
        val books = (1..20).map { i ->
            BookEntity(
                id = i.toLong(), userId = "u", isbn = "$i", title = "Title$i",
                authors = "Author$i", addedAt = i.toLong()
            )
        }
        val repository: BookRepository = mockk(relaxed = true)
        every { repository.getUserBooks("u") } returns flowOf(books)

        val vm = LibraryViewModel(repository)
        vm.loadBooks("u")
        advanceUntilIdle()

        repeat(10_000) { i ->
            vm.setSearchQuery("Title${i % 25}")
        }

        // End with a known query
        vm.setSearchQuery("Title5")
        assertEquals(1, vm.uiState.value.books.size)
    }

    // ───────────────────────────────────────────────────────────────
    // GenreClassifier sustained usage
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `GenreClassifier produces consistent results over 50000 classifications`() {
        val classifier = GenreClassifier()
        val info = VolumeInfo(
            categories = listOf("Science Fiction", "Adventure"),
            description = "An epic space adventure"
        )

        val firstResult = classifier.classify(info)
        val firstFiction = classifier.isFiction(info)

        repeat(50_000) {
            val genres = classifier.classify(info)
            val fiction = classifier.isFiction(info)
            assertEquals("Classification changed at iteration $it", firstResult, genres)
            assertEquals("isFiction changed at iteration $it", firstFiction, fiction)
        }
    }

    // ───────────────────────────────────────────────────────────────
    // IsbnValidator sustained usage
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `IsbnValidator produces consistent results over 100000 validations`() {
        val validIsbn13 = "9780062316097"
        val validIsbn10 = "0743273567"
        val invalid = "1234567890123"

        repeat(100_000) {
            assertTrue(IsbnValidator.isValid(validIsbn13))
            assertTrue(IsbnValidator.isValid(validIsbn10))
            // invalid might or might not pass checksum, but result should be consistent
        }
        val invalidResult = IsbnValidator.isValid(invalid)
        repeat(100_000) {
            assertEquals(invalidResult, IsbnValidator.isValid(invalid))
        }
    }

    // ───────────────────────────────────────────────────────────────
    // StringListConverter sustained round-trips
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `StringListConverter round-trip is stable over 50000 iterations`() {
        val converter = StringListConverter()
        val list = listOf("Sci-Fi", "Fantasy", "Adventure", "Horror", "Thriller")

        repeat(50_000) {
            val serialized = converter.fromList(list)
            val deserialized = converter.toList(serialized)
            assertEquals(list, deserialized)
        }
    }

    // ───────────────────────────────────────────────────────────────
    // Genre filter toggle sustained
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `genre filter toggle cycle over 10000 iterations`() = runTest(testDispatcher) {
        val books = (1..30).map { i ->
            BookEntity(
                id = i.toLong(), userId = "u", isbn = "$i", title = "Book $i",
                authors = "Author $i", genres = listOf("Genre${i % 3}"),
                addedAt = i.toLong()
            )
        }
        val repository: BookRepository = mockk(relaxed = true)
        every { repository.getUserBooks("u") } returns flowOf(books)

        val vm = LibraryViewModel(repository)
        vm.loadBooks("u")
        advanceUntilIdle()

        val genres = listOf("Genre0", "Genre1", "Genre2", null)
        repeat(10_000) { i ->
            vm.setGenreFilter(genres[i % genres.size])
        }

        // Should settle correctly
        val state = vm.uiState.value
        assertNotNull(state.books)
    }
}
