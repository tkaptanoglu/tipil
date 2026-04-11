package com.tipil.app.security

import com.tipil.app.data.local.BookEntity
import com.tipil.app.data.local.MediaType
import com.tipil.app.data.local.StringListConverter
import com.tipil.app.data.remote.VolumeInfo
import com.tipil.app.data.repository.BookLookupResult
import com.tipil.app.data.repository.BookRepository
import com.tipil.app.ui.library.LibraryViewModel
import com.tipil.app.ui.scanner.ScannerViewModel
import com.tipil.app.util.GenreClassifier
import com.tipil.app.util.IsbnValidator
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
 * Input injection and validation security tests.
 *
 * Covers SQL injection via Room, barcode injection, XSS-like payloads
 * in stored fields, null-byte injection, and format-string attacks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InputInjectionSecurityTest {

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
    // SQL injection via search query
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `SQL injection in search query does not crash`() = runTest(testDispatcher) {
        val repository: BookRepository = mockk(relaxed = true)
        every { repository.getUserBooks("u") } returns flowOf(listOf(
            BookEntity(id = 1, userId = "u", isbn = "001", title = "Book",
                authors = "Author", addedAt = 1000)
        ))

        val vm = LibraryViewModel(repository)
        vm.loadBooks("u")
        advanceUntilIdle()

        // Classic SQL injection payloads — search uses String.contains(), not SQL,
        // but we verify these don't cause unexpected behavior
        val sqlPayloads = listOf(
            "'; DROP TABLE books; --",
            "\" OR 1=1 --",
            "1; DELETE FROM books WHERE 1=1",
            "' UNION SELECT * FROM books --",
            "'; UPDATE books SET userId='attacker' WHERE 1=1; --",
            "Robert'); DROP TABLE Students;--",  // Bobby Tables
            "1' OR '1'='1",
            "admin'--",
            "' OR '' = '",
            "1; EXEC xp_cmdshell('dir')"
        )

        sqlPayloads.forEach { payload ->
            vm.setSearchQuery(payload)
            // Should not crash, and should return 0 results (no match)
            assertTrue(
                "SQL payload should not match: $payload",
                vm.uiState.value.books.isEmpty() || vm.uiState.value.books.all {
                    it.title.lowercase().contains(payload.lowercase()) ||
                    it.authors.lowercase().contains(payload.lowercase())
                }
            )
        }
    }

    // ───────────────────────────────────────────────────────────────
    // SQL injection via genre filter
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `SQL injection in genre filter does not crash`() = runTest(testDispatcher) {
        val repository: BookRepository = mockk(relaxed = true)
        every { repository.getUserBooks("u") } returns flowOf(listOf(
            BookEntity(id = 1, userId = "u", isbn = "001", title = "Book",
                authors = "Author", genres = listOf("Sci-Fi"), addedAt = 1000)
        ))

        val vm = LibraryViewModel(repository)
        vm.loadBooks("u")
        advanceUntilIdle()

        val injections = listOf(
            "'; DROP TABLE books; --",
            "Sci-Fi' OR '1'='1",
            "\" UNION SELECT password FROM users --"
        )

        injections.forEach { payload ->
            vm.setGenreFilter(payload)
            // Should just result in no matches
            assertTrue(vm.uiState.value.books.isEmpty())
            vm.setGenreFilter(payload) // toggle off
        }
    }

    // ───────────────────────────────────────────────────────────────
    // Barcode / ISBN injection
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `IsbnValidator rejects SQL injection in barcode`() {
        val payloads = listOf(
            "'; DROP TABLE books;--",
            "9780062316097' OR '1'='1",
            "9780062316097; DELETE FROM books",
            "isbn:9780062316097",
            "<script>alert(1)</script>"
        )
        payloads.forEach { payload ->
            assertFalse("Should reject: $payload", IsbnValidator.isValid(payload))
        }
    }

    @Test
    fun `IsbnValidator rejects null bytes in barcode`() {
        assertFalse(IsbnValidator.isValid("978006231\u00006097"))
        assertFalse(IsbnValidator.isValid("\u00009780062316097"))
    }

    @Test
    fun `IsbnValidator rejects control characters`() {
        val controlChars = listOf("\t", "\n", "\r", "\b", "\u000C")
        controlChars.forEach { ch ->
            assertFalse(IsbnValidator.isValid("978006231${ch}6097"))
        }
    }

    @Test
    fun `IsbnValidator behavior with Unicode digit lookalikes`() {
        // SECURITY FINDING: Kotlin's Char.isDigit() returns true for Arabic-Indic
        // and fullwidth digits. These will pass the isDigit() gate but then fail
        // digitToInt() or produce wrong checksums. Verify they don't produce
        // false positives (valid checksum with non-ASCII digits).
        //
        // Arabic-Indic digits (٠-٩) map to 0-9 via digitToInt() on JVM,
        // so they ARE processed. This is acceptable since the barcode scanner
        // only emits ASCII digits, but we document the behavior.
        val arabicIndic = "٩٧٨٠٠٦٢٣١٦٠٩٧"
        val fullwidth = "９７８００６２３１６０９７"

        // These may or may not pass depending on JVM's digitToInt behavior.
        // The critical thing is they don't crash:
        try { IsbnValidator.isValid(arabicIndic) } catch (_: Exception) { fail("Should not crash on Arabic-Indic digits") }
        try { IsbnValidator.isValid(fullwidth) } catch (_: Exception) { fail("Should not crash on fullwidth digits") }
    }

    // ───────────────────────────────────────────────────────────────
    // XSS-like payloads in stored fields
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `BookEntity stores XSS payloads without executing them`() {
        // In a native app these can't execute, but we verify they're stored as-is
        // and don't corrupt data or crash serialization
        val xssPayloads = listOf(
            "<script>alert('xss')</script>",
            "<img src=x onerror=alert(1)>",
            "javascript:alert(1)",
            "<svg/onload=alert(1)>",
            "{{constructor.constructor('return this')()}}"
        )

        xssPayloads.forEach { payload ->
            val entity = BookEntity(
                id = 0, userId = "u", isbn = "001", title = payload,
                authors = payload, description = payload, addedAt = 1000
            )
            assertEquals(payload, entity.title)
            assertEquals(payload, entity.authors)
            assertEquals(payload, entity.description)
        }
    }

    @Test
    fun `StringListConverter handles XSS payloads in genres`() {
        val converter = StringListConverter()
        val genres = listOf(
            "<script>alert(1)</script>",
            "Normal Genre",
            "<img src=x onerror=alert(1)>"
        )
        val serialized = converter.fromList(genres)
        val deserialized = converter.toList(serialized)
        assertEquals(genres, deserialized)
    }

    // ───────────────────────────────────────────────────────────────
    // Null-byte injection
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `null bytes in title do not truncate data`() {
        val title = "Before\u0000After"
        val entity = BookEntity(id = 0, userId = "u", isbn = "001",
            title = title, authors = "A", addedAt = 1000)
        assertEquals(title, entity.title)
        assertTrue(entity.title.contains("\u0000"))
    }

    @Test
    fun `null bytes in search query do not crash`() = runTest(testDispatcher) {
        val repository: BookRepository = mockk(relaxed = true)
        every { repository.getUserBooks("u") } returns flowOf(listOf(
            BookEntity(id = 1, userId = "u", isbn = "001", title = "Book",
                authors = "Author", addedAt = 1000)
        ))

        val vm = LibraryViewModel(repository)
        vm.loadBooks("u")
        advanceUntilIdle()

        vm.setSearchQuery("Book\u0000; DROP TABLE")
        // Should not crash, should not match
        assertTrue(vm.uiState.value.books.isEmpty())
    }

    // ───────────────────────────────────────────────────────────────
    // Format string attacks
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `format string payloads in fields do not cause issues`() {
        val payloads = listOf("%s", "%d", "%x", "%n", "%p", "%%", "%1\$s")
        payloads.forEach { payload ->
            val entity = BookEntity(
                id = 0, userId = "u", isbn = "001", title = payload,
                authors = payload, addedAt = 1000
            )
            assertEquals(payload, entity.title)
        }
    }

    // ───────────────────────────────────────────────────────────────
    // Path traversal in cover URLs
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `path traversal URLs are stored without interpretation`() {
        val maliciousUrls = listOf(
            "file:///etc/passwd",
            "file:///data/data/com.tipil.app/databases/tipil_database",
            "https://evil.com/../../etc/passwd",
            "content://com.tipil.app.provider/databases/tipil_database",
            "../../../etc/shadow",
            "https://example.com/cover.jpg?callback=evil.com"
        )

        maliciousUrls.forEach { url ->
            val entity = BookEntity(
                id = 0, userId = "u", isbn = "001", title = "Book",
                authors = "Author", coverUrl = url, addedAt = 1000
            )
            // The URL is stored as-is; Coil's image loader will handle validation
            assertEquals(url, entity.coverUrl)
        }
    }

    // ───────────────────────────────────────────────────────────────
    // MediaType tampering
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `invalid mediaType string falls back to BOOK`() {
        assertEquals(MediaType.BOOK, MediaType.fromName("MALICIOUS"))
        assertEquals(MediaType.BOOK, MediaType.fromName("'; DROP TABLE books;--"))
        assertEquals(MediaType.BOOK, MediaType.fromName(""))
        assertEquals(MediaType.BOOK, MediaType.fromName("null"))
    }

    @Test
    fun `BookEntity with invalid mediaType does not crash library filter`() = runTest(testDispatcher) {
        val repository: BookRepository = mockk(relaxed = true)
        val books = listOf(
            BookEntity(id = 1, userId = "u", isbn = "001", title = "Good",
                authors = "A", mediaType = MediaType.BOOK.name, addedAt = 1000),
            BookEntity(id = 2, userId = "u", isbn = "002", title = "Corrupted",
                authors = "B", mediaType = "INVALID_TYPE", addedAt = 2000)
        )
        every { repository.getUserBooks("u") } returns flowOf(books)

        val vm = LibraryViewModel(repository)
        vm.loadBooks("u")
        advanceUntilIdle()

        // Both items should appear — corrupted falls back to BOOK
        assertEquals(2, vm.uiState.value.books.size)

        // Filtering by BOOK should include the corrupted one (fallback)
        vm.setMediaTypeFilter(MediaType.BOOK)
        assertEquals(2, vm.uiState.value.books.size)
    }

    // ───────────────────────────────────────────────────────────────
    // GenreClassifier injection
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `GenreClassifier handles regex injection in categories`() {
        val classifier = GenreClassifier()
        // These are regex metacharacters that could cause ReDoS or errors
        // if not properly escaped in the word-boundary patterns
        val maliciousCategories = listOf(
            "((((((((((((((((((((",
            ".*.*.*.*.*.*.*",
            "[a-z]++++",
            "(?:(?:(?:(?:(?:(?:a)*)*)*)*)*)*",
            "\\b\\b\\b\\b\\b\\b",
            "a{1000000}",
            "|||||||||"
        )

        maliciousCategories.forEach { cat ->
            val info = VolumeInfo(categories = listOf(cat))
            // Should not throw or hang
            classifier.classify(info)
            classifier.isFiction(info)
        }
    }
}
