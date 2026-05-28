package com.example.data

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class BotRepository(private val botDao: BotDao) {

    val settingsFlow: Flow<BotSettings?> = botDao.getSettingsFlow()
    val logsFlow: Flow<List<BotLog>> = botDao.getLogsFlow()

    suspend fun getSettings(): BotSettings? = withContext(Dispatchers.IO) {
        botDao.getSettings()
    }

    suspend fun saveSettings(settings: BotSettings) = withContext(Dispatchers.IO) {
        botDao.saveSettings(settings)
    }

    suspend fun addLog(level: String, message: String) = withContext(Dispatchers.IO) {
        botDao.insertLog(BotLog(level = level, message = message))
    }

    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        botDao.clearLogs()
    }

    /**
     * Validates a Telegram Bot API Token by fetching Bot details.
     * Returns BotInfo or throws Exception
     */
    suspend fun validateTelegramBot(token: String): TelegramBotInfo = withContext(Dispatchers.IO) {
        val url = "https://api.telegram.org/bot$token/getMe"
        val response = NetworkClient.telegramService.getMe(url)
        if (response.ok && response.result != null) {
            response.result
        } else {
            throw Exception(response.description ?: "Token Telegram tidak valid")
        }
    }

    /**
     * Fetches new updates/messages from Telegram using Long Polling.
     */
    suspend fun fetchTelegramUpdates(
        token: String,
        offset: Long?,
        timeout: Int? = 10
    ): List<TelegramUpdate> = withContext(Dispatchers.IO) {
        val url = "https://api.telegram.org/bot$token/getUpdates"
        try {
            val response = NetworkClient.telegramService.getUpdates(
                url = url,
                offset = offset,
                timeout = timeout,
                limit = 10
            )
            if (response.ok) {
                response.result ?: emptyList()
            } else {
                throw Exception(response.description ?: "Gagal mengambil update Telegram")
            }
        } catch (e: Exception) {
            // Rethrow so the loop can handle or log it
            throw e
        }
    }

    /**
     * Sends a reply to Telegram chat.
     */
    suspend fun sendTelegramMessage(
        token: String,
        chatId: Long,
        text: String,
        replyToMessageId: Long? = null
    ): TelegramMessage = withContext(Dispatchers.IO) {
        val url = "https://api.telegram.org/bot$token/sendMessage"
        val request = TelegramSendMessageRequest(
            chatId = chatId,
            text = text,
            replyToMessageId = replyToMessageId,
            parseMode = "Markdown"
        )
        val response = NetworkClient.telegramService.sendMessage(url, request)
        if (response.ok && response.result != null) {
            response.result
        } else {
            throw Exception(response.description ?: "Gagal mengirim pesan Telegram")
        }
    }

    /**
     * Sends prompt to Gemini API.
     */
    suspend fun askGemini(
        prompt: String,
        customApiKey: String? = null,
        systemInstruction: String = "Anda adalah asisten AI Telegram yang ramah."
    ): String = withContext(Dispatchers.IO) {
        val key = customApiKey?.takeIf { it.isNotBlank() } ?: BuildConfig.GEMINI_API_KEY
        if (key.isBlank() || key == "MY_GEMINI_API_KEY") {
            throw Exception("API Key Gemini belum dikonfigurasi. Harap masukkan API Key Gemini yang valid.")
        }

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(parts = listOf(GeminiPart(text = prompt)))
            ),
            systemInstruction = GeminiContent(
                parts = listOf(GeminiPart(text = systemInstruction))
            ),
            generationConfig = GeminiGenerationConfig(temperature = 0.7f)
        )

        try {
            // Use gemini-3.5-flash as default for basic text chat tasks
            val response = NetworkClient.geminiService.generateContent(
                model = "gemini-3.5-flash",
                apiKey = key,
                request = request
            )
            val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            replyText ?: throw Exception("Menerima respon kosong dari API Gemini.")
        } catch (e: retrofit2.HttpException) {
            val code = e.code()
            if (code == 429) {
                throw Exception("Gemini API Error: HTTPS 429 (Too Many Requests / Batas Limit Terlampaui). Harap tunggu beberapa saat atau ganti API Key Anda di menu Pengaturan.")
            } else if (code == 401) {
                throw Exception("Gemini API Error: HTTPS 401 (Tidak Diizinkan). API Key Gemini Anda tidak valid atau telah kedaluwarsa. Silakan periksa kembali dan ganti API Key Anda di menu Pengaturan.")
            } else {
                throw Exception("Gemini API Error: HTTP $code - ${e.message() ?: "Error terjadi pada server Google"}")
            }
        } catch (e: Exception) {
            val message = e.localizedMessage ?: e.message ?: "Unknown error"
            if (message.contains("429")) {
                throw Exception("Gemini API Error: HTTPS 429 (Too Many Requests / Batas Limit Terlampaui). Harap tunggu beberapa saat atau ganti API Key Anda di menu Pengaturan.")
            } else if (message.contains("401") || message.contains("Unauthorized", ignoreCase = true)) {
                throw Exception("Gemini API Error: HTTPS 401 (Tidak Diizinkan). API Key Gemini Anda tidak valid atau telah kedaluwarsa. Silakan periksa kembali dan ganti API Key Anda di menu Pengaturan.")
            }
            throw Exception("Gemini API Error: $message")
        }
    }
}
