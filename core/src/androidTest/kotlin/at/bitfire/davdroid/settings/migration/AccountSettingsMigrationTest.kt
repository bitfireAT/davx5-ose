/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings.migration

import at.bitfire.davdroid.accounts.LegacyAccount
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.AccountSettings.Companion.CURRENT_VERSION
import at.bitfire.davdroid.settings.AccountSettings.Companion.KEY_SETTINGS_VERSION
import at.bitfire.davdroid.settings.InMemorySettingsStore
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.sync.AutomaticSyncManager
import at.bitfire.davdroid.sync.account.TestAccount
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Provider

@HiltAndroidTest
class AccountSettingsMigrationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private val store = InMemorySettingsStore()

    @Inject
    lateinit var automaticSyncManager: AutomaticSyncManager

    @Inject
    lateinit var logger: Logger

    @Inject
    lateinit var migrations: Map<Int, @JvmSuppressWildcards Provider<AccountSettingsMigration>>

    @Inject
    lateinit var settingsManager: SettingsManager

    lateinit var accountId: LegacyAccount

    @Before
    fun setUp() {
        hiltRule.inject()

        accountId = LegacyAccount(TestAccount.create(version = 6))
    }

    @After
    fun tearDown() {
        TestAccount.remove(accountId.androidAccount)
    }

    @Test
    fun runAllMigrations() {
        runBlocking { store.putValue(KEY_SETTINGS_VERSION, "6") }

        AccountSettings(
            accountId = accountId,
            abortOnMissingMigration = true,
            store = store,
            automaticSyncManager = automaticSyncManager,
            logger = logger,
            migrations = migrations,
            settingsManager = settingsManager,
            ioDispatcher = Dispatchers.Unconfined
        )

        assertEquals(CURRENT_VERSION.toString(), runBlocking { store.getValue(KEY_SETTINGS_VERSION) })
    }

    @Test
    fun testAbortOnMissingMigration() {
        runBlocking { store.putValue(KEY_SETTINGS_VERSION, "1") }

        try {
            AccountSettings(
                accountId = accountId,
                abortOnMissingMigration = true,
                store = store,
                automaticSyncManager = automaticSyncManager,
                logger = logger,
                migrations = migrations,
                settingsManager = settingsManager,
                ioDispatcher = Dispatchers.Unconfined
            )
            fail("Expected exception")
        } catch (e: IllegalArgumentException) {
            assertEquals("Missing AccountSettings migration 1 → 2", e.message)
        }
    }
}
