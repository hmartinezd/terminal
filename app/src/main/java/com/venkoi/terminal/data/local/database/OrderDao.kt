package com.venkoi.terminal.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.venkoi.terminal.core.LineId
import com.venkoi.terminal.core.SaleId
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface OrderDao {
    @Query("SELECT * FROM open_orders WHERE status = 'OPEN' ORDER BY updatedAtUtc DESC")
    fun observeOpenOrders(): Flow<List<OpenOrderEntity>>

    @Query("SELECT * FROM open_orders WHERE saleId = :saleId")
    fun observeOrder(saleId: SaleId): Flow<OpenOrderEntity?>

    @Query("SELECT * FROM open_order_lines WHERE saleId = :saleId")
    fun observeOrderLines(saleId: SaleId): Flow<List<OpenOrderLineEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OpenOrderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderLines(lines: List<OpenOrderLineEntity>)

    @Query("DELETE FROM open_order_lines WHERE lineId = :lineId")
    suspend fun deleteOrderLine(lineId: LineId)

    @Query("UPDATE open_orders SET status = 'DISCARDED', updatedAtUtc = :updatedAt WHERE saleId = :saleId")
    suspend fun discardOrder(saleId: SaleId, updatedAt: Instant)

    @Transaction
    suspend fun upsertOrderWithLines(order: OpenOrderEntity, lines: List<OpenOrderLineEntity>) {
        insertOrder(order)
        insertOrderLines(lines)
    }

    @Query("DELETE FROM open_order_lines WHERE saleId = :saleId")
    suspend fun deleteOrderLines(saleId: SaleId)
}
