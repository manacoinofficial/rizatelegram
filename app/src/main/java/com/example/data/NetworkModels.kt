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

// --- GROQ MODELS ---

@JsonClass(generateAdapter = true)
data class GroqRequest(
    val model: String,
    val messages: List<GroqMessage>,
    val temperature: Float? = null
)

@JsonClass(generateAdapter = true)
data class GroqMessage(
    val role: String,
    val content: String
)

@JsonClass(generateAdapter = true)
data class GroqResponse(
    val choices: List<GroqChoice>? = null
)

@JsonClass(generateAdapter = true)
data class GroqChoice(
    val message: GroqMessage? = null
)
