/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import android.database.sqlite.SQLiteConstraintException
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.junit4.MockKRule
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject


@HiltAndroidTest
class PrincipalDaoTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mockKRule = MockKRule(this)

    @Inject
    lateinit var db: AppDatabase

    private lateinit var principalDao: PrincipalDao
    private lateinit var service: Service
    private val url = "https://example.com/dav/principal".toHttpUrl()

    @Before
    fun setUp() {
        hiltRule.inject()
        principalDao = spyk(db.principalDao())

        service = Service(id = 1, accountName = "account", type = "webdav")
        db.serviceDao().insertOrReplace(service)
    }

    @Test
    fun insertOrUpdate_insertsIfNotExisting() = runTest {
        val principal = Principal(serviceId = service.id, url = url, displayName = "principal")
        val id = principalDao.insertOrUpdate(service.id, principal)
        assertTrue(id > 0)

        val stored = principalDao.get(id)
        assertEquals("principal", stored.displayName)
        verify(exactly = 0) { principalDao.update(any()) }
    }

    @Test
    fun insertOrUpdate_doesNotUpdateIfDisplayNameIsEqual() = runTest {
        val principalOld = Principal(serviceId = service.id, url = url, displayName = "principalOld")
        val idOld = principalDao.insertOrUpdate(service.id, principalOld)

        val principalNew = Principal(serviceId = service.id, url = url, displayName = "principalOld")
        val idNew = principalDao.insertOrUpdate(service.id, principalNew)

        assertEquals(idOld, idNew)
        val stored = principalDao.get(idOld)
        assertEquals("principalOld", stored.displayName)
        verify(exactly = 0) { principalDao.update(any()) }
    }

    @Test
    fun insertOrUpdate_updatesIfDisplayNameIsDifferent() = runTest {
        val principalOld = Principal(serviceId = service.id, url = url, displayName = "principalOld")
        val idOld = principalDao.insertOrUpdate(service.id, principalOld)

        val principalNew = Principal(serviceId = service.id, url = url, displayName = "principalNew")
        val idNew = principalDao.insertOrUpdate(service.id, principalNew)

        assertEquals(idOld, idNew)

        val updated = principalDao.get(idOld)
        assertEquals("principalNew", updated.displayName)
        verify(exactly = 1) { principalDao.update(any()) }
    }

    @Test(expected = SQLiteConstraintException::class)
    fun insertOrUpdate_throwsForeignKeyConstraintViolationException() = runTest {
        // throws on non-existing service
        val url = "https://example.com/dav/principal".toHttpUrl()
        val principal1 = Principal(serviceId = 999, url = url, displayName = "p1")
        principalDao.insertOrUpdate(999, principal1)
    }

}