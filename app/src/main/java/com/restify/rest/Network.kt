package com.restify.rest

import android.util.Log
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

// --- МОДЕЛІ ДЛЯ АВТОРИЗАЦІЇ ТА РЕЄСТРАЦІЇ ---

data class VerifInitResp(
    val token: String,
    val link: String
)

data class VerifCheckResp(
    val status: String,
    val phone: String?
)

// --- МОДЕЛІ ДЛЯ ПОШУКУ АДРЕСИ (OpenStreetMap) ---

data class NominatimResult(
    val display_name: String,
    val lat: String,
    val lon: String,
    val address: NominatimAddress?
)

data class NominatimAddress(
    val road: String?,
    val pedestrian: String?,
    val hamlet: String?,
    @SerializedName("house_number") val houseNumber: String?,
    val city: String?,
    val town: String?,
    val village: String?
)

// Модели данных для ресторана
data class PartnerOrder(
    val id: Int,
    val status: String,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("accepted_at") val acceptedAt: String?,
    @SerializedName("arrived_at") val arrivedAt: String?,
    @SerializedName("picked_up_at") val pickedUpAt: String?,
    @SerializedName("delivered_at") val deliveredAt: String?,
    @SerializedName("dropoff_address") val address: String,
    @SerializedName("customer_name") val customerName: String?,
    @SerializedName("order_price") val orderPrice: Double,
    @SerializedName("delivery_fee") val deliveryFee: Double,
    @SerializedName("payment_type") val paymentType: String,
    @SerializedName("is_ready") val isReady: Boolean,
    @SerializedName("is_return_required") val isReturnRequired: Boolean,
    @SerializedName("is_rated") val isRatedBackend: Boolean? = false,
    val courier: CourierInfo?
)

data class CourierInfo(
    val name: String,
    val phone: String,
    val rating: Double
)

// --- ДОДАНО МОДЕЛЬ ДЛЯ АКТИВНИХ КУРЬЄРІВ ---
data class ActiveCourier(
    val id: Int,
    val name: String,
    val phone: String
)

data class OrderCreateRequest(
    val address: String,
    val street: String?,        // <-- ДОДАНО ПОЛЕ
    val houseNumber: String?,   // <-- ДОДАНО ПОЛЕ
    val apartment: String?,     // <-- ДОДАНО ПОЛЕ
    val changeFrom: String?,    // <-- ДОДАНО ПОЛЕ
    val customerName: String,
    val phone: String,
    val price: Double,
    val fee: Double,
    val comment: String,
    val paymentType: String,
    val isReturnRequired: Boolean,
    val prepTime: Int, // <-- ДОДАНО ЧАС ПРИГОТУВАННЯ
    val targetCourierId: Int? = null // <-- ДОДАНО ПОЛЕ ДЛЯ ПЕРСОНАЛЬНОГО ЗАМОВЛЕННЯ
)

// --- МОДЕЛІ ДЛЯ ЧАТУ ТА ТРЕКІНГУ ---

data class ChatMessage(
    val role: String,
    val text: String,
    val time: String
)

data class TrackCourierResponse(
    val status: String,
    val lat: Double?,
    val lon: Double?,
    val name: String?,
    val phone: String?,
    @SerializedName("job_status") val jobStatus: String?
)

// --- НОВА МОДЕЛЬ ДЛЯ ПРОФІЛЮ ПАРТНЕРА ---
data class PartnerProfile(
    val name: String,
    val address: String
)

// --- НОВА МОДЕЛЬ ДЛЯ ДИНАМІЧНОЇ ЦІНИ ДОСТАВКИ ---
data class MinFeeResponse(
    val status: String,
    @SerializedName("min_fee") val minFee: Double,
    val reason: String
)

// Интерфейс API для заведения (Основний бекенд)
interface RestPartnerApi {

    // --- АВТОРИЗАЦІЯ ТА РЕЄСТРАЦІЯ ---

    @POST("/api/auth/init_verification")
    suspend fun initVerification(): Response<VerifInitResp>

    @GET("/api/auth/check_verification/{token}")
    suspend fun checkVerification(@Path("token") token: String): Response<VerifCheckResp>

    @FormUrlEncoded
    @POST("/api/partner/register_native")
    suspend fun registerNative(
        @Field("name") name: String,
        @Field("address") address: String,
        @Field("email") email: String,
        @Field("password") password: String,
        @Field("verification_token") token: String
    ): Response<Unit>

    @FormUrlEncoded
    @POST("/api/partner/login_native")
    suspend fun login(
        @Field("email") email: String,
        @Field("password") password: String
    ): Response<Unit>

    // --- НОВИЙ ЕНДПОІНТ: ВІДНОВЛЕННЯ ПАРОЛЯ ---
    @FormUrlEncoded
    @POST("/api/partner/reset_password")
    suspend fun resetPassword(
        @Field("email") email: String
    ): Response<Unit>

    // --- ПРОФІЛЬ ---
    @GET("/api/partner/profile_native")
    suspend fun getPartnerProfile(): Response<PartnerProfile>

    // --- НОВИЙ ЕНДПОІНТ: ДИНАМІЧНА ВАРТІСТЬ ДОСТАВКИ ---
    @GET("/api/partner/min_fee_native")
    suspend fun getMinFee(): Response<MinFeeResponse>

    // --- ЗАМОВЛЕННЯ ---

    @GET("/api/partner/orders_native")
    suspend fun getOrders(): Response<List<PartnerOrder>>

    // --- НОВИЙ ЕНДПОІНТ: АКТИВНІ КУРЬЄРИ ЗАКЛАДУ ---
    @GET("/api/partner/active_couriers")
    suspend fun getActiveCouriers(): Response<List<ActiveCourier>>

    @FormUrlEncoded
    @POST("/api/partner/create_order_native")
    suspend fun createOrder(
        @Field("dropoff_address") address: String,
        @Field("street") street: String?,
        @Field("house_number") houseNumber: String?,
        @Field("apartment") apartment: String?,
        @Field("change_from") changeFrom: String?,
        @Field("customer_name") customerName: String,
        @Field("customer_phone") phone: String,
        @Field("order_price") price: Double,
        @Field("delivery_fee") fee: Double,
        @Field("comment") comment: String,
        @Field("payment_type") paymentType: String,
        @Field("is_return_required") isReturn: Boolean,
        @Field("prep_time") prepTime: Int,
        @Field("target_courier_id") targetCourierId: Int? // <-- ДОДАНО ПАРАМЕТР
    ): Response<Unit>

    @FormUrlEncoded
    @POST("/api/partner/order_ready")
    suspend fun markAsReady(@Field("job_id") jobId: Int): Response<Unit>

    @FormUrlEncoded
    @POST("/api/partner/rate_courier")
    suspend fun rateCourier(
        @Field("job_id") jobId: Int,
        @Field("rating") rating: Int,
        @Field("review") review: String
    ): Response<Unit>

    // --- ЕНДПОІНТ ДЛЯ ПІДНЯТТЯ ЦІНИ ДОСТАВКИ ---
    @FormUrlEncoded
    @POST("/api/partner/boost_order")
    suspend fun boostOrder(
        @Field("job_id") jobId: Int,
        @Field("amount") amount: Double = 10.0
    ): Response<Unit>

    // --- ЕНДПОІНТ ДЛЯ ПІДТВЕРДЖЕННЯ ПОВЕРНЕННЯ КОШТІВ ---
    @FormUrlEncoded
    @POST("/api/partner/confirm_return")
    suspend fun confirmReturn(
        @Field("job_id") jobId: Int
    ): Response<Unit>

    // --- ЕНДПОІНТ ДЛЯ ПІДТВЕРДЖЕННЯ ОПЛАТИ ВИКУПУ КУР'ЄРОМ ---
    @FormUrlEncoded
    @POST("/api/partner/confirm_buyout_paid")
    suspend fun confirmBuyoutPaid(
        @Field("job_id") jobId: Int
    ): Response<Unit>

    // --- ЕНДПОІНТ ДЛЯ СКАСУВАННЯ ЗАМОВЛЕННЯ ---
    @FormUrlEncoded
    @POST("/api/partner/cancel_order")
    suspend fun cancelOrder(
        @Field("job_id") jobId: Int
    ): Response<Unit>

    // --- ЕНДПОІНТИ ДЛЯ ЧАТУ ТА ВІДСТЕЖЕННЯ ---

    @GET("/api/chat/history/{job_id}")
    suspend fun getChatHistory(
        @Path("job_id") jobId: Int
    ): Response<List<ChatMessage>>

    @FormUrlEncoded
    @POST("/api/chat/send")
    suspend fun sendChatMessage(
        @Field("job_id") jobId: Int,
        @Field("message") message: String,
        @Field("role") role: String = "partner"
    ): Response<Unit>

    @GET("/api/partner/track_courier/{job_id}")
    suspend fun trackCourier(
        @Path("job_id") jobId: Int
    ): Response<TrackCourierResponse>

    // --- ЕНДПОІНТ ДЛЯ ВІДПРАВКИ FCM ТОКЕНА ---
    @FormUrlEncoded
    @POST("/api/partner/fcm_token")
    suspend fun sendFcmToken(
        @Field("token") token: String
    ): Response<Unit>

    // --- ЕНДПОІНТ ДЛЯ ЗВОРОТНОГО ЗВ'ЯЗКУ (ПІДТРИМКА) ---
    @FormUrlEncoded
    @POST("/api/feedback")
    suspend fun submitFeedback(
        @Field("role") role: String,
        @Field("name") name: String,
        @Field("phone") phone: String,
        @Field("message") message: String
    ): Response<Unit>
}

// --- ОКРЕМИЙ ІНТЕРФЕЙС ДЛЯ OPENSTREETMAP ---
interface NominatimApi {
    @Headers("User-Agent: RestifyPartnerApp/1.0")
    @GET("search?format=json&accept-language=uk,ru&countrycodes=ua&addressdetails=1&viewbox=30.0,46.8,31.3,45.8&bounded=0&limit=5")
    suspend fun searchAddress(@Query("q") query: String): Response<List<NominatimResult>>
}


// ==========================================
// МЕНЕДЖЕР WEBSOCKET ДЛЯ ЗАКЛАДУ З ПІНГОМ ТА АВТО-РЕКОНЕКТОМ
// ==========================================

class PartnerWebSocketManager(private val client: OkHttpClient) {
    private var webSocket: WebSocket? = null

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val messages = _messages.asSharedFlow()

    // НОВЕ: Flow для сигналізації про протухлий токен
    private val _authErrors = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val authErrors = _authErrors.asSharedFlow()

    private var currentCookie: String? = null
    private var isIntentionallyClosed = false

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pingJob: Job? = null

    fun connect(cookie: String) {
        currentCookie = cookie
        isIntentionallyClosed = false
        startConnection()
    }

    // НОВЕ: Метод для виклику помилки авторизації з інших місць
    fun triggerAuthError() {
        _authErrors.tryEmit(Unit)
    }

    private fun startConnection() {
        if (webSocket != null) return
        val cookie = currentCookie ?: return

        val request = Request.Builder()
            .url("wss://restify.site/ws/partner")
            .addHeader("Cookie", cookie)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                Log.d("PartnerWebSocket", "Connected")
                startPingJob()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("PartnerWebSocket", "Message received: $text")
                if (text != "pong") {
                    _messages.tryEmit(text)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("PartnerWebSocket", "Closed: $code $reason")
                this@PartnerWebSocketManager.webSocket = null
                stopPingJob()

                // НОВЕ: Якщо бекенд закрив з кодом 1008 (Policy Violation)
                // або токен невалідний
                if (code == 1008 || code == 401 || code == 403) {
                    Log.e("PartnerWebSocket", "Auth error ($code). Stopping reconnects.")
                    triggerAuthError()
                    return // ВАЖЛИВО: Зупиняємо цикл реконектів
                }

                if (!isIntentionallyClosed) {
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                Log.e("PartnerWebSocket", "Error", t)
                this@PartnerWebSocketManager.webSocket = null
                stopPingJob()

                // ВИПРАВЛЕНО: Витягуємо код із захистом від null
                val httpCode = response?.code() ?: 0

                // Перевіряємо статус код при handshake
                if (httpCode == 401 || httpCode == 403) {
                    Log.e("PartnerWebSocket", "Handshake Auth Failed ($httpCode). Stopping reconnects.")
                    triggerAuthError()
                    return // ВАЖЛИВО: Зупиняємо цикл реконектів
                }

                if (!isIntentionallyClosed) {
                    scheduleReconnect()
                }
            }
        })
    }

    private fun startPingJob() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive) {
                delay(30000)
                sendPing()
            }
        }
    }

    private fun stopPingJob() {
        pingJob?.cancel()
        pingJob = null
    }

    private fun scheduleReconnect() {
        scope.launch {
            delay(5000)
            if (!isIntentionallyClosed && webSocket == null) {
                Log.d("PartnerWebSocket", "Attempting to reconnect...")
                startConnection()
            }
        }
    }

    fun sendPing() {
        webSocket?.send("ping")
    }

    fun disconnect() {
        isIntentionallyClosed = true
        stopPingJob()
        webSocket?.close(1000, "App closed/Logout")
        webSocket = null
        currentCookie = null
    }
}

// ==========================================
// КЛІЄНТ RETROFIT (Singleton)
// ==========================================

object RetrofitClient {
    private const val BASE_URL = "https://restify.site"

    // Ініціалізуємо webSocketManager відразу (але без клієнта поки що)
    lateinit var webSocketManager: PartnerWebSocketManager

    // НОВЕ: Глобальний перехоплювач помилок 401/403 для Retrofit API
    private val authInterceptor = okhttp3.Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        // Якщо будь-який API запит повертає 401 або 403, запалюємо подію
        if (response.code() == 401 || response.code() == 403) {
            if (::webSocketManager.isInitialized) {
                webSocketManager.triggerAuthError()
            }
        }
        response
    }

    private val okHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor(authInterceptor) // Додали перехоплювач сюди
        .build()

    init {
        // Прив'язуємо клієнт до WS менеджера
        webSocketManager = PartnerWebSocketManager(okHttpClient)
    }

    val apiService: RestPartnerApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RestPartnerApi::class.java)
    }

    val nominatimApi: NominatimApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://nominatim.openstreetmap.org/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NominatimApi::class.java)
    }
}