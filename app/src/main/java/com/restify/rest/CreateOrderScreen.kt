package com.restify.rest

import android.preference.PreferenceManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateOrderScreen(viewModel: MainViewModel, onOrderCreated: () -> Unit) {
    // Отримуємо мінімальну ціну та причину з ViewModel
    val minFee by viewModel.minFee.collectAsState()
    val feeReason by viewModel.feeReason.collectAsState()

    // --- СТАНИ ДЛЯ ПЕРСОНАЛЬНОГО ПРИЗНАЧЕННЯ ---
    val activeCouriers by viewModel.activeCouriers.collectAsState()
    var selectedCourierId by remember { mutableStateOf<Int?>(null) }

    // Завантажуємо активних кур'єрів при відкритті екрану
    LaunchedEffect(Unit) {
        viewModel.fetchActiveCouriers()
    }

    // НОВІ ПОЛЯ
    var street by remember { mutableStateOf("") }
    var houseNumber by remember { mutableStateOf("") }
    var apartment by remember { mutableStateOf("") }
    var changeFrom by remember { mutableStateOf("") }

    var customerName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }

    // fee тепер оновлюється автоматично через LaunchedEffect
    var fee by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var paymentType by remember { mutableStateOf("prepaid") }

    // --- СТЕЙТ ДЛЯ ЧАСУ ПРИГОТУВАННЯ (за замовчуванням 15) ---
    var prepTime by remember { mutableStateOf(15) }

    // Состояние для интерактивной кнопки
    var isSubmitting by remember { mutableStateOf(false) }

    // Валідація телефону (починається з 0, рівно 10 цифр)
    val isPhoneValid = phone.matches(Regex("^0\\d{9}$"))
    // Валідація ціни (щоб не було крашу при введенні тексту замість цифр)
    val isPriceValid = price.isEmpty() || price.toDoubleOrNull() != null

    // Состояние карты и автодополнения
    var mapCenter by remember { mutableStateOf(GeoPoint(46.4825, 30.7233)) } // Одеса за замовчуванням
    var mapZoom by remember { mutableStateOf(16.0) } // Динамічний зум
    var isDropdownExpanded by remember { mutableStateOf(false) }

    // Отримуємо результати пошуку з ViewModel замість прямого HTTP запиту в UI
    val searchResults by viewModel.searchResults.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val scrollState = rememberScrollState()

    // Настройка ТЕМНОЙ темы
    val backgroundColor = Color(0xFF121212) // Глибокий чорний
    val cardColor = Color(0xFF1E1E1E) // Темно-сірий для карток
    val primaryAccent = Color(0xFFBB86FC) // Фіолетовий акцент
    val textColor = Color.White
    val textSecondaryColor = Color(0xFFAAAAAA)
    val dividerColor = Color(0xFF333333)

    // Универсальные цвета для полей ввода
    val darkTextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = primaryAccent,
        unfocusedBorderColor = dividerColor,
        focusedContainerColor = cardColor,
        unfocusedContainerColor = cardColor,
        focusedTextColor = textColor,
        unfocusedTextColor = textColor,

        // --- ДОБАВЛЕННЫЕ ЦВЕТА ДЛЯ СОСТОЯНИЯ ОШИБКИ ---
        errorTextColor = MaterialTheme.colorScheme.error, // Чтобы текст всегда был белым
        errorBorderColor = MaterialTheme.colorScheme.error, // Красная рамка при ошибке
        errorCursorColor = MaterialTheme.colorScheme.error, // Красный курсор
        errorLeadingIconColor = MaterialTheme.colorScheme.error, // Красная иконка
        // ----------------------------------------------

        cursorColor = primaryAccent,
        focusedLabelColor = primaryAccent,
        unfocusedLabelColor = textSecondaryColor
    )

    // 1. ПОСТІЙНЕ ОНОВЛЕННЯ "НА ЛЬОТУ":
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.fetchMinFee()
            delay(15000) // 15 секунд
        }
    }

    // 2. АВТОМАТИЧНА ПІДСТАНОВКА:
    LaunchedEffect(minFee) {
        val currentFee = fee.toDoubleOrNull() ?: 0.0
        if (fee.isEmpty() || fee == "80" || currentFee == 80.0 || currentFee < minFee) {
            fee = if (minFee % 1 == 0.0) minFee.toInt().toString() else minFee.toString()
        }
    }

    // 3. ТИХИЙ ФОКУС КАРТИ НА БУДИНОК:
    LaunchedEffect(searchResults) {
        // Якщо дропдаун закритий, але є результати (це означає, що ми шукали будинок)
        if (!isDropdownExpanded && searchResults.isNotEmpty() && houseNumber.isNotEmpty()) {
            val first = searchResults.first()
            val lat = first.lat.toDoubleOrNull()
            val lon = first.lon.toDoubleOrNull()
            if (lat != null && lon != null) {
                mapCenter = GeoPoint(lat, lon)
                mapZoom = 18.0 // Близький зум на будинок
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "Нова доставка",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        // Блок адреса с картой
        PremiumCard(cardColor = cardColor) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Куди веземо?", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = textSecondaryColor)
                Spacer(modifier = Modifier.height(12.dp))

                // Ввод вулиці з автодоповненням
                ExposedDropdownMenuBox(
                    expanded = isDropdownExpanded,
                    onExpandedChange = { isDropdownExpanded = !isDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = street,
                        onValueChange = { newValue ->
                            street = newValue
                            isDropdownExpanded = true
                            searchJob?.cancel()
                            searchJob = coroutineScope.launch {
                                delay(600)
                                viewModel.searchAddress("$newValue, Одеса")
                            }
                        },
                        placeholder = { Text("Вулиця...", color = textSecondaryColor) },
                        leadingIcon = { Icon(Icons.Outlined.Place, contentDescription = "Street", tint = primaryAccent) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(12.dp),
                        colors = darkTextFieldColors,
                        singleLine = true
                    )

                    // Выпадающий список с результатами поиска
                    if (searchResults.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = isDropdownExpanded,
                            onDismissRequest = { isDropdownExpanded = false },
                            modifier = Modifier.background(cardColor)
                        ) {
                            searchResults.forEach { suggestion ->
                                val shortAddressName = suggestion.address?.let { addr ->
                                    val road = addr.road.orEmpty()
                                    val city = addr.city ?: addr.town ?: addr.village ?: ""
                                    listOf(road, city).filter { it.isNotBlank() }.joinToString(", ")
                                }.takeIf { !it.isNullOrBlank() } ?: suggestion.display_name

                                DropdownMenuItem(
                                    text = { Text(shortAddressName, color = textColor, maxLines = 2) },
                                    onClick = {
                                        val roadName = suggestion.address?.road ?: suggestion.display_name.split(",").firstOrNull()?.trim() ?: ""
                                        street = roadName

                                        val lat = suggestion.lat.toDoubleOrNull() ?: mapCenter.latitude
                                        val lon = suggestion.lon.toDoubleOrNull() ?: mapCenter.longitude
                                        mapCenter = GeoPoint(lat, lon)
                                        mapZoom = 16.0 // Загальний зум для вулиці

                                        isDropdownExpanded = false
                                        viewModel.searchAddress("")
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Блок: Будинок та Квартира
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = houseNumber,
                        onValueChange = { newValue ->
                            houseNumber = newValue
                            // Тихий пошук будинку для красивого фокусу карти
                            searchJob?.cancel()
                            searchJob = coroutineScope.launch {
                                delay(800)
                                if (street.isNotEmpty() && newValue.isNotEmpty()) {
                                    isDropdownExpanded = false // Не відкриваємо меню
                                    viewModel.searchAddress("$street, $newValue, Одеса")
                                }
                            }
                        },
                        placeholder = { Text("Будинок", color = textSecondaryColor) },
                        leadingIcon = { Icon(Icons.Outlined.Home, contentDescription = "House", tint = primaryAccent) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = darkTextFieldColors,
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = apartment,
                        onValueChange = { apartment = it },
                        placeholder = { Text("Кв. (необов.)", color = textSecondaryColor) },
                        leadingIcon = { Icon(Icons.Outlined.MeetingRoom, contentDescription = "Apartment", tint = primaryAccent) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = darkTextFieldColors,
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Контейнер карты
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    OpenStreetMapComponent(centerPoint = mapCenter, zoom = mapZoom)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Блок данных клиента
        PremiumCard(cardColor = cardColor) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Деталі замовлення", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = textSecondaryColor)
                Spacer(modifier = Modifier.height(12.dp))

                // === БЛОК: ДИНАМІЧНА ВАРТІСТЬ (АЛЕРТ) ===
                if (minFee > 80.0 || feeReason.isNotEmpty()) {
                    val reasonText = if (feeReason.isNotEmpty()) " Причина: $feeReason" else ""
                    val displayMinFee = if (minFee % 1 == 0.0) minFee.toInt().toString() else minFee.toString()

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .background(Color(0x33EF4444), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Outlined.Warning, contentDescription = null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Увага: Мінімальна ціна доставки зараз $displayMinFee грн.$reasonText",
                                fontSize = 13.sp,
                                color = Color(0xFFFCA5A5),
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
                // ============================================

                OutlinedTextField(
                    value = customerName,
                    onValueChange = { customerName = it },
                    placeholder = { Text("Ім'я клієнта", color = textSecondaryColor) },
                    leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = "Name", tint = primaryAccent) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = darkTextFieldColors,
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { newValue ->
                        val digitsOnly = newValue.filter { it.isDigit() }.take(10)
                        phone = digitsOnly
                    },
                    placeholder = { Text("Телефон (наприклад: 0632020619)", color = textSecondaryColor) },
                    leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = "Phone", tint = primaryAccent) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    isError = phone.isNotEmpty() && !isPhoneValid,
                    supportingText = {
                        if (phone.isNotEmpty() && !isPhoneValid) {
                            Text("Формат: 10 цифр, починається з 0 (без +38)", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = darkTextFieldColors,
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        placeholder = { Text("Сума (₴)", color = textSecondaryColor) },
                        leadingIcon = { Icon(Icons.Outlined.AttachMoney, contentDescription = "Price", tint = primaryAccent) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = !isPriceValid,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = darkTextFieldColors,
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = fee,
                        onValueChange = { fee = it },
                        placeholder = { Text("Доставка (₴)", color = textSecondaryColor) },
                        leadingIcon = { Icon(Icons.Outlined.LocalShipping, contentDescription = "Fee", tint = primaryAccent) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = darkTextFieldColors,
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Поле "Решта з" (Сдача)
                OutlinedTextField(
                    value = changeFrom,
                    onValueChange = { changeFrom = it },
                    placeholder = { Text("Решта з (₴) (необов.)", color = textSecondaryColor) },
                    leadingIcon = { Icon(Icons.Outlined.Payments, contentDescription = "Change", tint = primaryAccent) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = darkTextFieldColors,
                    singleLine = true
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Блок оплаты и настроек
        PremiumCard(cardColor = cardColor) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Оплата", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = textSecondaryColor)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    PaymentOption("Оплачено", Icons.Outlined.CheckCircle, paymentType == "prepaid", primaryAccent) {
                        paymentType = "prepaid"
                    }
                    PaymentOption("Викуп", Icons.Outlined.ShoppingBag, paymentType == "buyout", primaryAccent) {
                        paymentType = "buyout"
                    }
                }

                if (paymentType == "buyout") {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF3E2723), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFD84315), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Outlined.Info, contentDescription = null, tint = Color(0xFFFFCCBC), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Увага: Кур'єр викупить замовлення за власні кошти в закладі, а потім забере гроші у клієнта. Повертатися в заклад йому більше не потрібно. Не забудьте в додатку підтвердити отримання коштів від кур'єра під час видачі!",
                                fontSize = 13.sp,
                                color = Color(0xFFFFCCBC),
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- БЛОК: ЧАС ПРИГОТУВАННЯ ---
        PremiumCard(cardColor = cardColor) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Timer, contentDescription = "Timer", tint = primaryAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Час приготування (хв)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = textSecondaryColor
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                val timeOptions = listOf(10, 15, 20, 30, 45, 60)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(timeOptions) { time ->
                        val isSelected = prepTime == time
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) primaryAccent.copy(alpha = 0.2f) else Color.Transparent)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) primaryAccent else dividerColor,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { prepTime = time }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$time",
                                fontSize = 16.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) primaryAccent else textColor
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "'Готово' через $prepTime хв",
                    fontSize = 12.sp,
                    color = textSecondaryColor
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- БЛОК: ПЕРСОНАЛЬНЕ ПРИЗНАЧЕННЯ (ТІЛЬКИ ЯКЩО Є АКТИВНІ КУРЬЄРИ) ---
        if (activeCouriers.isNotEmpty()) {
            PremiumCard(cardColor = cardColor) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.PersonAddAlt1, contentDescription = "Assign", tint = primaryAccent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Докинути замовлення (Попутно)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = textSecondaryColor
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Можете запропонувати це замовлення кур'єру, який прямо зараз знаходиться у вас або виконує ваше замовлення.",
                        fontSize = 12.sp,
                        color = textSecondaryColor,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        item {
                            val isSelected = selectedCourierId == null
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) primaryAccent.copy(alpha = 0.2f) else Color.Transparent)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) primaryAccent else dividerColor,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { selectedCourierId = null }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Загальний пошук",
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) primaryAccent else textColor
                                )
                            }
                        }
                        items(activeCouriers) { courier ->
                            val isSelected = selectedCourierId == courier.id
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) primaryAccent.copy(alpha = 0.2f) else Color.Transparent)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) primaryAccent else dividerColor,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { selectedCourierId = courier.id }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = courier.name,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) primaryAccent else textColor
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = comment,
            onValueChange = { comment = it },
            placeholder = { Text("Коментар кур'єру...", color = textSecondaryColor) },
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            shape = RoundedCornerShape(16.dp),
            colors = darkTextFieldColors
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Интерактивная кнопка подтверждения
        Button(
            onClick = {
                isSubmitting = true

                val parsedFee = fee.toDoubleOrNull() ?: minFee
                val finalFee = if (parsedFee < minFee) minFee else parsedFee

                // Формуємо красивий адрес та коментар для максимальної сумісності
                val formattedAddress = "$street, буд. $houseNumber" + if (apartment.isNotBlank()) ", кв. $apartment" else ""
                val formattedComment = if (changeFrom.isNotBlank()) "[СУМА/РЕШТА: $changeFrom] $comment".trim() else comment

                val request = OrderCreateRequest(
                    address = formattedAddress,
                    street = street.takeIf { it.isNotBlank() },
                    houseNumber = houseNumber.takeIf { it.isNotBlank() },
                    apartment = apartment.takeIf { it.isNotBlank() },
                    changeFrom = changeFrom.takeIf { it.isNotBlank() },
                    customerName = customerName,
                    phone = phone,
                    price = price.toDoubleOrNull() ?: 0.0,
                    fee = finalFee,
                    comment = formattedComment,
                    paymentType = paymentType,
                    isReturnRequired = false,
                    prepTime = prepTime,
                    targetCourierId = selectedCourierId // <-- ДОДАНО ID ВИБРАНОГО КУРЬЄРА
                )

                viewModel.createNewOrder(request) {
                    isSubmitting = false
                    onOrderCreated()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryAccent,
                disabledContainerColor = dividerColor
            ),
            // Кнопка активна тільки якщо введено вулицю, будинок, ім'я, ВАЛІДНИЙ номер телефону та ціну
            enabled = street.isNotEmpty() && houseNumber.isNotEmpty() && customerName.isNotEmpty() && isPhoneValid && isPriceValid && !isSubmitting
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    color = backgroundColor,
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp
                )
            } else {
                Text("ВІДПРАВИТИ КУР'ЄРУ", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = backgroundColor)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun PremiumCard(cardColor: Color, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = cardColor,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        content()
    }
}

@Composable
fun PaymentOption(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val color = if (isSelected) accentColor else Color(0xFFAAAAAA)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Icon(icon, contentDescription = text, tint = color, modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = color)
    }
}

@Composable
fun OpenStreetMapComponent(centerPoint: GeoPoint, zoom: Double = 16.0) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapView by remember { mutableStateOf<MapView?>(null) }

    remember {
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
        Configuration.getInstance().userAgentValue = context.packageName
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView?.onDetach()
        }
    }

    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                mapView = this
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(zoom)
                controller.setCenter(centerPoint)
            }
        },
        update = { view ->
            // Плавная анимация карты к новой точке и обновление зума
            view.controller.animateTo(centerPoint)
            view.controller.setZoom(zoom)
        },
        modifier = Modifier.fillMaxSize()
    )
}