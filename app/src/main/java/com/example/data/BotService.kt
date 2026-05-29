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

    private data class BotConfig(
        val token: String,
        val model: String,
        val systemInstruction: String,
        val name: String
    )

    private fun startPolling() {
        if (pollingJob != null && pollingJob!!.isActive) return

        pollingJob = serviceScope.launch {
            val activeJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
            try {
                // Outer loop manages active bots configuration dynamically
                while (isActive) {
                    val settings = repository.getSettings() ?: BotSettings()
                    val activeUsers = repository.getActiveRegisteredUsers()

                    val targetBots = mutableListOf<BotConfig>()

                    // 1. Add system bot if enabled
                    if (settings.isBotRunning && settings.telegramToken.isNotBlank()) {
                        targetBots.add(
                            BotConfig(
                                token = settings.telegramToken,
                                model = settings.selectedModel,
                                systemInstruction = settings.systemInstruction,
                                name = "Main System Bot"
                            )
                        )
                    }

                    // 2. Add registered user bots
                    for (user in activeUsers) {
                        if (user.telegramToken.isNotBlank()) {
                            targetBots.add(
                                BotConfig(
                                    token = user.telegramToken,
                                    model = user.selectedModel,
                                    systemInstruction = settings.systemInstruction,
                                    name = user.name
                                )
                            )
                        }
                    }

                    val targetTokens = targetBots.map { it.token }.toSet()

                    // Cancel bots that shouldn't be running anymore
                    val tokensToStop = activeJobs.keys.filter { it !in targetTokens }
                    for (token in tokensToStop) {
                        activeJobs[token]?.cancel()
                        activeJobs.remove(token)
                        repository.addLog("INFO", "Bot Polling dinonaktifkan untuk bot token: ...${token.takeLast(6)}")
                    }

                    // Start bots that aren't running yet
                    for (bot in targetBots) {
                        if (!activeJobs.containsKey(bot.token) || activeJobs[bot.token]?.isActive != true) {
                            val job = launch(Dispatchers.IO) {
                                pollSingleBot(bot, settings.groqApiKey)
                            }
                            activeJobs[bot.token] = job
                        }
                    }

                    delay(5000) // Dynamically sync running bots every 5 seconds
                }
            } catch (e: CancellationException) {
                // Done
            } catch (e: Exception) {
                repository.addLog("ERROR", "Kesalahan pengelola bot background: ${e.message}")
            } finally {
                activeJobs.values.forEach { it.cancel() }
                activeJobs.clear()
            }
        }
    }

    private suspend fun pollSingleBot(bot: BotConfig, groqApiKey: String) {
        var lastUpdateId = 0L

        try {
            val info = repository.validateTelegramBot(bot.token)
            // Auto update display info in DB if registered user matching
            val activeUsers = repository.getActiveRegisteredUsers()
            val matchingUser = activeUsers.find { it.telegramToken == bot.token }
            if (matchingUser != null && (matchingUser.botUsername != info.username || matchingUser.botFirstName != info.firstName)) {
                repository.saveRegisteredUser(
                    matchingUser.copy(
                        botUsername = info.username ?: "",
                        botFirstName = info.firstName
                    )
                )
            }
            repository.addLog("SUCCESS", "Bot @${info.username} milik [${bot.name}] aktif di background.")
        } catch (e: Exception) {
            repository.addLog("ERROR", "Uji koneksi gagal untuk bot [${bot.name}]: ${e.message}")
            delay(15000)
        }

        while (currentCoroutineContext().isActive) {
            try {
                val updates = repository.fetchTelegramUpdates(
                    token = bot.token,
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
                        repository.addLog("INCOMING", "[$chatUsernameString -> @${bot.name}]: \"$text\"")

                        coroutineScope {
                            launch {
                                handleIncomingMessage(
                                    token = bot.token,
                                    chatId = chat.id,
                                    messageId = message.messageId,
                                    textMessage = text,
                                    senderName = senderName,
                                    systemInstruction = bot.systemInstruction,
                                    apiKey = groqApiKey,
                                    model = bot.model,
                                    botName = bot.name
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: e.message ?: "Koneksi Bermasalah"
                Log.e("BotService", "Polling error for ${bot.name}: $errorMsg")
                if (errorMsg.contains("401") || errorMsg.contains("Unauthorized")) {
                    repository.addLog("ERROR", "Token untuk bot [${bot.name}] tidak valid (HTTP 401). Polling dihentikan.")
                    break
                }
                delay(12000)
            }
            delay(1500)
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
        model: String,
        botName: String
    ) {
        try {
            repository.addLog("INFO", "Menghubungi Groq AI ($model) untuk bot [$botName]...")
            val enrichedPrompt = "Seorang pengguna bernama $senderName berinteraksi dengan Anda di bot Telegram. Dia berkata: \"$textMessage\". Harap balas dengan sopan sesuai instruksi sistem."
            val aiResponse = repository.askGroq(enrichedPrompt, apiKey, model, systemInstruction)

            repository.addLog("OUTGOING", "[@$botName -> @$senderName]: \"$aiResponse\"")

            repository.sendTelegramMessage(
                token = token,
                chatId = chatId,
                text = aiResponse,
                replyToMessageId = messageId
            )
            repository.addLog("SUCCESS", "Pesan dijawab sukses oleh bot [$botName] ke @$senderName")
        } catch (e: Exception) {
            val errMsg = e.localizedMessage ?: e.message ?: "Unknown Error"
            repository.addLog("ERROR", "Bot [$botName] gagal menjawab pesan: $errMsg")

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
            repository.addLog("INFO", "Semua aktivitas polling bot dihentikan sepenuhnya.")
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
