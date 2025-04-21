/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.repository

import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import dagger.hilt.android.testing.BindValueIntoSet
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class DavCollectionRepositoryTest {

    @Inject
    lateinit var collectionRepository: DavCollectionRepository

    @Inject
    lateinit var db: AppDatabase

    @BindValueIntoSet
    @MockK(relaxed = true)
    lateinit var testObserver: DavCollectionRepository.OnChangeListener

    @Inject
    lateinit var serviceRepository: DavServiceRepository

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mockkKRule = MockKRule(this)

    var service: Service? = null

    @Before
    fun setUp() {
        hiltRule.inject()

        // insert test service
        val serviceId = serviceRepository.insertOrReplace(
            Service(id=0, accountName="test", type=Service.TYPE_CARDDAV, principal = null)
        )
        service = serviceRepository.get(serviceId)!!
    }

    @After
    fun cleanUp() {
        db.close()
        serviceRepository.deleteAll()
    }


    @Test
    fun testOnChangeListener_setForceReadOnly() = runTest {
        val collectionId = db.collectionDao().insertOrUpdateByUrl(
            Collection(
                serviceId = service!!.id,
                type = Collection.TYPE_ADDRESSBOOK,
                url = "https://example.com".toHttpUrl(),
                forceReadOnly = false,
            )
        )

        assert(db.collectionDao().get(collectionId)?.forceReadOnly == false)
        verify(exactly = 0) {
            testObserver.onCollectionsChanged()
        }
        collectionRepository.setForceReadOnly(collectionId, true)
        assert(db.collectionDao().get(collectionId)?.forceReadOnly == true)
        verify(exactly = 1) {
            testObserver.onCollectionsChanged()
        }
    }

}