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
import java.io.File

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
        val name: String,
        val aiProvider: String = "Groq",
        val customApiKey: String = ""
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
                                name = "Main System Bot",
                                aiProvider = settings.aiProvider,
                                customApiKey = settings.customApiKey
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
                                    name = user.name,
                                    aiProvider = user.aiProvider,
                                    customApiKey = user.customApiKey
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
            try {
                repository.setTelegramCommands(bot.token)
            } catch (exCmd: Exception) {
                Log.e("BotService", "Gagal mendaftarkan menu untuk bot [${bot.name}]: ${exCmd.message}")
            }
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: e.message ?: ""
            if (errorMsg.contains("401") || errorMsg.contains("Unauthorized") || errorMsg.contains("403") || errorMsg.contains("Forbidden")) {
                repository.addLog("ERROR", "⚠️ DETEKSI: Token Bot [${bot.name}] telah diganti atau dinonaktifkan di BotFather! Bot dinonaktifkan secara otomatis.")
                
                // If this is the owner's main bot in settings, set isBotRunning to false
                val settings = repository.getSettings()
                if (settings != null && settings.telegramToken == bot.token) {
                    repository.saveSettings(settings.copy(isBotRunning = false))
                }
                
                // If this is a registered user bot, set isActive to false
                val activeUsers = repository.getActiveRegisteredUsers()
                val matchingUser = activeUsers.find { it.telegramToken == bot.token }
                if (matchingUser != null) {
                    repository.saveRegisteredUser(matchingUser.copy(isActive = false))
                }
                return
            }
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
                    val text = message?.text ?: message?.caption
                    val chat = message?.chat
                    val from = message?.from

                    if (text != null && chat != null && message != null) {
                        val senderName = from?.firstName ?: "User"
                        val chatUsernameString = from?.username?.let { "@$it" } ?: "ID: ${chat.id}"
                        repository.addLog("INCOMING", "[$chatUsernameString -> @${bot.name}]: \"$text\"")

                        coroutineScope {
                            launch {
                                handleIncomingMessage(
                                    token = bot.token,
                                    chatId = chat.id,
                                    incomingMessage = message,
                                    textMessage = text,
                                    senderName = senderName,
                                    systemInstruction = bot.systemInstruction,
                                    apiKey = groqApiKey,
                                    model = bot.model,
                                    botName = bot.name,
                                    aiProvider = bot.aiProvider,
                                    customApiKey = bot.customApiKey
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: e.message ?: "Koneksi Bermasalah"
                Log.e("BotService", "Polling error for ${bot.name}: $errorMsg")
                if (errorMsg.contains("401") || errorMsg.contains("Unauthorized") || errorMsg.contains("403") || errorMsg.contains("Forbidden")) {
                    repository.addLog("ERROR", "⚠️ DETEKSI: Token Bot [${bot.name}] telah diganti atau dinonaktifkan di BotFather! Polling dihentikan.")
                    
                    // Set status to false in DB so owner or users know
                    val settings = repository.getSettings()
                    if (settings != null && settings.telegramToken == bot.token) {
                        repository.saveSettings(settings.copy(isBotRunning = false))
                    }
                    
                    val activeUsers = repository.getActiveRegisteredUsers()
                    val matchingUser = activeUsers.find { it.telegramToken == bot.token }
                    if (matchingUser != null) {
                        repository.saveRegisteredUser(matchingUser.copy(isActive = false))
                    }
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
        incomingMessage: TelegramMessage,
        textMessage: String,
        senderName: String,
        systemInstruction: String,
        apiKey: String,
        model: String,
        botName: String,
        aiProvider: String,
        customApiKey: String
    ) {
        val trimmedText = textMessage.trim()
        val messageId = incomingMessage.messageId

        if (trimmedText.equals("/start", ignoreCase = true)) {
            val welcomeText = """
                👋 *Halo $senderName! Selamat datang di AI Bot Setup!*
                
                Saya adalah asisten AI Anda yang siap melayani 24 jam non-stop ditenagai oleh kecerdasan buatan super cepat.
                
                ⚙️ *Konfigurasi AI Aktif di Bot Ini:*
                - 🧠 *Mesin AI:* ${aiProvider}
                - 🤖 *Model:* ${model}
                - 🔑 *Kunci API Kustom:* ${if (customApiKey.isNotBlank()) "✅ Terpasang (${customApiKey.take(7)}...)" else "❌ Tidak Ada (Menggunakan sistem default)"}
                
                🛠️ *Perintah Konfigurasi AI:*
                - `/pilihai <groq_atau_gemini>` - Pilih mesin kecerdasan buatan
                - `/pilihmodel <nama_model>` - Ganti model AI yang digunakan aktif
                - `/masukanapikey <api_key>` - Setel kunci API kustom pribadi Anda
                
                🤖 *Bagian Pendaftaran Bot Baru:*
                Anda dapat mendaftarkan bot Telegram Anda sendiri untuk di-host di sistem kami dengan menggunakan perintah `/register`:
                Format: `/register <Nama Anda> | <No WhatsApp> | <Token BotFather>`
                Contoh: `/register Riza | 08123456789 | 12345:AAH_xyz`
                
                💵 *Langkah Aktivasi Bot:*
                Setelah mendaftar, lakukan donasi *Rp 50.000* ke:
                🔗 https://img.sanishtech.com/u/af27dc554e09173a9dfeb872c74e9038.jpg
                Dan gunakan perintah berikut untuk aktivasi instan agar bot langsung online:
                👉 `/admin/acc <@username_bot_atau_id_pendaftaran>`
                
                *Daftar Perintah Menarik yang Tersedia:*
                🎙️ `/tts <teks>` - Mengubah teks menjadi audio suara
                🖼️ `/imagen <deskripsi>` - Membuat gambar AI dari deskripsi
                🎬 `/veo3 <deskripsi>` - Membuat video/animasi AI dari deskripsi
                💥 `/spamngl <link_ngl> | <pesan> | <jumlah>` - Kirim spam pesan NGL Link
                ✨ `/hdvideo` - Tingkatkan kualitas video / lampiran video menjadi HD (Bisa dengan me-reply video/dokumen)
                🔌 `/cektagihanpln <id_pelanggan>` - Memeriksa tagihan listrik PLN
                📰 `/ccn` atau `/cnn` - Membaca berita hangat CNN Indonesia terbaru
                💳 `/pembayaran` - Menampilkan QRIS pembayaran resmi (langsung muncul gambar)
                🎁 `/donate` - Berikan dukungan donasi ke Riza Store
                🔑 `/gantitoken <nomor_wa_atau_owner> | <token_baru>` - Perbarui token bot dari @BotFather
                
                Silakan ketik atau pilih perintah yang Anda inginkan di bawah! 👇
            """.trimIndent()
            
            repository.sendTelegramMessage(
                token = token,
                chatId = chatId,
                text = welcomeText,
                replyToMessageId = messageId
            )
            return
        }

        if (trimmedText.startsWith("/pilihai")) {
            val providerParam = trimmedText.removePrefix("/pilihai").trim().lowercase()
            if (providerParam.isEmpty()) {
                val currentInfo = """
                    🧠 *Mesin AI Yang Digunakan:* *$aiProvider*
                    
                    Hubungi perintah ini untuk mengganti mesin AI yang melayani bot ini.
                    
                    *Pilihan Mesin:*
                    - *Groq* (Kecerdasan kilat, respons secepat kilat)
                    - *Gemini* (Pengetahuan luas dan pemahaman tingkat tinggi)
                    
                    *Format:* `/pilihai <groq_atau_gemini>`
                    *Contoh:* `/pilihai gemini` atau `/pilihai groq`
                """.trimIndent()
                repository.sendTelegramMessage(token, chatId, currentInfo, replyToMessageId = messageId)
                return
            }
            
            val validProvider = when (providerParam) {
                "groq" -> "Groq"
                "gemini" -> "Gemini"
                else -> null
            }
            
            if (validProvider == null) {
                repository.sendTelegramMessage(
                    token = token,
                    chatId = chatId,
                    text = "⚠️ *Mesin AI tidak dikenali!*\n\nSilakan pilih salah satu mesin berikut:\n- `groq`\n- `gemini`",
                    replyToMessageId = messageId
                )
                return
            }
            
            val success = repository.updateBotProvider(token, validProvider)
            if (success) {
                repository.sendTelegramMessage(
                    token = token,
                    chatId = chatId,
                    text = "✅ *BERHASIL DIUBAH!*\n\nBot Anda kini ditenagai oleh mesin AI: *$validProvider*.",
                    replyToMessageId = messageId
                )
                repository.addLog("SUCCESS", "Bot [$botName] mengubah mesin AI ke $validProvider")
            } else {
                repository.sendTelegramMessage(
                    token = token,
                    chatId = chatId,
                    text = "❌ *Gagal mengubah mesin AI!*",
                    replyToMessageId = messageId
                )
            }
            return
        }

        if (trimmedText.startsWith("/pilihmodel")) {
            val modelParam = trimmedText.removePrefix("/pilihmodel").trim()
            if (modelParam.isEmpty()) {
                val currentInfo = """
                    🤖 *Model AI Yang Digunakan:* *$model*
                    
                    Gunakan perintah ini untuk mengganti nama model AI yang aktif.
                    
                    *Rekomendasi Model Sesuai Mesin:*
                    
                    🔥 *Model Groq:*
                    - `llama-3.1-8b-instant` (Default cepat)
                    - `llama3-8b-8192` (Llama 8B)
                    - `llama-3.1-70b-versatile` (Llama premium)
                    - `gemma2-9b-it` (Google Gemma 9B)
                    - `mixtral-8x7b-32768` (Mixtral MoE)
                    
                    ✨ *Model Gemini:*
                    - `gemini-1.5-flash` (Pilihan seimbang & cepat)
                    - `gemini-1.5-pro` (Kecerdasan reasoning tinggi)
                    - `gemini-2.5-flash` (Generasi terbaru instant)
                    - `gemini-2.5-pro` (Generasi terbaru advanced)
                    
                    *Format:* `/pilihmodel <nama_model>`
                    *Contoh:* `/pilihmodel gemini-1.5-flash`
                """.trimIndent()
                repository.sendTelegramMessage(token, chatId, currentInfo, replyToMessageId = messageId)
                return
            }
            
            val success = repository.updateBotModel(token, modelParam)
            if (success) {
                repository.sendTelegramMessage(
                    token = token,
                    chatId = chatId,
                    text = "✅ *BERHASIL DIUBAH!*\n\nBot Anda kini menggunakan model: *$modelParam*.",
                    replyToMessageId = messageId
                )
                repository.addLog("SUCCESS", "Bot [$botName] mengubah model ke $modelParam")
            } else {
                repository.sendTelegramMessage(
                    token = token,
                    chatId = chatId,
                    text = "❌ *Gagal mengubah model AI!*",
                    replyToMessageId = messageId
                )
            }
            return
        }

        if (trimmedText.startsWith("/masukanapikey")) {
            val keyParam = trimmedText.removePrefix("/masukanapikey").trim()
            if (keyParam.isEmpty()) {
                val statusText = if (customApiKey.isNotBlank()) {
                    "✅ *Terpasang:* `${customApiKey.take(7)}... ${customApiKey.takeLast(5)}`"
                } else {
                    "❌ *Belum Terpasang* (Menggunakan kunci default developer/sistem)"
                }
                
                val instructionText = """
                    🔑 *Pengaturan Kunci API Kustom (API Key Anda):*
                    Status saat ini: $statusText
                    
                    Gunakan perintah ini jika Anda ingin menggunakan kunci API pribadi Anda sendiri untuk menghindari limitasi server global.
                    
                    *Format:* `/masukanapikey <kunci_api>`
                    *Contoh:* `/masukanapikey gsk_123456789...` atau jika Gemini `/masukanapikey AIzaSy...`
                    
                    _Untuk mereset / menghapus kunci kustom Anda dan kembali menggunakan default sistem, ketik:_
                    `/masukanapikey reset`
                """.trimIndent()
                repository.sendTelegramMessage(token, chatId, instructionText, replyToMessageId = messageId)
                return
            }
            
            val finalKey = if (keyParam.lowercase() == "reset" || keyParam.lowercase() == "delete" || keyParam.lowercase() == "hapus") "" else keyParam
            val success = repository.updateBotApiKey(token, finalKey)
            if (success) {
                val confirmText = if (finalKey.isEmpty()) {
                    "🗑️ *Kunci API kustom telah berhasil dihapus!*\n\nBot Anda telah kembali menggunakan API Key default dari server kami."
                } else {
                    "✅ *Kunci API kustom berhasil dipasang!*\n\nKunci API: `${finalKey.take(7)}...` sekarang aktif melayani request Anda."
                }
                repository.sendTelegramMessage(
                    token = token,
                    chatId = chatId,
                    text = confirmText,
                    replyToMessageId = messageId
                )
                repository.addLog("SUCCESS", "Bot [$botName] mengubah API Key kustom")
            } else {
                repository.sendTelegramMessage(
                    token = token,
                    chatId = chatId,
                    text = "❌ *Gagal memasang kunci API kustom!*",
                    replyToMessageId = messageId
                )
            }
            return
        }

        if (trimmedText.startsWith("/register")) {
            val paramsText = trimmedText.removePrefix("/register").trim()
            if (paramsText.isEmpty()) {
                repository.sendTelegramMessage(
                    token = token,
                    chatId = chatId,
                    text = "Gunakan perintah ini untuk meregistrasikan bot Telegram baru Anda.\n\n*Format:* `/register <nama_anda> | <nomor_whatsapp> | <bot_token_telegram>`\n\n*Contoh:* `/register Joko | 08123456789 | 1234567890:ABCDefGHiJklmnOpqrstuvw_xyz`\n\n_Pastikan bot token diperoleh secara gratis dari @BotFather di Telegram._",
                    replyToMessageId = messageId
                )
                return
            }
            
            val parts = paramsText.split("|").map { it.trim() }
            if (parts.size < 3) {
                repository.sendTelegramMessage(
                    token = token,
                    chatId = chatId,
                    text = "Format parameter salah atau kurang lengkap.\n\n*Format:* `/register <nama_anda> | <nomor_whatsapp> | <bot_token_telegram>`\n\n*Contoh:* `/register Joko | 08123456789 | 1234567890:ABCDefGHiJklmn...`",
                    replyToMessageId = messageId
                )
                return
            }
            
            val clientName = parts[0]
            val clientWhatsapp = parts[1]
            val clientToken = parts[2]
            
            if (clientName.isEmpty() || clientWhatsapp.isEmpty() || clientToken.isEmpty()) {
                repository.sendTelegramMessage(
                    token = token,
                    chatId = chatId,
                    text = "⚠️ Nama, nomor WhatsApp, dan Token Bot Telegram wajib diisi secara lengkap!",
                    replyToMessageId = messageId
                )
                return
            }
            
            try {
                repository.addLog("INFO", "Bot [$botName]: Memproses registrasi bot baru untuk $clientName via Telegram...")
                
                val botInfo = repository.validateTelegramBot(clientToken)
                val defaultModel = "llama-3.1-8b-instant"
                val priceDefault = 50000.0
                
                val newUser = RegisteredUser(
                    name = clientName,
                    whatsappNumber = clientWhatsapp,
                    telegramToken = clientToken,
                    selectedModel = defaultModel,
                    price = priceDefault,
                    isActive = false, // Initially inactive
                    botUsername = botInfo.username ?: "",
                    botFirstName = botInfo.firstName
                )
                
                val insertedId = repository.saveRegisteredUser(newUser)
                
                val successMessage = """
                    📝 *PENDAFTARAN BOT BERHASIL DIAJUKAN!* 🚀
                    
                    Halo *$clientName*, data pendirian bot Anda telah kami simpan dengan aman di sistem kami!
                    
                    *Detail Pendaftaran:*
                    - *ID Pendaftaran:* #$insertedId
                    - *Nama Bot:* ${botInfo.firstName}
                    - *Username Bot:* @${botInfo.username}
                    - *Model Utama:* $defaultModel
                    - *Status:* ⏳ *PENDING / BELUM AKTIF*
                    
                    💳 *Langkah Aktivasi:*
                    Untuk mengaktifkan bot Anda agar segera online 24 jam non-stop, silakan lakukan donasi terlebih dahulu sebesar *Rp 50.000* melalui link QRIS berikut:
                    🔗 https://img.sanishtech.com/u/af27dc554e09173a9dfeb872c74e9038.jpg
                    
                    Setelah Anda melakukan pembayaran/donasi, silakan kirimkan perintah aktivasi berikut langsung di chat ini untuk mengaktifkan bot Anda secara instan:
                    👉 `/admin/acc @${botInfo.username}`
                    
                    Atau jika ingin mengaktifkan dengan ID pendaftaran:
                    👉 `/admin/acc $insertedId`
                """.trimIndent()
                
                repository.sendTelegramMessage(
                    token = token,
                    chatId = chatId,
                    text = successMessage,
                    replyToMessageId = messageId
                )
                repository.addLog("SUCCESS", "Registrasi Pending (Menunggu Donasi) via Telegram! Bot @${botInfo.username} didaftarkan oleh $clientName")
            } catch (e: Exception) {
                val errMsg = e.localizedMessage ?: e.message ?: "Error"
                repository.addLog("ERROR", "Registrasi via Telegram gagal: $errMsg")
                repository.sendTelegramMessage(
                    token = token,
                    chatId = chatId,
                    text = "❌ *Registrasi Gagal!*\n\n*Penyebab:* $errMsg\n\nHarap periksa kembali token bot Anda dari @BotFather dan pastikan formatnya sudah benar.",
                    replyToMessageId = messageId
                )
            }
            return
        }

        if (trimmedText.startsWith("/gantitoken")) {
            val paramsText = trimmedText.removePrefix("/gantitoken").trim()
            if (paramsText.isEmpty()) {
                val instructionText = """
                    🔑 *Ubah / Ganti Token Bot Telegram* ⚙️
                    
                    Gunakan perintah ini untuk memperbarui token bot Anda jika Anda telah meregenerasi token baru di @BotFather.
                    
                    *Format untuk User:*
                    👉 `/gantitoken <nomor_whatsapp> | <token_baru_botfather>`
                    *Contoh:* `/gantitoken 08123456789 | 12345678:AAH_newtokenxyz...`
                    
                    *Format untuk Owner (Sistem Utama):*
                    👉 `/gantitoken owner | <token_baru_botfather>`
                """.trimIndent()
                repository.sendTelegramMessage(token, chatId, instructionText, replyToMessageId = messageId)
                return
            }
            
            val parts = paramsText.split("|").map { it.trim() }
            if (parts.size < 2) {
                repository.sendTelegramMessage(
                    token = token,
                    chatId = chatId,
                    text = "⚠️ *Format salah!* Gunakan:\n`/gantitoken <nomor_whatsapp_atau_owner> | <token_baru>`",
                    replyToMessageId = messageId
                )
                return
            }
            
            val targetKey = parts[0]
            val newToken = parts[1]
            
            try {
                repository.addLog("INFO", "Memvalidasi token baru dari @BotFather...")
                val botInfo = repository.validateTelegramBot(newToken)
                
                if (targetKey.lowercase() == "owner" || targetKey.lowercase() == "admin") {
                    val currentSettings = repository.getSettings()
                    if (currentSettings != null) {
                        repository.saveSettings(
                            currentSettings.copy(
                                telegramToken = newToken,
                                botUsername = botInfo.username ?: "",
                                botFirstName = botInfo.firstName
                            )
                        )
                        repository.addLog("SUCCESS", "Token Owner Utama berhasil diupdate ke @${botInfo.username}")
                        repository.sendTelegramMessage(
                            token = token,
                            chatId = chatId,
                            text = "✅ *BERHASIL!* Token Utama Owner telah diperbarui.\n\nBot baru Anda: @${botInfo.username} (${botInfo.firstName}).\nSystem akan otomatis restart polling dengan token baru.",
                            replyToMessageId = messageId
                        )
                    } else {
                        repository.sendTelegramMessage(token, chatId, "❌ Gagal: Pengaturan sistem utama tidak ditemukan di database.", replyToMessageId = messageId)
                    }
                } else {
                    val allUsers = repository.getAllRegisteredUsers()
                    val matchingUser = allUsers.find { it.whatsappNumber == targetKey || it.telegramToken == token }
                    if (matchingUser != null) {
                        repository.saveRegisteredUser(
                            matchingUser.copy(
                                telegramToken = newToken,
                                botUsername = botInfo.username ?: "",
                                botFirstName = botInfo.firstName,
                                isActive = true
                            )
                        )
                        repository.addLog("SUCCESS", "Token User [${matchingUser.name}] berhasil diupdate ke @${botInfo.username}")
                        repository.sendTelegramMessage(
                            token = token,
                            chatId = chatId,
                            text = "✅ *BERHASIL!* Token Bot milik *${matchingUser.name}* telah diperbarui.\n\nBot baru Anda: @${botInfo.username} (${botInfo.firstName}).\nBot akan otomatis aktif kembali.",
                            replyToMessageId = messageId
                        )
                    } else {
                        repository.sendTelegramMessage(
                            token = token,
                            chatId = chatId,
                            text = "❌ *Gagal!* Tidak dapat menemukan pengguna terdaftar dengan nomor WhatsApp ini.",
                            replyToMessageId = messageId
                        )
                    }
                }
            } catch (e: Exception) {
                val errMsg = e.localizedMessage ?: e.message ?: "Invalid Token"
                repository.addLog("ERROR", "Gagal memperbarui token: $errMsg")
                repository.sendTelegramMessage(
                    token = token,
                    chatId = chatId,
                    text = "❌ *Gagal memperbarui token!*\n\n*Error:* $errMsg\n\nPastikan token baru yang Anda masukkan valid dari @BotFather.",
                    replyToMessageId = messageId
                )
            }
            return
        }

        if (trimmedText.startsWith("/admin/acc")) {
            val param = trimmedText.removePrefix("/admin/acc").trim()
            if (param.isEmpty()) {
                repository.sendTelegramMessage(
                    token = token,
                    chatId = chatId,
                    text = "⚠️ *Format Perintah Salah!*\n\nGunakan perintah ini untuk menyetujui (ACC) dan meluncurkan bot Telegram yang didaftarkan.\n\n*Format:* `/admin/acc <id_pendaftaran_atau_@username_bot>`\n\n*Contoh:* `/admin/acc @${botName}` atau `/admin/acc 1`",
                    replyToMessageId = messageId
                )
                return
            }
            
            try {
                repository.addLog("INFO", "Bot [$botName]: Memproses aktivasi /admin/acc untuk parameter: $param")
                val allUsers = repository.getAllRegisteredUsers()
                
                // Try to find the matching user
                val matchedUser = allUsers.find { user ->
                    val cleanParam = param.removePrefix("@").trim().lowercase()
                    val cleanBotUsername = user.botUsername.lowercase()
                    val cleanName = user.name.lowercase()
                    
                    user.id.toString() == param ||
                    cleanBotUsername == cleanParam ||
                    cleanName == cleanParam ||
                    user.telegramToken == param
                }
                
                if (matchedUser == null) {
                    repository.sendTelegramMessage(
                        token = token,
                        chatId = chatId,
                        text = "❌ *Pendaftaran Tidak Ditemukan!*\n\nTidak dapat menemukan data pendaftaran berstatus pending dengan ID, username, atau nama: *\"$param\"*.",
                        replyToMessageId = messageId
                    )
                    return
                }
                
                if (matchedUser.isActive) {
                    repository.sendTelegramMessage(
                        token = token,
                        chatId = chatId,
                        text = "⚠️ *Informasi:* Bot *@${matchedUser.botUsername}* (${matchedUser.name}) sudah dalam keadaan *aktif* sebelumnya!",
                        replyToMessageId = messageId
                    )
                    return
                }
                
                // Activate the bot
                val activatedUser = matchedUser.copy(isActive = true)
                repository.saveRegisteredUser(activatedUser)
                
                val activationSuccessMsg = """
                    ✅ *BOT BERHASIL DIAKTIFKAN!* 🚀
                    
                    Selamat! Bot Telegram Anda sekarang telah *AKTIF* di sistem kami dan langsung dideploy sebagai bot Telegram mandiri secara instan!
                    
                    *Detail Bot Aktif:*
                    - *Pemilik:* ${activatedUser.name}
                    - *Nama Bot:* ${activatedUser.botFirstName}
                    - *Username Bot:* @${activatedUser.botUsername}
                    - *Status:* 🟢 *ONLINE / RUNNING*
                    
                    Silakan mulai chat dengan bot Anda di: @${activatedUser.botUsername} !
                """.trimIndent()
                
                repository.sendTelegramMessage(
                    token = token,
                    chatId = chatId,
                    text = activationSuccessMsg,
                    replyToMessageId = messageId
                )
                
                repository.addLog("SUCCESS", "Bot @${activatedUser.botUsername} berhasil diaktifkan melalui /admin/acc!")
            } catch (e: Exception) {
                val errMsg = e.localizedMessage ?: e.message ?: "Error"
                repository.addLog("ERROR", "Gagal memproses /admin/acc: $errMsg")
                repository.sendTelegramMessage(
                    token = token,
                    chatId = chatId,
                    text = "❌ *Gagal Mengaktifkan Bot!*\n\n*Error:* $errMsg",
                    replyToMessageId = messageId
                )
            }
            return
        }
        
        if (trimmedText.startsWith("/pembayaran")) {
            val caption = """
                💳 *Layanan Pembayaran QRIS* 🚀
                
                Silakan scan kode QRIS resmi di atas untuk melakukan pembayaran atau aktivasi bot Anda.
                
                _Setelah melakukan pembayaran, Anda dapat mengaktifkan bot Anda dengan perintah:_
                👉 `/admin/acc <@username_bot>`
            """.trimIndent()
            try {
                repository.sendTelegramPhotoUrl(
                    token = token,
                    chatId = chatId,
                    photoUrl = "https://img.sanishtech.com/u/af27dc554e09173a9dfeb872c74e9038.jpg",
                    caption = caption,
                    replyToMessageId = messageId
                )
            } catch (e: Exception) {
                // fallback to text message if photo fails
                val paymentMsg = """
                    💳 *Layanan Pembayaran QRIS* 🚀
                    
                    Gagal menampilkan gambar QRIS secara langsung. Silakan scan atau kunjungi link QRIS resmi kami di bawah ini:
                    
                    🔗 https://img.sanishtech.com/u/af27dc554e09173a9dfeb872c74e9038.jpg
                    
                    _Setelah melakukan pembayaran, Anda dapat mengaktifkan bot Anda dengan perintah:_
                    👉 `/admin/acc <@username_bot>`
                """.trimIndent()
                repository.sendTelegramMessage(
                    token = token,
                    chatId = chatId,
                    text = paymentMsg,
                    replyToMessageId = messageId
                )
            }
            return
        }

        if (trimmedText.startsWith("/donate")) {
            val donateMsg = """
                🎁 *Donasi Riza Store* 💖
                
                Terima kasih atas minat Anda untuk mendukung perkembangan layanan kami! Anda dapat menyalurkan donasi finansial melalui link pembayaran berikut:
                
                🔗 https://qris.zone.id/rizastore?payment_id=rizastore-fxi5sziq
                
                Dukungan Anda sangat berarti bagi kelangsungan server 24 jam non-stop!
            """.trimIndent()
            repository.sendTelegramMessage(
                token = token,
                chatId = chatId,
                text = donateMsg,
                replyToMessageId = messageId
            )
            return
        }

        if (trimmedText.startsWith("/tts")) {
            val targetText = trimmedText.removePrefix("/tts").trim()
            if (targetText.isEmpty()) {
                repository.sendTelegramMessage(
                    token = token,
                    chatId = chatId,
                    text = "Gunakan perintah ini untuk mengubah teks menjadi suara (Text-to-Speech).\n\n*Format:* `/tts <teks yang ingin diucapkan>`",
                    replyToMessageId = messageId
                )
                return
            }
            try {
                repository.addLog("INFO", "Bot [$botName]: Memproses TTS untuk teks: \"$targetText\"...")
                val voiceBytes = repository.generateGroqSpeech(apiKey, targetText)
                
                val tempFile = File.createTempFile("tts_", ".mp3", applicationContext.cacheDir)
                tempFile.writeBytes(voiceBytes)
                
                repository.sendTelegramVoice(token, chatId, tempFile, replyToMessageId = messageId)
                tempFile.delete()
                repository.addLog("SUCCESS", "Bot [$botName]: Sukses mengirim TTS voice note ke @$senderName")
            } catch (e: Exception) {
                val errMsg = e.localizedMessage ?: e.message ?: "Error"
                repository.addLog("ERROR", "Bot [$botName] gagal memproses TTS: $errMsg")
                try {
                    repository.sendTelegramMessage(token, chatId, "Gagal mengolah teks ke suara: $errMsg", replyToMessageId = messageId)
                } catch (ignored: Exception) {}
            }
            return
        }
        
        if (trimmedText.startsWith("/imagen")) {
            var targetPrompt = trimmedText.removePrefix("/imagen").trim()
            if (targetPrompt.isEmpty()) {
                repository.sendTelegramMessage(
                    token = token,
                    chatId = chatId,
                    text = "Gunakan perintah ini untuk membuat gambar AI via Nexray.\n\n*Format:* `/imagen <deskripsi_gambar> [--ratio <lebar:tinggi>]`",
                    replyToMessageId = messageId
                )
                return
            }
            try {
                // Parse ratio
                var ratio = "1:1"
                val ratioRegex = """--ratio\s+(\S+)""".toRegex(RegexOption.IGNORE_CASE)
                val match = ratioRegex.find(targetPrompt)
                if (match != null) {
                    ratio = match.groupValues[1]
                    targetPrompt = targetPrompt.replace(match.value, "").trim()
                }
                
                repository.addLog("INFO", "Bot [$botName]: Membuat gambar AI untuk prompt: \"$targetPrompt\" (ratio: $ratio)...")
                val imageBytes = repository.generateImageNexray(targetPrompt, ratio)
                
                val tempFile = File.createTempFile("img_", ".png", applicationContext.cacheDir)
                tempFile.writeBytes(imageBytes)
                
                repository.sendTelegramPhoto(
                    token = token,
                    chatId = chatId,
                    file = tempFile,
                    caption = "Prompt: \"$targetPrompt\"\nRatio: $ratio",
                    replyToMessageId = messageId
                )
                tempFile.delete()
                repository.addLog("SUCCESS", "Bot [$botName]: Sukses mengirim gambar AI ke @$senderName")
            } catch (e: Exception) {
                val errMsg = e.localizedMessage ?: e.message ?: "Error"
                repository.addLog("ERROR", "Bot [$botName] gagal memproses Gambar Nexray: $errMsg")
                try {
                    repository.sendTelegramMessage(token, chatId, "Gagal membuat gambar: $errMsg", replyToMessageId = messageId)
                } catch (ignored: Exception) {}
            }
            return
        }
        
        if (trimmedText.startsWith("/veo3")) {
            try {
                var targetPrompt = trimmedText.removePrefix("/veo3").trim()
                var imageUrl: String? = null
                
                // 1. Check if foto is attached directly to this message
                val attachedPhoto = incomingMessage.photo?.lastOrNull()
                if (attachedPhoto != null) {
                    val fileResponse = repository.getTelegramFile(token, attachedPhoto.fileId)
                    val filePath = fileResponse.result?.filePath
                    if (filePath != null) {
                        imageUrl = "https://api.telegram.org/file/bot$token/$filePath"
                    }
                }
                
                // 2. Check if replying to a photo
                if (imageUrl == null) {
                    val repliedPhoto = incomingMessage.replyToMessage?.photo?.lastOrNull()
                    if (repliedPhoto != null) {
                        val fileResponse = repository.getTelegramFile(token, repliedPhoto.fileId)
                        val filePath = fileResponse.result?.filePath
                        if (filePath != null) {
                            imageUrl = "https://api.telegram.org/file/bot$token/$filePath"
                        }
                    }
                }
                
                // 3. Check for explicit image URL separation with |
                val pipeIndex = targetPrompt.indexOf("|")
                if (pipeIndex != -1) {
                    val parsedUrl = targetPrompt.substring(pipeIndex + 1).trim()
                    targetPrompt = targetPrompt.substring(0, pipeIndex).trim()
                    if (parsedUrl.startsWith("http")) {
                        imageUrl = parsedUrl
                    }
                }
                
                if (imageUrl == null) {
                    repository.sendTelegramMessage(
                        token = token,
                        chatId = chatId,
                        text = "Gunakan perintah ini untuk membuat video/animasi AI (Veo3) dari sebuah foto.\n\n*Format Penggunaan:*\n1. Upload foto dengan caption: `/veo3 [instruksi]`\n2. Balas foto di chat dengan pesan: `/veo3 [instruksi]`\n3. Ketik link langsung: `/veo3 [instruksi] | <link_foto_direct_http>`",
                        replyToMessageId = messageId
                    )
                    return
                }
                
                if (targetPrompt.isEmpty()) {
                    targetPrompt = "An elegant look around camera movement"
                }
                
                repository.addLog("INFO", "Bot [$botName]: Memproses veo3 video dengan prompt: \"$targetPrompt\"...")
                val videoBytes = repository.generateVeo3Nexray(targetPrompt, imageUrl)
                
                val tempFile = File.createTempFile("veo3_", ".mp4", applicationContext.cacheDir)
                tempFile.writeBytes(videoBytes)
                
                repository.sendTelegramVideo(
                    token = token,
                    chatId = chatId,
                    file = tempFile,
                    caption = "Veo3 Prompt: \"$targetPrompt\"",
                    replyToMessageId = messageId
                )
                tempFile.delete()
                repository.addLog("SUCCESS", "Bot [$botName]: Sukses mengirim veo3 video ke @$senderName")
            } catch (e: Exception) {
                val errMsg = e.localizedMessage ?: e.message ?: "Error"
                repository.addLog("ERROR", "Bot [$botName] gagal memproses veo3 video: $errMsg")
                try {
                    repository.sendTelegramMessage(token, chatId, "Gagal membuat video veo3: $errMsg", replyToMessageId = messageId)
                } catch (ignored: Exception) {}
            }
            return
        }

        if (trimmedText.startsWith("/spamngl")) {
            val paramsText = trimmedText.removePrefix("/spamngl").trim()
            if (paramsText.isEmpty()) {
                repository.sendTelegramMessage(
                    token = token,
                    chatId = chatId,
                    text = "Gunakan perintah ini untuk mengirim spam pesan ke NGL Link.\n\n*Format:* `/spamngl <url_ngl> | <pesan> | <jumlah>`\n\n*Atau tanpa pipa:* `/spamngl <url_ngl> <pesan> <jumlah>`\n\n*Contoh:* `/spamngl https://ngl.link/johndoe | Salken ya! | 5`",
                    replyToMessageId = messageId
                )
                return
            }
            
            val parts = if (paramsText.contains("|")) {
                paramsText.split("|").map { it.trim() }
            } else {
                val tokens = paramsText.split("\\s+".toRegex()).map { it.trim() }
                if (tokens.size >= 3) {
                    val url = tokens.first()
                    val count = tokens.last()
                    val msg = tokens.subList(1, tokens.size - 1).joinToString(" ")
                    listOf(url, msg, count)
                } else {
                    emptyList()
                }
            }
            
            if (parts.size < 3) {
                repository.sendTelegramMessage(
                    token = token,
                    chatId = chatId,
                    text = "Format parameter salah atau kurang lengkap.\n\n*Format:* `/spamngl <url_ngl> | <pesan> | <jumlah>`\n\n*Contoh:* `/spamngl https://ngl.link/johndoe | Salken ya! | 5`",
                    replyToMessageId = messageId
                )
                return
            }
            
            val nglUrl = parts[0]
            val pesan = parts[1]
            val jumlah = parts[2]
            
            val num = jumlah.toIntOrNull()
            if (num == null || num <= 0 || num > 50) {
                repository.sendTelegramMessage(
                    token = token,
                    chatId = chatId,
                    text = "Jumlah spam harus berupa angka positif antara 1 - 50.",
                    replyToMessageId = messageId
                )
                return
            }
            
            try {
                repository.addLog("INFO", "Bot [$botName]: Menjalankan Spam NGL ke $nglUrl sebanyak $jumlah kali...")
                val apiResponse = repository.spamNgl(nglUrl, pesan, jumlah)
                val cleanResponse = repository.formatJsonToIndonesian(apiResponse)
                
                repository.sendTelegramMessage(
                    token = token,
                    chatId = chatId,
                    text = "✅ *Spam NGL Berhasil Dikirim!*\n\n*Target:* $nglUrl\n*Pesan:* \"$pesan\"\n*Jumlah:* $jumlah\n\n*Respon API:*\n$cleanResponse",
                    replyToMessageId = messageId
                )
                repository.addLog("SUCCESS", "Bot [$botName]: Sukses spam NGL ke $nglUrl")
            } catch (e: Exception) {
                val errMsg = e.localizedMessage ?: e.message ?: "Error"
                repository.addLog("ERROR", "Bot [$botName] gagal memproses Spam NGL: $errMsg")
                try {
                    repository.sendTelegramMessage(token, chatId, "Gagal menjalankan Spam NGL: $errMsg", replyToMessageId = messageId)
                } catch (ignored: Exception) {}
            }
            return
        }
        
        if (trimmedText.startsWith("/hdvideo")) {
            try {
                var targetUrl: String? = null
                val userParam = trimmedText.removePrefix("/hdvideo").trim()
                
                // A. Check for direct video in current message
                val attachedVideo = incomingMessage.video
                if (attachedVideo != null) {
                    val fileResponse = repository.getTelegramFile(token, attachedVideo.fileId)
                    val filePath = fileResponse.result?.filePath
                    if (filePath != null) {
                        targetUrl = "https://api.telegram.org/file/bot$token/$filePath"
                    }
                }
                
                // B. Check for document in current message
                if (targetUrl == null) {
                    val attachedDoc = incomingMessage.document
                    if (attachedDoc != null) {
                        val fileResponse = repository.getTelegramFile(token, attachedDoc.fileId)
                        val filePath = fileResponse.result?.filePath
                        if (filePath != null) {
                            targetUrl = "https://api.telegram.org/file/bot$token/$filePath"
                        }
                    }
                }
                
                // C. Check for replied message's video
                if (targetUrl == null) {
                    val repliedVideo = incomingMessage.replyToMessage?.video
                    if (repliedVideo != null) {
                        val fileResponse = repository.getTelegramFile(token, repliedVideo.fileId)
                        val filePath = fileResponse.result?.filePath
                        if (filePath != null) {
                            targetUrl = "https://api.telegram.org/file/bot$token/$filePath"
                        }
                    }
                }
                
                // D. Check for replied message's document
                if (targetUrl == null) {
                    val repliedDoc = incomingMessage.replyToMessage?.document
                    if (repliedDoc != null) {
                        val fileResponse = repository.getTelegramFile(token, repliedDoc.fileId)
                        val filePath = fileResponse.result?.filePath
                        if (filePath != null) {
                            targetUrl = "https://api.telegram.org/file/bot$token/$filePath"
                        }
                    }
                }
                
                // E. Check for URL string in parameter
                if (targetUrl == null && userParam.startsWith("http")) {
                    targetUrl = userParam
                }
                
                if (targetUrl == null) {
                    repository.sendTelegramMessage(
                        token = token,
                        chatId = chatId,
                        text = "Gunakan perintah ini untuk meningkatkan kualitas video menjadi HD.\n\n*Format Penggunaan:*\n1. Upload video / animasi dengan caption: `/hdvideo`\n2. Balas pesan video di chat dengan pesan: `/hdvideo`\n3. Kirim link langsung: `/hdvideo <link_video_http>`",
                        replyToMessageId = messageId
                    )
                    return
                }
                
                repository.addLog("INFO", "Bot [$botName]: Memproses HD Video untuk URL: $targetUrl...")
                val videoBytes = repository.hdVideo(targetUrl)
                
                val tempFile = File.createTempFile("hdvideo_", ".mp4", applicationContext.cacheDir)
                tempFile.writeBytes(videoBytes)
                
                repository.sendTelegramVideo(
                    token = token,
                    chatId = chatId,
                    file = tempFile,
                    caption = "✨ Sukses meningkatkan kualitas video ke HD!",
                    replyToMessageId = messageId
                )
                tempFile.delete()
                repository.addLog("SUCCESS", "Bot [$botName]: Sukses mengirim HD Video ke @$senderName")
            } catch (e: Exception) {
                val errMsg = e.localizedMessage ?: e.message ?: "Error"
                repository.addLog("ERROR", "Bot [$botName] gagal memproses HD Video: $errMsg")
                try {
                    repository.sendTelegramMessage(token, chatId, "Gagal meningkatkan kualitas video: $errMsg", replyToMessageId = messageId)
                } catch (ignored: Exception) {}
            }
            return
        }
        
        if (trimmedText.startsWith("/cektagihanpln")) {
            val nopel = trimmedText.removePrefix("/cektagihanpln").trim()
            if (nopel.isEmpty()) {
                repository.sendTelegramMessage(
                    token = token,
                    chatId = chatId,
                    text = "Gunakan perintah ini untuk memeriksa tagihan PLN pascabayar.\n\n*Format:* `/cektagihanpln <id_pelanggan>`\n\n*Contoh:* `/cektagihanpln 530000000001`",
                    replyToMessageId = messageId
                )
                return
            }
            
            try {
                repository.addLog("INFO", "Bot [$botName]: Memeriksa tagihan PLN untuk ID: $nopel...")
                val apiResponse = repository.cekTagihanPln(nopel)
                val formattedDetails = repository.formatJsonToIndonesian(apiResponse)
                
                repository.sendTelegramMessage(
                    token = token,
                    chatId = chatId,
                    text = "🔌 *HASIL CEK TAGIHAN PLN*\n\n$formattedDetails",
                    replyToMessageId = messageId
                )
                repository.addLog("SUCCESS", "Bot [$botName]: Sukses cek tagihan PLN untuk ID: $nopel")
            } catch (e: Exception) {
                val errMsg = e.localizedMessage ?: e.message ?: "Error"
                repository.addLog("ERROR", "Bot [$botName] gagal cek tagihan PLN: $errMsg")
                try {
                    repository.sendTelegramMessage(token, chatId, "Gagal memeriksa tagihan PLN: $errMsg", replyToMessageId = messageId)
                } catch (ignored: Exception) {}
            }
            return
        }
        
        if (trimmedText.startsWith("/ccn") || trimmedText.startsWith("/cnn")) {
            try {
                repository.addLog("INFO", "Bot [$botName]: Mengambil berita terbaru dari CNN Indonesia...")
                val apiResponse = repository.getCnnNews()
                val formattedNews = repository.formatCnnNews(apiResponse)
                
                repository.sendTelegramMessage(
                    token = token,
                    chatId = chatId,
                    text = formattedNews,
                    replyToMessageId = messageId
                )
                repository.addLog("SUCCESS", "Bot [$botName]: Sukses mengirim berita CNN ke @$senderName")
            } catch (e: Exception) {
                val errMsg = e.localizedMessage ?: e.message ?: "Error"
                repository.addLog("ERROR", "Bot [$botName] gagal mengambil berita CNN: $errMsg")
                try {
                    repository.sendTelegramMessage(token, chatId, "Gagal mengambil berita: $errMsg", replyToMessageId = messageId)
                } catch (ignored: Exception) {}
            }
            return
        }
        
        try {
            val enrichedPrompt = "Seorang pengguna bernama $senderName berinteraksi dengan Anda di bot Telegram. Dia berkata: \"$textMessage\". Harap balas dengan sopan sesuai instruksi sistem."
            val aiResponse = if (aiProvider == "Gemini") {
                repository.addLog("INFO", "Menghubungi Gemini AI ($model) untuk bot [$botName]...")
                val activeApiKey = if (customApiKey.isNotBlank()) customApiKey else ""
                repository.askGemini(enrichedPrompt, activeApiKey, model, systemInstruction)
            } else {
                repository.addLog("INFO", "Menghubungi Groq AI ($model) untuk bot [$botName]...")
                val activeApiKey = if (customApiKey.isNotBlank()) customApiKey else apiKey
                repository.askGroq(enrichedPrompt, activeApiKey, model, systemInstruction)
            }

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
