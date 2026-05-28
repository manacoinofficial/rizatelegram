package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// --- TELEGRAM MODELS ---

@JsonClass(generateAdapter = true)
data class TelegramMeResponse(
    val ok: Boolean,
    val result: TelegramBotInfo? = null,
    val description: String? = null
)

@JsonClass(generateAdapter = true)
data class TelegramBotInfo(
    val id: Long,
    val is_bot: Boolean,
    @Json(name = "first_name") val firstName: String,
    val username: String? = null
)

@JsonClass(generateAdapter = true)
data class TelegramUpdatesResponse(
    val ok: Boolean,
    val result: List<TelegramUpdate>? = null,
    val description: String? = null
)

@JsonClass(generateAdapter = true)
data class TelegramUpdate(
    @Json(name = "update_id") val updateId: Long,
    val message: TelegramMessage? = null
)

@JsonClass(generateAdapter = true)
data class TelegramMessage(
    @Json(name = "message_id") val messageId: Long,
    val from: TelegramUser? = null,
    val chat: TelegramChat,
    val date: Long,
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class TelegramUser(
    val id: Long,
    @Json(name = "is_bot") val isBot: Boolean,
    @Json(name = "first_name") val firstName: String,
    @Json(name = "last_name") val lastName: String? = null,
    val username: String? = null
)

@JsonClass(generateAdapter = true)
data class TelegramChat(
    val id: Long,
    val type: String,
    @Json(name = "first_name") val firstName: String? = null,
    @Json(name = "last_name") val lastName: String? = null,
    val username: String? = null,
    val title: String? = null
)

@JsonClass(generateAdapter = true)
data class TelegramSendMessageRequest(
    @Json(name = "chat_id") val chatId: Long,
    val text: String,
    @Json(name = "reply_to_message_id") val replyToMessageId: Long? = null,
    @Json(name = "parse_mode") val parseMode: String? = null
)

@JsonClass(generateAdapter = true)
data class TelegramSendMessageResponse(
    val ok: Boolean,
    val result: TelegramMessage? = null,
    val description: String? = null
)

// --- GEMINI MODELS ---

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    val temperature: Float? = null,
    val maxOutputTokens: Int? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    val content: GeminiContent? = null
)
