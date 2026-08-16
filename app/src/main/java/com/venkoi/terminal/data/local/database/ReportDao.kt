package com.venkoi.terminal.data.local.database

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import com.venkoi.terminal.core.TerminalId
import java.time.LocalDate

data class SaleWithLinesEntity(
    @Embedded val sale: SaleEntity,
    @Relation(
        parentColumn = "saleId",
        entityColumn = "saleId"
    )
    val lines: List<SaleLineEntity>
)

@Dao
interface ReportDao {
    @Transaction
    @Query("""
        SELECT * FROM sales 
        WHERE terminalId = :terminalId
        AND businessDate = :businessDate
        AND status IN ('COMPLETED', 'VOIDED')
    """)
    fun observeSalesWithLinesForDate(
        terminalId: TerminalId,
        businessDate: LocalDate
    ): Flow<List<SaleWithLinesEntity>>
}
