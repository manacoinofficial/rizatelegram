package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BotDao {
    @Query("SELECT * FROM bot_settings WHERE id = 1")
    fun getSettingsFlow(): Flow<BotSettings?>

    @Query("SELECT * FROM bot_settings WHERE id = 1")
    suspend fun getSettings(): BotSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: BotSettings)

    @Query("SELECT * FROM bot_logs ORDER BY id DESC LIMIT 150")
    fun getLogsFlow(): Flow<List<BotLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: BotLog)

    @Query("DELETE FROM bot_logs")
    suspend fun clearLogs()

    @Query("SELECT * FROM registered_users ORDER BY id DESC")
    fun getRegisteredUsersFlow(): Flow<List<RegisteredUser>>

    @Query("SELECT * FROM registered_users WHERE isActive = 1")
    suspend fun getActiveRegisteredUsers(): List<RegisteredUser>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRegisteredUser(user: RegisteredUser): Long

    @Query("DELETE FROM registered_users WHERE id = :userId")
    suspend fun deleteRegisteredUserById(userId: Long)
}
