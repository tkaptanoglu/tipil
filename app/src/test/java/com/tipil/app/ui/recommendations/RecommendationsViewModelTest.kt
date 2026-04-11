package com.tipil.app.ui.recommendations

import com.tipil.app.data.local.BookEntity
import com.tipil.app.data.repository.BookRecommendation
import com.tipil.app.data.repository.BookRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class RecommendationsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: BookRepository
    private lateinit var viewModel: RecommendationsViewModel

    private val userBooks = listOf(
        BookEntity(
            id = 1, userId = "user1", isbn = "001", title = "Dune",
            authors = "Frank Herbert", isFiction = true, genres = listOf("Sci-Fi"),
            addedAt = 1000
        ),
        BookEntity(
            id = 2, userId = "user1", isbn = "002", title = "Sapiens",
            authors = "Yuval Noah Harari", isFiction = false, genres = listOf("History"),
            addedAt = 2000
        )
    )

    private val recommendations = listOf(
        BookRecommendation(
            title = "Foundation", authors = "Isaac Asimov",
            coverUrl = "", description = "Sci-fi epic", reason = "Based on Sci-Fi",
            isbn = "003", isFiction = true
        ),
        BookRecommendation(
            title = "Guns, Germs, and Steel", authors = "Jared Diamond",
            coverUrl = "", description = "History of civilization", reason = "Based on History",
            isbn = "004", isFiction = false
        ),
        BookRecommendation(
            title = "Neuromancer", authors = "William Gibson",
            coverUrl = "", description = "Cyberpunk fiction", reason = "Based on Sci-Fi",
            isbn = "005", isFiction = true
        )
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        every { repository.getUserBooks("user1") } returns flowOf(userBooks)
        coEvery { repository.getRecommendations("user1") } returns recommendations
        viewModel = RecommendationsViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is not loading with empty lists`() {
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.allRecommendations.isEmpty())
    }

    @Test
    fun `loadRecommendations populates state`() = runTest(testDispatcher) {
        viewModel.loadRecommendations("user1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(3, state.allRecommendations.size)
        assertEquals(3, state.globalRecommendations.size)
        assertTrue(state.hasFiction)
        assertTrue(state.hasNonFiction)
    }

    @Test
    fun `userGenres excludes tier-1 labels`() = runTest(testDispatcher) {
        viewModel.loadRecommendations("user1")
        advanceUntilIdle()

        val genres = viewModel.uiState.value.userGenres
        genres.forEach { genre ->
            assertFalse("Tier-1 label leaked: $genre", genre.lowercase() in com.tipil.app.util.Tier1Labels.labels)
        }
    }

    // ───────────────────────────────────────────────────────────────
    // Type browse filtering
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `setTypeBrowse FICTION filters to fiction only`() = runTest(testDispatcher) {
        viewModel.loadRecommendations("user1")
        advanceUntilIdle()

        viewModel.setTypeBrowse(TypeBrowse.FICTION)
        val state = viewModel.uiState.value
        assertEquals(TypeBrowse.FICTION, state.typeBrowse)
        assertEquals(2, state.globalRecommendations.size)
        assertTrue(state.globalRecommendations.all { it.isFiction })
    }

    @Test
    fun `setTypeBrowse NON_FICTION filters to non-fiction only`() = runTest(testDispatcher) {
        viewModel.loadRecommendations("user1")
        advanceUntilIdle()

        viewModel.setTypeBrowse(TypeBrowse.NON_FICTION)
        val state = viewModel.uiState.value
        assertEquals(1, state.globalRecommendations.size)
        assertFalse(state.globalRecommendations.first().isFiction)
    }

    @Test
    fun `setTypeBrowse ALL restores all recommendations`() = runTest(testDispatcher) {
        viewModel.loadRecommendations("user1")
        advanceUntilIdle()

        viewModel.setTypeBrowse(TypeBrowse.FICTION)
        viewModel.setTypeBrowse(TypeBrowse.ALL)
        assertEquals(3, viewModel.uiState.value.globalRecommendations.size)
    }

    // ───────────────────────────────────────────────────────────────
    // Genre selection
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `selectGenre sets selectedGenre`() = runTest(testDispatcher) {
        val genreRecs = listOf(
            BookRecommendation("Book", "Author", "", "", "Reason", "006", true)
        )
        coEvery { repository.getRecommendationsByGenre("user1", "Sci-Fi") } returns genreRecs

        viewModel.loadRecommendations("user1")
        advanceUntilIdle()

        viewModel.selectGenre("user1", "Sci-Fi")
        advanceUntilIdle()

        assertEquals("Sci-Fi", viewModel.uiState.value.selectedGenre)
        assertEquals(genreRecs, viewModel.uiState.value.genreRecommendations["Sci-Fi"])
    }

    @Test
    fun `selectGenre toggles off when same genre selected again`() = runTest(testDispatcher) {
        viewModel.loadRecommendations("user1")
        advanceUntilIdle()

        viewModel.selectGenre("user1", "Sci-Fi")
        advanceUntilIdle()
        viewModel.selectGenre("user1", "Sci-Fi")

        assertNull(viewModel.uiState.value.selectedGenre)
    }

    // ───────────────────────────────────────────────────────────────
    // Error handling
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `loadRecommendations handles error`() = runTest(testDispatcher) {
        coEvery { repository.getRecommendations("user1") } throws RuntimeException("Network error")

        viewModel.loadRecommendations("user1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.error)
    }
}
