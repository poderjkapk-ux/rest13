package com.restify.rest

import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainViewModel(
    private val api: RestPartnerApi,
    private val nominatimApi: NominatimApi, // Додано API для пошуку адрес
    private val prefs: SharedPreferences // Для збереження сесії
) : ViewModel() {

    // --- БАЗОВІ СТАНИ АВТОРИЗАЦІЇ ТА ЗАМОВЛЕНЬ ---
    private val _isLoggedIn = MutableStateFlow(prefs.getBoolean("is_logged_in", false))
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn

    // --- СТАНИ ДЛЯ ПРОФІЛЮ (НАЗВА ТА АДРЕСА ЗАКЛАДУ) ---
    // Беремо з кешу при старті, якщо вони там є
    private val _restaurantName = MutableStateFlow(prefs.getString("rest_name", "") ?: "")
    val restaurantName: StateFlow<String> = _restaurantName.asStateFlow()

    private val _restaurantAddress = MutableStateFlow(prefs.getString("rest_address", "") ?: "")
    val restaurantAddress: StateFlow<String> = _restaurantAddress.asStateFlow()

    // --- СТАНИ ДЛЯ ДИНАМІЧНОЇ ВАРТОСТІ ДОСТАВКИ ---
    private val _minFee = MutableStateFlow(80.0)
    val minFee: StateFlow<Double> = _minFee.asStateFlow()

    private val _feeReason = MutableStateFlow("")
    val feeReason: StateFlow<String> = _feeReason.asStateFlow()

    private val _orders = MutableStateFlow<List<PartnerOrder>>(emptyList())
    val orders: StateFlow<List<PartnerOrder>> = _orders

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages

    private val _ratedOrders = MutableStateFlow<Set<Int>>(emptySet())
    val ratedOrders: StateFlow<Set<Int>> = _ratedOrders

    private val _unreadChats = MutableStateFlow<Set<Int>>(emptySet())
    val unreadChats: StateFlow<Set<Int>> = _unreadChats

    val isLoading = mutableStateOf(false)
    val errorMessage = mutableStateOf<String?>(null)

    private var pollingJob: Job? = null

    // --- НОВІ СТАНИ ДЛЯ РЕЄСТРАЦІЇ ТА ПОШУКУ АДРЕС ---
    val searchResults = MutableStateFlow<List<NominatimResult>>(emptyList())
    val verificationToken = mutableStateOf<String?>(null)
    val verificationLink = mutableStateOf<String?>(null)
    val isPhoneVerified = mutableStateOf(false)
    val verifiedPhone = mutableStateOf<String?>(null)

    private var verificationPollingJob: Job? = null

    init {
        // Якщо при запуску додатку ми вже авторизовані — одразу вантажимо дані та підключаємо WebSocket
        if (_isLoggedIn.value) {
            fetchPartnerProfile() // Завантажуємо дані профілю (назву і адресу)
            fetchMinFee()         // ЗАВАНТАЖУЄМО МІНІМАЛЬНУ ЦІНУ
            fetchOrders()
            connectWebSocket()
        }

        // --- СЛУХАЄМО WEBSOCKET ПОВІДОМЛЕННЯ В ФОНІ ---
        viewModelScope.launch {
            RetrofitClient.webSocketManager.messages.collect { messageJson ->
                try {
                    val json = JSONObject(messageJson)
                    val type = json.optString("type")

                    // ВИПРАВЛЕНО: розширено список статусів для миттєвого оновлення списку замовлень
                    if (type == "order_update" || type == "job_update" || type == "new_order" || type == "job_ready" || type == "status_update") {
                        Log.d("MainViewModel", "Отримано сигнал WebSocket ($type), оновлюємо замовлення")
                        fetchOrders()
                    } else if (type == "chat_message") {
                        val jobId = json.optInt("job_id")
                        // Позначаємо, що є нове повідомлення, та оновлюємо чат, якщо він відкритий
                        markChatAsUnread(jobId)
                        loadChatHistory(jobId)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // --- НОВЕ: СЛУХАЄМО ПОМИЛКИ АВТОРИЗАЦІЇ (ПРОТУХЛИЙ ТОКЕН) ---
        viewModelScope.launch {
            RetrofitClient.webSocketManager.authErrors.collect {
                Log.e("MainViewModel", "Виявлено помилку авторизації (401/403). Примусовий вихід.")
                // Робимо логаут, що очистить кукі, перерве всі підключення
                // і реактивно викине користувача на екран логіну завдяки Compose
                logout()
            }
        }
    }

    // --- ПІДКЛЮЧЕННЯ WEBSOCKET З КУКАМИ СЕСІЇ ---
    private fun connectWebSocket() {
        val cookies = prefs.getStringSet("cookies_restify.site", emptySet())
        val cookieString = cookies?.joinToString("; ") ?: ""
        if (cookieString.isNotEmpty()) {
            RetrofitClient.webSocketManager.connect(cookieString)
        }
    }

    // ==========================================================
    // ЛОГІКА ПРОФІЛЮ ТА МІНІМАЛЬНОЇ ЦІНИ
    // ==========================================================

    fun fetchPartnerProfile() {
        viewModelScope.launch {
            try {
                val response = api.getPartnerProfile()
                if (response.isSuccessful && response.body() != null) {
                    val profile = response.body()!!
                    _restaurantName.value = profile.name
                    _restaurantAddress.value = profile.address

                    // Зберігаємо локально, щоб при наступному вході не було затримки відображення
                    prefs.edit()
                        .putString("rest_name", profile.name)
                        .putString("rest_address", profile.address)
                        .apply()
                }
            } catch (e: Exception) {
                Log.e("Profile", "Помилка завантаження профілю: ${e.message}")
            }
        }
    }

    fun fetchMinFee() {
        viewModelScope.launch {
            try {
                val response = api.getMinFee()
                if (response.isSuccessful && response.body() != null) {
                    _minFee.value = response.body()?.minFee ?: 80.0
                    _feeReason.value = response.body()?.reason ?: ""
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error fetching min fee: ${e.message}")
            }
        }
    }

    // ==========================================================
    // ЛОГІКА РЕЄСТРАЦІЇ ТА ВЕРИФІКАЦІЇ TELEGRAM
    // ==========================================================

    fun initVerification() {
        viewModelScope.launch {
            try {
                val response = api.initVerification()
                if (response.isSuccessful) {
                    val body = response.body()
                    verificationToken.value = body?.token
                    verificationLink.value = body?.link
                }
            } catch (e: Exception) {
                errorMessage.value = "Помилка ініціалізації Telegram"
            }
        }
    }

    fun startVerificationPolling() {
        if (verificationPollingJob?.isActive == true) return
        val token = verificationToken.value ?: return

        verificationPollingJob = viewModelScope.launch {
            while (!isPhoneVerified.value) {
                try {
                    val response = api.checkVerification(token)
                    if (response.isSuccessful && response.body()?.status == "verified") {
                        isPhoneVerified.value = true
                        verifiedPhone.value = response.body()?.phone
                        stopVerificationPolling()
                    }
                } catch (e: Exception) {
                    // Ігноруємо помилки мережі при опитуванні
                }
                delay(2000) // Запит кожні 2 секунди
            }
        }
    }

    fun stopVerificationPolling() {
        verificationPollingJob?.cancel()
        verificationPollingJob = null
    }

    fun register(name: String, address: String, email: String, pass: String, onSuccess: () -> Unit) {
        val token = verificationToken.value
        if (token == null || !isPhoneVerified.value) {
            errorMessage.value = "Спочатку підтвердіть телефон"
            return
        }
        viewModelScope.launch {
            isLoading.value = true
            try {
                val response = api.registerNative(name, address, email, pass, token)
                if (response.isSuccessful) {
                    errorMessage.value = null
                    onSuccess()
                } else {
                    errorMessage.value = "Помилка реєстрації. Можливо, Email зайнятий."
                }
            } catch (e: Exception) {
                errorMessage.value = "Помилка мережі"
            } finally {
                isLoading.value = false
            }
        }
    }

    // ==========================================================
    // ЛОГІКА ПОШУКУ АДРЕС (OPENSTREETMAP)
    // ==========================================================

    fun searchAddress(query: String) {
        if (query.length < 3) {
            searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            try {
                val response = nominatimApi.searchAddress(query)
                if (response.isSuccessful) {
                    searchResults.value = response.body() ?: emptyList()
                }
            } catch (e: Exception) {
                // Ігноруємо помилки мережі при автокомпліті
            }
        }
    }

    // ==========================================================
    // ЛОГІКА АВТОРИЗАЦІЇ ТА ВІДНОВЛЕННЯ ПАРОЛЯ
    // ==========================================================

    fun login(email: String, pass: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            try {
                val response = api.login(email, pass)
                if (response.isSuccessful) {
                    prefs.edit().putBoolean("is_logged_in", true).apply()
                    _isLoggedIn.value = true

                    fetchPartnerProfile() // ЗАВАНТАЖУЄМО ПРОФІЛЬ
                    fetchMinFee()         // ЗАВАНТАЖУЄМО МІНІМАЛЬНУ ЦІНУ
                    fetchOrders()
                    connectWebSocket()    // ПІДКЛЮЧАЄМО WEBSOCKET ПІСЛЯ ЛОГІНУ

                    onSuccess()
                } else {
                    errorMessage.value = "Невірний логін або пароль"
                }
            } catch (e: Exception) {
                errorMessage.value = "Помилка мережі: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    // --- НОВА ФУНКЦІЯ: Відновлення пароля ---
    fun resetPassword(email: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            isLoading.value = true
            try {
                val response = api.resetPassword(email)
                if (response.isSuccessful) {
                    onSuccess("Успіх! Новий пароль відправлено у ваш Telegram-бот.")
                } else {
                    // Парсимо JSON помилки з бекенда (напр., "Заклад з таким Email не знайдено.")
                    val errorStr = response.errorBody()?.string()
                    val detail = try {
                        JSONObject(errorStr ?: "").getString("detail")
                    } catch (e: Exception) {
                        "Невідома помилка"
                    }
                    onError("Помилка: $detail")
                }
            } catch (e: Exception) {
                onError("Помилка з'єднання з сервером")
            } finally {
                isLoading.value = false
            }
        }
    }

    fun logout() {
        prefs.edit().clear().apply()
        _isLoggedIn.value = false
        _restaurantName.value = ""     // Очищаємо назву при виході
        _restaurantAddress.value = ""  // Очищаємо адресу при виході
        _orders.value = emptyList()
        stopPolling()
        RetrofitClient.webSocketManager.disconnect() // ВІДКЛЮЧАЄМО WEBSOCKET
    }

    // ==========================================================
    // ЛОГІКА ЗАМОВЛЕНЬ ТА ФОНОВОГО ОНОВЛЕННЯ
    // ==========================================================

    fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (true) {
                try {
                    // ОНОВЛЮЄМО ЦІНУ РАЗОМ ІЗ ПОЛІНГОМ (НА ЛЬОТУ)
                    fetchMinFee()

                    val response = api.getOrders()
                    if (response.isSuccessful) {
                        _orders.value = response.body() ?: emptyList()
                    } else if (response.code() == 401 || response.code() == 403) {
                        logout()
                    }
                } catch (e: Exception) {
                    // Ігноруємо помилки мережі в фоні
                }
                // Збільшуємо інтервал поллінгу до 15 секунд, бо тепер ми покладаємося на WebSocket
                delay(15000)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun fetchOrders() {
        viewModelScope.launch {
            isLoading.value = true
            try {
                fetchMinFee() // ДОДАНО: Завжди оновлюємо ціну, якщо оновлюємо список

                val response = api.getOrders()
                if (response.isSuccessful) {
                    _orders.value = response.body() ?: emptyList()
                    errorMessage.value = null
                } else if (response.code() == 401 || response.code() == 403) {
                    logout()
                } else {
                    errorMessage.value = "Помилка завантаження: ${response.code()}"
                }
            } catch (e: Exception) {
                errorMessage.value = "Помилка мережі: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun markOrderAsReady(jobId: Int) {
        viewModelScope.launch {
            try {
                val response = api.markAsReady(jobId)
                if (response.isSuccessful) fetchOrders()
            } catch (e: Exception) {
                errorMessage.value = "Не вдалося оновити статус"
            }
        }
    }

    fun createNewOrder(request: OrderCreateRequest, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading.value = true
            try {
                val response = api.createOrder(
                    address = request.address,
                    street = request.street,
                    houseNumber = request.houseNumber,
                    apartment = request.apartment,
                    changeFrom = request.changeFrom,
                    customerName = request.customerName,
                    phone = request.phone,
                    price = request.price,
                    fee = request.fee,
                    comment = request.comment,
                    paymentType = request.paymentType,
                    isReturn = request.isReturnRequired,
                    prepTime = request.prepTime
                )
                if (response.isSuccessful) {
                    fetchOrders()
                    onSuccess()
                    errorMessage.value = null
                } else {
                    errorMessage.value = "Помилка при створенні замовлення"
                }
            } catch (e: Exception) {
                errorMessage.value = "Збій мережі"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun boostOrder(jobId: Int) {
        viewModelScope.launch {
            isLoading.value = true
            try {
                val response = api.boostOrder(jobId, 10.0)
                if (response.isSuccessful) {
                    fetchOrders()
                } else {
                    errorMessage.value = "Не вдалося підняти ціну"
                }
            } catch (e: Exception) {
                errorMessage.value = "Помилка мережі"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun confirmReturn(jobId: Int) {
        viewModelScope.launch {
            isLoading.value = true
            try {
                val response = api.confirmReturn(jobId)
                if (response.isSuccessful) {
                    fetchOrders()
                } else {
                    errorMessage.value = "Не вдалося підтвердити"
                }
            } catch (e: Exception) {
                errorMessage.value = "Помилка мережі"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun confirmBuyoutPaid(jobId: Int) {
        viewModelScope.launch {
            isLoading.value = true
            try {
                val response = api.confirmBuyoutPaid(jobId)
                if (response.isSuccessful) {
                    fetchOrders()
                } else {
                    errorMessage.value = "Не вдалося підтвердити оплату"
                }
            } catch (e: Exception) {
                errorMessage.value = "Помилка мережі"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun cancelOrder(jobId: Int) {
        viewModelScope.launch {
            isLoading.value = true
            try {
                val response = api.cancelOrder(jobId)
                if (response.isSuccessful) {
                    fetchOrders()
                } else {
                    errorMessage.value = "Не вдалося скасувати замовлення"
                }
            } catch (e: Exception) {
                errorMessage.value = "Помилка мережі"
            } finally {
                isLoading.value = false
            }
        }
    }

    // ==========================================================
    // ЛОГІКА ЧАТУ
    // ==========================================================

    fun markChatAsUnread(jobId: Int) {
        _unreadChats.value = _unreadChats.value + jobId
    }

    fun markChatAsRead(jobId: Int) {
        _unreadChats.value = _unreadChats.value - jobId
    }

    fun loadChatHistory(jobId: Int) {
        markChatAsRead(jobId)
        viewModelScope.launch {
            try {
                val response = api.getChatHistory(jobId)
                if (response.isSuccessful) {
                    _chatMessages.value = response.body() ?: emptyList()
                }
            } catch (e: Exception) {
                errorMessage.value = "Помилка завантаження чату"
            }
        }
    }

    fun clearChat() {
        _chatMessages.value = emptyList()
    }

    fun sendMessage(jobId: Int, message: String) {
        viewModelScope.launch {
            try {
                val response = api.sendChatMessage(jobId, message)
                if (response.isSuccessful) loadChatHistory(jobId)
                else errorMessage.value = "Не вдалося відправити повідомлення"
            } catch (e: Exception) {
                errorMessage.value = "Помилка мережі при відправці"
            }
        }
    }

    // ==========================================================
    // ЛОГІКА РЕЙТИНГУ ТА ТРЕКІНГУ
    // ==========================================================

    fun rateCourier(jobId: Int, rating: Int, review: String) {
        viewModelScope.launch {
            try {
                val response = api.rateCourier(jobId, rating, review)
                if (response.isSuccessful) {
                    // Успіх: локально ховаємо кнопку
                    _ratedOrders.value = _ratedOrders.value + jobId
                    fetchOrders()
                } else {
                    // Читаємо повідомлення про помилку від сервера
                    val errorStr = response.errorBody()?.string()
                    val serverMessage = try {
                        org.json.JSONObject(errorStr ?: "").getString("message")
                    } catch (e: Exception) {
                        "Помилка при відправці оцінки"
                    }

                    errorMessage.value = serverMessage

                    // Якщо сервер повернув 400 (відгук вже існує) — примусово ховаємо кнопку
                    if (response.code() == 400) {
                        _ratedOrders.value = _ratedOrders.value + jobId
                    }
                }
            } catch (e: Exception) {
                errorMessage.value = "Помилка мережі"
            }
        }
    }

    fun trackCourier(jobId: Int, onSuccess: (lat: Double, lon: Double) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val response = api.trackCourier(jobId)
                if (response.isSuccessful) {
                    val trackData = response.body()
                    if (trackData != null && trackData.status == "ok" && trackData.lat != null && trackData.lon != null) {
                        onSuccess(trackData.lat, trackData.lon)
                    } else {
                        onError("Кур'єр ще не призначений або координати недоступні")
                    }
                } else onError("Помилка сервера")
            } catch (e: Exception) {
                onError("Помилка мережі")
            }
        }
    }

    // ==========================================================
    // ЛОГІКА PUSH-СПОВІЩЕНЬ
    // ==========================================================

    fun updateFcmToken(token: String) {
        viewModelScope.launch {
            try {
                val response = api.sendFcmToken(token)
                if (response.isSuccessful) Log.d("FCM_TOKEN", "Токен успішно оновлено")
            } catch (e: Exception) {
                Log.e("FCM_TOKEN", "Помилка відправки токена: ${e.message}")
            }
        }
    }

    // ==========================================================
    // ЛОГІКА ПІДТРИМКИ (ЗВОРОТНИЙ ЗВ'ЯЗОК)
    // ==========================================================

    fun submitFeedback(message: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            isLoading.value = true
            try {
                // Беремо назву закладу та адресу з кешу (якщо є)
                val restName = _restaurantName.value.takeIf { it.isNotBlank() } ?: "Невідомий заклад"
                val restAddress = _restaurantAddress.value.takeIf { it.isNotBlank() } ?: "Невідома адреса"

                val response = api.submitFeedback(
                    role = "Заклад",
                    name = restName,
                    // Оскільки ми не зберігаємо телефон в кеші додатку, передамо адресу,
                    // щоб адміністратор бачив, з якого закладу пишуть
                    phone = restAddress,
                    message = message
                )

                if (response.isSuccessful) {
                    onSuccess()
                } else {
                    onError("Помилка відправки. Спробуйте пізніше.")
                }
            } catch (e: Exception) {
                onError("Помилка мережі: ${e.message}")
            } finally {
                isLoading.value = false
            }
        }
    }
}