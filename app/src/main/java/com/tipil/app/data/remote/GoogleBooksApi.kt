package com.tipil.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface GoogleBooksApi {

    @GET("volumes")
    suspend fun searchByIsbn(
        @Query("q") query: String,
        @Query("key") apiKey: String
    ): GoogleBooksResponse

    @GET("volumes")
    suspend fun searchBooks(
        @Query("q") query: String,
        @Query("maxResults") maxResults: Int = 10,
        @Query("key") apiKey: String
    ): GoogleBooksResponse
}
