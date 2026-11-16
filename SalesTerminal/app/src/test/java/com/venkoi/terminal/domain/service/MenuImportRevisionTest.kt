package com.venkoi.terminal.domain.service

import com.venkoi.terminal.core.*
import com.venkoi.terminal.domain.model.*
import com.venkoi.terminal.domain.repository.*
import com.venkoi.terminal.integration.menu.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class MenuImportRevisionTest {
    private val parser = MenuPackageParser(Json { ignoreUnknownKeys = true })
    private fun fixture(): MenuPackageImportResult.Success = parser.parse(
        requireNotNull(javaClass.getResource("/fixtures/menu_package_v1_valid.json")).readText()
    ) as MenuPackageImportResult.Success

    private class Catalog(var installed: MenuPackageImportResult.Success) : MenuRepository {
        var installs = 0
        var failInstall = false
        override fun observeRestaurantConfiguration() = flowOf(installed.restaurant)
        override fun observePublishedMenu() = flowOf(installed.menu)
        override fun observeCategories() = flowOf(installed.categories)
        override fun observeMenuItems() = flowOf(installed.items)
        override fun observeActiveMenuItems() = flowOf(installed.items.filter { it.active })
        override suspend fun getPublishedMenu() = installed.menu
        override suspend fun getRestaurantConfiguration() = installed.restaurant
        override suspend fun getMenuItem(id: String) = installed.items.find { it.id == id }
        override suspend fun installMenu(restaurant: RestaurantConfiguration, menu: PublishedMenu,
                                        categories: List<MenuCategory>, items: List<MenuItem>) {
            if (failInstall) error("Complete or discard open orders before switching to a different menu")
            installed = MenuPackageImportResult.Success(restaurant, menu, categories, items)
            installs++
        }
    }
    private class Terminal(restaurantId: String) : TerminalConfigurationRepository {
        val original = TerminalConfiguration(TerminalId("terminal-stable"), RestaurantId(restaurantId), "T1", Instant.EPOCH)
        var writes = 0
        override suspend fun getConfiguration() = original
        override fun observeConfiguration() = flowOf(original)
        override suspend fun saveConfiguration(configuration: TerminalConfiguration) { writes++ }
        override suspend fun clearConfiguration() { writes++ }
        override suspend fun provisionTerminal(configuration: TerminalConfiguration, restaurant: RestaurantConfiguration,
                                               menu: PublishedMenu, categories: List<MenuCategory>, items: List<MenuItem>) { writes++ }
    }
    private fun service(catalog: Catalog, terminal: Terminal) = MenuImportService(
        parser, catalog, terminal,
        object : IdGenerator { override fun nextId() = "unused" },
        object : Clock { override fun now() = Instant.EPOCH }
    )

    @Test fun `different menu accepts lower equal and higher independent revision`() = runBlocking {
        for (revision in listOf(1, 58, 59)) {
            val old = fixture()
            val catalog = Catalog(old)
            val terminal = Terminal(old.restaurant.restaurantId)
            val incoming = old.copy(menu = old.menu.copy(menuId = "other-menu", publicationRevision = revision))
            assertTrue(service(catalog, terminal).importMenu(incoming) is MenuImportStatus.Success)
            assertEquals(incoming, catalog.installed)
            assertEquals(1, catalog.installs)
            assertEquals(0, terminal.writes)
        }
    }
    @Test fun `same menu stale and conflicting revisions still fail without writes`() = runBlocking {
        val old = fixture()
        val cases = listOf(
            old.copy(menu = old.menu.copy(publicationRevision = 57)),
            old.copy(items = old.items.map { it.copy(name = "Changed") }),
            old.copy(menu = old.menu.copy(publishedAtUtc = old.menu.publishedAtUtc.plusSeconds(1))),
            old.copy(restaurant = old.restaurant.copy(restaurantName = "Changed"))
        )
        for (incoming in cases) {
            val catalog = Catalog(old)
            assertTrue(service(catalog, Terminal(old.restaurant.restaurantId)).importMenu(incoming) is MenuImportStatus.Failure)
            assertEquals(old, catalog.installed)
            assertEquals(0, catalog.installs)
        }
    }
    @Test fun `same menu same revision same content is no-op and newer installs`() = runBlocking {
        val old = fixture()
        val catalog = Catalog(old)
        val service = service(catalog, Terminal(old.restaurant.restaurantId))
        assertTrue(service.importMenu(old) is MenuImportStatus.Success)
        assertEquals(0, catalog.installs)
        assertTrue(service.importMenu(old.copy(menu = old.menu.copy(publicationRevision = 59))) is MenuImportStatus.Success)
        assertEquals(1, catalog.installs)
    }
    @Test fun `different restaurant remains forbidden even with different menu and newer revision`() = runBlocking {
        val old = fixture()
        val catalog = Catalog(old)
        val incoming = old.copy(restaurant = old.restaurant.copy(restaurantId = "other-restaurant"),
            menu = old.menu.copy(menuId = "other-menu", publicationRevision = 99))
        assertTrue(service(catalog, Terminal(old.restaurant.restaurantId)).importMenu(incoming) is MenuImportStatus.Failure)
        assertEquals(old, catalog.installed)
        assertEquals(0, catalog.installs)
    }
    @Test fun `installation rejection is surfaced and leaves installed menu intact`() = runBlocking {
        val old = fixture()
        val catalog = Catalog(old).apply { failInstall = true }
        val incoming = old.copy(menu = old.menu.copy(menuId = "other-menu", publicationRevision = 1))
        val status = service(catalog, Terminal(old.restaurant.restaurantId)).importMenu(incoming)
        assertTrue(status is MenuImportStatus.Failure)
        assertTrue((status as MenuImportStatus.Failure).message.contains("open orders"))
        assertEquals(old, catalog.installed)
    }
    @Test fun `reported package replaces synthetic prior menu at equal revision`() = runBlocking {
        val input = System.getenv("MENU_REPORTED_INPUT")
        org.junit.Assume.assumeNotNull(input)
        val incoming = parser.parse(java.io.File(requireNotNull(input)).readText())
        assertTrue(incoming is MenuPackageImportResult.Success)
        val validated = incoming as MenuPackageImportResult.Success
        assertEquals(2, validated.menu.publicationRevision)
        assertEquals(7, validated.categories.size)
        assertEquals(62, validated.items.size)
        val prior = fixture().copy(restaurant = validated.restaurant,
            menu = fixture().menu.copy(menuId = "synthetic-prior-menu", publicationRevision = 2))
        val catalog = Catalog(prior)
        val terminal = Terminal(validated.restaurant.restaurantId)
        assertTrue(service(catalog, terminal).importMenu(validated) is MenuImportStatus.Success)
        assertEquals(validated, catalog.installed)
        assertEquals(1, catalog.installs)
        assertEquals(0, terminal.writes)
    }

}
