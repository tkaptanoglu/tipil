package com.tipil.app.data.repository

import android.util.Log
import com.tipil.app.BuildConfig
import com.tipil.app.data.local.BookDao
import com.tipil.app.data.local.BookEntity
import com.tipil.app.data.remote.GoogleBooksApi
import com.tipil.app.data.remote.VolumeInfo
import com.tipil.app.util.GenreClassifier
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BookRepository"

@Singleton
class BookRepository @Inject constructor(
    private val bookDao: BookDao,
    private val googleBooksApi: GoogleBooksApi,
    private val genreClassifier: GenreClassifier
) {

    fun getUserBooks(userId: String): Flow<List<BookEntity>> =
        bookDao.getBooksByUser(userId)

    fun getReadBooks(userId: String): Flow<List<BookEntity>> =
        bookDao.getReadBooks(userId)

    fun getUnreadBooks(userId: String): Flow<List<BookEntity>> =
        bookDao.getUnreadBooks(userId)

    fun getBookCount(userId: String): Flow<Int> =
        bookDao.getBookCount(userId)

    suspend fun getBookById(bookId: Long, userId: String): BookEntity? =
        bookDao.getBookById(bookId, userId)

    suspend fun addBook(book: BookEntity): Long =
        bookDao.insertBook(book)

    suspend fun updateBook(book: BookEntity) =
        bookDao.updateBook(book)

    suspend fun deleteBook(book: BookEntity) =
        bookDao.deleteBook(book)

    suspend fun setReadStatus(bookId: Long, userId: String, isRead: Boolean) =
        bookDao.setReadStatus(bookId, userId, isRead)

    suspend fun isBookInLibrary(userId: String, isbn: String): Boolean =
        bookDao.getBookByIsbn(userId, isbn) != null

    suspend fun lookupBookByIsbn(isbn: String): BookLookupResult? {
        return try {
            val response = googleBooksApi.searchByIsbn(
                query = "isbn:$isbn",
                apiKey = BuildConfig.GOOGLE_BOOKS_API_KEY
            )
            val item = response.items?.firstOrNull() ?: return null
            val info = item.volumeInfo
            val genres = genreClassifier.classify(info)
            val isFiction = genreClassifier.isFiction(info)

            BookLookupResult(
                isbn = isbn,
                title = info.title,
                subtitle = info.subtitle ?: "",
                authors = info.authors?.joinToString(", ") ?: "",
                publisher = info.publisher ?: "",
                editor = "",
                publishedYear = info.publishedDate?.take(4) ?: "",
                pageCount = info.pageCount ?: 0,
                isFiction = isFiction,
                genres = genres,
                coverUrl = info.imageLinks?.thumbnail?.replace("http://", "https://") ?: "",
                description = info.description ?: ""
            )
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "ISBN lookup failed for $isbn", e)
            null
        }
    }

    suspend fun getRecommendations(userId: String): List<BookRecommendation> {
        val userBooks = bookDao.getAllBooksByUser(userId)
            .takeIf { it.isNotEmpty() } ?: return emptyList()

        val allGenres = collectUserGenres(userId)
        val favoriteAuthors = userBooks
            .groupBy { it.authors }
            .entries
            .sortedByDescending { it.value.size }
            .take(3)
            .map { it.key }

        val recommendations = mutableListOf<BookRecommendation>()
        val existingIsbns = userBooks.map { it.isbn }.toSet()

        // Search by top genres
        for (genre in allGenres.take(3)) {
            try {
                val response = googleBooksApi.searchBooks(
                    query = "subject:$genre",
                    maxResults = 5,
                    apiKey = BuildConfig.GOOGLE_BOOKS_API_KEY
                )
                response.items?.forEach { item ->
                    val isbn = item.volumeInfo.industryIdentifiers
                        ?.firstOrNull { it.type == "ISBN_13" || it.type == "ISBN_10" }
                        ?.identifier ?: ""
                    if (isbn.isNotEmpty() && isbn !in existingIsbns) {
                        recommendations.add(item.toRecommendation("Based on your interest in $genre", genreClassifier))
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Genre search failed for: $genre", e)
            }
        }

        // Search by favorite authors
        for (author in favoriteAuthors) {
            try {
                val response = googleBooksApi.searchBooks(
                    query = "inauthor:$author",
                    maxResults = 3,
                    apiKey = BuildConfig.GOOGLE_BOOKS_API_KEY
                )
                response.items?.forEach { item ->
                    val isbn = item.volumeInfo.industryIdentifiers
                        ?.firstOrNull { it.type == "ISBN_13" || it.type == "ISBN_10" }
                        ?.identifier ?: ""
                    if (isbn.isNotEmpty() && isbn !in existingIsbns) {
                        recommendations.add(item.toRecommendation("More from $author", genreClassifier))
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Author search failed for: $author", e)
            }
        }

        return recommendations.distinctBy { it.title }.take(20)
    }

    suspend fun getRecommendationsByGenre(userId: String, genre: String): List<BookRecommendation> {
        val existingIsbns = bookDao.getAllBooksByUser(userId)
            .map { it.isbn }.toSet()

        return try {
            val response = googleBooksApi.searchBooks(
                query = "subject:$genre",
                maxResults = 15,
                apiKey = BuildConfig.GOOGLE_BOOKS_API_KEY
            )
            response.items
                ?.filter { item ->
                    val isbn = item.volumeInfo.industryIdentifiers
                        ?.firstOrNull { it.type == "ISBN_13" || it.type == "ISBN_10" }
                        ?.identifier ?: ""
                    isbn.isNotEmpty() && isbn !in existingIsbns
                }
                ?.map { it.toRecommendation("Recommended in $genre", genreClassifier) }
                ?: emptyList()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Genre recommendation failed for: $genre", e)
            emptyList()
        }
    }

    private suspend fun collectUserGenres(userId: String): List<String> {
        val rawGenres = bookDao.getAllGenresRaw(userId)
        return rawGenres
            .flatMap { it.split("||") }
            .filter { it.isNotBlank() }
            .groupBy { it }
            .entries
            .sortedByDescending { it.value.size }
            .map { it.key }
    }
}

data class BookLookupResult(
    val isbn: String,
    val title: String,
    val subtitle: String,
    val authors: String,
    val publisher: String,
    val editor: String,
    val publishedYear: String,
    val pageCount: Int,
    val isFiction: Boolean,
    val genres: List<String>,
    val coverUrl: String,
    val description: String
)

data class BookRecommendation(
    val title: String,
    val authors: String,
    val coverUrl: String,
    val description: String,
    val reason: String,
    val isbn: String,
    val isFiction: Boolean = true
)

private fun com.tipil.app.data.remote.BookItem.toRecommendation(
    reason: String,
    genreClassifier: GenreClassifier
): BookRecommendation {
    return BookRecommendation(
        title = volumeInfo.title,
        authors = volumeInfo.authors?.joinToString(", ") ?: "",
        coverUrl = volumeInfo.imageLinks?.thumbnail?.replace("http://", "https://") ?: "",
        description = volumeInfo.description?.take(200) ?: "",
        reason = reason,
        isbn = volumeInfo.industryIdentifiers
            ?.firstOrNull { it.type == "ISBN_13" || it.type == "ISBN_10" }
            ?.identifier ?: "",
        isFiction = genreClassifier.isFiction(volumeInfo)
    )
}
