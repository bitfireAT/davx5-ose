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

    @Inject
    lateinit var repository: DavHomeSetRepository

    @Inject
    lateinit var serviceRepository: DavServiceRepository

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    var serviceId: Long = 0

    @Before
    fun setUp() {
        hiltRule.inject()

        serviceId = serviceRepository.insertOrReplaceBlocking(
            Service(id=0, accountName="test", type= Service.TYPE_CALDAV, principal = null)
        )
    }


    @Test
    fun testInsertOrUpdate() {
        // should insert new row or update (upsert) existing row - without changing its key!
        val entry1 = HomeSet(id=0, serviceId=serviceId, personal=true, url="https://example.com/1".toHttpUrl())
        val insertId1 = repository.insertOrUpdateByUrlBlocking(entry1)
        assertEquals(1L, insertId1)
        assertEquals(entry1.copy(id = 1L), repository.getByIdBlocking(1L))

        val updatedEntry1 = HomeSet(id=0, serviceId=serviceId, personal=true, url="https://example.com/1".toHttpUrl(), displayName="Updated Entry")
        val updateId1 = repository.insertOrUpdateByUrlBlocking(updatedEntry1)
        assertEquals(1L, updateId1)
        assertEquals(updatedEntry1.copy(id = 1L), repository.getByIdBlocking(1L))

        val entry2 = HomeSet(id=0, serviceId=serviceId, personal=true, url= "https://example.com/2".toHttpUrl())
        val insertId2 = repository.insertOrUpdateByUrlBlocking(entry2)
        assertEquals(2L, insertId2)
        assertEquals(entry2.copy(id = 2L), repository.getByIdBlocking(2L))
    }

    @Test
    fun testDeleteBlocking() {
        // should delete row with given primary key (id)
        val entry1 = HomeSet(id=1, serviceId=serviceId, personal=true, url= "https://example.com/1".toHttpUrl())

        val insertId1 = repository.insertOrUpdateByUrlBlocking(entry1)
        assertEquals(1L, insertId1)
        assertEquals(entry1, repository.getByIdBlocking(1L))

        repository.deleteBlocking(entry1)
        assertEquals(null, repository.getByIdBlocking(1L))
    }

}