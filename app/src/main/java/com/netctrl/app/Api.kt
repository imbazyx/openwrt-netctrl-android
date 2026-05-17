package com.netctrl.app

import com.google.gson.annotations.SerializedName
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

data class OWMAgent(
    val agent_id: String,
    val agent_name: String,
    val online: Boolean,
    val last_seen_secs: Long?,
    val cpu_load: Double?,
    val ram_usage: Long?,   // MB
    val ram_total: Long?,   // MB
    val wifi_clients: Int? = null
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

data class MetricPoint(
    @SerializedName("load1") val load1: Double?,
    @SerializedName("load5") val load5: Double?,
    @SerializedName("load15") val load15: Double?,
    @SerializedName("mem_free") val memFree: Long?,
    @SerializedName("mem_total") val memTotal: Long?,
    @SerializedName("temperature") val temperature: Double?,
    @SerializedName("wifi_clients") val wifiClients: Int?,
    @SerializedName("wan_rx") val wanRx: Long?,
    @SerializedName("wan_tx") val wanTx: Long?,
    @SerializedName("uptime") val uptime: Double?,
    @SerializedName("timestamp") val timestamp: Long?
)

data class Metric(
    val timestamp: Long,
    val uptime: Double,
    @SerializedName(value = "load1", alternate = ["cpu_load"]) val load1: Double,
    @SerializedName(value = "load5", alternate = ["load_5"]) val load5: Double,
    @SerializedName(value = "load15", alternate = ["load_15"]) val load15: Double,
    val mem_free: Long,
    val mem_total: Long?,
    val temperature: Float?,
    val wifi_clients: Int?,
    @SerializedName(value = "wan_rx", alternate = ["wan_rx_bytes"]) val wan_rx: Long?,
    @SerializedName(value = "wan_tx", alternate = ["wan_tx_bytes"]) val wan_tx: Long?
)

// Объединённая модель для UI
data class AgentFull(
    val agent_id: String,
    val online: Boolean,
    val last_seen_secs: Long?,
    val display_name: String?,
    val address: String?,
    val local_ip: String?,
    val internal_ip: String? = null, // P2P IP для будущей интеграции с H3363T-нодой
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

// ─── H3363T Models ───

data class H3363tNodeStatus(
    val pubkey: String,
    val trust_level: Int,
    val peer_count: Int,
    val feed_size: Long,
    val uptime_sec: Long,
    val osiis_active: Boolean,
    val last_sync: Long,
    val updated_at: String
)

data class H3363tEvent(
    val event_type: String,
    val node_pubkey: String,
    val payload: Map<String, Any?>,
    val timestamp: Long
)

data class H3363tCommandRequest(
    val command: String,
    val port: Int? = null,
    val rule: String? = null,
    val config: Map<String, Any?>? = null
)

data class H3363tCommandResponse(
    val success: Boolean,
    val message: String,
    val data: Map<String, Any?>? = null
)

data class AdminInfo(
    val username: String,
    val role: String
)

data class CreateAdminRequest(
    val username: String,
    val password: String,
    val role: String = "admin"
)

data class ChangePasswordRequest(val new_password: String)

data class CreateAgentRequest(
    val agent_id: String? = null,
    val display_name: String? = null,
    val local_ip: String? = null,
    val luci_url: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val description: String? = null,
    val address: String? = null,
    val ssh_host: String? = null,
    val ssh_port: Int? = null,
    val ssh_user: String? = null,
    val ssh_password: String? = null
)

data class AgentDetailMetrics(
    val timestamp: Long?,
    @SerializedName("cpu_load") val cpu_load: Double?,
    @SerializedName("ram_usage") val ram_usage: Long?,
    @SerializedName("ram_total") val ram_total: Long?,
    val uptime: Long?,
    @SerializedName("wifi_clients") val wifi_clients: Int?,
    @SerializedName("wan_rx_bytes") val wan_rx_bytes: Long?,
    @SerializedName("wan_tx_bytes") val wan_tx_bytes: Long?,
    @SerializedName("node_peer_count") val node_peer_count: Int?,
    @SerializedName("node_status") val node_status: String?,
    @SerializedName("temperature") val temperature: Double? = null
)

data class AgentDetailData(
    val agent_id: String,
    val display_name: String?,
    val local_ip: String?,
    val internal_ip: String? = null,
    val luci_url: String?,
    val lat: Double?,
    val lng: Double?,
    val description: String?,
    val address: String?,
    val online: Boolean,
    val last_seen_secs: Long?,
    val metrics: AgentDetailMetrics?
)

interface NetCtrlApi {
    @GET("health")
    suspend fun health(): Map<String, String>

    @POST("auth/login")
    suspend fun login(@Body req: LoginRequest): LoginResponse

    @GET("agents")
    suspend fun agents(@Header("Authorization") bearer: String): ApiResponse<List<OWMAgent>>

    @GET("configs")
    suspend fun configs(@Header("Authorization") bearer: String): ApiResponse<List<RouterConfig>>

    @GET("metrics/{agent_id}")
    suspend fun metrics(
        @Path("agent_id") agentId: String,
        @Header("Authorization") bearer: String,
        @Query("limit") limit: Int = 1
    ): ApiResponse<List<Metric>>

    // ─── H3363T Endpoints ───

    @GET("api/v1/h3363t/status")
    suspend fun h3363tStatus(
        @Header("Authorization") bearer: String
    ): ApiResponse<List<H3363tNodeStatus>>

    @GET("api/v1/h3363t/status/{pubkey}")
    suspend fun h3363tNodeStatus(
        @Path("pubkey") pubkey: String,
        @Header("Authorization") bearer: String
    ): ApiResponse<H3363tNodeStatus>

    @POST("api/v1/h3363t/command")
    suspend fun h3363tCommand(
        @Header("Authorization") bearer: String,
        @Body req: H3363tCommandRequest
    ): H3363tCommandResponse

    @GET("api/v1/h3363t/events")
    suspend fun h3363tEvents(
        @Header("Authorization") bearer: String
    ): ApiResponse<List<H3363tEvent>>

    @GET("api/v1/h3363t/mapping")
    suspend fun h3363tMapping(
        @Header("Authorization") bearer: String
    ): ApiResponse<List<Map<String, String>>>

    @GET("api/v1/admins")
    suspend fun listAdmins(
        @Header("Authorization") bearer: String
    ): ApiResponse<List<AdminInfo>>

    @POST("api/v1/admins")
    suspend fun createAdmin(
        @Header("Authorization") bearer: String,
        @Body req: CreateAdminRequest
    ): ApiResponse<Unit?>

    @DELETE("api/v1/admins/{username}")
    suspend fun deleteAdmin(
        @Header("Authorization") bearer: String,
        @Path("username") username: String
    ): ApiResponse<Unit?>

    @PUT("api/v1/admins/me/password")
    suspend fun changePassword(
        @Header("Authorization") bearer: String,
        @Body req: ChangePasswordRequest
    ): ApiResponse<Unit?>

    @POST("api/v1/agents")
    suspend fun createAgent(
        @Header("Authorization") bearer: String,
        @Body req: CreateAgentRequest
    ): ApiResponse<Map<String, String>>

    @DELETE("api/v1/agents/{id}")
    suspend fun deleteAgent(
        @Header("Authorization") bearer: String,
        @Path("id") id: String
    ): ApiResponse<Unit?>

    @GET("api/v1/agents/{id}")
    suspend fun getAgent(
        @Path("id") id: String
    ): ApiResponse<AgentDetailData>

    @PUT("api/v1/agents/{id}")
    suspend fun updateAgent(
        @Header("Authorization") bearer: String,
        @Path("id") id: String,
        @Body req: UpdateAgentRequest
    ): ApiResponse<Unit?>
}

data class UpdateAgentRequest(
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("local_ip") val localIp: String? = null,
    @SerializedName("address") val address: String? = null
)

fun buildApi(baseUrl: String): NetCtrlApi {
    val logging = HttpLoggingInterceptor().apply { 
        level = HttpLoggingInterceptor.Level.BODY 
    }
    val client = OkHttpClient.Builder()
        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .addInterceptor(logging)
        .build()
    return Retrofit.Builder()
        .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(NetCtrlApi::class.java)
}
