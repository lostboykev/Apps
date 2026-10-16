package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppStateDao {
    @Query("SELECT value FROM app_states WHERE `key` = :key LIMIT 1")
    suspend fun getState(key: String): String?

    @Query("SELECT value FROM app_states WHERE `key` = :key LIMIT 1")
    fun observeState(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setState(state: AppStateEntity)

    @Query("DELETE FROM app_states WHERE `key` = :key")
    suspend fun removeState(key: String)
}
