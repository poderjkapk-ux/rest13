package com.restify.rest

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoginScreen(
    viewModel: MainViewModel,
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // Стан для діалогу відновлення пароля
    var showResetDialog by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()
    val context = LocalContext.current // Для показу Toast повідомлень

    val isLoading = viewModel.isLoading.value
    val errorMessage = viewModel.errorMessage.value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Вхід в систему",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Пароль") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { viewModel.login(email, password, onLoginSuccess) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = email.isNotBlank() && password.isNotBlank() && !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
            } else {
                Text("УВІЙТИ", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- КНОПКА: ЗАБУЛИ ПАРОЛЬ ---
        TextButton(onClick = {
            resetEmail = email // Підставляємо введений email, якщо є
            showResetDialog = true
        }) {
            Text(
                text = "Забули пароль?",
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Normal
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- КНОПКА: ПЕРЕХІД НА РЕЄСТРАЦІЮ ---
        TextButton(onClick = onNavigateToRegister) {
            Text(
                text = "Немає акаунту? Створити",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }

    // --- ДІАЛОГ ВІДНОВЛЕННЯ ПАРОЛЯ ---
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(text = "Відновлення пароля", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Введіть ваш Email. Новий пароль буде відправлено у ваш підключений Telegram-бот.",
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        label = { Text("Ваш Email") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetPassword(
                            email = resetEmail.trim(),
                            onSuccess = { msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                showResetDialog = false
                            },
                            onError = { err ->
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    enabled = resetEmail.isNotBlank() && !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Відновити")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetDialog = false },
                    enabled = !isLoading
                ) {
                    Text("Скасувати")
                }
            }
        )
    }
}