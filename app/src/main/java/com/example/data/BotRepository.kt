package com.example.data

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class BotRepository(private val botDao: BotDao) {

    val settingsFlow: Flow<BotSettings?> = botDao.getSettingsFlow()
    val logsFlow: Flow<List<BotLog>> = botDao.getLogsFlow()
    val registeredUsersFlow: Flow<List<RegisteredUser>> = botDao.getRegisteredUsersFlow()

    suspend fun getActiveRegisteredUsers(): List<RegisteredUser> = withContext(Dispatchers.IO) {
        botDao.getActiveRegisteredUsers()
    }

    suspend fun saveRegisteredUser(user: RegisteredUser): Long = withContext(Dispatchers.IO) {
        botDao.saveRegisteredUser(user)
    }

    suspend fun deleteRegisteredUserById(userId: Long) = withContext(Dispatchers.IO) {
        botDao.deleteRegisteredUserById(userId)
    }

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

    /**
     * Retrieves file metadata from Telegram bot api.
     */
    suspend fun getTelegramFile(token: String, fileId: String): TelegramFileResponse = withContext(Dispatchers.IO) {
        val url = "https://api.telegram.org/bot$token/getFile?file_id=$fileId"
        NetworkClient.telegramService.getFile(url)
    }

    /**
     * Uploads and sends a Voice message to Telegram chat.
     */
    suspend fun sendTelegramVoice(token: String, chatId: Long, file: File, replyToMessageId: Long? = null) = withContext(Dispatchers.IO) {
        val url = "https://api.telegram.org/bot$token/sendVoice"
        val mediaType = "audio/mpeg".toMediaTypeOrNull()
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId.toString())
            .addFormDataPart("voice", file.name, file.asRequestBody(mediaType))
            .apply {
                if (replyToMessageId != null) {
                    addFormDataPart("reply_to_message_id", replyToMessageId.toString())
                }
            }
            .build()
        val request = Request.Builder().url(url).post(requestBody).build()
        OkHttpClient().newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Gagal mengirim voice ke Telegram: HTTP ${response.code}")
        }
    }

    /**
     * Uploads and sends a Photo message to Telegram chat.
     */
    suspend fun sendTelegramPhoto(token: String, chatId: Long, file: File, caption: String? = null, replyToMessageId: Long? = null) = withContext(Dispatchers.IO) {
        val url = "https://api.telegram.org/bot$token/sendPhoto"
        val mediaType = "image/png".toMediaTypeOrNull()
        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId.toString())
            .addFormDataPart("photo", file.name, file.asRequestBody(mediaType))
        if (caption != null) {
            builder.addFormDataPart("caption", caption)
        }
        if (replyToMessageId != null) {
            builder.addFormDataPart("reply_to_message_id", replyToMessageId.toString())
        }
        val request = Request.Builder().url(url).post(builder.build()).build()
        OkHttpClient().newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Gagal mengirim foto ke Telegram: HTTP ${response.code}")
        }
    }

    /**
     * Uploads and sends a Video/Animation message to Telegram chat.
     */
    suspend fun sendTelegramVideo(token: String, chatId: Long, file: File, caption: String? = null, replyToMessageId: Long? = null) = withContext(Dispatchers.IO) {
        val url = "https://api.telegram.org/bot$token/sendVideo"
        val mediaType = "video/mp4".toMediaTypeOrNull()
        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId.toString())
            .addFormDataPart("video", file.name, file.asRequestBody(mediaType))
        if (caption != null) {
            builder.addFormDataPart("caption", caption)
        }
        if (replyToMessageId != null) {
            builder.addFormDataPart("reply_to_message_id", replyToMessageId.toString())
        }
        val request = Request.Builder().url(url).post(builder.build()).build()
        OkHttpClient().newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Gagal mengirim video ke Telegram: HTTP ${response.code}")
        }
    }

    /**
     * Call Groq Text-to-Speech API
     */
    suspend fun generateGroqSpeech(apiKey: String, text: String): ByteArray = withContext(Dispatchers.IO) {
        val url = "https://api.groq.com/openai/v1/audio/speech"
        val jsonPayload = """
            {
                "model": "tts-1",
                "input": ${com.squareup.moshi.Moshi.Builder().build().adapter(String::class.java).toJson(text)},
                "voice": "alloy"
            }
        """.trimIndent()
        
        val mediaType = "application/json".toMediaTypeOrNull()
        val body = jsonPayload.toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .post(body)
            .build()
            
        OkHttpClient().newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorString = response.body?.string() ?: response.message
                throw Exception("Groq TTS failed: HTTP ${response.code} - $errorString")
            }
            response.body?.bytes() ?: throw Exception("Menerima respon kosong dari Groq TTS")
        }
    }

    /**
     * Call Nexray Image Generation
     */
    suspend fun generateImageNexray(prompt: String, ratio: String): ByteArray = withContext(Dispatchers.IO) {
        val encodedPrompt = java.net.URLEncoder.encode(prompt, "UTF-8")
        val encodedRatio = java.net.URLEncoder.encode(ratio, "UTF-8")
        val url = "https://api.nexray.eu.cc/ai/writecreamimg?prompt=${encodedPrompt}&ratio=${encodedRatio}"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
            
        val client = OkHttpClient.Builder()
            .connectTimeout(90, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build()
            
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Gagal menghasilkan gambar: HTTP ${response.code} - ${response.message}")
            }
            val contentType = response.header("Content-Type") ?: ""
            val bodyBytes = response.body?.bytes() ?: throw Exception("Menerima respon kosong dari API gambar")
            
            if (contentType.contains("application/json")) {
                val jsonStr = String(bodyBytes)
                val extractedUrl = extractUrlFromJson(jsonStr) ?: throw Exception("Tidak ada URL gambar ditemukan dalam respon: $jsonStr")
                return@withContext downloadUrl(extractedUrl)
            }
            bodyBytes
        }
    }

    /**
     * Call Nexray Veo3 Video Generation
     */
    suspend fun generateVeo3Nexray(prompt: String, imageUrl: String): ByteArray = withContext(Dispatchers.IO) {
        val encodedPrompt = java.net.URLEncoder.encode(prompt, "UTF-8")
        val encodedImageUrl = java.net.URLEncoder.encode(imageUrl, "UTF-8")
        val url = "https://api.nexray.eu.cc/ai/veo3?prompt=${encodedPrompt}&peompt=${encodedPrompt}&image_url=${encodedImageUrl}"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
            
        val client = OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
            
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Gagal menghasilkan veo3 video: HTTP ${response.code} - ${response.message}")
            }
            val contentType = response.header("Content-Type") ?: ""
            val bodyBytes = response.body?.bytes() ?: throw Exception("Menerima respon kosong dari API veo3")
            
            if (contentType.contains("application/json")) {
                val jsonStr = String(bodyBytes)
                val extractedUrl = extractUrlFromJson(jsonStr) ?: throw Exception("Tidak ada URL video ditemukan dalam respon: $jsonStr")
                return@withContext downloadUrl(extractedUrl)
            }
            bodyBytes
        }
    }

    private fun extractUrlFromJson(json: String): String? {
        val pattern = """https?://[^\s"'}]+""".toRegex()
        return pattern.find(json)?.value
    }
    
    private suspend fun downloadUrl(url: String): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        OkHttpClient().newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Gagal mengunduh media: ${response.message}")
            response.body?.bytes() ?: throw Exception("Unduhan media kosong")
        }
    }

    /**
     * Call spamngl tools API
     */
    suspend fun spamNgl(nglUrl: String, pesan: String, jumlah: String): String = withContext(Dispatchers.IO) {
        val encodedUrl = java.net.URLEncoder.encode(nglUrl, "UTF-8")
        val encodedPesan = java.net.URLEncoder.encode(pesan, "UTF-8")
        val encodedJumlah = java.net.URLEncoder.encode(jumlah, "UTF-8")
        val url = "https://api.nexray.eu.cc/tools/spamngl?url=${encodedUrl}&pesan=${encodedPesan}&jumlah=${encodedJumlah}"
        val request = Request.Builder().url(url).get().build()
        
        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
            
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw Exception("API Error ${response.code}: $body")
            }
            body
        }
    }

    /**
     * Call HD Video Converter/Optimizer API
     */
    suspend fun hdVideo(videoUrl: String): ByteArray = withContext(Dispatchers.IO) {
        val encodedUrl = java.net.URLEncoder.encode(videoUrl, "UTF-8")
        val url = "https://api.nexray.eu.cc/tools/hdvideo?url=${encodedUrl}"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
            
        val client = OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
            
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Gagal memproses HD Video: HTTP ${response.code} - ${response.message}")
            }
            val contentType = response.header("Content-Type") ?: ""
            val bodyBytes = response.body?.bytes() ?: throw Exception("Menerima respon kosong dari API HD Video")
            
            if (contentType.contains("application/json")) {
                val jsonStr = String(bodyBytes)
                val extractedUrl = extractUrlFromJson(jsonStr) ?: throw Exception("Tidak ada URL video ditemukan dalam respon: $jsonStr")
                return@withContext downloadUrl(extractedUrl)
            }
            bodyBytes
        }
    }

    /**
     * Call PLN Tagihan Checker API
     */
    suspend fun cekTagihanPln(nopel: String): String = withContext(Dispatchers.IO) {
        val encodedNopel = java.net.URLEncoder.encode(nopel, "UTF-8")
        val url = "https://api.nexray.eu.cc/information/cektagihanpln?nopel=${encodedNopel}"
        val request = Request.Builder().url(url).get().build()
        
        OkHttpClient().newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw Exception("API Error ${response.code}: $body")
            }
            body
        }
    }

    fun formatJsonToIndonesian(jsonStr: String): String {
        try {
            val jsonObject = org.json.JSONObject(jsonStr)
            val sb = java.lang.StringBuilder()
            
            // Check success/status/result
            if (jsonObject.has("status")) {
                val statusVal = jsonObject.get("status")
                if (statusVal is Boolean && !statusVal) {
                    val message = jsonObject.optString("message", "Error")
                    return "❌ *Gagal Cek Tagihan PLN:*\nDetail: $message"
                } else if (statusVal is String && (statusVal.lowercase() == "false" || statusVal.lowercase() == "failed" || statusVal.lowercase() == "error")) {
                    val message = jsonObject.optString("message", "Error")
                    return "❌ *Gagal Cek Tagihan PLN:*\nDetail: $message"
                }
            }
            
            val dataObj = jsonObject.optJSONObject("data") ?: jsonObject.optJSONObject("result") ?: jsonObject
            
            val keys = dataObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = dataObj.get(key)
                if (value is org.json.JSONObject || value is org.json.JSONArray) {
                    continue
                }
                val label = when(key.lowercase()) {
                    "nopel", "idpel", "customer_id", "nomor_pelanggan" -> "ID Pelanggan"
                    "nama", "name", "customer_name", "nama_pelanggan" -> "Nama Pelanggan"
                    "tarif", "rate" -> "Tarif"
                    "daya", "power" -> "Daya"
                    "tagihan", "total", "nominal", "amount", "total_tagihan", "tag" -> "Total Tagihan"
                    "periode", "period", "bulan", "month" -> "Periode"
                    "denda", "penalty" -> "Denda"
                    "admin", "fee" -> "Biaya Admin"
                    "status" -> "Status"
                    "stand_meter", "meter" -> "Stand Meter"
                    else -> key.replace("_", " ").split(" ").joinToString(" ") { it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(java.util.Locale.getDefault()) else char.toString() } }
                }
                
                val valStr = if ((key.lowercase().contains("tagihan") || key.lowercase().contains("nominal") || key.lowercase().contains("amount") || key.lowercase() == "total" || key.lowercase() == "denda" || key.lowercase() == "admin" || key.lowercase() == "tag") && value is Number) {
                    val formatted = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("id", "ID")).format(value)
                    formatted.replace("Rp", "Rp ").replace(",00", "")
                } else {
                    value.toString()
                }
                
                sb.append("⚡ *").append(label).append(":* ").append(valStr).append("\n")
            }
            
            if (sb.isEmpty()) {
                return jsonStr
            }
            return sb.toString()
        } catch (e: Exception) {
            return jsonStr.replace("{", "")
                .replace("}", "")
                .replace("\"", "")
                .replace("[", "")
                .replace("]", "")
        }
    }

    /**
     * Call CNN News API
     */
    suspend fun getCnnNews(): String = withContext(Dispatchers.IO) {
        val url = "https://api.nexray.eu.cc/berita/cnn"
        val request = Request.Builder().url(url).get().build()
        
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
            
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw Exception("API Error ${response.code}: $body")
            }
            body
        }
    }

    fun formatCnnNews(jsonStr: String): String {
        try {
            val jsonObject = org.json.JSONObject(jsonStr)
            val sb = java.lang.StringBuilder()
            
            // Try to find the array of news
            val newsArray = jsonObject.optJSONArray("result") 
                ?: jsonObject.optJSONArray("data")
                ?: jsonObject.optJSONArray("articles")
                ?: jsonObject.optJSONArray("news")
                ?: jsonObject.optJSONArray("berita")
                
            if (newsArray != null && newsArray.length() > 0) {
                val limit = minOf(newsArray.length(), 6)
                sb.append("📰 *CNN INDONESIA - BERITA TERBARU*\n\n")
                for (i in 0 until limit) {
                    val item = newsArray.optJSONObject(i) ?: continue
                    val title = item.optString("title") ?: item.optString("judul") ?: ""
                    val link = item.optString("link") ?: item.optString("url") ?: ""
                    val desc = item.optString("description") ?: item.optString("desc") ?: item.optString("content") ?: ""
                    
                    if (title.isNotEmpty()) {
                        sb.append("${i + 1}. *${title.trim()}*\n")
                        if (desc.isNotEmpty()) {
                            val cleanDesc = if (desc.length > 120) desc.substring(0, 115) + "..." else desc
                            sb.append("   _${cleanDesc.trim()}_\n")
                        }
                        if (link.isNotEmpty()) {
                            sb.append("   🔗 [Baca Selengkapnya](${link.trim()})\n")
                        }
                        sb.append("\n")
                    }
                }
            } else {
                // Let's check if the root element itself is an array
                try {
                    val rootArray = org.json.JSONArray(jsonStr)
                    val limit = minOf(rootArray.length(), 6)
                    sb.append("📰 *CNN INDONESIA - BERITA TERBARU*\n\n")
                    for (i in 0 until limit) {
                        val item = rootArray.optJSONObject(i) ?: continue
                        val title = item.optString("title") ?: item.optString("judul") ?: ""
                        val link = item.optString("link") ?: item.optString("url") ?: ""
                        val desc = item.optString("description") ?: item.optString("desc") ?: item.optString("content") ?: ""
                        
                        if (title.isNotEmpty()) {
                            sb.append("${i + 1}. *${title.trim()}*\n")
                            if (desc.isNotEmpty()) {
                                val cleanDesc = if (desc.length > 120) desc.substring(0, 115) + "..." else desc
                                sb.append("   _${cleanDesc.trim()}_\n")
                            }
                            if (link.isNotEmpty()) {
                                sb.append("   🔗 [Baca Selengkapnya](${link.trim()})\n")
                            }
                            sb.append("\n")
                        }
                    }
                } catch (arrEx: Exception) {
                    // Try formatting generally
                    return formatJsonToIndonesian(jsonStr)
                }
            }
            if (sb.isEmpty()) {
                return "Tidak ada berita ditemukan dalam respon API."
            }
            return sb.toString()
        } catch (e: Exception) {
            // General parsing fallback
            return "Gagal memproses berita: ${e.message}\nRespon asli:\n$jsonStr"
        }
    }

    /**
     * Registers bot commands menu dynamically in Telegram
     */
    suspend fun setTelegramCommands(token: String): Boolean = withContext(Dispatchers.IO) {
        val url = "https://api.telegram.org/bot${token}/setMyCommands"
        val jsonPayload = """
            {
              "commands": [
                {"command": "start", "description": "Mulai bot & info cara pakai"},
                {"command": "register", "description": "Daftarkan bot Telegram baru"},
                {"command": "tts", "description": "Teks menjadi suara (TTS)"},
                {"command": "imagen", "description": "Buat gambar dengan AI"},
                {"command": "veo3", "description": "Buat video animasi dari foto"},
                {"command": "spamngl", "description": "Spam pesan ke link NGL"},
                {"command": "hdvideo", "description": "Ubah resolusi video ke HD"},
                {"command": "cektagihanpln", "description": "Periksa tagihan listrik PLN"},
                {"command": "ccn", "description": "Berita terkini dari CNN Indonesia"}
              ]
            }
        """.trimIndent()
        
        val body = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()
            
        try {
            OkHttpClient().newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            android.util.Log.e("BotRepository", "Failed to setTelegramCommands: ${e.message}")
            false
        }
    }
}
