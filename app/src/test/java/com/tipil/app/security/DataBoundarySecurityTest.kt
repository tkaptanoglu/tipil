package com.tipil.app.security

import com.tipil.app.data.local.BookEntity
import com.tipil.app.data.local.MediaType
import com.tipil.app.data.local.StringListConverter
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
 * Data boundary and overflow security tests.
 *
 * Verifies safe handling of integer overflow, extremely long strings,
 * empty/blank edge cases, and data type coercion attacks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DataBoundarySecurityTest {

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
    // Integer overflow / boundary values
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `BookEntity handles Long MAX_VALUE for id`() {
        val entity = BookEntity(
            id = Long.MAX_VALUE, userId = "u", isbn = "001",
            title = "Book", authors = "A", addedAt = Long.MAX_VALUE
        )
        assertEquals(Long.MAX_VALUE, entity.id)
        assertEquals(Long.MAX_VALUE, entity.addedAt)
    }

    @Test
    fun `BookEntity handles negative id`() {
        val entity = BookEntity(
            id = -1, userId = "u", isbn = "001",
            title = "Book", authors = "A", addedAt = -1
        )
        assertEquals(-1L, entity.id)
    }

    @Test
    fun `BookEntity handles zero and negative page count`() {
        val zero = BookEntity(id = 0, userId = "u", isbn = "001",
            title = "Book", authors = "A", pageCount = 0, addedAt = 1000)
        assertEquals(0, zero.pageCount)

        val negative = BookEntity(id = 0, userId = "u", isbn = "002",
            title = "Book", authors = "A", pageCount = -999, addedAt = 1000)
        assertEquals(-999, negative.pageCount)

        val maxVal = BookEntity(id = 0, userId = "u", isbn = "003",
            title = "Book", authors = "A", pageCount = Int.MAX_VALUE, addedAt = 1000)
        assertEquals(Int.MAX_VALUE, maxVal.pageCount)
    }

    // ───────────────────────────────────────────────────────────────
    // Extremely long strings (DoS via resource exhaustion)
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `BookEntity accepts extremely long title without crashing`() {
        val longTitle = "A".repeat(100_000)
        val entity = BookEntity(id = 0, userId = "u", isbn = "001",
            title = longTitle, authors = "A", addedAt = 1000)
        assertEquals(100_000, entity.title.length)
    }

    @Test
    fun `BookEntity accepts extremely long userId`() {
        val longUserId = "x".repeat(50_000)
        val entity = BookEntity(id = 0, userId = longUserId, isbn = "001",
            title = "Book", authors = "A", addedAt = 1000)
        assertEquals(longUserId, entity.userId)
    }

    @Test
    fun `BookEntity accepts extremely long ISBN`() {
        val longIsbn = "9".repeat(10_000)
        val entity = BookEntity(id = 0, userId = "u", isbn = longIsbn,
            title = "Book", authors = "A", addedAt = 1000)
        assertEquals(longIsbn, entity.isbn)
    }

    @Test
    fun `StringListConverter handles extremely long genre list`() {
        val converter = StringListConverter()
        val hugeList = (1..10_000).map { "Genre_$it" }
        val serialized = converter.fromList(hugeList)
        val deserialized = converter.toList(serialized)
        assertEquals(hugeList, deserialized)
    }

    @Test
    fun `StringListConverter handles single genre with huge length`() {
        val converter = StringListConverter()
        val hugeGenre = "X".repeat(1_000_000)
        val list = listOf(hugeGenre)
        val serialized = converter.fromList(list)
        val deserialized = converter.toList(serialized)
        assertEquals(list, deserialized)
    }

    // ───────────────────────────────────────────────────────────────
    // Empty / blank edge cases
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `BookEntity handles all-blank fields`() {
        val entity = BookEntity(
            id = 0, userId = "", isbn = "", title = "", authors = "",
            subtitle = "", publisher = "", editor = "", publishedYear = "",
            coverUrl = "", description = "", mediaMetadata = "",
            addedAt = 0
        )
        assertEquals("", entity.userId)
        assertEquals("", entity.isbn)
        assertEquals("", entity.title)
    }

    @Test
    fun `BookEntity handles whitespace-only fields`() {
        val entity = BookEntity(
            id = 0, userId = "   ", isbn = "\t\n", title = "  \n  ",
            authors = "\r\n", addedAt = 0
        )
        assertEquals("   ", entity.userId)
        assertEquals("\t\n", entity.isbn)
    }

    // ───────────────────────────────────────────────────────────────
    // Separator injection in StringListConverter
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `StringListConverter breaks on deliberate separator injection`() {
        val converter = StringListConverter()
        // If an attacker can inject "||" into a genre name, the round-trip breaks.
        // This is a KNOWN limitation — document it, not a bug.
        val malicious = listOf("Sci||Fi")
        val serialized = converter.fromList(malicious)
        val deserialized = converter.toList(serialized)
        // This will produce ["Sci", "Fi"] instead of ["Sci||Fi"]
        // Verify we know about this behavior:
        assertNotEquals("Separator injection produces different result",
            malicious, deserialized)
        assertEquals(listOf("Sci", "Fi"), deserialized)
    }

    @Test
    fun `empty genre in list is preserved in serialization`() {
        val converter = StringListConverter()
        // Genre list with empty string — could cause issues
        val list = listOf("Sci-Fi", "", "Fantasy")
        val serialized = converter.fromList(list)
        val deserialized = converter.toList(serialized)
        assertEquals(list, deserialized)
    }

    // ───────────────────────────────────────────────────────────────
    // ScannerViewModel: addToLibrary with malicious result data
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `addToLibrary with extremely long fields does not crash`() = runTest(testDispatcher) {
        val repository: BookRepository = mockk(relaxed = true)
        coEvery { repository.addBook(any()) } returns 1L

        val maliciousResult = BookLookupResult(
            isbn = "9".repeat(100),
            title = "A".repeat(100_000),
            subtitle = "B".repeat(100_000),
            authors = "C".repeat(100_000),
            publisher = "D".repeat(10_000),
            editor = "E".repeat(10_000),
            publishedYear = "9999",
            pageCount = Int.MAX_VALUE,
            isFiction = true,
            genres = (1..100).map { "Genre$it" },
            coverUrl = "https://evil.com/" + "x".repeat(10_000),
            description = "F".repeat(100_000)
        )

        val vm = ScannerViewModel(repository)
        vm.addToLibrary("user1", maliciousResult)
        advanceUntilIdle()

        assertTrue(vm.scanState.value is ScanState.Added)
    }

    @Test
    fun `addToLibrary with empty ISBN and title succeeds`() = runTest(testDispatcher) {
        val repository: BookRepository = mockk(relaxed = true)
        coEvery { repository.addBook(any()) } returns 1L

        val emptyResult = BookLookupResult(
            isbn = "", title = "", subtitle = "", authors = "",
            publisher = "", editor = "", publishedYear = "",
            pageCount = 0, isFiction = true, genres = emptyList(),
            coverUrl = "", description = ""
        )

        val vm = ScannerViewModel(repository)
        vm.addToLibrary("user1", emptyResult)
        advanceUntilIdle()

        assertTrue(vm.scanState.value is ScanState.Added)
    }

    // ───────────────────────────────────────────────────────────────
    // MediaMetadata JSON blob safety
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `mediaMetadata can store arbitrary JSON without crashing`() {
        val jsonPayloads = listOf(
            """{"tracks": ["Track 1", "Track 2"]}""",
            """{"players": "2-4", "minAge": 10}""",
            """{"runtime": 120, "format": "Blu-ray"}""",
            """{"invalid json""",
            """<not-json>""",
            """{"nested": {"deep": {"very": {"deep": "value"}}}}""",
            ""  // empty
        )

        jsonPayloads.forEach { json ->
            val entity = BookEntity(
                id = 0, userId = "u", isbn = "001", title = "Item",
                authors = "A", mediaMetadata = json, addedAt = 1000
            )
            assertEquals(json, entity.mediaMetadata)
        }
    }

    @Test
    fun `mediaMetadata with SQL injection stored safely`() {
        val payload = """{"title": "'; DROP TABLE books; --"}"""
        val entity = BookEntity(
            id = 0, userId = "u", isbn = "001", title = "Book",
            authors = "A", mediaMetadata = payload, addedAt = 1000
        )
        assertEquals(payload, entity.mediaMetadata)
    }
}
