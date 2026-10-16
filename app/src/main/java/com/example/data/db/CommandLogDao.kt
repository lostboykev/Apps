package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CommandLogDao {
    @Query("SELECT * FROM command_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<CommandLogEntity>>

    @Query("SELECT * FROM command_logs WHERE category = :category ORDER BY timestamp DESC")
    fun getLogsByCategory(category: String): Flow<List<CommandLogEntity>>

    @Query("SELECT * FROM command_logs WHERE id = :id LIMIT 1")
    suspend fun getLogById(id: Long): CommandLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: CommandLogEntity): Long

    @Update
    suspend fun updateLog(log: CommandLogEntity)

    @Query("DELETE FROM command_logs WHERE id = :id")
    suspend fun deleteLogById(id: Long)

    @Query("DELETE FROM command_logs")
    suspend fun clearAllLogs()

    // Keep database lean and battery/storage safe by trimming old logs beyond limit
    @Query("DELETE FROM command_logs WHERE id NOT IN (SELECT id FROM command_logs ORDER BY timestamp DESC LIMIT :keepCount)")
    suspend fun trimOldLogs(keepCount: Int = 300)
}
