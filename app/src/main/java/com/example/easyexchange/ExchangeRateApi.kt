package com.example.easyexchange

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface ExchangeRateApi {
    @GET("v6/{apiKey}/latest/{base}")
    suspend fun getLatestRates(
        @Path("apiKey") apiKey: String,
        @Path("base") baseCurrency: String
    ): Response<ExchangeRateResponse>
}
