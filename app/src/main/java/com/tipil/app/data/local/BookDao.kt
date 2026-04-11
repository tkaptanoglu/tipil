package com.tipil.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    // ── All media types ──

    @Query("SELECT * FROM books WHERE userId = :userId ORDER BY addedAt DESC")
    fun getBooksByUser(userId: String): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE userId = :userId AND isRead = 1 ORDER BY addedAt DESC")
    fun getReadBooks(userId: String): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE userId = :userId AND isRead = 0 ORDER BY addedAt DESC")
    fun getUnreadBooks(userId: String): Flow<List<BookEntity>>

    // ── Filtered by media type ──

    @Query("SELECT * FROM books WHERE userId = :userId AND mediaType = :mediaType ORDER BY addedAt DESC")
    fun getBooksByUserAndType(userId: String, mediaType: String): Flow<List<BookEntity>>

    @Query("SELECT COUNT(*) FROM books WHERE userId = :userId AND mediaType = :mediaType")
    fun getBookCountByType(userId: String, mediaType: String): Flow<Int>

    @Query("SELECT * FROM books WHERE userId = :userId AND mediaType = :mediaType")
    suspend fun getAllBooksByUserAndType(userId: String, mediaType: String): List<BookEntity>

    // ── Single-item lookups ──

    @Query("SELECT * FROM books WHERE id = :bookId AND userId = :userId")
    suspend fun getBookById(bookId: Long, userId: String): BookEntity?

    @Query("SELECT * FROM books WHERE userId = :userId AND isbn = :isbn LIMIT 1")
    suspend fun getBookByIsbn(userId: String, isbn: String): BookEntity?

    // ── Aggregates ──

    @Query("SELECT DISTINCT genres FROM books WHERE userId = :userId")
    suspend fun getAllGenresRaw(userId: String): List<String>

    @Query("SELECT DISTINCT genres FROM books WHERE userId = :userId AND mediaType = :mediaType")
    suspend fun getAllGenresRawByType(userId: String, mediaType: String): List<String>

    @Query("SELECT COUNT(*) FROM books WHERE userId = :userId")
    fun getBookCount(userId: String): Flow<Int>

    @Query("SELECT DISTINCT mediaType FROM books WHERE userId = :userId")
    fun getMediaTypesForUser(userId: String): Flow<List<String>>

    // ── Mutations ──

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBook(book: BookEntity): Long

    @Update
    suspend fun updateBook(book: BookEntity)

    @Delete
    suspend fun deleteBook(book: BookEntity)

    @Query("UPDATE books SET isRead = :isRead WHERE id = :bookId AND userId = :userId")
    suspend fun setReadStatus(bookId: Long, userId: String, isRead: Boolean)

    // ── Legacy/utility ──

    @Query("SELECT * FROM books WHERE userId = :userId")
    suspend fun getAllBooksByUser(userId: String): List<BookEntity>

    @Query("SELECT * FROM books WHERE userId = :userId AND genres LIKE '%' || :genre || '%'")
    suspend fun getBooksByGenre(userId: String, genre: String): List<BookEntity>
}
