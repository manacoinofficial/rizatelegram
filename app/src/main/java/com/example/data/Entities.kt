package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bot_settings")
data class BotSettings(
    @PrimaryKey val id: Int = 1, // Store a single configuration instance
    val telegramToken: String = "",
    val groqApiKey: String = "",
    val selectedModel: String = "llama-3.1-8b-instant",
    val groqModels: String = "llama-3.1-8b-instant,llama3-8b-8192,llama-3.1-70b-versatile,gemma2-9b-it,mixtral-8x7b-32768",
    val systemInstruction: String = "Anda adalah asisten AI Telegram yang ramah dan siap membantu menjawab semua pertanyaan.",
    val botUsername: String = "",
    val botFirstName: String = "",
    val isBotRunning: Boolean = false,
    
    // Tokopay Integration Fields
    val tokopayMerchantId: String = "",
    val tokopayApiKey: String = "",
    val tokopaySecretKey: String = "",
    val tokopayIsActive: Boolean = false
)

@Entity(tableName = "registered_users")
data class RegisteredUser(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String = "",
    val whatsappNumber: String = "",
    val telegramToken: String = "",
    val selectedModel: String = "llama-3.1-8b-instant",
    val price: Double = 0.0,
    val isActive: Boolean = true,
    val botUsername: String = "",
    val botFirstName: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "bot_logs")
data class BotLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val level: String = "INFO", // INFO, SUCCESS, WARNING, ERROR, INCOMING, OUTGOING
    val message: String
)
