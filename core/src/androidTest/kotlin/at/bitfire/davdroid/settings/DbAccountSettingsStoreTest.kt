/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings

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
    lateinit var dbAccount: DbAccount
    lateinit var store: DbAccountSettingsStore

    @Before
    fun setUp() {
        hiltRule.inject()
        dao = db.accountSettingDao()
        accountDao = db.dbAccountDao()

        dbAccount = DbAccount(name = "test").let {
            val id = db.dbAccountDao().insert(it)
            // Make sure the id of the returned account is correctly set
            it.copy(id = id)
        }
        store = storeFactory.create(at.bitfire.davdroid.accounts.DbAccount(dbAccount))
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
            AccountSetting(accountId = dbAccount.id, key = "test", value = "value")
        )

        // And verify that it's retrieved correctly
        assertEquals("value", store.getValue("test"))

        // Now insert a sensitive value
        dao.insertBlocking(
            AccountSetting(accountId = dbAccount.id, key = "sensitive-test", sensitiveValue = "sensitive-value".toSensitiveString())
        )

        // And verify it cannot be fetched
        assertNull(store.getValue("sensitive-test"))
    }

    @Test
    fun test_putValue() {
        // Verify that initially there's no value
        assertNull(dao.getBlocking(accountId = dbAccount.id, key = "test"))

        // Put a value
        store.putValue(key = "test", value = "value")

        // And verify it has been set correctly
        dao.getBlocking(accountId = dbAccount.id, key = "test").let {
            assertNotNull(it)
            assertEquals(dbAccount.id, it!!.accountId)
            assertEquals("test", it.key)
            assertEquals("value", it.value)
            assertNull(it.sensitiveValue)
        }
    }

    @Test
    fun test_getSensitiveValue() {
        // Make sure initially it doesn't have a value
        assertNull(store.getSensitiveValue("sensitive-test"))

        // Insert a value now
        dao.insertBlocking(
            AccountSetting(accountId = dbAccount.id, key = "sensitive-test", sensitiveValue = "sensitive-value".toSensitiveString())
        )

        // And verify that it's retrieved correctly
        assertEquals("sensitive-value".toSensitiveString(), store.getSensitiveValue("sensitive-test"))

        // Now insert a regular value
        dao.insertBlocking(
            AccountSetting(accountId = dbAccount.id, key = "test", value = "value")
        )

        // And verify it cannot be fetched
        assertNull(store.getSensitiveValue("test"))
    }

    @Test
    fun test_putSensitiveValue() {
        // Verify that initially there's no value
        assertNull(dao.getBlocking(accountId = dbAccount.id, key = "sensitive-test"))

        // Put a value
        store.putSensitiveValue(key = "sensitive-test", value = "sensitive-value".toSensitiveString())

        // And verify it has been set correctly
        dao.getBlocking(accountId = dbAccount.id, key = "sensitive-test").let {
            assertNotNull(it)
            assertEquals(dbAccount.id, it!!.accountId)
            assertEquals("sensitive-test", it.key)
            assertEquals("sensitive-value".toSensitiveString(), it.sensitiveValue)
            assertNull(it.value)
        }
    }
}