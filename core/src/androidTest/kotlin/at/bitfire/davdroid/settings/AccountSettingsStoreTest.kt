/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import at.bitfire.davdroid.accounts.DbAccountId
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.DbAccount
import at.bitfire.davdroid.db.migration.AutoMigration12
import at.bitfire.davdroid.db.migration.AutoMigration16
import at.bitfire.davdroid.db.migration.AutoMigration18
import at.bitfire.davdroid.sync.account.TestAccount
import at.bitfire.synctools.util.SensitiveString.Companion.toSensitiveString
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

/**
 * Tests all [AccountSettingsStore] implementations.
 */
@RunWith(Parameterized::class)
class AccountSettingsStoreTest(private val parameters: TestParameters) {

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun data(): List<TestParameters> {
            return listOf(
                TestParameters(
                    name = "InMemorySettingsStore",
                    storeFactory = { InMemorySettingsStore() },
                    throwOnValueTypeMixup = true
                ),
                createDbAccountSettingsStoreTestParameters(),
                createAccountManagerSettingsStoreTestParameters()
            )
        }

        private fun createDbAccountSettingsStoreTestParameters(): TestParameters {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val appDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>(context)
                .addAutoMigrationSpec(mockk<AutoMigration12>())
                .addAutoMigrationSpec(mockk<AutoMigration16>())
                .addAutoMigrationSpec(mockk<AutoMigration18>())
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
            lateinit var accountId: DbAccountId

            return TestParameters(
                name = "DbAccountSettingsStore",
                storeFactory = {
                    val id = appDatabase.dbAccountDao().insert(DbAccount(name = "test"))
                    accountId = DbAccountId(id)

                    DbAccountSettingsStore(accountId, appDatabase)
                },
                throwOnValueTypeMixup = true,
                cleanUp = { appDatabase.dbAccountDao().deleteAllBlocking() }
            )
        }

        private fun createAccountManagerSettingsStoreTestParameters(): TestParameters {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val accountManager = AccountManager.get(context)
            lateinit var account: Account

            return TestParameters(
                name = "AccountManagerSettingsStore",
                storeFactory = {
                    account = TestAccount.create()
                    AccountManagerSettingsStore(account, accountManager)
                },
                throwOnValueTypeMixup = false,
                cleanUp = { TestAccount.remove(account) }
            )
        }

        private const val KEY = "key"
        private const val VALUE = "value"
        private const val VALUE2 = "value2"
        private val SENSITIVE_VALUE = "sensitive".toSensitiveString()
        private val SENSITIVE_VALUE2 = "sensitive2".toSensitiveString()
    }


    private val store = parameters.storeFactory()

    @After
    fun tearDown() {
        parameters.cleanUp()
    }

    @Test
    fun test_getValue_withUnknownKey() {
        val result = store.getValue(KEY)

        assertNull(result)
    }

    @Test
    fun test_putValue() {
        store.putValue(KEY, VALUE)

        assertEquals(VALUE, store.getValue(KEY))
    }

    @Test
    fun test_putValue_overwriting_value() {
        store.putValue(KEY, VALUE)

        store.putValue(KEY, VALUE2)

        assertEquals(VALUE2, store.getValue(KEY))
    }

    @Test
    fun test_getSensitiveValue_withUnknownKey() {
        val result = store.getSensitiveValue(KEY)

        assertNull(result)
    }

    @Test
    fun test_putSensitiveValue() {
        store.putSensitiveValue(KEY, SENSITIVE_VALUE)

        assertEquals(SENSITIVE_VALUE, store.getSensitiveValue(KEY))
    }

    @Test
    fun test_putSensitiveValue_overwriting_sensitiveValue() {
        store.putSensitiveValue(KEY, SENSITIVE_VALUE)

        store.putSensitiveValue(KEY, SENSITIVE_VALUE2)

        assertEquals(SENSITIVE_VALUE2, store.getSensitiveValue(KEY))
    }

    @Test
    fun test_putValue_overwritingSensitiveValue() {
        assumeTrue(parameters.throwOnValueTypeMixup)

        store.putSensitiveValue(KEY, SENSITIVE_VALUE)

        try {
            store.putValue(KEY, VALUE)
            fail("Expected exception")
        } catch (e: IllegalStateException) {
            assertEquals("""Key "$KEY" is already used for a sensitive value""", e.message)
        }
    }

    @Test
    fun test_putSensitiveValue_overwritesValue() {
        assumeTrue(parameters.throwOnValueTypeMixup)

        store.putValue(KEY, VALUE)

        try {
            store.putSensitiveValue(KEY, SENSITIVE_VALUE)
            fail("Expected exception")
        } catch (e: IllegalStateException) {
            assertEquals("""Key "$KEY" is already used for a non-sensitive value""", e.message)
        }
    }
}

/**
 * Test parameters for [AccountSettingsStoreTest].
 *
 * @property name The name of the [AccountSettingsStore] implementation.
 * @property storeFactory A code block that is run before any of the tests to create the `AccountSettingsStore` instance
 *   to be tested.
 * @property throwOnValueTypeMixup If this is `true`, tests run to make sure the `AccountSettingsStore` instance under
 *   test throws when [AccountSettingsStore.putValue] and [AccountSettingsStore.putSensitiveValue] are called with the
 *   same key. Only the "legacy" implementation [AccountManagerSettingsStore] should use `false` here.
 * @property cleanUp A code block that is run to clean up after a test run.
 */
data class TestParameters(
    val name: String,
    val storeFactory: () -> AccountSettingsStore,
    val throwOnValueTypeMixup: Boolean,
    val cleanUp: () -> Unit = {},
) {
    override fun toString() = name
}