/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings

import at.bitfire.davdroid.accounts.DbAccountId
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

// Note: General functionality is tested in AccountSettingsStoreTest. This is only testing implementation details.
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
}
