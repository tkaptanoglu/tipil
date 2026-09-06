package com.tipil.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Open Library API — free, no API key required.
 * Base URL: https://openlibrary.org/
 *
 * Used as a fallback when Google Books cannot find a book by ISBN.
 */
interface OpenLibraryApi {

    /** Look up a book by ISBN. Returns edition data. */
    @GET("isbn/{isbn}.json")
    suspend fun getByIsbn(@Path("isbn") isbn: String): OlEdition

    /**
     * The Books API — a different index from [getByIsbn], keyed by "bibkey".
     *
     * Resolves some ISBNs that /isbn/{isbn}.json 404s on, and returns authors,
     * publishers and subjects already denormalised, so no follow-up author or
     * work requests are needed.
     *
     * The response is a map keyed by the bibkey that was requested
     * (e.g. "ISBN:9780262033848"), empty when nothing matched.
     */
    @GET("api/books")
    suspend fun getByBibkeys(
        @Query("bibkeys") bibkeys: String,
        @Query("format") format: String = "json",
        @Query("jscmd") jscmd: String = "data"
    ): Map<String, OlApiBook>

    /** Look up a work to get subjects/description. */
    @GET("works/{workId}.json")
    suspend fun getWork(@Path("workId") workId: String): OlWork

    /** Look up an author by key (e.g. "/authors/OL1234A"). */
    @GET("{authorKey}.json")
    suspend fun getAuthor(@Path("authorKey", encoded = true) authorKey: String): OlAuthor

    /**
     * Full-text search across works.
     *
     * Used as the last-resort ISBN fallback: the search index matches ISBNs
     * that have no standalone edition record, so it finds books that
     * [getByIsbn] returns 404 for.
     */
    @GET("search.json")
    suspend fun search(
        @Query("q") query: String,
        @Query("limit") limit: Int = 5
    ): OlSearchResponse
}
