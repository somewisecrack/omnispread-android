package com.example.omnispread.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {
    @POST("scan")
    suspend fun startScan(@Body request: ScanRequest): ScanStartResponse

    @GET("results/{taskId}")
    suspend fun getResults(@Path("taskId") taskId: String): TaskResult

    @POST("backtest")
    suspend fun runBacktest(@Body request: BacktestRequest): BacktestResult
}
