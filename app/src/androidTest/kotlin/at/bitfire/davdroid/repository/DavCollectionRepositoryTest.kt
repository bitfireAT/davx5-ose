package at.bitfire.davdroid.repository

import android.accounts.Account
import android.content.Context
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.sync.account.TestAccountAuthenticator
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
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
import at.bitfire.davdroid.db.Account as DbAccount

@HiltAndroidTest
class DavCollectionRepositoryTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var accountRepository: AccountRepository

    @Inject
    lateinit var accountSettingsFactory: AccountSettings.Factory

    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var db: AppDatabase

    @Inject
    lateinit var serviceRepository: DavServiceRepository

    lateinit var account: Account
    var serviceId: Long = 0L

    @Before
    fun setUp() {
        hiltRule.inject()

        account = TestAccountAuthenticator.create()
        db.accountDao().insertOrIgnore(DbAccount(name = account.name))

        val service = Service(id=0, accountName=account.name, type= Service.TYPE_CALDAV, principal = null)
        serviceId = serviceRepository.insertOrReplace(service)
    }

    @After
    fun cleanUp() {
        db.close()

        serviceRepository.deleteAll()
        TestAccountAuthenticator.remove(account)
    }


    @Test
    fun testOnChangeListener_setForceReadOnly() = runBlocking {
        val collectionId = db.collectionDao().insertOrUpdateByUrl(
            Collection(
                serviceId = serviceId,
                type = Collection.TYPE_ADDRESSBOOK,
                url = "https://example.com".toHttpUrl(),
                forceReadOnly = false,
            )
        )
        val testObserver = mockk<DavCollectionRepository.OnChangeListener>(relaxed = true)
        val collectionRepository = DavCollectionRepository(
            accountRepository,
            accountSettingsFactory,
            context,
            db,
            object : Lazy<Set<DavCollectionRepository.OnChangeListener>> {
                override fun get(): Set<DavCollectionRepository.OnChangeListener> {
                    return mutableSetOf(testObserver)
                }
            },
            serviceRepository
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


    // Test helpers and dependencies

}