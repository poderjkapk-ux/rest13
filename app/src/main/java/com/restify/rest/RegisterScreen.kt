package com.restify.rest

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(viewModel: MainViewModel, onNavigateBack: () -> Unit, onRegisterSuccess: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // Стан для адреси
    var addressQuery by remember { mutableStateOf("") }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    // Отримуємо результати пошуку з ViewModel
    val searchResults by viewModel.searchResults.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    // Стан скролу для екрану
    val scrollState = rememberScrollState()

    val isLoading by viewModel.isLoading
    val errorMessage by viewModel.errorMessage
    val verifLink by viewModel.verificationLink
    val isPhoneVerified by viewModel.isPhoneVerified
    val verifiedPhone by viewModel.verifiedPhone

    // Ініціалізація Telegram при відкритті екрану
    LaunchedEffect(Unit) {
        viewModel.isPhoneVerified.value = false
        viewModel.verifiedPhone.value = null
        viewModel.initVerification()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState) // Додано можливість скролу
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Реєстрація закладу",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Telegram Верифікація
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(12.dp),
            color = if (isPhoneVerified) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant,
            border = if (isPhoneVerified) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF4CAF50)) else null
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isPhoneVerified) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(32.dp))
                    Text("Телефон підтверджено", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                    Text(verifiedPhone ?: "", color = Color.DarkGray)
                } else {
                    Text("Для захисту від ботів підтвердіть номер", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                    Button(
                        onClick = {
                            verifLink?.let { link ->
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                                viewModel.startVerificationPolling()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF24A1DE))
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Підтвердити в Telegram")
                    }
                }
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Назва закладу") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(12.dp))

        // Автокомпліт адреси (Оновлено під нову архітектуру)
        ExposedDropdownMenuBox(
            expanded = isDropdownExpanded,
            onExpandedChange = { isDropdownExpanded = !isDropdownExpanded }
        ) {
            OutlinedTextField(
                value = addressQuery,
                onValueChange = { newValue ->
                    addressQuery = newValue
                    isDropdownExpanded = true
                    searchJob?.cancel()
                    searchJob = coroutineScope.launch {
                        delay(600) // Чекаємо 600мс після останнього вводу
                        viewModel.searchAddress(newValue)
                    }
                },
                label = { Text("Адреса (вулиця, будинок)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Випадаючий список з результатами пошуку
            if (searchResults.isNotEmpty()) {
                ExposedDropdownMenu(
                    expanded = isDropdownExpanded,
                    onDismissRequest = { isDropdownExpanded = false }
                ) {
                    searchResults.forEach { suggestion ->
                        // Формуємо коротку адресу (Вулиця, Будинок, Місто)
                        val shortAddressName = suggestion.address?.let { addr ->
                            val road = addr.road.orEmpty()
                            val houseNumber = addr.houseNumber.orEmpty()
                            val city = addr.city ?: addr.town ?: addr.village ?: ""
                            listOf(road, houseNumber, city).filter { it.isNotBlank() }.joinToString(", ")
                        }.takeIf { !it.isNullOrBlank() } ?: suggestion.display_name

                        DropdownMenuItem(
                            text = { Text(shortAddressName, maxLines = 2) },
                            onClick = {
                                addressQuery = shortAddressName
                                isDropdownExpanded = false
                                viewModel.searchAddress("") // Очищаємо результати
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Пароль") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { viewModel.register(name, addressQuery, email, password, onRegisterSuccess) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = isPhoneVerified && name.isNotBlank() && addressQuery.isNotBlank() && email.isNotBlank() && password.isNotBlank() && !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text("ЗАРЕЄСТРУВАТИСЯ", fontWeight = FontWeight.Bold)
            }
        }

        TextButton(
            onClick = {
                viewModel.stopVerificationPolling()
                onNavigateBack()
            },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("Вже є акаунт? Увійти", color = MaterialTheme.colorScheme.primary)
        }
    }
}