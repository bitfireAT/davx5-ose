/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import android.database.sqlite.SQLiteConstraintException
import at.bitfire.davdroid.util.DavUtils.toUrl
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.junit4.MockKRule
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
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
    private val url = "https://example.com/dav/principal".toUrl()

    @Before
    fun setUp() {
        hiltRule.inject()
        principalDao = spyk(db.principalDao())

        service = Service(id = 1, accountName = "account", type = "webdav")
        db.serviceDao().insertOrReplaceBlocking(service)
    }

    @Test
    fun insertOrUpdate_insertsIfNotExisting() = runTest {
        val principal = Principal(serviceId = service.id, url = url, displayName = "principal")
        val id = principalDao.insertOrUpdateBlocking(service.id, principal)
        assertTrue(id > 0)

        val stored = principalDao.getBlocking(id)
        assertEquals("principal", stored.displayName)
        verify(exactly = 0) { principalDao.updateBlocking(any()) }
    }

    @Test
    fun insertOrUpdate_doesNotUpdateIfDisplayNameIsEqual() = runTest {
        val principalOld = Principal(serviceId = service.id, url = url, displayName = "principalOld")
        val idOld = principalDao.insertOrUpdateBlocking(service.id, principalOld)

        val principalNew = Principal(serviceId = service.id, url = url, displayName = "principalOld")
        val idNew = principalDao.insertOrUpdateBlocking(service.id, principalNew)

        assertEquals(idOld, idNew)
        val stored = principalDao.getBlocking(idOld)
        assertEquals("principalOld", stored.displayName)
        verify(exactly = 0) { principalDao.updateBlocking(any()) }
    }

    @Test
    fun insertOrUpdate_updatesIfDisplayNameIsDifferent() = runTest {
        val principalOld = Principal(serviceId = service.id, url = url, displayName = "principalOld")
        val idOld = principalDao.insertOrUpdateBlocking(service.id, principalOld)

        val principalNew = Principal(serviceId = service.id, url = url, displayName = "principalNew")
        val idNew = principalDao.insertOrUpdateBlocking(service.id, principalNew)

        assertEquals(idOld, idNew)

        val updated = principalDao.getBlocking(idOld)
        assertEquals("principalNew", updated.displayName)
        verify(exactly = 1) { principalDao.updateBlocking(any()) }
    }

    @Test(expected = SQLiteConstraintException::class)
    fun insertOrUpdate_throwsForeignKeyConstraintViolationException() = runTest {
        // throws on non-existing service
        val url = "https://example.com/dav/principal".toUrl()
        val principal1 = Principal(serviceId = 999, url = url, displayName = "p1")
        principalDao.insertOrUpdateBlocking(999, principal1)
    }

}