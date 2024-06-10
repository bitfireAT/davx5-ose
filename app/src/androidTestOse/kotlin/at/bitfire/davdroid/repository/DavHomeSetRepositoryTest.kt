/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.repository

import at.bitfire.davdroid.db.HomeSet
import at.bitfire.davdroid.db.Service
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class DavHomeSetRepositoryTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var repository: DavHomeSetRepository

    @Inject
    lateinit var serviceRepository: DavServiceRepository

    @Before
    fun setUp() {
        hiltRule.inject()
    }


    @Test
    fun testInsertOrUpdate() {
        // should insert new row or update (upsert) existing row - without changing its key!
        val serviceId = createTestService()

        val entry1 = HomeSet(id=0, serviceId=serviceId, personal=true, url="https://example.com/1".toHttpUrl())
        val insertId1 = repository.insertOrUpdateByUrl(entry1)
        assertEquals(1L, insertId1)
        assertEquals(entry1.apply { id = 1L }, repository.getById(1L))

        val updatedEntry1 = HomeSet(id=0, serviceId=serviceId, personal=true, url="https://example.com/1".toHttpUrl(), displayName="Updated Entry")
        val updateId1 = repository.insertOrUpdateByUrl(updatedEntry1)
        assertEquals(1L, updateId1)
        assertEquals(updatedEntry1.apply { id = 1L }, repository.getById(1L))

        val entry2 = HomeSet(id=0, serviceId=serviceId, personal=true, url= "https://example.com/2".toHttpUrl())
        val insertId2 = repository.insertOrUpdateByUrl(entry2)
        assertEquals(2L, insertId2)
        assertEquals(entry2.apply { id = 2L }, repository.getById(2L))
    }

    @Test
    fun testDelete() {
        // should delete row with given primary key (id)
        val serviceId = createTestService()

        val entry1 = HomeSet(id=1, serviceId=serviceId, personal=true, url= "https://example.com/1".toHttpUrl())

        val insertId1 = repository.insertOrUpdateByUrl(entry1)
        assertEquals(1L, insertId1)
        assertEquals(entry1, repository.getById(1L))

        repository.delete(entry1)
        assertEquals(null, repository.getById(1L))
    }


    private fun createTestService() : Long {
        val service = Service(id=0, accountName="test", type= Service.TYPE_CALDAV, principal = null)
        return serviceRepository.insertOrReplace(service)
    }

}