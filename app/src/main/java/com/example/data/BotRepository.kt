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
     * Sends prompt to Groq API.
     */
    suspend fun askGroq(
        prompt: String,
        customApiKey: String? = null,
        model: String = "llama-3.1-8b-instant",
        systemInstruction: String = "Anda adalah asisten AI Telegram yang ramah."
    ): String = withContext(Dispatchers.IO) {
        val key = customApiKey?.takeIf { it.isNotBlank() } ?: ""
        if (key.isBlank()) {
            throw Exception("API Key Groq belum dikonfigurasi. Harap masukkan API Key Groq yang valid.")
        }

        val authHeader = "Bearer $key"
        val request = GroqRequest(
            model = model.trim(),
            messages = listOf(
                GroqMessage(role = "system", content = systemInstruction),
                GroqMessage(role = "user", content = prompt)
            ),
            temperature = 0.7f
        )

        try {
            val response = NetworkClient.groqService.generateChatCompletion(
                authHeader = authHeader,
                request = request
            )
            val replyText = response.choices?.firstOrNull()?.message?.content
            replyText ?: throw Exception("Menerima respon kosong dari API Groq.")
        } catch (e: retrofit2.HttpException) {
            val code = e.code()
            if (code == 429) {
                throw Exception("Groq API Error: HTTPS 429 (Too Many Requests / Batas Limit Terlampaui). Harap ganti API Key Anda atau kurangi jumlah request.")
            } else if (code == 401) {
                throw Exception("Groq API Error: HTTPS 401 (Tidak Diizinkan). API Key Groq Anda tidak valid atau telah kedaluwarsa. Silakan periksa kembali dan ganti API Key Anda di menu Pengaturan.")
            } else {
                throw Exception("Groq API Error: HTTP $code - ${e.message() ?: "Error terjadi pada server Groq"}")
            }
        } catch (e: Exception) {
            val message = e.localizedMessage ?: e.message ?: "Unknown error"
            if (message.contains("429")) {
                throw Exception("Groq API Error: HTTPS 429 (Too Many Requests / Batas Limit Terlampaui). Harap ganti API Key Anda.")
            } else if (message.contains("401") || message.contains("Unauthorized", ignoreCase = true)) {
                throw Exception("Groq API Error: HTTPS 401 (Tidak Diizinkan). API Key Groq Anda tidak valid atau telah kedaluwarsa. Silakan periksa kembali dan ganti API Key Anda di menu Pengaturan.")
            }
            throw Exception("Groq API Error: $message")
        }
    }
}
