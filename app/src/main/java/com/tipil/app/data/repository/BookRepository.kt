package com.tipil.app.data.repository

import android.util.Log
import com.tipil.app.BuildConfig
import com.tipil.app.data.local.BookDao
import com.tipil.app.data.local.BookEntity
import com.tipil.app.data.local.MediaType
import com.tipil.app.data.local.NotFoundScanDao
import com.tipil.app.data.local.NotFoundScanEntity
import com.tipil.app.data.remote.GoogleBooksApi
import com.tipil.app.data.remote.K10plusApi
import com.tipil.app.data.remote.MarcRecord
import com.tipil.app.data.remote.MarcXmlParser
import com.tipil.app.data.remote.MusicBrainzApi
import com.tipil.app.data.remote.OpenLibraryApi
import com.tipil.app.data.remote.VolumeInfo
import com.tipil.app.util.GenreClassifier
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BookRepository"

@Singleton
class BookRepository @Inject constructor(
    private val bookDao: BookDao,
    private val notFoundScanDao: NotFoundScanDao,
    private val googleBooksApi: GoogleBooksApi,
    private val musicBrainzApi: MusicBrainzApi,
    private val openLibraryApi: OpenLibraryApi,
    private val k10plusApi: K10plusApi,
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

    /**
     * Resolve an ISBN to book metadata, trying each source in turn until one hits.
     *
     * Ordered cheapest and most precise first; every step short-circuits, so a
     * title Google Books knows costs a single request. Only a genuine miss
     * walks the whole chain.
     *
     *   1. Google Books, `isbn:` query — richest metadata when it hits.
     *   2. Google Books, free text     — the structured ISBN index is less
     *                                    complete than the full-text one, so
     *                                    the same volume is often findable
     *                                    as a raw term.
     *   3. Open Library edition        — /isbn/{isbn}.json.
     *   4. Open Library Books API      — a separate index; resolves some ISBNs
     *                                    that step 3 404s on.
     *   5. Open Library search         — matches ISBNs listed on a work whose
     *                                    edition record is missing.
     *   6. K10plus (MARCXML)           — a different corpus entirely; the only
     *                                    step that can find a book Google and
     *                                    Open Library have never indexed.
     */
    suspend fun lookupBookByIsbn(isbn: String): BookLookupResult? {
        lookupViaGoogleBooks(isbn)?.let { return it }
        lookupViaGoogleBooksFreeText(isbn)?.let { return it }
        lookupViaOpenLibrary(isbn)?.let { return it }
        lookupViaOpenLibraryApiBooks(isbn)?.let { return it }
        lookupViaOpenLibrarySearch(isbn)?.let { return it }
        lookupViaK10plus(isbn)?.let { return it }
        return null
    }

    /**
     * Auto-detect lookup: tries the book chain and MusicBrainz in parallel.
     * Returns the first hit, with the [MediaType] inferred from whichever
     * source matched (and, for music, from the release's format).
     *
     * [preferredType] biases which answer wins when both could match. Scanning
     * leaves it null, so books take precedence — an ISBN match is stronger
     * evidence than a UPC one. Refreshing an item passes the type it already
     * has, so a CD in the library cannot be silently reclassified as a book by
     * a coincidental match.
     *
     * Returns null only when neither side recognises the barcode.
     */
    suspend fun lookupByBarcode(
        barcode: String,
        preferredType: MediaType? = null
    ): BookLookupResult? = coroutineScope {
        val bookDeferred = async { lookupBookByIsbn(barcode) }
        val musicDeferred = async { lookupMusicByBarcodeAutoDetect(barcode) }

        if (preferredType?.isMusic == true) {
            musicDeferred.await()?.let {
                bookDeferred.cancel()
                return@coroutineScope it
            }
            return@coroutineScope bookDeferred.await()
        }

        bookDeferred.await()?.let {
            // Cancel the music lookup if still running — we have our answer
            musicDeferred.cancel()
            return@coroutineScope it
        }

        musicDeferred.await()
    }

    /**
     * Re-runs the lookup chain for an item already in the library and writes
     * the fresh metadata over the stored record.
     *
     * Everything the user owns is preserved: the row [BookEntity.id], its
     * [BookEntity.userId], the scanned [BookEntity.isbn], the read/listened
     * flag, and the original [BookEntity.addedAt] date. Everything descriptive
     * is replaced.
     *
     * Returns the updated entity, or null when no source recognises the
     * barcode — in which case the stored record is left untouched rather than
     * being blanked out.
     */
    suspend fun refreshItem(book: BookEntity): BookEntity? {
        val currentType = MediaType.fromName(book.mediaType)
        val result = lookupByBarcode(book.isbn, preferredType = currentType) ?: return null

        // A refresh may resolve through a different source than the original
        // scan did, and the sources do not all carry the same fields — K10plus
        // has no cover art, for one. Overwrite with what the new source knows,
        // but let an existing value stand where it knows nothing, so refreshing
        // never strips detail the record already had.
        val updated = book.copy(
            title = result.title.ifBlank { book.title },
            subtitle = result.subtitle.ifBlank { book.subtitle },
            authors = result.authors.ifBlank { book.authors },
            publisher = result.publisher.ifBlank { book.publisher },
            editor = result.editor.ifBlank { book.editor },
            publishedYear = result.publishedYear.ifBlank { book.publishedYear },
            pageCount = if (result.pageCount > 0) result.pageCount else book.pageCount,
            isFiction = result.isFiction,
            genres = result.genres.ifEmpty { book.genres },
            coverUrl = result.coverUrl.ifBlank { book.coverUrl },
            description = result.description.ifBlank { book.description },
            mediaType = result.mediaType.name
        )

        bookDao.updateBook(updated)
        return updated
    }

    /**
     * MusicBrainz lookup that auto-detects the physical format from the
     * release's media list (CD, Vinyl, Cassette, etc.).
     */
    private suspend fun lookupMusicByBarcodeAutoDetect(barcode: String): BookLookupResult? {
        return try {
            var searchResponse = musicBrainzApi.searchByBarcode("barcode:$barcode")
            var release = searchResponse.releases?.firstOrNull()

            if (release == null) {
                searchResponse = musicBrainzApi.searchByBarcode(barcode)
                release = searchResponse.releases?.firstOrNull()
            }

            if (release == null) return null

            // Infer MediaType from the release's media format field
            val detectedType = release.media
                ?.firstOrNull()
                ?.format
                ?.let { inferMediaTypeFromFormat(it) }
                ?: MediaType.CD

            // Delegate to the existing music lookup, passing the detected type
            lookupMusicByBarcode(barcode, detectedType)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Auto-detect music lookup failed for $barcode", e)
            null
        }
    }

    /**
     * Maps MusicBrainz media format strings to our [MediaType].
     *
     * MusicBrainz formats include: "CD", "12\" Vinyl", "7\" Vinyl",
     * "Cassette", "Digital Media", "DVD-Video", etc.
     */
    private fun inferMediaTypeFromFormat(format: String): MediaType {
        val f = format.lowercase()
        return when {
            f.contains("vinyl") -> MediaType.VINYL
            f.contains("cassette") -> MediaType.CASSETTE
            f.contains("dvd") -> MediaType.DVD
            else -> MediaType.CD  // CD, Digital Media, and anything else
        }
    }

    private suspend fun lookupViaGoogleBooks(isbn: String): BookLookupResult? {
        return try {
            val response = googleBooksApi.searchByIsbn(
                query = "isbn:$isbn",
                apiKey = BuildConfig.GOOGLE_BOOKS_API_KEY
            )
            val info = response.items?.firstOrNull()?.volumeInfo ?: return null
            volumeInfoToResult(info, isbn)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Google Books lookup failed for $isbn", e)
            null
        }
    }

    /**
     * Google Books again, this time with the ISBN as a plain search term.
     *
     * Google's structured `isbn:` index is noticeably thinner than its
     * full-text index, so a volume that the first query misses is often still
     * reachable this way.
     *
     * Free text will happily return unrelated books for an unmatched number,
     * so a candidate is only accepted when its own industry identifiers list
     * the ISBN that was asked for.
     */
    private suspend fun lookupViaGoogleBooksFreeText(isbn: String): BookLookupResult? {
        return try {
            val response = googleBooksApi.searchBooks(
                query = isbn,
                maxResults = 5,
                apiKey = BuildConfig.GOOGLE_BOOKS_API_KEY
            )
            val target = normalizeIsbn(isbn)
            val info = response.items
                ?.map { it.volumeInfo }
                ?.firstOrNull { vi ->
                    vi.industryIdentifiers?.any { normalizeIsbn(it.identifier) == target } == true
                } ?: return null

            volumeInfoToResult(info, isbn)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Google Books free-text lookup failed for $isbn", e)
            null
        }
    }

    /** Shared mapping for both Google Books query styles. */
    private fun volumeInfoToResult(info: VolumeInfo, isbn: String): BookLookupResult =
        BookLookupResult(
            isbn = isbn,
            title = info.title,
            subtitle = info.subtitle ?: "",
            authors = info.authors?.joinToString(", ") ?: "",
            publisher = info.publisher ?: "",
            editor = "",
            publishedYear = info.publishedDate?.take(4) ?: "",
            pageCount = info.pageCount ?: 0,
            isFiction = genreClassifier.isFiction(info),
            genres = genreClassifier.classify(info),
            coverUrl = info.imageLinks?.thumbnail?.replace("http://", "https://") ?: "",
            description = info.description ?: ""
        )

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

            val genres = subjectsToGenres(subjects)
            val isFiction = inferFictionFromSubjects(subjects)

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

    /**
     * Last-resort ISBN lookup via the Open Library search index.
     *
     * Reached only when both Google Books and the Open Library edition endpoint
     * come up empty. The search index carries ISBNs at the *work* level, so it
     * still matches editions that have no /isbn/{isbn}.json record of their own.
     *
     * Because search is fuzzy and will happily return a loosely-related title
     * rather than nothing, the returned doc is only accepted if it actually
     * lists the ISBN we asked for.
     */
    private suspend fun lookupViaOpenLibrarySearch(isbn: String): BookLookupResult? {
        return try {
            val response = openLibraryApi.search(query = "isbn:$isbn")

            val doc = response.docs?.firstOrNull { doc ->
                doc.isbn?.any { normalizeIsbn(it) == normalizeIsbn(isbn) } == true
            } ?: return null

            val subjects = doc.subjects ?: emptyList()

            BookLookupResult(
                isbn = isbn,
                title = doc.title,
                subtitle = doc.subtitle ?: "",
                authors = doc.authorName?.joinToString(", ") ?: "",
                publisher = doc.publisher?.firstOrNull() ?: "",
                editor = "",
                publishedYear = doc.firstPublishYear?.toString() ?: "",
                pageCount = doc.pageCount ?: 0,
                isFiction = inferFictionFromSubjects(subjects),
                genres = subjectsToGenres(subjects),
                coverUrl = doc.coverId
                    ?.takeIf { it > 0 }
                    ?.let { "https://covers.openlibrary.org/b/id/$it-M.jpg" }
                    ?: "",
                description = ""
            )
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Open Library search lookup failed for $isbn", e)
            null
        }
    }

    /**
     * Open Library's Books API — a separate index from the edition endpoint,
     * which resolves some ISBNs that /isbn/{isbn}.json has no record for.
     *
     * Authors, publishers and subjects come back already named, so unlike
     * [lookupViaOpenLibrary] this needs no follow-up author or work requests.
     */
    private suspend fun lookupViaOpenLibraryApiBooks(isbn: String): BookLookupResult? {
        return try {
            val bibkey = "ISBN:$isbn"
            val book = openLibraryApi.getByBibkeys(bibkeys = bibkey)[bibkey] ?: return null

            val subjects = book.subjects?.map { it.name }.orEmpty()

            BookLookupResult(
                isbn = isbn,
                title = book.title,
                subtitle = book.subtitle ?: "",
                authors = book.authors?.joinToString(", ") { it.name } ?: "",
                publisher = book.publishers?.firstOrNull()?.name ?: "",
                editor = "",
                publishedYear = extractYear(book.publishDate),
                pageCount = book.numberOfPages ?: 0,
                isFiction = inferFictionFromSubjects(subjects),
                genres = subjectsToGenres(subjects),
                coverUrl = (book.cover?.medium ?: book.cover?.large ?: book.cover?.small)
                    ?.replace("http://", "https://").orEmpty(),
                description = book.excerpts?.firstNotNullOfOrNull { it.text }.orEmpty()
            )
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Open Library Books API lookup failed for $isbn", e)
            null
        }
    }

    /**
     * K10plus union catalogue over SRU, returning MARCXML.
     *
     * The last link in the chain and the only one backed by a different corpus
     * than Google Books and Open Library, so it is the step that can rescue a
     * title neither of those has ever indexed.
     *
     * A matched record may describe a different manifestation than the copy in
     * hand — an ebook edition of the same work, say — so the scanned ISBN is
     * kept rather than replaced with the record's own 020.
     */
    private suspend fun lookupViaK10plus(isbn: String): BookLookupResult? {
        return try {
            val xml = k10plusApi.search(query = "pica.isb=$isbn").string()
            val record = MarcXmlParser.parseFirstRecord(xml) ?: return null
            marcRecordToResult(record, isbn)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "K10plus lookup failed for $isbn", e)
            null
        }
    }

    /**
     * Maps the MARC21 fields the app displays:
     *   245 title/subtitle, 100+700 authors, 264 (else 260) publisher and date,
     *   300 extent, 520 summary, 650 subject headings.
     */
    private fun marcRecordToResult(record: MarcRecord, isbn: String): BookLookupResult? {
        val title = record.first("245", "a")?.let { stripMarcPunctuation(it) } ?: return null

        val authors = buildList {
            record.first("100", "a")?.let { add(it) }
            addAll(record.all("700", "a"))
        }.map { stripMarcPunctuation(it) }.distinct()

        val publisher = record.firstPreferringInd2("264", "b", "1")
            ?: record.first("260", "b")

        val rawDate = record.firstPreferringInd2("264", "c", "1")
            ?: record.first("260", "c")

        val subjects = record.all("650", "a").map { stripMarcPunctuation(it) }

        return BookLookupResult(
            isbn = isbn,
            title = title,
            subtitle = record.first("245", "b")?.let { stripMarcPunctuation(it) } ?: "",
            authors = authors.joinToString(", "),
            publisher = publisher?.let { stripMarcPunctuation(it) } ?: "",
            editor = "",
            publishedYear = extractYear(rawDate),
            pageCount = extractPageCount(record.first("300", "a")),
            isFiction = inferFictionFromSubjects(subjects),
            genres = subjectsToGenres(subjects),
            coverUrl = "",  // K10plus carries no cover art
            description = record.first("520", "a").orEmpty()
        )
    }

    /**
     * MARC values carry ISBD punctuation that marks the *next* subfield —
     * "Introduction to algorithms /" or "Cambridge :". None of it belongs on
     * screen.
     */
    private fun stripMarcPunctuation(value: String): String =
        value.trim().trimEnd(' ', '/', ':', ';', ',', '=').trim()

    /** Pulls a 4-digit year out of strings like "[2009]", "c2009" or "2009-". */
    private fun extractYear(raw: String?): String =
        raw?.let { Regex("\\d{4}").find(it)?.value }.orEmpty()

    /**
     * MARC 300$a is prose: "1 Online-Ressource (xix, 1292 Seiten)", "328 p.".
     * The largest number in it is the page count; the first one often is not.
     */
    private fun extractPageCount(extent: String?): Int {
        if (extent == null) return 0
        return Regex("\\d+").findAll(extent)
            .mapNotNull { it.value.toIntOrNull() }
            .maxOrNull() ?: 0
    }

    /**
     * Strips formatting so ISBNs from different sources compare equal.
     * Open Library returns them variously as "9780140328721" or "0-14-032872-X".
     */
    private fun normalizeIsbn(isbn: String): String =
        isbn.filter { it.isLetterOrDigit() }.uppercase()

    /** Maps Open Library subjects to display genres (top 5, capitalized). */
    private fun subjectsToGenres(subjects: List<String>): List<String> =
        subjects.take(5).map { it.replaceFirstChar { c -> c.uppercase() } }

    /** Infers fiction status from Open Library subjects. */
    private fun inferFictionFromSubjects(subjects: List<String>): Boolean {
        val lower = subjects.map { it.lowercase() }
        return lower.any { it.contains("fiction") } &&
                !lower.any { it.contains("non-fiction") || it.contains("nonfiction") }
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

    // ── Not-found scans ──

    fun getNotFoundScans(userId: String): Flow<List<NotFoundScanEntity>> =
        notFoundScanDao.getByUser(userId)

    suspend fun saveNotFoundScan(userId: String, barcode: String, mediaType: MediaType) {
        notFoundScanDao.insert(
            NotFoundScanEntity(
                userId = userId,
                barcode = barcode,
                mediaType = mediaType.name
            )
        )
    }

    suspend fun removeNotFoundScan(id: Long) {
        notFoundScanDao.deleteById(id)
    }

    suspend fun removeNotFoundScanByBarcode(userId: String, barcode: String) {
        notFoundScanDao.deleteByBarcode(userId, barcode)
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
