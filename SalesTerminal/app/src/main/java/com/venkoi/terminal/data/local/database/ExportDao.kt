package com.venkoi.terminal.data.local.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.venkoi.terminal.core.SaleId
import com.venkoi.terminal.core.TerminalId
import com.venkoi.terminal.domain.model.ExportedSaleRevision
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "sale_export_state")
data class SaleExportStateEntity(
    @PrimaryKey val saleId: SaleId,
    val lastExportedRevision: Int,
    val lastExportedAtUtc: Instant,
    val lastExportBatchId: String
)

data class ExportSummaryRow(
    val pendingCount: Int,
    val legacyUnsupportedCount: Int,
    val lastSuccessfulExportAtUtc: Instant?
)

@Dao
interface ExportDao {
    @Query("SELECT * FROM sale_export_state WHERE saleId = :saleId")
    suspend fun getExportState(saleId: SaleId): SaleExportStateEntity?

    @Transaction
    @Query("""
        SELECT * FROM sales WHERE terminalId = :terminalId
        AND status IN ('COMPLETED', 'VOIDED') AND revision > 0
        AND TRIM(restaurantIdSnapshot) <> '' AND TRIM(menuIdSnapshot) <> ''
        AND publicationRevisionSnapshot > 0
        AND TRIM(currencyCodeSnapshot) <> ''
        AND (NOT EXISTS (SELECT 1 FROM sale_export_state e WHERE e.saleId = sales.saleId)
             OR revision > (SELECT lastExportedRevision FROM sale_export_state e WHERE e.saleId = sales.saleId))
        ORDER BY businessDate, completedAtUtc, saleId
    """)
    suspend fun getPendingChanges(terminalId: TerminalId): List<SaleWithLinesEntity>

    @Transaction
    @Query("""
        SELECT * FROM sales WHERE terminalId = :terminalId
        AND status IN ('COMPLETED', 'VOIDED') AND revision > 0
        AND businessDate = :businessDate
        AND TRIM(restaurantIdSnapshot) <> '' AND TRIM(menuIdSnapshot) <> ''
        AND publicationRevisionSnapshot > 0
        AND TRIM(currencyCodeSnapshot) <> ''
        AND (NOT EXISTS (SELECT 1 FROM sale_export_state e WHERE e.saleId = sales.saleId)
             OR revision > (SELECT lastExportedRevision FROM sale_export_state e WHERE e.saleId = sales.saleId))
        ORDER BY businessDate, completedAtUtc, saleId
    """)
    suspend fun getPendingChangesForDay(terminalId: TerminalId, businessDate: LocalDate): List<SaleWithLinesEntity>

    @Transaction
    @Query("""
        SELECT * FROM sales WHERE terminalId = :terminalId
        AND status IN ('COMPLETED', 'VOIDED') AND revision > 0
        AND (TRIM(COALESCE(restaurantIdSnapshot, '')) = ''
             OR TRIM(COALESCE(menuIdSnapshot, '')) = '' OR COALESCE(publicationRevisionSnapshot, 0) <= 0)
        AND (NOT EXISTS (SELECT 1 FROM sale_export_state e WHERE e.saleId = sales.saleId)
             OR revision > (SELECT lastExportedRevision FROM sale_export_state e WHERE e.saleId = sales.saleId))
        ORDER BY businessDate, completedAtUtc, saleId
    """)
    suspend fun getLegacyPendingChanges(terminalId: TerminalId): List<SaleWithLinesEntity>

    @Transaction
    @Query("""
        SELECT * FROM sales WHERE terminalId = :terminalId AND businessDate = :businessDate
        AND status IN ('COMPLETED', 'VOIDED') AND revision > 0
        AND TRIM(restaurantIdSnapshot) <> '' AND TRIM(menuIdSnapshot) <> ''
        AND publicationRevisionSnapshot > 0
        AND TRIM(currencyCodeSnapshot) <> ''
        ORDER BY businessDate, completedAtUtc, saleId
    """)
    suspend fun getSalesForDay(terminalId: TerminalId, businessDate: LocalDate): List<SaleWithLinesEntity>

    @Transaction
    @Query("""
        SELECT * FROM sales WHERE terminalId = :terminalId AND businessDate = :businessDate
        AND status IN ('COMPLETED', 'VOIDED') AND revision > 0
        AND (TRIM(COALESCE(restaurantIdSnapshot, '')) = ''
             OR TRIM(COALESCE(menuIdSnapshot, '')) = '' OR COALESCE(publicationRevisionSnapshot, 0) <= 0)
        ORDER BY businessDate, completedAtUtc, saleId
    """)
    suspend fun getLegacySalesForDay(terminalId: TerminalId, businessDate: LocalDate): List<SaleWithLinesEntity>

    @Query("""
        SELECT
          (SELECT COUNT(*) FROM sales s LEFT JOIN sale_export_state e ON e.saleId = s.saleId
           WHERE s.terminalId = :terminalId AND s.status IN ('COMPLETED','VOIDED')
           AND s.revision > 0
           AND TRIM(s.restaurantIdSnapshot) <> '' AND TRIM(s.menuIdSnapshot) <> ''
           AND s.publicationRevisionSnapshot > 0
           AND TRIM(s.currencyCodeSnapshot) <> ''
           AND (e.saleId IS NULL OR s.revision > e.lastExportedRevision)) AS pendingCount,
          (SELECT COUNT(*) FROM sales s LEFT JOIN sale_export_state e ON e.saleId = s.saleId
           WHERE s.terminalId = :terminalId AND s.status IN ('COMPLETED','VOIDED')
           AND s.revision > 0
           AND (TRIM(COALESCE(s.restaurantIdSnapshot, '')) = ''
                OR TRIM(COALESCE(s.menuIdSnapshot, '')) = '' OR COALESCE(s.publicationRevisionSnapshot, 0) <= 0)
           AND (e.saleId IS NULL OR s.revision > e.lastExportedRevision)) AS legacyUnsupportedCount,
          (SELECT MAX(e.lastExportedAtUtc) FROM sale_export_state e JOIN sales s ON s.saleId = e.saleId
           WHERE s.terminalId = :terminalId) AS lastSuccessfulExportAtUtc
    """)
    fun observeSummary(terminalId: TerminalId): Flow<ExportSummaryRow>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertState(state: SaleExportStateEntity): Long

    @Query("""
        UPDATE sale_export_state SET lastExportedRevision = :revision,
        lastExportedAtUtc = :exportedAtUtc, lastExportBatchId = :batchId
        WHERE saleId = :saleId AND (
            lastExportedRevision < :revision OR
            (lastExportedRevision = :revision AND lastExportedAtUtc < :exportedAtUtc)
        )
    """)
    suspend fun advanceState(saleId: SaleId, revision: Int, exportedAtUtc: Instant, batchId: String): Int

    @Transaction
    suspend fun markExported(revisions: List<ExportedSaleRevision>, exportedAtUtc: Instant, batchId: String) {
        revisions.forEach { exported ->
            val inserted = insertState(SaleExportStateEntity(exported.saleId, exported.revision, exportedAtUtc, batchId))
            if (inserted == -1L) advanceState(exported.saleId, exported.revision, exportedAtUtc, batchId)
        }
    }
}
