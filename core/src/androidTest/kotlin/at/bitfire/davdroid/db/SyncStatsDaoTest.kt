/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import androidx.sqlite.SQLiteException
import at.bitfire.davdroid.sync.SyncDataType
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class SyncStatsDaoTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var db: AppDatabase
    var collectionId: Long = 0

    @Before
    fun setUp() {
        hiltRule.inject()

        val serviceId = db.serviceDao().insertOrReplace(Service(
            id = 0,
            accountName = "test@example.com",
            type = Service.TYPE_CALDAV
        ))
        collectionId = db.collectionDao().insert(Collection(
            id = 0,
            serviceId = serviceId,
            type = Collection.TYPE_CALENDAR,
            url = "https://example.com".toHttpUrl()
        ))
    }

    @After
    fun tearDown() {
        db.serviceDao().deleteAll()
    }

    @Test
    fun testInsertOrReplace_ExistingForeignKey() = runTest {
        val dao = db.syncStatsDao()
        dao.insertOrReplace(
            SyncStats(
                id = 0,
                collectionId = collectionId,
                dataType = SyncDataType.CONTACTS.toString(),
                lastSync = System.currentTimeMillis()
            )
        )
    }

    @Test(expected = SQLiteException::class)
    fun testInsertOrReplace_MissingForeignKey() = runTest {
        val dao = db.syncStatsDao()
        dao.insertOrReplace(
            SyncStats(
                id = 0,
                collectionId = 12345,
                dataType = SyncDataType.CONTACTS.toString(),
                lastSync = System.currentTimeMillis()
            )
        )
    }

}