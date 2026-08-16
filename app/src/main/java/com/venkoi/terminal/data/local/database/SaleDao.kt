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
import java.time.LocalDate

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

    @Query("UPDATE sales SET status = 'COMPLETED', revision = 1, completedAtUtc = :completedAt, businessDate = :businessDate, updatedAtUtc = :updatedAt WHERE saleId = :saleId AND status = 'OPEN' AND revision IS NULL")
    suspend fun completeSaleGuarded(
        saleId: SaleId,
        completedAt: Instant,
        businessDate: LocalDate,
        updatedAt: Instant
    ): Int

    @Query("UPDATE sales SET status = 'VOIDED', revision = 2, voidedAtUtc = :voidedAt, updatedAtUtc = :updatedAt WHERE saleId = :saleId AND status = 'COMPLETED' AND revision = 1")
    suspend fun voidSaleGuarded(
        saleId: SaleId,
        voidedAt: Instant,
        updatedAt: Instant
    ): Int

    @Query("UPDATE sales SET tableLabel = :label, updatedAtUtc = :updatedAt WHERE saleId = :saleId AND status = 'OPEN'")
    suspend fun updateSaleLabelGuarded(saleId: SaleId, label: String?, updatedAt: Instant): Int

    @Query("DELETE FROM sale_lines WHERE lineId = :lineId AND saleId = :saleId")
    suspend fun deleteSaleLineScoped(lineId: LineId, saleId: SaleId)

    @Query("UPDATE sales SET status = 'DISCARDED', updatedAtUtc = :updatedAt WHERE saleId = :saleId AND status = 'OPEN'")
    suspend fun discardSaleGuarded(saleId: SaleId, updatedAt: Instant): Int

    @Transaction
    suspend fun upsertSaleWithLines(sale: SaleEntity, lines: List<SaleLineEntity>) {
        val affected = updateSaleTimestampGuarded(sale.saleId, sale.updatedAtUtc)
        if (affected > 0) {
            insertSaleLines(lines)
        }
    }

    @Query("UPDATE sales SET updatedAtUtc = :updatedAt WHERE saleId = :saleId AND status = 'OPEN'")
    suspend fun updateSaleTimestampGuarded(saleId: SaleId, updatedAt: Instant): Int

    @Transaction
    suspend fun removeLineAndUpdateSale(saleId: SaleId, lineId: LineId, updatedAt: Instant) {
        val affected = updateSaleTimestampGuarded(saleId, updatedAt)
        if (affected > 0) {
            deleteSaleLineScoped(lineId, saleId)
        }
    }

    @Transaction
    suspend fun updateLinesAndSale(saleId: SaleId, lines: List<SaleLineEntity>, updatedAt: Instant) {
        val affected = updateSaleTimestampGuarded(saleId, updatedAt)
        if (affected > 0) {
            insertSaleLines(lines)
        }
    }

    @Transaction
    suspend fun mergeLinesAndSale(
        saleId: SaleId,
        lineIdToRemove: LineId,
        lineEntityToUpdate: SaleLineEntity,
        updatedAt: Instant
    ) {
        val affected = updateSaleTimestampGuarded(saleId, updatedAt)
        if (affected > 0) {
            deleteSaleLineScoped(lineIdToRemove, saleId)
            insertSaleLines(listOf(lineEntityToUpdate))
        }
    }

    @Query("DELETE FROM sale_lines")
    suspend fun clearSaleLines()

    @Query("DELETE FROM sales")
    suspend fun clearSales()
}
