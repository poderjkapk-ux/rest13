package com.restify.rest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.preference.PreferenceManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerDashboardScreen(viewModel: MainViewModel) {
    val orders by viewModel.orders.collectAsState()
    val chatHistory by viewModel.chatMessages.collectAsState()
    val ratedOrders by viewModel.ratedOrders.collectAsState(initial = emptySet())
    val unreadChats by viewModel.unreadChats.collectAsState() // Стан для непрочитаних повідомлень

    var chatOrderId by remember { mutableStateOf<Int?>(null) }
    var rateOrderId by remember { mutableStateOf<Int?>(null) }
    var mapCoordinates by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var selectedTab by remember { mutableStateOf(0) }

    // --- СТАН ДЛЯ ФІЛЬТРУ ЗА ДАТОЮ ---
    var selectedDateFilter by remember { mutableStateOf<LocalDate?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }

    val context = LocalContext.current

    val errorMessage by viewModel.errorMessage
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.errorMessage.value = null
        }
    }

    // --- ФОНОВЕ ОНОВЛЕННЯ ---
    DisposableEffect(Unit) {
        viewModel.startPolling()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                viewModel.fetchOrders()

                // Обробка міток чату з Push-сповіщень
                val isChat = intent?.getBooleanExtra("is_chat", false) ?: false
                val incomingJobId = intent?.getIntExtra("job_id", -1) ?: -1

                if (isChat && incomingJobId != -1) {
                    viewModel.markChatAsUnread(incomingJobId)

                    // ВИПРАВЛЕНО: Якщо чат відкрито, одразу завантажуємо нові повідомлення
                    if (chatOrderId == incomingJobId) {
                        viewModel.loadChatHistory(incomingJobId)
                    }
                }
            }
        }
        val filter = IntentFilter("com.restify.rest.UPDATE_ORDERS")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        onDispose {
            viewModel.stopPolling()
            context.unregisterReceiver(receiver)
        }
    }

    val activeOrders = orders.filter { it.status != "delivered" && it.status != "cancelled" }

    // Фільтруємо виконані замовлення за обраною датою
    val completedOrders = orders.filter { it.status == "delivered" || it.status == "cancelled" }
    val filteredCompletedOrders = completedOrders.filter { order ->
        if (selectedDateFilter == null) true
        else {
            // Витягуємо дату з createdAt формату "2023-10-27 14:30:00"
            val dateString = order.createdAt?.substringBefore(" ")
            dateString == selectedDateFilter.toString()
        }
    }

    val displayedOrders = if (selectedTab == 0) activeOrders else filteredCompletedOrders

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Замовлення",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                IconButton(onClick = { viewModel.fetchOrders() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Оновити", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Активні (${activeOrders.size})") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Виконані (${filteredCompletedOrders.size})") }
            )
        }

        // --- БЛОК ФІЛЬТРІВ ДЛЯ ІСТОРІЇ ---
        AnimatedVisibility(visible = selectedTab == 1) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    FilterChip(
                        selected = selectedDateFilter == null,
                        onClick = { selectedDateFilter = null },
                        label = { Text("За весь час") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedDateFilter == today,
                        onClick = { selectedDateFilter = today },
                        label = { Text("Сьогодні") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedDateFilter == today.minusDays(1),
                        onClick = { selectedDateFilter = today.minusDays(1) },
                        label = { Text("Вчора") }
                    )
                }
                item {
                    val isCustomDate = selectedDateFilter != null && selectedDateFilter != today && selectedDateFilter != today.minusDays(1)
                    FilterChip(
                        selected = isCustomDate,
                        onClick = { showDatePicker = true },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (isCustomDate)
                                        selectedDateFilter!!.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                                    else "Обрати дату..."
                                )
                            }
                        }
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (viewModel.isLoading.value && orders.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (displayedOrders.isEmpty()) {
                EmptyStateView()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(displayedOrders, key = { it.id }) { order ->
                        OrderCard(
                            order = order,
                            isRated = ratedOrders.contains(order.id),
                            hasUnreadChat = unreadChats.contains(order.id), // Передаємо мітку чату
                            onReadyClick = { viewModel.markOrderAsReady(order.id) },
                            onChatClick = {
                                chatOrderId = order.id
                                viewModel.loadChatHistory(order.id)
                            },
                            onTrackClick = {
                                viewModel.trackCourier(
                                    jobId = order.id,
                                    onSuccess = { lat, lon ->
                                        mapCoordinates = Pair(lat, lon)
                                    },
                                    onError = { errorMsg -> viewModel.errorMessage.value = errorMsg }
                                )
                            },
                            onRateClick = { rateOrderId = order.id },
                            onBoostClick = { viewModel.boostOrder(order.id) },
                            onConfirmReturnClick = { viewModel.confirmReturn(order.id) }, // Підтвердження повернення
                            onConfirmBuyoutPaidClick = { viewModel.confirmBuyoutPaid(order.id) }, // Підтвердження оплати викупу
                            onCancelClick = { viewModel.cancelOrder(order.id) } // Скасування замовлення
                        )
                    }
                }
            }
        }
    }

    // --- ДІАЛОГ ВИБОРУ ДАТИ ---
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDateFilter?.atStartOfDay(ZoneId.of("UTC"))?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDateFilter = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("ОК") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Скасувати") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (chatOrderId != null) {
        ChatDialog(
            orderId = chatOrderId!!,
            messages = chatHistory,
            onSendMessage = { msg -> viewModel.sendMessage(chatOrderId!!, msg) },
            onDismiss = {
                chatOrderId = null
                viewModel.clearChat()
            }
        )
    }

    if (rateOrderId != null) {
        RateCourierDialog(
            orderId = rateOrderId!!,
            onDismiss = { rateOrderId = null },
            onSubmit = { rating, review ->
                viewModel.rateCourier(rateOrderId!!, rating, review)
                rateOrderId = null
            }
        )
    }

    mapCoordinates?.let { (lat, lon) ->
        MapDialog(
            lat = lat,
            lon = lon,
            onDismiss = { mapCoordinates = null }
        )
    }
}

@Composable
fun EmptyStateView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Color.LightGray
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Замовлень поки немає",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Gray
        )
        Text(
            text = "Ви можете створити нове замовлення",
            fontSize = 14.sp,
            color = Color.LightGray,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun OrderCard(
    order: PartnerOrder,
    isRated: Boolean,
    hasUnreadChat: Boolean,
    onReadyClick: () -> Unit,
    onChatClick: () -> Unit,
    onTrackClick: () -> Unit,
    onRateClick: () -> Unit,
    onBoostClick: () -> Unit,
    onConfirmReturnClick: () -> Unit,
    onConfirmBuyoutPaidClick: () -> Unit,
    onCancelClick: () -> Unit // ДОДАНО
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // Стан для кнопок (інформативність)
    var isPaymentProcessing by remember { mutableStateOf(false) }
    var isCancelProcessing by remember { mutableStateOf(false) }

    // Скидаємо стан завантаження, якщо статус замовлення змінився (прийшла відповідь від сервера)
    LaunchedEffect(order.status) {
        isPaymentProcessing = false
        isCancelProcessing = false
    }

    // Выделяем заказ, если он в работе (курьер назначен)
    val isCourierAssigned = order.courier != null && order.status != "delivered" && order.status != "cancelled"

    // Анимация рамки для активного заказа
    val borderColor by animateColorAsState(
        targetValue = if (isCourierAssigned) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(500),
        label = "borderColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(300))
            .border(
                width = if (isCourierAssigned) 2.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (isCourierAssigned) 8.dp else 4.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isCourierAssigned) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Замовлення #${order.id}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                val (statusText, statusColor, bgColor) = when (order.status.lowercase()) {
                    "delivered" -> Triple("Доставлено", Color(0xFF2E7D32), Color(0xFFE8F5E9))
                    "cancelled" -> Triple("Скасовано", Color(0xFFC62828), Color(0xFFFFEBEE))
                    "pending" -> Triple("Очікує", Color(0xFFE65100), Color(0xFFFFF3E0))
                    "in_progress", "picked_up", "assigned", "arrived_pickup" -> Triple("В дорозі", Color(0xFF1565C0), Color(0xFFE3F2FD))
                    "returning" -> Triple("Повернення коштів", Color(0xFFD84315), Color(0xFFFFCCBC))
                    else -> Triple(order.status, Color.DarkGray, Color(0xFFF5F5F5))
                }

                Surface(
                    shape = CircleShape,
                    color = bgColor
                ) {
                    Text(
                        text = statusText.uppercase(),
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            InfoRow(icon = Icons.Default.LocationOn, text = order.address)
            InfoRow(icon = Icons.Default.ShoppingCart, text = "Сума: ${order.orderPrice} ₴ (Дост: ${order.deliveryFee} ₴)")

            // Вывод имени клиента если оно есть
            if (!order.customerName.isNullOrEmpty()) {
                InfoRow(icon = Icons.Default.Person, text = "Клієнт: ${order.customerName}")
            }

            val paymentIcon = when (order.paymentType) {
                "prepaid", "buyout_paid" -> Icons.Default.CheckCircle
                "buyout" -> Icons.Default.Info
                else -> Icons.Default.Info
            }
            val paymentText = when (order.paymentType) {
                "prepaid" -> "Оплачено онлайн"
                "buyout" -> "Викуп кур'єром"
                "buyout_paid" -> "Оплачено"
                "cash" -> "Готівка (Старе)"
                else -> order.paymentType
            }
            InfoRow(icon = paymentIcon, text = paymentText, iconTint = Color(0xFF4CAF50))

            AnimatedVisibility(visible = order.courier != null) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = order.courier?.name ?: "", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "${order.courier?.rating}", fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    // ДОДАЄМО ТАЙМЛАЙН
                    OrderTimeline(order = order)

                    if (order.status != "delivered" && order.status != "cancelled") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ActionIconButton(
                                icon = Icons.Default.Phone,
                                color = Color(0xFF4CAF50),
                                onClick = {
                                    val rawPhone = order.courier?.phone ?: ""
                                    val cleanPhone = when {
                                        rawPhone.startsWith("+38") -> rawPhone.substring(3)
                                        rawPhone.startsWith("38") -> rawPhone.substring(2)
                                        else -> rawPhone
                                    }
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanPhone"))
                                    context.startActivity(intent)
                                }
                            )
                            ActionIconButton(
                                icon = Icons.Default.Email,
                                color = Color(0xFF2196F3),
                                hasBadge = hasUnreadChat, // Встановлюємо бейдж
                                onClick = onChatClick
                            )
                            ActionIconButton(
                                icon = Icons.Default.Place,
                                color = Color(0xFFFF9800),
                                onClick = onTrackClick
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // НОВЫЙ БЛОК: Красивая анимация поиска и функциональные кнопки
            if (order.status == "pending") {
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()

                var isBoosting by remember { mutableStateOf(false) }

                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.95f else 1.0f,
                    animationSpec = tween(durationMillis = 150),
                    label = "buttonScale"
                )

                // Сброс состояния анимации загрузки
                LaunchedEffect(order) {
                    isBoosting = false
                    // isCancelProcessing сбрасывается в начале OrderCard
                }

                // Настройка бесконечной анимации пульсации (эффект радара)
                val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.8f, // Насколько сильно расширяется круг
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "pulse_scale"
                )
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.5f,
                    targetValue = 0f, // Плавно растворяется в конце
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "pulse_alpha"
                )

                // Контейнер статуса "В поиске"
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Блок с анимированной иконкой
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
                            // Анимированный пульсирующий круг
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .scale(pulseScale)
                                    .alpha(pulseAlpha)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            )
                            // Статичный внутренний круг с иконкой
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp),
                                shadowElevation = 6.dp
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Пошук",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Шукаємо кур'єра...",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Зазвичай це займає 1-3 хвилини",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                        )

                        // Ряд с кнопками управления заказом
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Кнопка отмены (Outline - чтобы не перетягивала внимание)
                            OutlinedButton(
                                onClick = {
                                    isCancelProcessing = true
                                    onCancelClick()
                                },
                                enabled = !isCancelProcessing,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                if (isCancelProcessing) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("СКАСУВАТИ", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            // Кнопка поднятия цены (Акцентная)
                            Button(
                                onClick = {
                                    if (!isBoosting) {
                                        isBoosting = true
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onBoostClick()
                                    }
                                },
                                interactionSource = interactionSource,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF59E0B),
                                    contentColor = Color.White
                                ),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 0.dp)
                            ) {
                                if (isBoosting) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("+10 ГРН", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }

            // ВИПРАВЛЕНО: Кнопка підтвердження оплати викупу кур'єром на місці!
            if (order.status == "arrived_pickup" && order.paymentType == "buyout") {
                Button(
                    onClick = {
                        isPaymentProcessing = true
                        onConfirmBuyoutPaidClick()
                    },
                    enabled = !isPaymentProcessing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF81C784)
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    if (isPaymentProcessing) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ОБРОБКА...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ОПЛАЧЕНО", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Кнопка замовлення готове
            if (!order.isReady && order.status.lowercase() != "delivered" && order.status.lowercase() != "cancelled" && order.courier != null) {
                Button(
                    onClick = onReadyClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ЗАМОВЛЕННЯ ГОТОВЕ", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            } else if (order.isReady && order.status.lowercase() != "delivered" && order.status.lowercase() != "cancelled") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Готово. Очікує кур'єра", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (order.status.lowercase() == "delivered" && !isRated) {
                Button(
                    onClick = onRateClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107), contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ОЦІНИТИ КУР'ЄРА", fontWeight = FontWeight.Bold)
                }
            }

            // ВИПРАВЛЕНО: Кнопка підтвердження повернення коштів
            if (order.status == "returning") {
                Button(
                    onClick = onConfirmReturnClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .padding(top = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.White),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ПІДТВЕРДИТИ ПОВЕРНЕННЯ", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun InfoRow(icon: ImageVector, text: String, iconTint: Color = Color.Gray) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ActionIconButton(icon: ImageVector, color: Color, hasBadge: Boolean = false, onClick: () -> Unit) {
    Box {
        Surface(
            shape = CircleShape,
            color = color.copy(alpha = 0.15f),
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.padding(14.dp)
            )
        }

        // Червона крапка (badge), якщо є нові повідомлення
        if (hasBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-2).dp, y = 2.dp)
                    .size(14.dp)
                    .background(Color.Red, CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}

@Composable
fun ChatDialog(
    orderId: Int,
    messages: List<ChatMessage>,
    onSendMessage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.8f)
        ) {
            Column {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Закрити", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Чат з кур'єром",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    reverseLayout = false
                ) {
                    items(messages) { msg ->
                        val isPartner = msg.role == "partner"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = if (isPartner) Arrangement.End else Arrangement.Start
                        ) {
                            Surface(
                                shape = RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isPartner) 16.dp else 4.dp,
                                    bottomEnd = if (isPartner) 4.dp else 16.dp
                                ),
                                color = if (isPartner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.widthIn(max = 280.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = msg.text,
                                        fontSize = 15.sp,
                                        color = if (isPartner) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = msg.time,
                                        fontSize = 10.sp,
                                        modifier = Modifier
                                            .align(Alignment.End)
                                            .padding(top = 4.dp),
                                        color = if (isPartner) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp)
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Написати повідомлення...") },
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                onSendMessage(messageText)
                                messageText = ""
                            }
                        },
                        modifier = Modifier.size(50.dp),
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Відправити")
                    }
                }
            }
        }
    }
}

@Composable
fun RateCourierDialog(orderId: Int, onDismiss: () -> Unit, onSubmit: (Int, String) -> Unit) {
    var rating by remember { mutableStateOf(5) }
    var review by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Оцініть доставку", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Наскільки ви задоволені кур'єром?", fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))

                Spacer(modifier = Modifier.height(24.dp))

                Row(horizontalArrangement = Arrangement.Center) {
                    for (i in 1..5) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = if (i <= rating) Color(0xFFFFC107) else Color.LightGray,
                            modifier = Modifier
                                .size(44.dp)
                                .clickable { rating = i }
                                .padding(4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = review,
                    onValueChange = { review = it },
                    label = { Text("Короткий відгук (за бажанням)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Скасувати", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSubmit(rating, review) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("Відправити", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun MapDialog(lat: Double, lon: Double, onDismiss: () -> Unit) {
    val context = LocalContext.current

    remember {
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
        Configuration.getInstance().userAgentValue = context.packageName
    }

    val geoPoint = GeoPoint(lat, lon)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.8f),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Локація кур'єра", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Закрити", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            controller.setZoom(17.0)
                            controller.setCenter(geoPoint)

                            val marker = Marker(this)
                            marker.position = geoPoint
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            marker.title = "Кур'єр"
                            overlays.add(marker)
                        }
                    },
                    update = { view ->
                        view.controller.animateTo(geoPoint)

                        view.overlays.removeAll { it is Marker }
                        val marker = Marker(view)
                        marker.position = geoPoint
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        marker.title = "Кур'єр"
                        view.overlays.add(marker)
                        view.invalidate()
                    }
                )
            }
        }
    }
}

// --- УТИЛІТИ ДЛЯ ТАЙМЛАЙНУ ---

fun calculateMinutesDifference(startObj: String?, endObj: String?): String? {
    if (startObj == null || endObj == null) return null
    try {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val date1 = format.parse(startObj)
        val date2 = format.parse(endObj)
        if (date1 != null && date2 != null) {
            val diffMs = date2.time - date1.time
            val diffMinutes = diffMs / (1000 * 60)
            return if (diffMinutes > 0) "+$diffMinutes хв" else "< 1 хв"
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

@Composable
fun OrderTimeline(order: PartnerOrder) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 12.dp)
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
        .padding(16.dp)
    ) {
        Text("Хронологія виконання", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))

        TimelineStep("Створено", order.createdAt, null, isCompleted = true, isLast = order.acceptedAt == null)

        if (order.acceptedAt != null || order.status != "pending") {
            val duration = calculateMinutesDifference(order.createdAt, order.acceptedAt)
            TimelineStep("Кур'єр прийняв", order.acceptedAt, duration, isCompleted = order.acceptedAt != null, isLast = order.arrivedAt == null)
        }

        if (order.arrivedAt != null || order.status in listOf("arrived_pickup", "picked_up", "delivered")) {
            val duration = calculateMinutesDifference(order.acceptedAt, order.arrivedAt)
            TimelineStep("Прибув у заклад", order.arrivedAt, duration, isCompleted = order.arrivedAt != null, isLast = order.pickedUpAt == null)
        }

        if (order.pickedUpAt != null || order.status in listOf("picked_up", "delivered")) {
            val duration = calculateMinutesDifference(order.arrivedAt, order.pickedUpAt)
            TimelineStep("Забрав замовлення", order.pickedUpAt, duration, isCompleted = order.pickedUpAt != null, isLast = order.deliveredAt == null)
        }

        if (order.deliveredAt != null || order.status == "delivered") {
            val duration = calculateMinutesDifference(order.pickedUpAt, order.deliveredAt)
            TimelineStep("Доставлено клієнту", order.deliveredAt, duration, isCompleted = order.deliveredAt != null, isLast = true)
        }
    }
}

@Composable
fun TimelineStep(title: String, timeText: String?, durationFromPrev: String?, isCompleted: Boolean, isLast: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(24.dp)) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (isCompleted) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f))
            )
            if (!isLast) {
                Box(modifier = Modifier.width(2.dp).weight(1f).background(if (isCompleted) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Gray.copy(alpha = 0.2f)))
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = if (isCompleted) FontWeight.ExtraBold else FontWeight.Normal,
                    color = if (isCompleted) MaterialTheme.colorScheme.onSurface else Color.Gray
                )
                if (durationFromPrev != null) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(8.dp)) {
                        Text(text = durationFromPrev, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
            if (timeText != null) {
                // Витягуємо лише години та хвилини з формату "2023-10-27 14:30:00"
                val displayTime = timeText.split(" ").lastOrNull()?.substring(0, 5) ?: timeText
                Text(text = displayTime, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}