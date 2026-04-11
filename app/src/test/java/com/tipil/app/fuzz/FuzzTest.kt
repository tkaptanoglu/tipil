package com.tipil.app.fuzz

import com.tipil.app.data.local.BookEntity
import com.tipil.app.data.local.StringListConverter
import com.tipil.app.data.remote.VolumeInfo
import com.tipil.app.data.repository.BookRepository
import com.tipil.app.ui.library.LibraryViewModel
import com.tipil.app.util.GenreClassifier
import com.tipil.app.util.IsbnValidator
import com.tipil.app.util.Tier1Labels
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
 * Fuzz tests: random, malformed, and adversarial inputs to verify robustness.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FuzzTest {

    private val testDispatcher = StandardTestDispatcher()
    private val random = java.util.Random(12345)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun randomString(maxLen: Int = 100): String {
        val len = random.nextInt(maxLen + 1)
        return (1..len).map { (32 + random.nextInt(95)).toChar() }.joinToString("")
    }

    private fun randomUnicode(maxLen: Int = 50): String {
        val len = random.nextInt(maxLen + 1)
        return (1..len).map { Char(random.nextInt(0xFFFF)) }.joinToString("")
    }

    // ───────────────────────────────────────────────────────────────
    // ISBN validator fuzz
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `IsbnValidator never crashes on random ASCII input`() {
        repeat(5_000) {
            val input = randomString(30)
            IsbnValidator.isValid(input)
        }
    }

    @Test
    fun `IsbnValidator never crashes on unicode input`() {
        repeat(5_000) {
            val input = randomUnicode(30)
            IsbnValidator.isValid(input)
        }
    }

    @Test
    fun `IsbnValidator never crashes on empty and null-like inputs`() {
        val edgeCases = listOf("", " ", "\t", "\n", "\u0000", "null", "undefined", "NaN")
        edgeCases.forEach { IsbnValidator.isValid(it) }
    }

    @Test
    fun `IsbnValidator rejects strings with special characters`() {
        val inputs = listOf(
            "978-0-06-231609-7",   // dashes
            "978 006 2316097",     // spaces
            "978.006.2316097",     // dots
            "ISBN9780062316097",   // prefix
            "9780062316097\n",     // trailing newline
            "97800623160\u0000",   // null byte
        )
        inputs.forEach { input ->
            assertFalse("Should reject: '$input'", IsbnValidator.isValid(input))
        }
    }

    // ───────────────────────────────────────────────────────────────
    // GenreClassifier fuzz
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `GenreClassifier never crashes on random VolumeInfo`() {
        val classifier = GenreClassifier()
        repeat(2_000) {
            val info = VolumeInfo(
                categories = if (random.nextBoolean()) (1..random.nextInt(5)).map { randomString(50) } else null,
                mainCategory = if (random.nextBoolean()) randomString(50) else null,
                description = if (random.nextBoolean()) randomString(500) else null
            )
            classifier.classify(info)
            classifier.isFiction(info)
        }
    }

    @Test
    fun `GenreClassifier never returns tier-1 labels on random input`() {
        val classifier = GenreClassifier()
        repeat(2_000) {
            val info = VolumeInfo(
                categories = (1..random.nextInt(5) + 1).map { randomString(50) },
                description = randomString(200)
            )
            val genres = classifier.classify(info)
            genres.forEach { genre ->
                assertFalse(
                    "Tier-1 label '$genre' leaked",
                    genre.lowercase() in Tier1Labels.labels
                )
            }
        }
    }

    @Test
    fun `GenreClassifier handles unicode categories`() {
        val classifier = GenreClassifier()
        repeat(1_000) {
            val info = VolumeInfo(
                categories = listOf(randomUnicode(30)),
                description = randomUnicode(100)
            )
            // Should not throw
            classifier.classify(info)
            classifier.isFiction(info)
        }
    }

    @Test
    fun `GenreClassifier returns at most 5 genres`() {
        val classifier = GenreClassifier()
        repeat(1_000) {
            val info = VolumeInfo(
                categories = (1..20).map { randomString(30) },
                description = randomString(500)
            )
            val genres = classifier.classify(info)
            assertTrue("Got ${genres.size} genres", genres.size <= 5)
        }
    }

    // ───────────────────────────────────────────────────────────────
    // StringListConverter fuzz
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `StringListConverter never crashes on random strings`() {
        val converter = StringListConverter()
        repeat(2_000) {
            val input = randomString(200)
            converter.toList(input)
        }
    }

    @Test
    fun `StringListConverter round-trips when values contain no double-pipe`() {
        val converter = StringListConverter()
        val safeChars = "abcdefghijklmnopqrstuvwxyz0123456789 -&/'"
        repeat(1_000) {
            val list = (1..random.nextInt(10)).map {
                val len = random.nextInt(30) + 1
                (1..len).map { safeChars[random.nextInt(safeChars.length)] }.joinToString("")
            }
            val serialized = converter.fromList(list)
            val deserialized = converter.toList(serialized)
            assertEquals(list, deserialized)
        }
    }

    // ───────────────────────────────────────────────────────────────
    // Library search fuzz
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `library search never crashes on random queries`() = runTest(testDispatcher) {
        val books = listOf(
            BookEntity(id = 1, userId = "u", isbn = "001", title = "Dune",
                authors = "Frank Herbert", genres = listOf("Sci-Fi"), addedAt = 1000)
        )
        val repository: BookRepository = mockk(relaxed = true)
        every { repository.getUserBooks("u") } returns flowOf(books)

        val vm = LibraryViewModel(repository)
        vm.loadBooks("u")
        advanceUntilIdle()

        repeat(500) {
            vm.setSearchQuery(randomString(100))
        }
        // Should not throw
    }

    @Test
    fun `library search with regex-like characters does not crash`() = runTest(testDispatcher) {
        val books = listOf(
            BookEntity(id = 1, userId = "u", isbn = "001", title = "Dune",
                authors = "Frank Herbert", addedAt = 1000)
        )
        val repository: BookRepository = mockk(relaxed = true)
        every { repository.getUserBooks("u") } returns flowOf(books)

        val vm = LibraryViewModel(repository)
        vm.loadBooks("u")
        advanceUntilIdle()

        val regexStrings = listOf(
            "[a-z]+", "(.*)", "\\d+", "^start$", "a{1,3}",
            "foo|bar", "(?:test)", "a*b+c?", ".+?", "\\bword\\b"
        )
        regexStrings.forEach { vm.setSearchQuery(it) }
        // Library search uses String.contains(), not regex, so this should be fine
    }

    // ───────────────────────────────────────────────────────────────
    // BookEntity with adversarial data
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `BookEntity handles extreme field values`() {
        // Should not throw at construction time
        val entity = BookEntity(
            id = Long.MAX_VALUE,
            userId = "a".repeat(10_000),
            isbn = "9".repeat(13),
            title = randomUnicode(5_000),
            subtitle = randomUnicode(5_000),
            authors = randomUnicode(5_000),
            publisher = randomUnicode(1_000),
            editor = randomUnicode(1_000),
            publishedYear = "99999",
            pageCount = Int.MAX_VALUE,
            isFiction = true,
            genres = (1..100).map { randomUnicode(50) },
            coverUrl = "https://example.com/" + "a".repeat(5_000),
            description = randomUnicode(10_000),
            isRead = true,
            addedAt = Long.MAX_VALUE
        )
        assertNotNull(entity)
        assertEquals(Long.MAX_VALUE, entity.id)
    }
}
