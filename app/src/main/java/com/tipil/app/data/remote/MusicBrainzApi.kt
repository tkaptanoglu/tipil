package com.tipil.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * MusicBrainz Web Service v2 (JSON).
 * Base URL: https://musicbrainz.org/ws/2/
 *
 * Rate limit: 1 request/second. A custom User-Agent is required.
 * Docs: https://musicbrainz.org/doc/MusicBrainz_API
 */
interface MusicBrainzApi {

    /** Search releases by UPC/EAN barcode. */
    @GET("release/")
    suspend fun searchByBarcode(
        @Query("query") query: String,
        @Query("fmt") fmt: String = "json",
        @Query("limit") limit: Int = 5
    ): MusicBrainzSearchResponse

    /** Lookup a release-group by ID to get genre tags. */
    @GET("release-group/{id}")
    suspend fun getReleaseGroup(
        @Path("id") id: String,
        @Query("inc") inc: String = "tags",
        @Query("fmt") fmt: String = "json"
    ): MbReleaseGroupDetail

    /** Search releases by free-text query (for recommendations). */
    @GET("release/")
    suspend fun searchReleases(
        @Query("query") query: String,
        @Query("fmt") fmt: String = "json",
        @Query("limit") limit: Int = 10
    ): MusicBrainzSearchResponse
}
