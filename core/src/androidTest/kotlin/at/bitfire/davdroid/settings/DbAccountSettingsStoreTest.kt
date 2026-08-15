/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings

import at.bitfire.davdroid.accounts.DbAccountId
import at.bitfire.davdroid.db.AccountSetting
import at.bitfire.davdroid.db.AccountSettingDao
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.DbAccount
import at.bitfire.davdroid.db.DbAccountDao
import at.bitfire.synctools.util.SensitiveString.Companion.toSensitiveString
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class DbAccountSettingsStoreTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var db: AppDatabase
    @Inject
    lateinit var storeFactory: DbAccountSettingsStore.Factory

    lateinit var dao: AccountSettingDao
    lateinit var accountDao: DbAccountDao
    var dbAccountId: Long = 0L
    lateinit var store: DbAccountSettingsStore

    @Before
    fun setUp() {
        hiltRule.inject()
        dao = db.accountSettingDao()
        accountDao = db.dbAccountDao()

        dbAccountId = DbAccount(name = "test").let {
            // insert returns the id of the inserted account, which is what we want
            db.dbAccountDao().insert(it)
        }
        store = storeFactory.create(DbAccountId(dbAccountId))
    }

    @After
    fun tearDown() {
        accountDao.deleteAllBlocking()
    }

    @Test
    fun test_getValue() {
        // Make sure initially it doesn't have a value
        assertNull(store.getValue("test"))

        // Insert a value now
        dao.insertBlocking(
            AccountSetting(accountId = dbAccountId, key = "test", value = "value")
        )

        // And verify that it's retrieved correctly
        assertEquals("value", store.getValue("test"))

        // Now insert a sensitive value
        dao.insertBlocking(
            AccountSetting(accountId = dbAccountId, key = "sensitive-test", sensitiveValue = "sensitive-value".toSensitiveString())
        )

        // And verify it cannot be fetched
        assertNull(store.getValue("sensitive-test"))
    }

    @Test
    fun test_putValue() {
        // Verify that initially there's no value
        assertNull(dao.getBlocking(accountId = dbAccountId, key = "test"))

        // Put a value
        store.putValue(key = "test", value = "value")

        // And verify it has been set correctly
        dao.getBlocking(accountId = dbAccountId, key = "test").let {
            assertNotNull(it)
            assertEquals(dbAccountId, it!!.accountId)
            assertEquals("test", it.key)
            assertEquals("value", it.value)
            assertNull(it.sensitiveValue)
        }

        // Now try deleting it
        store.putValue(key = "test", value = null)

        // And make sure it doesn't exist
        assertNull(dao.getBlocking(accountId = dbAccountId, key = "test"))
    }

    @Test
    fun test_getSensitiveValue() {
        // Make sure initially it doesn't have a value
        assertNull(store.getSensitiveValue("sensitive-test"))

        // Insert a value now
        dao.insertBlocking(
            AccountSetting(accountId = dbAccountId, key = "sensitive-test", sensitiveValue = "sensitive-value".toSensitiveString())
        )

        // And verify that it's retrieved correctly
        assertEquals("sensitive-value".toSensitiveString(), store.getSensitiveValue("sensitive-test"))

        // Now insert a regular value
        dao.insertBlocking(
            AccountSetting(accountId = dbAccountId, key = "test", value = "value")
        )

        // And verify it cannot be fetched
        assertNull(store.getSensitiveValue("test"))
    }

    @Test
    fun test_putSensitiveValue() {
        // Verify that initially there's no value
        assertNull(dao.getBlocking(accountId = dbAccountId, key = "sensitive-test"))

        // Put a value
        store.putSensitiveValue(key = "sensitive-test", value = "sensitive-value".toSensitiveString())

        // And verify it has been set correctly
        dao.getBlocking(accountId = dbAccountId, key = "sensitive-test").let {
            assertNotNull(it)
            assertEquals(dbAccountId, it!!.accountId)
            assertEquals("sensitive-test", it.key)
            assertEquals("sensitive-value".toSensitiveString(), it.sensitiveValue)
            assertNull(it.value)
        }

        // Now try deleting it
        store.putSensitiveValue(key = "sensitive-test", value = null)

        // And make sure it doesn't exist
        assertNull(dao.getBlocking(accountId = dbAccountId, key = "sensitive-test"))
    }

    /**
     * Utility for [test_deleteMixed_putValue] and [test_deleteMixed_putSensitiveValue]:
     * - Makes sure initially there's no value in `mixed-test`
     * - Inserts `value` and `sensitive-value` for regular and sensitive values respectively.
     * - Verifies both of them have been set correctly.
     */
    private fun prepareDeleteMixed() {
        // Verify that initially there's no value
        assertNull(dao.getBlocking(accountId = dbAccountId, key = "mixed-test"))

        // Put a value as regular and sensitive
        store.putValue(key = "mixed-test", value = "value")
        store.putSensitiveValue(key = "mixed-test", value = "sensitive-value".toSensitiveString())

        // Verify they have been set correctly
        dao.getBlocking(accountId = dbAccountId, key = "mixed-test").let {
            assertNotNull(it)
            assertEquals(dbAccountId, it!!.accountId)
            assertEquals("mixed-test", it.key)
            assertEquals("value", it.value)
            assertEquals("sensitive-value".toSensitiveString(), it.sensitiveValue)
        }
    }

    @Test
    fun test_deleteMixed_putValue() {
        prepareDeleteMixed()

        // Now try deleting it from putValue
        store.putValue(key = "mixed-test", value = null)

        // It should be removed from value, but not sensitiveValue
        dao.getBlocking(accountId = dbAccountId, key = "mixed-test").let {
            assertNotNull(it)
            assertEquals(dbAccountId, it!!.accountId)
            assertEquals("mixed-test", it.key)
            assertNull(it.value)
            assertEquals("sensitive-value".toSensitiveString(), it.sensitiveValue)
        }
    }

    @Test
    fun test_deleteMixed_putSensitiveValue() {
        prepareDeleteMixed()

        // Now try deleting it from putSensitiveValue
        store.putSensitiveValue(key = "mixed-test", value = null)

        // It should be removed from sensitiveValue, but not value
        dao.getBlocking(accountId = dbAccountId, key = "mixed-test").let {
            assertNotNull(it)
            assertEquals(dbAccountId, it!!.accountId)
            assertEquals("mixed-test", it.key)
            assertEquals("value", it.value)
            assertNull(it.sensitiveValue)
        }
    }
}