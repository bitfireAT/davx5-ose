/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class HomeSetDaoTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var db: AppDatabase
    lateinit var dao: HomeSetDao
    var serviceId: Long = 0

    @Before
    fun setUp() {
        hiltRule.inject()
        dao = db.homeSetDao()

        serviceId = db.serviceDao().insertOrReplace(
            Service(id=0, accountName="test", type= Service.TYPE_CALDAV, principal = null)
        )
    }

    @After
    fun tearDown() {
        db.serviceDao().deleteAll()
    }


    @Test
    fun testInsertOrUpdate() {
        // should insert new row or update (upsert) existing row - without changing its key!
        val entry1 = HomeSet(id=0, serviceId=serviceId, personal=true, url="https://example.com/1".toHttpUrl())
        val insertId1 = dao.insertOrUpdateByUrlBlocking(entry1)
        assertEquals(1L, insertId1)
        assertEquals(entry1.copy(id = 1L), dao.getById(1))

        val updatedEntry1 = HomeSet(id=0, serviceId=serviceId, personal=true, url="https://example.com/1".toHttpUrl(), displayName="Updated Entry")
        val updateId1 = dao.insertOrUpdateByUrlBlocking(updatedEntry1)
        assertEquals(1L, updateId1)
        assertEquals(updatedEntry1.copy(id = 1L), dao.getById(1))

        val entry2 = HomeSet(id=0, serviceId=serviceId, personal=true, url= "https://example.com/2".toHttpUrl())
        val insertId2 = dao.insertOrUpdateByUrlBlocking(entry2)
        assertEquals(2L, insertId2)
        assertEquals(entry2.copy(id = 2L), dao.getById(2))
    }

    @Test
    fun testInsertOrUpdate_TransactionSafe() {
        runBlocking(Dispatchers.IO) {
            for (i in 0..9999)
                launch {
                    dao.insertOrUpdateByUrlBlocking(
                        HomeSet(
                            id = 0,
                            serviceId = serviceId,
                            url = "https://example.com/".toHttpUrl(),
                            personal = true
                        )
                    )
                }
        }
        assertEquals(1, dao.getByService(serviceId).size)
    }

    @Test
    fun testDelete() {
        // should delete row with given primary key (id)
        val entry1 = HomeSet(id=1, serviceId=serviceId, personal=true, url= "https://example.com/1".toHttpUrl())

        val insertId1 = dao.insertOrUpdateByUrlBlocking(entry1)
        assertEquals(1L, insertId1)
        assertEquals(entry1, dao.getById(1L))

        dao.delete(entry1)
        assertEquals(null, dao.getById(1L))
    }

}