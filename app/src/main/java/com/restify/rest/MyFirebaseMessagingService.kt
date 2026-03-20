package com.restify.rest

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    // Викликається, коли Firebase генерує новий токен для пристрою
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_TOKEN", "Новий токен: $token")
        // Відправка токена на бекенд відбувається у MainViewModel
    }

    // Обробка вхідного пуш-сповіщення
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Бекенд відправляє дані у полі `data`
        val title = remoteMessage.data["title"] ?: remoteMessage.notification?.title ?: "DAYBERG Партнер"
        val body = remoteMessage.data["body"] ?: remoteMessage.notification?.body ?: "У вас нове сповіщення"

        sendNotification(title, body)

        // Відправляємо сигнал для оновлення списку замовлень в UI
        val updateIntent = Intent("com.restify.rest.UPDATE_ORDERS")

        // Витягуємо job_id (якщо він переданий з бекенду)
        val jobIdStr = remoteMessage.data["job_id"]
        if (jobIdStr != null) {
            try {
                updateIntent.putExtra("job_id", jobIdStr.toInt())
            } catch (e: Exception) {
                Log.e("FCM", "Помилка парсингу job_id: $jobIdStr")
            }
        }

        // Перевіряємо, чи це повідомлення з чату (слово "Чат" у заголовку)
        if (title.contains("Чат", ignoreCase = true)) {
            updateIntent.putExtra("is_chat", true)
        }

        sendBroadcast(updateIntent)
    }

    // Створення та показ системного сповіщення Android
    private fun sendNotification(title: String, messageBody: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            // Флаг FLAG_ACTIVITY_SINGLE_TOP, щоб додаток не перезапускався
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        // PendingIntent для відкриття додатку по кліку на сповіщення
        val pendingIntent = PendingIntent.getActivity(
            this,
            0 /* Request code */,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )

        val channelId = "partner_notifications"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Рекомендується замінити на вашу іконку (напр. R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true) // Сповіщення зникне після кліку
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Для Android 8.0 (API 26) і вище обов'язково потрібен NotificationChannel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Замовлення та Чат",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Сповіщення про статуси замовлень та повідомлення від кур'єрів"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Генеруємо унікальний ID, щоб сповіщення не перекривали одне одного
        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
    }
}