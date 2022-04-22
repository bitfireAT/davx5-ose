/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.db

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DaoToolsTest {

    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().context
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testSyncAll() {
        val serviceDao = db.serviceDao()
        val service = Service(id=0, accountName="test", type=Service.TYPE_CALDAV, principal = null)
        service.id = serviceDao.insertOrReplace(service)

        val homeSetDao = db.homeSetDao()
        val entry1 = HomeSet(id=1, serviceId=service.id, personal=true, url= "https://example.com/1".toHttpUrl())
        val entry3 = HomeSet(id=3, serviceId=service.id, personal=true, url= "https://example.com/3".toHttpUrl())
        val oldItems = listOf(
                entry1,
                HomeSet(id=2, serviceId=service.id, personal=true, url= "https://example.com/2".toHttpUrl()),
                entry3
        )
        homeSetDao.insert(oldItems)

        val newItems = mutableMapOf<HttpUrl, HomeSet>()
        newItems[entry1.url] = entry1

        // no id, because identity is given by the url
        val updated = HomeSet(id=0, serviceId=service.id, personal=true,
                url= "https://example.com/2".toHttpUrl(), displayName="Updated Entry")
        newItems[updated.url] = updated

        val created = HomeSet(id=4, serviceId=service.id, personal=true, url= "https://example.com/4".toHttpUrl())
        newItems[created.url] = created

        DaoTools(homeSetDao).syncAll(oldItems, newItems, { it.url })

        val afterSync = homeSetDao.getByService(service.id)
        assertEquals(afterSync.size, 3)
        assertFalse(afterSync.contains(entry3))
        assertTrue(afterSync.contains(entry1))
        assertTrue(afterSync.contains(updated))
        assertTrue(afterSync.contains(created))
    }

}