package at.bitfire.davdroid.repository

import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class DavCollectionRepositoryTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var db: AppDatabase

    val context = InstrumentationRegistry.getInstrumentation().targetContext

    var service: Service? = null

    @Before
    fun setUp() {
        hiltRule.inject()
        service = createTestService(Service.TYPE_CARDDAV)!!
    }

    @After
    fun cleanUp() {
        db.close()
        db.serviceDao().deleteAll()
    }

    @Test
    fun testOnChangeListener_setForceReadOnly() = runBlocking {
        val collectionId = db.collectionDao().insertOrUpdateByUrl(
            Collection(
                serviceId = service!!.id,
                type = Collection.TYPE_ADDRESSBOOK,
                url = "https://example.com".toHttpUrl(),
                forceReadOnly = false,
            )
        )
        val testObserver = mockk<DavCollectionRepository.OnChangeListener>(relaxed = true)
        val collectionRepository = DavCollectionRepository(context, mutableSetOf(testObserver), db)

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


    // Test helpers and dependencies

    private fun createTestService(serviceType: String) : Service? {
        val service = Service(id=0, accountName="test", type=serviceType, principal = null)
        val serviceId = db.serviceDao().insertOrReplace(service)
        return db.serviceDao().get(serviceId)
    }
}