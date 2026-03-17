package com.netctrl.app

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

data class LoginRequest(val username: String, val password: String)
data class LoginResponse(val access_token: String)

data class AgentInfo(
    val agent_id: String,
    val online: Boolean,
    val last_seen_secs: Long?
)

data class RouterConfig(
    val agent_id: String,
    val display_name: String?,
    val local_ip: String?,
    val luci_url: String?,
    val lat: Double?,
    val lng: Double?,
    val description: String?,
    val address: String?
)

data class Metric(
    val timestamp: Long,
    val uptime: Double,
    val load1: Double,
    val load5: Double,
    val load15: Double,
    val mem_free: Long,
    val mem_total: Long?,
    val temperature: Float?,
    val wifi_clients: Int?,
    val wan_rx: Long?,
    val wan_tx: Long?
)

// Объединённая модель для UI
data class AgentFull(
    val agent_id: String,
    val online: Boolean,
    val last_seen_secs: Long?,
    val display_name: String?,
    val address: String?,
    val local_ip: String?,
    val luci_url: String?,
    val lat: Double?,
    val lng: Double?,
    val metric: Metric?
)

data class ApiResponse<T>(
    val success: Boolean,
    val message: String?,
    val data: T?
)

interface NetCtrlApi {
    @POST("auth/login")
    suspend fun login(@Body req: LoginRequest): LoginResponse

    @GET("agents")
    suspend fun agents(@Header("Authorization") bearer: String): ApiResponse<List<AgentInfo>>

    @GET("configs")
    suspend fun configs(@Header("Authorization") bearer: String): ApiResponse<List<RouterConfig>>

    @GET("metrics/{agent_id}")
    suspend fun metrics(
        @Path("agent_id") agentId: String,
        @Header("Authorization") bearer: String,
        @Query("limit") limit: Int = 1
    ): ApiResponse<List<Metric>>
}

fun buildApi(baseUrl: String): NetCtrlApi {
    val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()
    return Retrofit.Builder()
        .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(NetCtrlApi::class.java)
}
