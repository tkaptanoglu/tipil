package com.tipil.app.security

import com.tipil.app.data.local.BookEntity
import com.tipil.app.data.repository.BookRepository
import com.tipil.app.ui.bookdetail.BookDetailViewModel
import com.tipil.app.ui.library.LibraryViewModel
import io.mockk.coEvery
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
 * Authentication boundary security tests.
 *
 * Verifies behavior when userId is empty, blank, forged, or
 * contains special characters — simulating session issues or
 * manipulated auth tokens.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthBoundarySecurityTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: BookRepository

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
    // Empty / blank userId
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `library with empty userId loads empty`() = runTest(testDispatcher) {
        every { repository.getUserBooks("") } returns flowOf(emptyList())

        val vm = LibraryViewModel(repository)
        vm.loadBooks("")
        advanceUntilIdle()

        assertEquals(0, vm.uiState.value.books.size)
    }

    @Test
    fun `book detail with empty userId returns null`() = runTest(testDispatcher) {
        coEvery { repository.getBookById(1L, "") } returns null

        val vm = BookDetailViewModel(repository)
        vm.loadBook(1L, "")
        advanceUntilIdle()

        assertNull(vm.uiState.value.book)
    }

    @Test
    fun `blank userId does not leak data from other users`() = runTest(testDispatcher) {
        // A book exists for a real user
        coEvery { repository.getBookById(1L, " ") } returns null

        val vm = BookDetailViewModel(repository)
        vm.loadBook(1L, " ")
        advanceUntilIdle()

        assertNull(vm.uiState.value.book)
    }

    // ───────────────────────────────────────────────────────────────
    // Forged / manipulated userId
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `SQL injection in userId does not return data`() = runTest(testDispatcher) {
        val maliciousIds = listOf(
            "' OR '1'='1",
            "' UNION SELECT * FROM books --",
            "admin'--",
            "'; DROP TABLE books; --",
            "\" OR 1=1 --"
        )

        maliciousIds.forEach { maliciousId ->
            every { repository.getUserBooks(maliciousId) } returns flowOf(emptyList())
            coEvery { repository.getBookById(any(), maliciousId) } returns null

            val vm = LibraryViewModel(repository)
            vm.loadBooks(maliciousId)
            advanceUntilIdle()
            assertEquals(0, vm.uiState.value.books.size)

            val detailVm = BookDetailViewModel(repository)
            detailVm.loadBook(1L, maliciousId)
            advanceUntilIdle()
            assertNull(detailVm.uiState.value.book)
        }
    }

    @Test
    fun `extremely long userId does not crash`() = runTest(testDispatcher) {
        val longId = "x".repeat(100_000)
        every { repository.getUserBooks(longId) } returns flowOf(emptyList())

        val vm = LibraryViewModel(repository)
        vm.loadBooks(longId)
        advanceUntilIdle()

        assertEquals(0, vm.uiState.value.books.size)
    }

    @Test
    fun `userId with null bytes does not crash`() = runTest(testDispatcher) {
        val nullId = "user\u0000id"
        every { repository.getUserBooks(nullId) } returns flowOf(emptyList())

        val vm = LibraryViewModel(repository)
        vm.loadBooks(nullId)
        advanceUntilIdle()

        assertEquals(0, vm.uiState.value.books.size)
    }

    @Test
    fun `userId with unicode characters works correctly`() = runTest(testDispatcher) {
        val unicodeId = "user_用户_пользователь"
        val books = listOf(
            BookEntity(id = 1, userId = unicodeId, isbn = "001",
                title = "Book", authors = "A", addedAt = 1000)
        )
        every { repository.getUserBooks(unicodeId) } returns flowOf(books)

        val vm = LibraryViewModel(repository)
        vm.loadBooks(unicodeId)
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.books.size)
        assertEquals(unicodeId, vm.uiState.value.books.first().userId)
    }

    // ───────────────────────────────────────────────────────────────
    // Cross-user data leakage through userId manipulation
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `wildcard userId does not match all users`() = runTest(testDispatcher) {
        // Room uses parameterized queries, so '%' won't act as SQL wildcard
        every { repository.getUserBooks("%") } returns flowOf(emptyList())

        val vm = LibraryViewModel(repository)
        vm.loadBooks("%")
        advanceUntilIdle()

        assertEquals(0, vm.uiState.value.books.size)
    }

    @Test
    fun `underscore userId does not act as SQL single-char wildcard`() = runTest(testDispatcher) {
        every { repository.getUserBooks("_") } returns flowOf(emptyList())

        val vm = LibraryViewModel(repository)
        vm.loadBooks("_")
        advanceUntilIdle()

        assertEquals(0, vm.uiState.value.books.size)
    }
}
