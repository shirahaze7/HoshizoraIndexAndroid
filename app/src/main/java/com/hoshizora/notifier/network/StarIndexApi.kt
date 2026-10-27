package com.hoshizora.notifier.network

import retrofit2.http.GET
import retrofit2.http.Query

interface StarIndexApi {

    @GET("get-star-index")
    suspend fun getStarIndex(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("alt") alt: Double
    ): StarIndexResponse
}
