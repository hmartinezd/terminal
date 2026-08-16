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
interface SaleDao {
    @Query("SELECT * FROM sales WHERE status = 'OPEN' ORDER BY updatedAtUtc DESC")
    fun observeOpenSales(): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE status IN ('COMPLETED', 'VOIDED') ORDER BY completedAtUtc DESC")
    fun observeHistorySales(): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE saleId = :saleId")
    fun observeSale(saleId: SaleId): Flow<SaleEntity?>

    @Query("SELECT * FROM sales WHERE saleId = :saleId")
    suspend fun getSaleSync(saleId: SaleId): SaleEntity?

    @Query("SELECT * FROM sale_lines WHERE saleId = :saleId")
    fun observeSaleLines(saleId: SaleId): Flow<List<SaleLineEntity>>

    @Query("SELECT * FROM sale_lines WHERE saleId = :saleId")
    suspend fun getSaleLinesSync(saleId: SaleId): List<SaleLineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(sale: SaleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleLines(lines: List<SaleLineEntity>)

    @Query("DELETE FROM sale_lines WHERE lineId = :lineId AND saleId = :saleId")
    suspend fun deleteSaleLineScoped(lineId: LineId, saleId: SaleId)

    @Query("UPDATE sales SET status = 'DISCARDED', updatedAtUtc = :updatedAt WHERE saleId = :saleId")
    suspend fun discardSale(saleId: SaleId, updatedAt: Instant)

    @Transaction
    suspend fun upsertSaleWithLines(sale: SaleEntity, lines: List<SaleLineEntity>) {
        insertSale(sale)
        insertSaleLines(lines)
    }

    @Query("DELETE FROM sale_lines WHERE saleId = :saleId")
    suspend fun deleteSaleLines(saleId: SaleId)

    @Transaction
    suspend fun removeLineAndUpdateSale(saleId: SaleId, lineId: LineId, sale: SaleEntity) {
        deleteSaleLineScoped(lineId, saleId)
        insertSale(sale)
    }

    @Transaction
    suspend fun updateLinesAndSale(sale: SaleEntity, lines: List<SaleLineEntity>) {
        insertSale(sale)
        insertSaleLines(lines)
    }

    @Transaction
    suspend fun mergeLinesAndSale(
        saleId: SaleId,
        lineIdToRemove: LineId,
        lineEntityToUpdate: SaleLineEntity,
        saleEntity: SaleEntity
    ) {
        deleteSaleLineScoped(lineIdToRemove, saleId)
        insertSaleLines(listOf(lineEntityToUpdate))
        insertSale(saleEntity)
    }

    @Query("DELETE FROM sale_lines")
    suspend fun clearSaleLines()

    @Query("DELETE FROM sales")
    suspend fun clearSales()
}
