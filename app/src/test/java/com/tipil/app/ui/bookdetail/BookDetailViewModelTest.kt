package com.tipil.app.ui.bookdetail

import com.tipil.app.data.local.BookEntity
import com.tipil.app.data.repository.BookRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class BookDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: BookRepository
    private lateinit var viewModel: BookDetailViewModel

    private val testBook = BookEntity(
        id = 1, userId = "user1", isbn = "9780062316097", title = "Sapiens",
        authors = "Yuval Noah Harari", isFiction = false, genres = listOf("History"),
        isRead = false, addedAt = 1000
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        viewModel = BookDetailViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is loading with null book`() {
        val state = viewModel.uiState.value
        assertTrue(state.isLoading)
        assertNull(state.book)
    }

    @Test
    fun `loadBook populates book`() = runTest(testDispatcher) {
        coEvery { repository.getBookById(1L, "user1") } returns testBook

        viewModel.loadBook(1L, "user1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.book)
        assertEquals("Sapiens", state.book!!.title)
    }

    @Test
    fun `loadBook with invalid id yields null book`() = runTest(testDispatcher) {
        coEvery { repository.getBookById(999L, "user1") } returns null

        viewModel.loadBook(999L, "user1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.book)
    }

    @Test
    fun `loadBook enforces userId - different user gets null`() = runTest(testDispatcher) {
        coEvery { repository.getBookById(1L, "user2") } returns null

        viewModel.loadBook(1L, "user2")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.book)
    }

    @Test
    fun `toggleReadStatus flips read status`() = runTest(testDispatcher) {
        coEvery { repository.getBookById(1L, "user1") } returns testBook
        viewModel.loadBook(1L, "user1")
        advanceUntilIdle()

        viewModel.toggleReadStatus()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.book!!.isRead)
        coVerify { repository.setReadStatus(1L, "user1", true) }
    }

    @Test
    fun `toggleReadStatus with no book loaded is no-op`() = runTest(testDispatcher) {
        viewModel.toggleReadStatus()
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.setReadStatus(any(), any(), any()) }
    }

    @Test
    fun `deleteBook sets isDeleted flag`() = runTest(testDispatcher) {
        coEvery { repository.getBookById(1L, "user1") } returns testBook
        viewModel.loadBook(1L, "user1")
        advanceUntilIdle()

        viewModel.deleteBook()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isDeleted)
        coVerify { repository.deleteBook(testBook) }
    }

    @Test
    fun `deleteBook with no book loaded is no-op`() = runTest(testDispatcher) {
        viewModel.deleteBook()
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.deleteBook(any()) }
        assertFalse(viewModel.uiState.value.isDeleted)
    }
}
