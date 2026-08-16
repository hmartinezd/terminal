package com.venkoi.terminal.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TerminalDao {
    @Query("SELECT * FROM terminal_configuration WHERE id = 0")
    suspend fun getTerminalConfiguration(): TerminalEntity?

    @Query("SELECT * FROM terminal_configuration WHERE id = 0")
    fun observeTerminalConfiguration(): Flow<TerminalEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTerminalConfiguration(terminal: TerminalEntity)

    @Query("DELETE FROM terminal_configuration")
    suspend fun clear()
}
