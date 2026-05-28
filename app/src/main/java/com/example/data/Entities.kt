package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bot_settings")
data class BotSettings(
    @PrimaryKey val id: Int = 1, // Store a single configuration instance
    val telegramToken: String = "",
    val geminiApiKey: String = "",
    val systemInstruction: String = "Anda adalah asisten AI Telegram yang ramah dan siap membantu menjawab semua pertanyaan.",
    val botUsername: String = "",
    val botFirstName: String = "",
    val isBotRunning: Boolean = false
)

@Entity(tableName = "bot_logs")
data class BotLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val level: String = "INFO", // INFO, SUCCESS, WARNING, ERROR, INCOMING, OUTGOING
    val message: String
)
