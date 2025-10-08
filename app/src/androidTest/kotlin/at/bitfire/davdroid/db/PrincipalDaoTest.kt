/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import android.database.sqlite.SQLiteConstraintException
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.logging.Logger
import javax.inject.Inject


@HiltAndroidTest
class PrincipalDaoTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var db: AppDatabase

    lateinit var principalDao: PrincipalDao

    @Before
    fun setUp() {
        hiltRule.inject()
        principalDao = db.principalDao()
    }

    @Test
    fun insertOrUpdate_insertsAndUpdatesCorrectly() = runTest {
        // Prepare
        val service = Service(id = 1, accountName = "account", type = "webdav")
        db.serviceDao().insertOrReplace(service)

        // Inserts if not existing
        val url = "https://example.com/dav/principal".toHttpUrl()
        val principal1 = Principal(serviceId = service.id, url = url, displayName = "p1")
        val id1 = principalDao.insertOrUpdate(service.id, principal1)
        assertTrue(id1 > 0)

        // Does not update if display name is equal
        val principal2 = Principal(serviceId = service.id, url = url, displayName = "p1")
        val id2 = principalDao.insertOrUpdate(service.id, principal2)
        assertEquals(id1, id2)

        // Update does not change id
        val principal3 = Principal(serviceId = service.id, url = url, displayName = "p3")
        val id3 = principalDao.insertOrUpdate(service.id, principal3)
        assertEquals(id1, id3)

        // Updates principal, if display name is different
        val updated = principalDao.get(id1)
        assertEquals("p3", updated.displayName)
    }

    @Test(expected = SQLiteConstraintException::class)
    fun insertOrUpdate_throwsForeignKeyConstraintViolationException() = runTest {
        // throws on non-existing service
        val url = "https://example.com/dav/principal".toHttpUrl()
        val principal1 = Principal(serviceId = 999, url = url, displayName = "p1")
        principalDao.insertOrUpdate(999, principal1)
    }

}