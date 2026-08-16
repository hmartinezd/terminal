package com.venkoi.terminal.data.local.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.venkoi.terminal.core.RestaurantId
import com.venkoi.terminal.core.TerminalId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class TerminalDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var terminalDao: TerminalDao

    @Before
    fun createDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        terminalDao = database.terminalDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun emptyState_returnsNull() = runBlocking {
        assertNull(terminalDao.getTerminalConfiguration())
        assertNull(terminalDao.observeTerminalConfiguration().first())
    }

    @Test
    fun saveAndRead_preservesTypesAndValues() = runBlocking {
        val terminalId = TerminalId("term-123")
        val restaurantId = RestaurantId("rest-456")
        val now = Instant.parse("2026-08-15T12:00:00Z")
        
        val entity = TerminalEntity(
            terminalId = terminalId,
            restaurantId = restaurantId,
            terminalName = "Main Terminal",
            createdAt = now
        )
        
        terminalDao.saveTerminalConfiguration(entity)
        
        val retrieved = terminalDao.getTerminalConfiguration()
        assertNotNull(retrieved)
        assertEquals(terminalId, retrieved?.terminalId)
        assertEquals(restaurantId, retrieved?.restaurantId)
        assertEquals("Main Terminal", retrieved?.terminalName)
        assertEquals(now, retrieved?.createdAt)
    }

    @Test
    fun saveReplacement_enforcesSingleRecordInvariant() = runBlocking {
        val t1 = TerminalEntity(
            terminalId = TerminalId("T1"),
            restaurantId = RestaurantId("R1"),
            terminalName = "Name 1",
            createdAt = Instant.now()
        )
        val t2 = TerminalEntity(
            terminalId = TerminalId("T2"),
            restaurantId = RestaurantId("R2"),
            terminalName = "Name 2",
            createdAt = Instant.now()
        )
        
        terminalDao.saveTerminalConfiguration(t1)
        terminalDao.saveTerminalConfiguration(t2)
        
        val retrieved = terminalDao.getTerminalConfiguration()
        assertEquals(TerminalId("T2"), retrieved?.terminalId)
        
        // Double check there's only one row in the table
        val count = database.openHelper.readableDatabase.compileStatement("SELECT COUNT(*) FROM terminal_configuration").simpleQueryForLong()
        assertEquals(1L, count)
    }

    @Test
    fun observeConfiguration_reflectsChanges() = runBlocking {
        val terminal = TerminalEntity(
            terminalId = TerminalId("T1"),
            restaurantId = RestaurantId("R1"),
            terminalName = "Name 1",
            createdAt = Instant.now()
        )
        
        val flow = terminalDao.observeTerminalConfiguration()
        
        assertNull(flow.first())
        
        terminalDao.saveTerminalConfiguration(terminal)
        assertEquals(TerminalId("T1"), flow.first()?.terminalId)
        
        terminalDao.clear()
        assertNull(flow.first())
    }
}
