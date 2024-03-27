/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class HomesetDaoTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var db: AppDatabase

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testInsertOrUpdate() {
        // should insert new row or update (upsert) existing row - without changing its key!
        val serviceId = createTestService()
        val homeSetDao = db.homeSetDao()

        val entry1 = HomeSet(id=0, serviceId=serviceId, personal=true, url="https://example.com/1".toHttpUrl())
        val insertId1 = homeSetDao.insertOrUpdateByUrl(entry1)
        assertEquals(1L, insertId1)
        assertEquals(entry1.apply { id = 1L }, homeSetDao.getById(1L))

        val updatedEntry1 = HomeSet(id=0, serviceId=serviceId, personal=true, url="https://example.com/1".toHttpUrl(), displayName="Updated Entry")
        val updateId1 = homeSetDao.insertOrUpdateByUrl(updatedEntry1)
        assertEquals(1L, updateId1)
        assertEquals(updatedEntry1.apply { id = 1L }, homeSetDao.getById(1L))

        val entry2 = HomeSet(id=0, serviceId=serviceId, personal=true, url= "https://example.com/2".toHttpUrl())
        val insertId2 = homeSetDao.insertOrUpdateByUrl(entry2)
        assertEquals(2L, insertId2)
        assertEquals(entry2.apply { id = 2L }, homeSetDao.getById(2L))
    }

    @Test
    fun testDelete() {
        // should delete row with given primary key (id)
        val serviceId = createTestService()
        val homesetDao = db.homeSetDao()

        val entry1 = HomeSet(id=1, serviceId=serviceId, personal=true, url= "https://example.com/1".toHttpUrl())

        val insertId1 = homesetDao.insertOrUpdateByUrl(entry1)
        assertEquals(1L, insertId1)
        assertEquals(entry1, homesetDao.getById(1L))

        homesetDao.delete(entry1)

        assertEquals(null, homesetDao.getById(1L))
    }

    fun createTestService() : Long {
        val serviceDao = db.serviceDao()
        val service = Service(id=0, accountName="test", type=Service.TYPE_CALDAV, principal = null)
        return serviceDao.insertOrReplace(service)
    }

}