package com.example.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class BotService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null
    private var lastUpdateId: Long = 0

    private lateinit var database: AppDatabase
    private lateinit var repository: BotRepository

    companion object {
        const val CHANNEL_ID = "telegram_bot_service_channel"
        const val NOTIFICATION_ID = 24

        fun startService(context: Context) {
            val intent = Intent(context, BotService::class.java).apply {
                action = "START"
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e("BotService", "Failed to start service: ${e.message}")
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, BotService::class.java).apply {
                action = "STOP"
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e("BotService", "Failed to stop service: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(applicationContext)
        repository = BotRepository(database.botDao())
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: "START"
        if (action == "STOP") {
            stopPollingAndService()
        } else {
            startForegroundNotification()
            startPolling()
        }
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AI Telegram Bot Aktif")
            .setContentText("Bot sedang berjalan 24 jam non-stop tanggap pesan.")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("BotService", "Failed to start foreground FGS: ${e.message}")
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (ex: Exception) {
                Log.e("BotService", "Generic startForeground failed: ${ex.message}")
            }
        }
    }

    private fun startPolling() {
        if (pollingJob != null && pollingJob!!.isActive) return

        pollingJob = serviceScope.launch {
            try {
                val settings = repository.getSettings() ?: BotSettings()
                if (settings.telegramToken.isBlank()) {
                    repository.addLog("ERROR", "Token Telegram kosong, tidak dapat mengaktifkan bot.")
                    stopPollingAndService()
                    return@launch
                }

                // Pre-validation to obtain bot info
                try {
                    val info = repository.validateTelegramBot(settings.telegramToken)
                    repository.saveSettings(settings.copy(
                        isBotRunning = true,
                        botFirstName = info.firstName,
                        botUsername = info.username ?: ""
                    ))
                    repository.addLog("SUCCESS", "Bot @${info.username} aktif di background (24 Jam Non-Stop).")
                } catch (e: Exception) {
                    repository.addLog("ERROR", "Uji koneksi awal bot gagal: ${e.message}")
                }

                while (isActive) {
                    val currentSettings = repository.getSettings() ?: break
                    if (!currentSettings.isBotRunning) {
                        break
                    }

                    try {
                        val updates = repository.fetchTelegramUpdates(
                            token = currentSettings.telegramToken,
                            offset = if (lastUpdateId > 0) lastUpdateId else null,
                            timeout = 10
                        )

                        for (update in updates) {
                            lastUpdateId = update.updateId + 1
                            val message = update.message
                            val text = message?.text
                            val chat = message?.chat
                            val from = message?.from

                            if (text != null && chat != null) {
                                val senderName = from?.firstName ?: "User"
                                val chatUsernameString = from?.username?.let { "@$it" } ?: "ID: ${chat.id}"
                                repository.addLog("INCOMING", "Pesan dari $senderName ($chatUsernameString): \"$text\"")

                                launch {
                                    handleIncomingMessage(
                                        token = currentSettings.telegramToken,
                                        chatId = chat.id,
                                        messageId = message.messageId,
                                        textMessage = text,
                                        senderName = senderName,
                                        systemInstruction = currentSettings.systemInstruction,
                                        apiKey = currentSettings.groqApiKey,
                                         model = currentSettings.selectedModel
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        val errorMsg = e.localizedMessage ?: e.message ?: "Koneksi Bermasalah"
                        Log.e("BotService", "Polling error: $errorMsg")
                        if (errorMsg.contains("401") || errorMsg.contains("Unauthorized")) {
                            repository.addLog("ERROR", "Token Telegram tidak valid (HTTP 401). Menonaktifkan bot.")
                            break
                        }
                        repository.addLog("WARNING", "Koneksi telegram terganggu: $errorMsg. Mencoba kembali...")
                        delay(10000)
                    }
                    delay(1500)
                }
            } catch (e: CancellationException) {
                // Task canceled normally
            } catch (e: Exception) {
                repository.addLog("ERROR", "Kesalahan sistem polling: ${e.message}")
            } finally {
                val currentSettings = repository.getSettings()
                if (currentSettings != null) {
                    repository.saveSettings(currentSettings.copy(isBotRunning = false))
                }
            }
        }
    }

    private suspend fun handleIncomingMessage(
        token: String,
        chatId: Long,
        messageId: Long,
        textMessage: String,
        senderName: String,
        systemInstruction: String,
        apiKey: String,
        model: String
    ) {
        try {
            repository.addLog("INFO", "Menghubungi Groq AI ($model)...")
            val enrichedPrompt = "Seorang pengguna bernama $senderName berinteraksi dengan Anda di bot Telegram. Dia berkata: \"$textMessage\". Harap balas dengan sopan sesuai instruksi sistem."
            val aiResponse = repository.askGroq(enrichedPrompt, apiKey, model, systemInstruction)

            repository.addLog("OUTGOING", "Balasan AI Groq: \"$aiResponse\"")

            repository.sendTelegramMessage(
                token = token,
                chatId = chatId,
                text = aiResponse,
                replyToMessageId = messageId
            )
            repository.addLog("SUCCESS", "Balasan berhasil dikirim ke @$senderName")
        } catch (e: Exception) {
            val errMsg = e.localizedMessage ?: e.message ?: "Unknown Error"
            repository.addLog("ERROR", "Gagal menjawab pesan: $errMsg")

            try {
                repository.sendTelegramMessage(
                    token = token,
                    chatId = chatId,
                    text = "Maaf, sistem AI sedang mengalami gangguan teknis. Silakan coba kirim pesan lagi nanti.\n*(Detail: $errMsg)*",
                    replyToMessageId = messageId
                )
            } catch (ignored: Exception) {}
        }
    }

    private fun stopPollingAndService() {
        pollingJob?.cancel()
        pollingJob = null
        serviceScope.launch {
            val settings = repository.getSettings()
            if (settings != null) {
                repository.saveSettings(settings.copy(isBotRunning = false))
            }
            repository.addLog("INFO", "Bot telegram dinonaktifkan.")
            withContext(Dispatchers.Main) {
                stopForeground(true)
                stopSelf()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Telegram Bot Active Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
