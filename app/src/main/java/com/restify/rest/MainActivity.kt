package com.restify.rest

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.google.firebase.messaging.FirebaseMessaging
import com.restify.rest.ui.theme.RestTheme
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : ComponentActivity() {

    // Зберігаємо інстанс ViewModel на рівні Activity
    private lateinit var viewModel: MainViewModel

    // Додаємо SharedPreferences для збереження сесії
    private lateinit var sharedPrefs: SharedPreferences

    // Ресівер для автоматичного оновлення замовлень при отриманні пуша
    private val orderUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.restify.rest.UPDATE_ORDERS") {
                Log.d("MainActivity", "Отримано сигнал для оновлення замовлень")
                if (::viewModel.isInitialized) {
                    viewModel.fetchOrders()
                }
            }
        }
    }

    // Ланчер для запиту дозволу на сповіщення (Android 13+)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("MainActivity", "Дозвіл на сповіщення надано")
        } else {
            Log.w("MainActivity", "Дозвіл на сповіщення відхилено - пуші для чату не працюватимуть")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Запитуємо дозвіл на показ пуш-сповіщень
        askNotificationPermission()

        // Ініціалізуємо SharedPreferences
        sharedPrefs = getSharedPreferences("RestifyPrefs", Context.MODE_PRIVATE)

        // Оновлений CookieJar, який зберігає кукі в пам'ять пристрою
        val cookieJar = object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val editor = sharedPrefs.edit()
                val cookieStrings = cookies.map { "${it.name()}=${it.value()}" }.toSet()
                editor.putStringSet("cookies_${url.host()}", cookieStrings)

                // ИСПРАВЛЕНИЕ: Заменено apply() на commit(), чтобы куки сохранялись СИНХРОННО.
                // Это решает проблему отвала WebSocket сразу после логина.
                editor.commit()
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                val cookieStrings = sharedPrefs.getStringSet("cookies_${url.host()}", emptySet())
                return cookieStrings?.mapNotNull {
                    val parts = it.split("=", limit = 2)
                    if (parts.size == 2) {
                        Cookie.Builder()
                            .name(parts[0])
                            .value(parts[1])
                            .domain(url.host())
                            .build()
                    } else null
                } ?: emptyList()
            }
        }

        val client = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .build()

        // Ініціалізація основного API (ваш сервер)
        val retrofit = Retrofit.Builder()
            .baseUrl("https://restify.site")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val api = retrofit.create(RestPartnerApi::class.java)

        // Ініціалізація API для пошуку адрес (OpenStreetMap Nominatim)
        val nominatimRetrofit = Retrofit.Builder()
            .baseUrl("https://nominatim.openstreetmap.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val nominatimApi = nominatimRetrofit.create(NominatimApi::class.java)

        val viewModelFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                // Передаємо api, nominatimApi та sharedPrefs у ViewModel
                return MainViewModel(api, nominatimApi, sharedPrefs) as T
            }
        }

        // Ініціалізуємо ViewModel на рівні Activity
        viewModel = ViewModelProvider(this, viewModelFactory)[MainViewModel::class.java]

        // Відправляємо FCM токен, якщо користувач вже має сесію (наприклад, при повторному відкритті додатку)
        sendFcmTokenIfPossible()

        setContent {
            RestTheme {
                MainAppScreen(viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Реєструємо BroadcastReceiver для прослуховування сигналів оновлення
        val filter = IntentFilter("com.restify.rest.UPDATE_ORDERS")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(orderUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(orderUpdateReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        // Обов'язково відписуємося від ресівера, щоб уникнути витоку пам'яті
        unregisterReceiver(orderUpdateReceiver)
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                // Дозвіл вже є
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun sendFcmTokenIfPossible() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("MainActivity", "Не вдалося отримати FCM токен", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result
            Log.d("MainActivity", "Ваш FCM Token: $token")

            // Відправляємо на бекенд
            if (::viewModel.isInitialized) {
                viewModel.updateFcmToken(token)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

    // SharedPreferences для перевірки першого запуску
    val sharedPrefs = remember { context.getSharedPreferences("RestifyPrefs", Context.MODE_PRIVATE) }
    var isFirstLaunch by remember {
        mutableStateOf(sharedPrefs.getBoolean("is_first_launch", true))
    }

    // Визначаємо стартовий екран
    val startDestination = when {
        isFirstLaunch -> "onboarding"
        isLoggedIn -> "orders"
        else -> "login"
    }

    // Відслідковуємо поточний маршрут
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val hideBarsRoutes = listOf("login", "register", "onboarding")
    val shouldShowBars = currentRoute !in hideBarsRoutes

    // 1. Получаем доступ к жизненному циклу текущего экрана
    val lifecycleOwner = LocalLifecycleOwner.current

    // 2. Добавляем наблюдатель за жизненным циклом
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // Когда приложение разворачивается из фона
            if (event == Lifecycle.Event.ON_RESUME) {
                // Проверяем, что пользователь залогинен, чтобы не дергать API на экране входа
                if (viewModel.isLoggedIn.value) {
                    viewModel.fetchOrders()
                }
            }
        }

        // Подписываемся на события
        lifecycleOwner.lifecycle.addObserver(observer)

        // Отписываемся при уничтожении компонента, чтобы избежать утечек памяти
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Слухаємо дані профілю з ViewModel
    val restaurantName by viewModel.restaurantName.collectAsState()
    val restaurantAddress by viewModel.restaurantAddress.collectAsState()

    // Стан для відображення вікна підтримки
    var showSupportDialog by remember { mutableStateOf(false) }

    // Відслідковуємо стан авторизації для правильної навігації при виході
    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn && !isFirstLaunch) {
            navController.navigate("login") {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        topBar = {
            if (shouldShowBars) {
                if (isLoggedIn) {
                    // ПАНЕЛЬ ДЛЯ АВТОРИЗОВАНИХ КОРИСТУВАЧІВ (ІМ'Я + АДРЕСА З БАЗИ)
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = if (restaurantName.isNotEmpty()) restaurantName else "Завантаження...",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                if (restaurantAddress.isNotEmpty()) {
                                    Text(
                                        text = restaurantAddress,
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        },
                        actions = {
                            // Кнопка підтримки (виклик адміністратора)
                            IconButton(onClick = { showSupportDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Email, // Змінено на іконку конверта
                                    contentDescription = "Написати в підтримку"
                                )
                            }

                            // Кнопка виходу
                            IconButton(onClick = { viewModel.logout() }) {
                                Icon(
                                    imageVector = Icons.Default.ExitToApp,
                                    contentDescription = "Вийти"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = MaterialTheme.colorScheme.onPrimary,
                            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                } else {
                    // ПАНЕЛЬ ДЛЯ ЕКРАНУ ЛОГІНУ ТА РЕЄСТРАЦІЇ
                    TopAppBar(
                        title = { Text("Авторизація", fontWeight = FontWeight.Bold) },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        },
        bottomBar = {
            if (isLoggedIn && shouldShowBars) {
                NavigationBar {
                    val currentDestination = navBackStackEntry?.destination

                    NavigationBarItem(
                        icon = { Icon(Icons.Default.List, contentDescription = "Замовлення") },
                        label = { Text("Замовлення") },
                        selected = currentDestination?.hierarchy?.any { it.route == "orders" } == true,
                        onClick = {
                            navController.navigate("orders") {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Add, contentDescription = "Нова доставка") },
                        label = { Text("Нова доставка") },
                        selected = currentDestination?.hierarchy?.any { it.route == "create_order" } == true,
                        onClick = {
                            navController.navigate("create_order") {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            // ЕКРАН ОНБОРДИНГУ
            composable("onboarding") {
                OnboardingScreen(
                    onFinish = {
                        sharedPrefs.edit().putBoolean("is_first_launch", false).apply()
                        isFirstLaunch = false
                        val nextRoute = if (isLoggedIn) "orders" else "login"
                        navController.navigate(nextRoute) {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                )
            }

            composable("login") {
                LoginScreen(
                    viewModel = viewModel,
                    onLoginSuccess = {
                        // Після успішного логіну обов'язково отримуємо та відправляємо токен на сервер
                        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                viewModel.updateFcmToken(task.result)
                            }
                        }

                        navController.navigate("orders") {
                            popUpTo("login") { inclusive = true }
                        }
                    },
                    onNavigateToRegister = {
                        navController.navigate("register")
                    }
                )
            }
            composable("register") {
                RegisterScreen(
                    viewModel = viewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onRegisterSuccess = {
                        navController.navigate("login") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                )
            }
            composable("orders") {
                PartnerDashboardScreen(viewModel)
            }
            composable("create_order") {
                CreateOrderScreen(viewModel) {
                    navController.navigate("orders") {
                        popUpTo("orders") { inclusive = true }
                    }
                }
            }
        }
    }

    // Виклик діалогу підтримки, якщо стан true
    if (showSupportDialog) {
        SupportDialog(
            viewModel = viewModel, // Передаємо viewModel сюди
            onDismiss = { showSupportDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var messageText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Красива кругла іконка зверху
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Служба підтримки",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Опишіть вашу проблему або питання. Адміністратор отримає повідомлення та зв'яжеться з вами.",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Поле для вводу тексту
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    label = { Text("Ваше повідомлення") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    shape = RoundedCornerShape(16.dp),
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.LightGray
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Кнопка "Надіслати"
                Button(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            isSending = true
                            viewModel.submitFeedback(
                                message = messageText,
                                onSuccess = {
                                    isSending = false
                                    Toast.makeText(context, "Повідомлення відправлено адміну!", Toast.LENGTH_SHORT).show()
                                    onDismiss() // Закриваємо діалог після успіху
                                },
                                onError = { errorMsg ->
                                    isSending = false
                                    Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                                }
                            )
                        } else {
                            Toast.makeText(context, "Будь ласка, введіть текст", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isSending && messageText.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Send, contentDescription = "Надіслати", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Надіслати", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(onClick = onDismiss, enabled = !isSending) {
                    Text("Скасувати", color = Color.Gray, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}