package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface JarvisDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommand(command: JarvisCommandEntity): Long

    @Query("SELECT * FROM jarvis_commands ORDER BY timestamp DESC LIMIT 100")
    fun getAllCommands(): Flow<List<JarvisCommandEntity>>

    @Query("DELETE FROM jarvis_commands")
    suspend fun clearAllCommands()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: JarvisChatMessageEntity): Long

    @Query("SELECT * FROM jarvis_chat ORDER BY timestamp ASC LIMIT 200")
    fun getAllChatMessages(): Flow<List<JarvisChatMessageEntity>>

    @Query("DELETE FROM jarvis_chat")
    suspend fun clearAllChatMessages()
}
