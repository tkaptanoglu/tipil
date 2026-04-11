package com.tipil.app.security

import com.tipil.app.data.local.BookEntity
import com.tipil.app.data.local.MediaType
import com.tipil.app.data.repository.BookRepository
import com.tipil.app.ui.bookdetail.BookDetailViewModel
import com.tipil.app.ui.library.LibraryViewModel
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
 * IDOR (Insecure Direct Object Reference) security tests.
 *
 * These verify that every data access path enforces userId ownership,
 * preventing user A from reading, modifying, or deleting user B's data.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IdorSecurityTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: BookRepository

    private val victimBook = BookEntity(
        id = 42, userId = "victim_user", isbn = "9780000000001", title = "Victim's Book",
        authors = "Author", isFiction = true, genres = listOf("Fiction"),
        isRead = false, addedAt = 1000
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
    // BookDetailViewModel: read access
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `attacker cannot load another user's book by ID`() = runTest(testDispatcher) {
        // Repository enforces userId — returns null for wrong user
        coEvery { repository.getBookById(42L, "attacker") } returns null

        val vm = BookDetailViewModel(repository)
        vm.loadBook(42L, "attacker")
        advanceUntilIdle()

        assertNull("Attacker should not see victim's book", vm.uiState.value.book)
    }

    @Test
    fun `legitimate owner can load their own book`() = runTest(testDispatcher) {
        coEvery { repository.getBookById(42L, "victim_user") } returns victimBook

        val vm = BookDetailViewModel(repository)
        vm.loadBook(42L, "victim_user")
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.book)
        assertEquals("Victim's Book", vm.uiState.value.book!!.title)
    }

    // ───────────────────────────────────────────────────────────────
    // BookDetailViewModel: write access (toggle read, delete)
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `toggleReadStatus passes correct userId to repository`() = runTest(testDispatcher) {
        coEvery { repository.getBookById(42L, "victim_user") } returns victimBook
        val vm = BookDetailViewModel(repository)
        vm.loadBook(42L, "victim_user")
        advanceUntilIdle()

        vm.toggleReadStatus()
        advanceUntilIdle()

        // Verify the userId passed to setReadStatus matches the book's owner
        coVerify { repository.setReadStatus(42L, "victim_user", true) }
        // Verify it was NOT called with any other userId
        coVerify(exactly = 0) { repository.setReadStatus(any(), neq("victim_user"), any()) }
    }

    @Test
    fun `toggleReadStatus without loading book is no-op`() = runTest(testDispatcher) {
        // Attacker creates VM and tries to toggle without loading
        val vm = BookDetailViewModel(repository)
        vm.toggleReadStatus()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.setReadStatus(any(), any(), any()) }
    }

    @Test
    fun `deleteBook only deletes the loaded book entity`() = runTest(testDispatcher) {
        coEvery { repository.getBookById(42L, "victim_user") } returns victimBook
        val vm = BookDetailViewModel(repository)
        vm.loadBook(42L, "victim_user")
        advanceUntilIdle()

        vm.deleteBook()
        advanceUntilIdle()

        // The exact entity passed to delete should have the victim's userId
        coVerify {
            repository.deleteBook(match { it.userId == "victim_user" && it.id == 42L })
        }
    }

    @Test
    fun `deleteBook without loading is no-op`() = runTest(testDispatcher) {
        val vm = BookDetailViewModel(repository)
        vm.deleteBook()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.deleteBook(any()) }
    }

    // ───────────────────────────────────────────────────────────────
    // LibraryViewModel: data isolation
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `library only shows books for the authenticated user`() = runTest(testDispatcher) {
        val user1Books = listOf(
            BookEntity(id = 1, userId = "user1", isbn = "001", title = "User1 Book",
                authors = "A", addedAt = 1000)
        )
        every { repository.getUserBooks("user1") } returns flowOf(user1Books)

        val vm = LibraryViewModel(repository)
        vm.loadBooks("user1")
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.books.size)
        assertTrue(vm.uiState.value.books.all { it.userId == "user1" })
    }

    @Test
    fun `toggleReadStatus in library uses the book's own userId`() = runTest(testDispatcher) {
        val book = BookEntity(id = 1, userId = "user1", isbn = "001", title = "Book",
            authors = "A", isRead = false, addedAt = 1000)
        every { repository.getUserBooks("user1") } returns flowOf(listOf(book))

        val vm = LibraryViewModel(repository)
        vm.loadBooks("user1")
        advanceUntilIdle()

        vm.toggleReadStatus(book)
        advanceUntilIdle()

        coVerify { repository.setReadStatus(1L, "user1", true) }
    }

    // ───────────────────────────────────────────────────────────────
    // ScannerViewModel: userId scoping
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `scanner checks library membership for the correct user`() = runTest(testDispatcher) {
        coEvery { repository.isBookInLibrary("user1", "9780062316097") } returns false
        coEvery { repository.lookupBookByIsbn("9780062316097") } returns mockk(relaxed = true)

        val vm = ScannerViewModel(repository)
        vm.onBarcodeDetected("9780062316097", "user1")
        advanceUntilIdle()

        // Verify library check used the correct userId
        coVerify { repository.isBookInLibrary("user1", "9780062316097") }
        coVerify(exactly = 0) { repository.isBookInLibrary(neq("user1"), any()) }
    }

    @Test
    fun `addToLibrary stamps the book with the correct userId`() = runTest(testDispatcher) {
        val result = com.tipil.app.data.repository.BookLookupResult(
            isbn = "001", title = "Test", subtitle = "", authors = "A",
            publisher = "", editor = "", publishedYear = "", pageCount = 0,
            isFiction = true, genres = emptyList(), coverUrl = "", description = ""
        )
        coEvery { repository.addBook(any()) } returns 1L

        val vm = ScannerViewModel(repository)
        vm.addToLibrary("user1", result)
        advanceUntilIdle()

        coVerify {
            repository.addBook(match { it.userId == "user1" })
        }
    }

    @Test
    fun `addToLibrary never stamps a different userId`() = runTest(testDispatcher) {
        val result = com.tipil.app.data.repository.BookLookupResult(
            isbn = "001", title = "Test", subtitle = "", authors = "A",
            publisher = "", editor = "", publishedYear = "", pageCount = 0,
            isFiction = true, genres = emptyList(), coverUrl = "", description = ""
        )
        coEvery { repository.addBook(any()) } returns 1L

        val vm = ScannerViewModel(repository)
        vm.addToLibrary("user1", result)
        advanceUntilIdle()

        coVerify(exactly = 0) {
            repository.addBook(match { it.userId != "user1" })
        }
    }
}
