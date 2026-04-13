package com.tipil.app.data.repository

import android.util.Log
import com.tipil.app.BuildConfig
import com.tipil.app.data.local.BookDao
import com.tipil.app.data.local.BookEntity
import com.tipil.app.data.local.MediaType
import com.tipil.app.data.remote.GoogleBooksApi
import com.tipil.app.data.remote.MusicBrainzApi
import com.tipil.app.data.remote.OpenLibraryApi
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
    private val musicBrainzApi: MusicBrainzApi,
    private val openLibraryApi: OpenLibraryApi,
    private val genreClassifier: GenreClassifier
) {

    fun getUserBooks(userId: String): Flow<List<BookEntity>> =
        bookDao.getBooksByUser(userId)

    fun getUserBooksByType(userId: String, mediaType: MediaType): Flow<List<BookEntity>> =
        bookDao.getBooksByUserAndType(userId, mediaType.name)

    fun getReadBooks(userId: String): Flow<List<BookEntity>> =
        bookDao.getReadBooks(userId)

    fun getUnreadBooks(userId: String): Flow<List<BookEntity>> =
        bookDao.getUnreadBooks(userId)

    fun getBookCount(userId: String): Flow<Int> =
        bookDao.getBookCount(userId)

    fun getBookCountByType(userId: String, mediaType: MediaType): Flow<Int> =
        bookDao.getBookCountByType(userId, mediaType.name)

    fun getMediaTypesForUser(userId: String): Flow<List<String>> =
        bookDao.getMediaTypesForUser(userId)

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
        // Try Google Books first
        val googleResult = lookupViaGoogleBooks(isbn)
        if (googleResult != null) return googleResult

        // Fallback: Open Library
        val olResult = lookupViaOpenLibrary(isbn)
        if (olResult != null) return olResult

        return null
    }

    private suspend fun lookupViaGoogleBooks(isbn: String): BookLookupResult? {
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
            if (BuildConfig.DEBUG) Log.e(TAG, "Google Books lookup failed for $isbn", e)
            null
        }
    }

    private suspend fun lookupViaOpenLibrary(isbn: String): BookLookupResult? {
        return try {
            val edition = openLibraryApi.getByIsbn(isbn)

            // Resolve author names from author keys
            val authorNames = edition.authors?.mapNotNull { ref ->
                try {
                    val author = openLibraryApi.getAuthor(ref.key)
                    author.name.ifBlank { author.personalName }
                } catch (e: Exception) {
                    null
                }
            } ?: emptyList()

            // Get subjects/description from the work if available
            var subjects = edition.subjects ?: emptyList()
            var description = extractDescription(edition.description)
            val workKey = edition.works?.firstOrNull()?.key
            if (workKey != null) {
                try {
                    val work = openLibraryApi.getWork(workKey)
                    if (subjects.isEmpty()) subjects = work.subjects ?: emptyList()
                    if (description.isBlank()) description = extractDescription(work.description)
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "Open Library work lookup failed", e)
                }
            }

            // Map subjects to genres (take top 5, capitalize)
            val genres = subjects
                .take(5)
                .map { it.replaceFirstChar { c -> c.uppercase() } }

            // Determine fiction status from subjects
            val subjectsLower = subjects.map { it.lowercase() }
            val isFiction = subjectsLower.any { it.contains("fiction") }
                    && !subjectsLower.any { it.contains("non-fiction") || it.contains("nonfiction") }

            // Build cover URL from cover ID
            val coverId = edition.covers?.firstOrNull()
            val coverUrl = if (coverId != null && coverId > 0) {
                "https://covers.openlibrary.org/b/id/$coverId-M.jpg"
            } else ""

            val resolvedIsbn = edition.isbn13?.firstOrNull()
                ?: edition.isbn10?.firstOrNull()
                ?: isbn

            BookLookupResult(
                isbn = resolvedIsbn,
                title = edition.title,
                subtitle = edition.subtitle ?: "",
                authors = authorNames.joinToString(", "),
                publisher = edition.publishers?.firstOrNull() ?: "",
                editor = "",
                publishedYear = edition.publishDate?.takeLast(4)?.takeIf { it.all { c -> c.isDigit() } } ?: "",
                pageCount = edition.numberOfPages ?: 0,
                isFiction = isFiction,
                genres = genres,
                coverUrl = coverUrl,
                description = description
            )
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Open Library lookup failed for $isbn", e)
            null
        }
    }

    /** Open Library description can be a String or a Map with "value" key. */
    private fun extractDescription(desc: Any?): String {
        return when (desc) {
            is String -> desc
            is Map<*, *> -> desc["value"]?.toString() ?: ""
            else -> ""
        }
    }

    /**
     * Look up a music item (CD, Cassette, or Vinyl) by its UPC/EAN barcode via MusicBrainz.
     * Two-step process:
     *   1. Search releases by barcode to get release info + release-group ID
     *   2. Lookup the release-group with ?inc=tags to get genre tags
     *
     * @param mediaType The specific format (CD, CASSETTE, VINYL) to tag the result with.
     */
    suspend fun lookupMusicByBarcode(barcode: String, mediaType: MediaType = MediaType.CD): BookLookupResult? {
        return try {
            // Step 1: Search by barcode — try exact barcode query first
            var searchResponse = musicBrainzApi.searchByBarcode("barcode:$barcode")
            var release = searchResponse.releases?.firstOrNull()

            // Fallback: try without the "barcode:" prefix (free-text search)
            // Many cassettes and older media have inconsistent barcode registrations
            if (release == null) {
                searchResponse = musicBrainzApi.searchByBarcode(barcode)
                release = searchResponse.releases?.firstOrNull()
            }

            if (release == null) return null

            // Extract artist name from credits
            val artist = release.artistCredit
                ?.joinToString(separator = "") { credit ->
                    (credit.name) + (credit.joinPhrase ?: "")
                } ?: ""

            // Extract label
            val label = release.labelInfo?.firstOrNull()?.label?.name ?: ""

            // Track count from media
            val trackCount = release.media?.sumOf { it.trackCount } ?: release.trackCount

            // Cover art from Cover Art Archive
            val coverUrl = "https://coverartarchive.org/release/${release.id}/front-250"

            // Step 2: Get genre tags from release-group
            val genres = mutableListOf<String>()
            val releaseGroupId = release.releaseGroup?.id
            if (!releaseGroupId.isNullOrBlank()) {
                try {
                    val rgDetail = musicBrainzApi.getReleaseGroup(releaseGroupId)
                    genres.addAll(
                        rgDetail.tags
                            ?.sortedByDescending { it.count }
                            ?.take(5)
                            ?.map { tag ->
                                // Capitalize each word for display
                                tag.name.split(" ").joinToString(" ") { word ->
                                    word.replaceFirstChar { it.uppercase() }
                                }
                            }
                            ?: emptyList()
                    )
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "Release-group tag lookup failed", e)
                }
            }

            BookLookupResult(
                isbn = barcode,
                title = release.title,
                subtitle = "",
                authors = artist,            // artist goes in the authors field
                publisher = label,           // label goes in the publisher field
                editor = "",
                publishedYear = release.date?.take(4) ?: "",
                pageCount = trackCount,      // track count reuses pageCount field
                isFiction = false,           // not applicable for music
                genres = genres,
                coverUrl = coverUrl,
                description = "",
                mediaType = mediaType
            )
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Music barcode lookup failed for $barcode", e)
            null
        }
    }

    /**
     * Get music recommendations based on the user's music library (CDs, cassettes, vinyl).
     *
     * Collects genres and artists across ALL music formats. Duplicate albums
     * (same title+artist on different formats) count as one signal.
     */
    suspend fun getMusicRecommendations(userId: String): List<BookRecommendation> {
        // Collect all music items across CD, Cassette, Vinyl
        val allMusic = MediaType.MUSIC_TYPES.flatMap { type ->
            bookDao.getAllBooksByUserAndType(userId, type.name)
        }.takeIf { it.isNotEmpty() } ?: return emptyList()

        val existingBarcodes = allMusic.map { it.isbn }.toSet()
        val recommendations = mutableListOf<BookRecommendation>()

        // Deduplicate by title+artist (case-insensitive) for genre/artist counting
        val uniqueAlbums = allMusic.distinctBy {
            (it.title.lowercase() + "||" + it.authors.lowercase())
        }

        // Collect top genres from unique albums
        val topGenres = uniqueAlbums
            .flatMap { it.genres }
            .filter { it.isNotBlank() }
            .groupBy { it }
            .entries
            .sortedByDescending { it.value.size }
            .take(3)
            .map { it.key }

        // Collect favorite artists from unique albums
        val topArtists = uniqueAlbums
            .groupBy { it.authors }
            .entries
            .sortedByDescending { it.value.size }
            .take(3)
            .map { it.key }

        // Search by genre tags
        for (genre in topGenres) {
            try {
                val response = musicBrainzApi.searchReleases("tag:${genre.lowercase()}")
                response.releases?.forEach { release ->
                    if (release.id !in existingBarcodes) {
                        val artist = release.artistCredit
                            ?.joinToString("") { it.name + (it.joinPhrase ?: "") } ?: ""
                        recommendations.add(
                            BookRecommendation(
                                title = release.title,
                                authors = artist,
                                coverUrl = "https://coverartarchive.org/release/${release.id}/front-250",
                                description = release.releaseGroup?.primaryType ?: "",
                                reason = "Based on your interest in $genre",
                                isbn = release.id,
                                isFiction = false,
                                mediaType = MediaType.CD
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Music genre search failed for: $genre", e)
            }
        }

        // Search by artist
        for (artist in topArtists) {
            try {
                val response = musicBrainzApi.searchReleases("artist:\"$artist\"")
                response.releases?.forEach { release ->
                    if (release.id !in existingBarcodes) {
                        val creditArtist = release.artistCredit
                            ?.joinToString("") { it.name + (it.joinPhrase ?: "") } ?: ""
                        recommendations.add(
                            BookRecommendation(
                                title = release.title,
                                authors = creditArtist,
                                coverUrl = "https://coverartarchive.org/release/${release.id}/front-250",
                                description = release.releaseGroup?.primaryType ?: "",
                                reason = "More from $artist",
                                isbn = release.id,
                                isFiction = false,
                                mediaType = MediaType.CD
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Music artist search failed for: $artist", e)
            }
        }

        return recommendations.distinctBy { it.title + it.authors }.take(20)
    }

    /**
     * Get music recommendations filtered by a specific genre tag.
     * Checks all music formats to avoid recommending already-owned albums.
     */
    suspend fun getMusicRecommendationsByGenre(userId: String, genre: String): List<BookRecommendation> {
        val existingBarcodes = MediaType.MUSIC_TYPES.flatMap { type ->
            bookDao.getAllBooksByUserAndType(userId, type.name)
        }.map { it.isbn }.toSet()

        return try {
            val response = musicBrainzApi.searchReleases("tag:${genre.lowercase()}")
            response.releases
                ?.filter { it.id !in existingBarcodes }
                ?.map { release ->
                    val artist = release.artistCredit
                        ?.joinToString("") { it.name + (it.joinPhrase ?: "") } ?: ""
                    BookRecommendation(
                        title = release.title,
                        authors = artist,
                        coverUrl = "https://coverartarchive.org/release/${release.id}/front-250",
                        description = release.releaseGroup?.primaryType ?: "",
                        reason = "Recommended in $genre",
                        isbn = release.id,
                        isFiction = false,
                        mediaType = MediaType.CD
                    )
                }
                ?: emptyList()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Music genre recommendation failed for: $genre", e)
            emptyList()
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
    val description: String,
    val mediaType: MediaType = MediaType.BOOK
)

data class BookRecommendation(
    val title: String,
    val authors: String,
    val coverUrl: String,
    val description: String,
    val reason: String,
    val isbn: String,
    val isFiction: Boolean = true,
    val mediaType: MediaType = MediaType.BOOK
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
