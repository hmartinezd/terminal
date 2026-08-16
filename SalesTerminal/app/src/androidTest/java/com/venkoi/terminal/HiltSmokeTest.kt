package com.venkoi.terminal

import com.venkoi.terminal.data.local.database.AppDatabase
import com.venkoi.terminal.domain.repository.TerminalConfigurationRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class HiltSmokeTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: AppDatabase

    @Inject
    lateinit var repository: TerminalConfigurationRepository

    @Inject
    lateinit var json: Json

    @Test
    fun dependenciesAreInjected() {
        hiltRule.inject()
        assertNotNull(database)
        assertNotNull(repository)
        assertNotNull(json)
    }
}
