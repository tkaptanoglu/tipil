package com.tipil.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for BookDao and Room database.
 * Runs on an Android device or emulator.
 */
@RunWith(AndroidJUnit4::class)
class BookDaoTest {

    private lateinit var database: TipilDatabase
    private lateinit var dao: BookDao

    private fun testBook(
        isbn: String = "9780062316097",
        userId: String = "user1",
        title: String = "Sapiens",
        authors: String = "Yuval Noah Harari",
        isFiction: Boolean = false,
        genres: List<String> = listOf("History"),
        isRead: Boolean = false,
        addedAt: Long = System.currentTimeMillis()
    ) = BookEntity(
        userId = userId, isbn = isbn, title = title, authors = authors,
        isFiction = isFiction, genres = genres, isRead = isRead, addedAt = addedAt
    )

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TipilDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.bookDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ───────────────────────────────────────────────────────────────
    // Insert and retrieve
    // ───────────────────────────────────────────────────────────────

    @Test
    fun insertAndRetrieveBook() = runTest {
        val book = testBook()
        val id = dao.insertBook(book)
        assertTrue(id > 0)

        val retrieved = dao.getBookById(id, "user1")
        assertNotNull(retrieved)
        assertEquals("Sapiens", retrieved!!.title)
        assertEquals("Yuval Noah Harari", retrieved.authors)
        assertEquals(listOf("History"), retrieved.genres)
    }

    @Test
    fun insertAndRetrieveByIsbn() = runTest {
        dao.insertBook(testBook())

        val found = dao.getBookByIsbn("user1", "9780062316097")
        assertNotNull(found)
        assertEquals("Sapiens", found!!.title)
    }

    // ───────────────────────────────────────────────────────────────
    // User isolation (IDOR protection)
    // ───────────────────────────────────────────────────────────────

    @Test
    fun getBookByIdEnforcesUserId() = runTest {
        val id = dao.insertBook(testBook(userId = "user1"))
        val result = dao.getBookById(id, "user2")
        assertNull("Different user should not access book", result)
    }

    @Test
    fun getBooksByUserReturnsOnlyOwnBooks() = runTest {
        dao.insertBook(testBook(isbn = "001", userId = "user1", title = "Book 1"))
        dao.insertBook(testBook(isbn = "002", userId = "user2", title = "Book 2"))

        val user1Books = dao.getBooksByUser("user1").first()
        assertEquals(1, user1Books.size)
        assertEquals("Book 1", user1Books.first().title)
    }

    // ───────────────────────────────────────────────────────────────
    // Unique ISBN constraint per user
    // ───────────────────────────────────────────────────────────────

    @Test(expected = android.database.sqlite.SQLiteConstraintException::class)
    fun duplicateIsbnForSameUserThrows() = runTest {
        dao.insertBook(testBook(isbn = "001", userId = "user1"))
        dao.insertBook(testBook(isbn = "001", userId = "user1"))
    }

    @Test
    fun sameIsbnDifferentUsersSucceeds() = runTest {
        dao.insertBook(testBook(isbn = "001", userId = "user1"))
        dao.insertBook(testBook(isbn = "001", userId = "user2"))
        // Should not throw
    }

    // ───────────────────────────────────────────────────────────────
    // Read status
    // ───────────────────────────────────────────────────────────────

    @Test
    fun setReadStatusUpdatesCorrectly() = runTest {
        val id = dao.insertBook(testBook(isRead = false))
        dao.setReadStatus(id, "user1", true)

        val book = dao.getBookById(id, "user1")
        assertTrue(book!!.isRead)
    }

    @Test
    fun setReadStatusEnforcesUserId() = runTest {
        val id = dao.insertBook(testBook(userId = "user1", isRead = false))
        dao.setReadStatus(id, "user2", true) // wrong user

        val book = dao.getBookById(id, "user1")
        assertFalse("Status should not change for wrong user", book!!.isRead)
    }

    @Test
    fun getReadBooksReturnsOnlyRead() = runTest {
        dao.insertBook(testBook(isbn = "001", isRead = true, title = "Read Book"))
        dao.insertBook(testBook(isbn = "002", isRead = false, title = "Unread Book"))

        val readBooks = dao.getReadBooks("user1").first()
        assertEquals(1, readBooks.size)
        assertEquals("Read Book", readBooks.first().title)
    }

    @Test
    fun getUnreadBooksReturnsOnlyUnread() = runTest {
        dao.insertBook(testBook(isbn = "001", isRead = true))
        dao.insertBook(testBook(isbn = "002", isRead = false, title = "Unread"))

        val unread = dao.getUnreadBooks("user1").first()
        assertEquals(1, unread.size)
        assertEquals("Unread", unread.first().title)
    }

    // ───────────────────────────────────────────────────────────────
    // Delete
    // ───────────────────────────────────────────────────────────────

    @Test
    fun deleteBookRemovesIt() = runTest {
        val id = dao.insertBook(testBook())
        val book = dao.getBookById(id, "user1")!!
        dao.deleteBook(book)

        assertNull(dao.getBookById(id, "user1"))
    }

    // ───────────────────────────────────────────────────────────────
    // Update
    // ───────────────────────────────────────────────────────────────

    @Test
    fun updateBookModifiesFields() = runTest {
        val id = dao.insertBook(testBook())
        val book = dao.getBookById(id, "user1")!!
        val updated = book.copy(title = "Sapiens: Updated")
        dao.updateBook(updated)

        assertEquals("Sapiens: Updated", dao.getBookById(id, "user1")!!.title)
    }

    // ───────────────────────────────────────────────────────────────
    // Count
    // ───────────────────────────────────────────────────────────────

    @Test
    fun getBookCountReturnsCorrectCount() = runTest {
        dao.insertBook(testBook(isbn = "001"))
        dao.insertBook(testBook(isbn = "002"))
        dao.insertBook(testBook(isbn = "003"))

        val count = dao.getBookCount("user1").first()
        assertEquals(3, count)
    }

    @Test
    fun getBookCountForDifferentUserReturnsZero() = runTest {
        dao.insertBook(testBook(isbn = "001", userId = "user1"))

        val count = dao.getBookCount("user2").first()
        assertEquals(0, count)
    }

    // ───────────────────────────────────────────────────────────────
    // Genres
    // ───────────────────────────────────────────────────────────────

    @Test
    fun getAllGenresRawReturnsSerializedGenres() = runTest {
        dao.insertBook(testBook(isbn = "001", genres = listOf("Sci-Fi", "Adventure")))
        dao.insertBook(testBook(isbn = "002", genres = listOf("History")))

        val rawGenres = dao.getAllGenresRaw("user1")
        assertEquals(2, rawGenres.size)
        assertTrue(rawGenres.any { it.contains("Sci-Fi") })
    }

    @Test
    fun getBooksByGenreFindsPartialMatch() = runTest {
        dao.insertBook(testBook(isbn = "001", genres = listOf("Sci-Fi", "Adventure")))
        dao.insertBook(testBook(isbn = "002", genres = listOf("History")))

        val sciFiBooks = dao.getBooksByGenre("user1", "Sci-Fi")
        assertEquals(1, sciFiBooks.size)
    }

    // ───────────────────────────────────────────────────────────────
    // getAllBooksByUser
    // ───────────────────────────────────────────────────────────────

    @Test
    fun getAllBooksByUserReturnsSuspendList() = runTest {
        dao.insertBook(testBook(isbn = "001"))
        dao.insertBook(testBook(isbn = "002"))

        val all = dao.getAllBooksByUser("user1")
        assertEquals(2, all.size)
    }

    // ───────────────────────────────────────────────────────────────
    // TypeConverter (genres through Room)
    // ───────────────────────────────────────────────────────────────

    @Test
    fun genresRoundTripThroughRoom() = runTest {
        val genres = listOf("Sci-Fi", "Fantasy", "Adventure")
        val id = dao.insertBook(testBook(genres = genres))

        val book = dao.getBookById(id, "user1")!!
        assertEquals(genres, book.genres)
    }

    @Test
    fun emptyGenresRoundTripThroughRoom() = runTest {
        val id = dao.insertBook(testBook(genres = emptyList()))
        val book = dao.getBookById(id, "user1")!!
        assertTrue(book.genres.isEmpty())
    }
}
