package com.tipil.app.data.remote

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * K10plus union catalogue via SRU (Search/Retrieve via URL).
 * Base URL: https://sru.k10plus.de/
 *
 * Free and keyless. Aggregates the German union catalogues plus a large body
 * of international holdings, so it reaches many titles that Google Books and
 * Open Library have no record of — particularly non-English and academic
 * imprints.
 *
 * Responses are MARCXML rather than JSON. The body is taken raw as a
 * [ResponseBody] and handed to [MarcXmlParser]; Retrofit serves ResponseBody
 * through its built-in converters, so no XML converter dependency is needed.
 *
 * The [query] uses CQL against PICA indexes — "pica.isb=<isbn>" for ISBN.
 */
interface K10plusApi {

    @GET("gvk")
    suspend fun search(
        @Query("query") query: String,
        @Query("maximumRecords") maximumRecords: Int = 1,
        @Query("version") version: String = "1.1",
        @Query("operation") operation: String = "searchRetrieve",
        @Query("recordSchema") recordSchema: String = "marcxml"
    ): ResponseBody
}
