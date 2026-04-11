package com.tipil.app.localization

import com.tipil.app.data.local.BookEntity
import com.tipil.app.data.local.StringListConverter
import com.tipil.app.data.remote.VolumeInfo
import com.tipil.app.data.repository.BookRepository
import com.tipil.app.ui.library.LibraryViewModel
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
 * Localization tests: Unicode text, non-Latin scripts, RTL languages,
 * accented characters, and international edge cases.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalizationTest {

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
    // CJK (Chinese, Japanese, Korean) text
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `BookEntity handles Chinese title and author`() {
        val book = BookEntity(
            id = 1, userId = "u", isbn = "001", title = "三体",
            authors = "刘慈欣", genres = listOf("科幻小说"), addedAt = 1000
        )
        assertEquals("三体", book.title)
        assertEquals("刘慈欣", book.authors)
    }

    @Test
    fun `BookEntity handles Japanese title and author`() {
        val book = BookEntity(
            id = 1, userId = "u", isbn = "001", title = "ノルウェイの森",
            authors = "村上春樹", genres = listOf("小説"), addedAt = 1000
        )
        assertEquals("ノルウェイの森", book.title)
        assertEquals("村上春樹", book.authors)
    }

    @Test
    fun `BookEntity handles Korean title and author`() {
        val book = BookEntity(
            id = 1, userId = "u", isbn = "001", title = "채식주의자",
            authors = "한강", genres = listOf("소설"), addedAt = 1000
        )
        assertEquals("채식주의자", book.title)
    }

    // ───────────────────────────────────────────────────────────────
    // Arabic / RTL
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `BookEntity handles Arabic text`() {
        val book = BookEntity(
            id = 1, userId = "u", isbn = "001", title = "ألف ليلة وليلة",
            authors = "مؤلف مجهول", genres = listOf("أدب"), addedAt = 1000
        )
        assertEquals("ألف ليلة وليلة", book.title)
    }

    @Test
    fun `BookEntity handles Hebrew text`() {
        val book = BookEntity(
            id = 1, userId = "u", isbn = "001", title = "אלכימאי",
            authors = "פאולו קואלו", addedAt = 1000
        )
        assertEquals("אלכימאי", book.title)
    }

    // ───────────────────────────────────────────────────────────────
    // Accented / diacritic characters
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `BookEntity handles French accents`() {
        val book = BookEntity(
            id = 1, userId = "u", isbn = "001", title = "Les Misérables",
            authors = "Victor Hugo", addedAt = 1000
        )
        assertEquals("Les Misérables", book.title)
    }

    @Test
    fun `BookEntity handles German umlauts`() {
        val book = BookEntity(
            id = 1, userId = "u", isbn = "001", title = "Die Blechtrommel",
            authors = "Günter Grass", addedAt = 1000
        )
        assertEquals("Günter Grass", book.authors)
    }

    @Test
    fun `BookEntity handles Spanish tildes and accents`() {
        val book = BookEntity(
            id = 1, userId = "u", isbn = "001", title = "Cien años de soledad",
            authors = "Gabriel García Márquez", addedAt = 1000
        )
        assertEquals("Gabriel García Márquez", book.authors)
    }

    @Test
    fun `BookEntity handles Cyrillic text`() {
        val book = BookEntity(
            id = 1, userId = "u", isbn = "001", title = "Война и мир",
            authors = "Лев Толстой", addedAt = 1000
        )
        assertEquals("Война и мир", book.title)
    }

    // ───────────────────────────────────────────────────────────────
    // StringListConverter with international genres
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `StringListConverter round-trips CJK genres`() {
        val converter = StringListConverter()
        val genres = listOf("科幻小说", "历史", "哲学")
        assertEquals(genres, converter.toList(converter.fromList(genres)))
    }

    @Test
    fun `StringListConverter round-trips Arabic genres`() {
        val converter = StringListConverter()
        val genres = listOf("أدب", "تاريخ", "فلسفة")
        assertEquals(genres, converter.toList(converter.fromList(genres)))
    }

    @Test
    fun `StringListConverter round-trips mixed-script genres`() {
        val converter = StringListConverter()
        val genres = listOf("Sci-Fi", "科幻", "サイエンス・フィクション", "Научная фантастика")
        assertEquals(genres, converter.toList(converter.fromList(genres)))
    }

    // ───────────────────────────────────────────────────────────────
    // Library search with international text
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `library search finds Chinese title`() = runTest(testDispatcher) {
        val books = listOf(
            BookEntity(id = 1, userId = "u", isbn = "001", title = "三体",
                authors = "刘慈欣", addedAt = 1000),
            BookEntity(id = 2, userId = "u", isbn = "002", title = "Dune",
                authors = "Frank Herbert", addedAt = 2000)
        )
        val repository: BookRepository = mockk(relaxed = true)
        every { repository.getUserBooks("u") } returns flowOf(books)

        val vm = LibraryViewModel(repository)
        vm.loadBooks("u")
        advanceUntilIdle()

        vm.setSearchQuery("三体")
        assertEquals(1, vm.uiState.value.books.size)
        assertEquals("三体", vm.uiState.value.books.first().title)
    }

    @Test
    fun `library search finds accented author`() = runTest(testDispatcher) {
        val books = listOf(
            BookEntity(id = 1, userId = "u", isbn = "001",
                title = "Cien años de soledad",
                authors = "Gabriel García Márquez", addedAt = 1000)
        )
        val repository: BookRepository = mockk(relaxed = true)
        every { repository.getUserBooks("u") } returns flowOf(books)

        val vm = LibraryViewModel(repository)
        vm.loadBooks("u")
        advanceUntilIdle()

        vm.setSearchQuery("garcía")
        assertEquals(1, vm.uiState.value.books.size)
    }

    @Test
    fun `library search is case-insensitive for accented characters`() = runTest(testDispatcher) {
        val books = listOf(
            BookEntity(id = 1, userId = "u", isbn = "001",
                title = "Les Misérables", authors = "Victor Hugo", addedAt = 1000)
        )
        val repository: BookRepository = mockk(relaxed = true)
        every { repository.getUserBooks("u") } returns flowOf(books)

        val vm = LibraryViewModel(repository)
        vm.loadBooks("u")
        advanceUntilIdle()

        vm.setSearchQuery("misérables")
        assertEquals(1, vm.uiState.value.books.size)
    }

    // ───────────────────────────────────────────────────────────────
    // GenreClassifier with international categories
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `GenreClassifier handles non-Latin categories gracefully`() {
        val classifier = GenreClassifier()
        val info = VolumeInfo(categories = listOf("科幻小说", "冒险"))
        // Won't match any English keywords, falls back to raw categories
        val genres = classifier.classify(info)
        assertTrue(genres.isNotEmpty())
        // Should contain the raw Chinese categories
    }

    @Test
    fun `GenreClassifier isFiction defaults to true for non-English categories`() {
        val classifier = GenreClassifier()
        val info = VolumeInfo(categories = listOf("小説", "ロマンス"))
        // No English keywords match → 0-0 tie → defaults to fiction
        assertTrue(classifier.isFiction(info))
    }

    // ───────────────────────────────────────────────────────────────
    // Emoji in titles
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `BookEntity handles emoji in title`() {
        val book = BookEntity(
            id = 1, userId = "u", isbn = "001",
            title = "The Art of 🎨 Painting",
            authors = "An Artist 🖌️", addedAt = 1000
        )
        assertEquals("The Art of 🎨 Painting", book.title)
    }

    @Test
    fun `StringListConverter round-trips genres with emoji`() {
        val converter = StringListConverter()
        val genres = listOf("Art 🎨", "Music 🎵", "Science 🔬")
        assertEquals(genres, converter.toList(converter.fromList(genres)))
    }

    // ───────────────────────────────────────────────────────────────
    // Author last-name extraction with international names
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `sorting works with accented author names`() = runTest(testDispatcher) {
        val books = listOf(
            BookEntity(id = 1, userId = "u", isbn = "001", title = "Book A",
                authors = "Gabriel García Márquez", addedAt = 1000),
            BookEntity(id = 2, userId = "u", isbn = "002", title = "Book B",
                authors = "Albert Camus", addedAt = 2000),
            BookEntity(id = 3, userId = "u", isbn = "003", title = "Book C",
                authors = "Fyodor Dostoevsky", addedAt = 3000)
        )
        val repository: BookRepository = mockk(relaxed = true)
        every { repository.getUserBooks("u") } returns flowOf(books)

        val vm = LibraryViewModel(repository)
        vm.loadBooks("u")
        advanceUntilIdle()

        // Default sort is author A-Z by last name: Camus, Dostoevsky, Márquez
        val titles = vm.uiState.value.books.map { it.title }
        assertEquals(listOf("Book B", "Book C", "Book A"), titles)
    }

    @Test
    fun `sorting handles single-name authors`() = runTest(testDispatcher) {
        val books = listOf(
            BookEntity(id = 1, userId = "u", isbn = "001", title = "Book A",
                authors = "Voltaire", addedAt = 1000),
            BookEntity(id = 2, userId = "u", isbn = "002", title = "Book B",
                authors = "Plato", addedAt = 2000)
        )
        val repository: BookRepository = mockk(relaxed = true)
        every { repository.getUserBooks("u") } returns flowOf(books)

        val vm = LibraryViewModel(repository)
        vm.loadBooks("u")
        advanceUntilIdle()

        // Plato, Voltaire
        val titles = vm.uiState.value.books.map { it.title }
        assertEquals(listOf("Book B", "Book A"), titles)
    }

    @Test
    fun `sorting handles empty author string`() = runTest(testDispatcher) {
        val books = listOf(
            BookEntity(id = 1, userId = "u", isbn = "001", title = "Unknown Author",
                authors = "", addedAt = 1000),
            BookEntity(id = 2, userId = "u", isbn = "002", title = "Known Author",
                authors = "Zelda Fitzgerald", addedAt = 2000)
        )
        val repository: BookRepository = mockk(relaxed = true)
        every { repository.getUserBooks("u") } returns flowOf(books)

        val vm = LibraryViewModel(repository)
        vm.loadBooks("u")
        advanceUntilIdle()

        // Empty string sorts before "fitzgerald"
        assertEquals("Unknown Author", vm.uiState.value.books.first().title)
    }
}
