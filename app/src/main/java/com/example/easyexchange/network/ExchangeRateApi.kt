package com.example.easyexchange.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface ExchangeRateApi {
    @GET("v6/{apiKey}/latest/{base}")
    suspend fun getLatestRates(
        @Path("apiKey") apiKey: String,
        @Path("base") base: String
    ): Response<ExchangeRateResponse>
}
