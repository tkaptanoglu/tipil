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

    /** Look up a work to get subjects/description. */
    @GET("works/{workId}.json")
    suspend fun getWork(@Path("workId") workId: String): OlWork

    /** Look up an author by key (e.g. "/authors/OL1234A"). */
    @GET("{authorKey}.json")
    suspend fun getAuthor(@Path("authorKey", encoded = true) authorKey: String): OlAuthor

    /** Search for books (used for author lookups). */
    @GET("search.json")
    suspend fun search(
        @Query("q") query: String,
        @Query("limit") limit: Int = 5
    ): OlSearchResponse
}
