/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.push.PushRegistrationManager
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.sync.worker.SyncWorkerManager
import at.bitfire.davdroid.util.MainDispatcher
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.coVerify
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class CollectionSelectedUseCaseTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mockkRule = MockKRule(this)

    val collection = Collection(
        id = 2,
        serviceId = 1,
        type = Collection.Companion.TYPE_CALENDAR,
        url = "https://example.com".toHttpUrl()
    )

    @Inject
    lateinit var collectionRepository: DavCollectionRepository

    @Inject
    @MainDispatcher
    lateinit var mainDispatcher: CoroutineDispatcher

    val service = Service(
        id = 1,
        type = Service.Companion.TYPE_CALDAV,
        accountName = "test@example.com"
    )

    @BindValue
    @RelaxedMockK
    lateinit var pushRegistrationManager: PushRegistrationManager

    @Inject
    lateinit var serviceRepository: DavServiceRepository

    @BindValue
    @RelaxedMockK
    lateinit var syncWorkerManager: SyncWorkerManager

    @Inject
    lateinit var useCase: CollectionSelectedUseCase

    @Before
    fun setUp() {
        hiltRule.inject()

        serviceRepository.insertOrReplace(service)
        collectionRepository.insertOrUpdateByUrl(collection)
    }

    @After
    fun tearDown() {
        serviceRepository.deleteAll()
    }


    @Test
    fun testHandleWithDelay() = runTest(mainDispatcher) {
        useCase.handleWithDelay(collectionId = collection.id)

        advanceUntilIdle()
        coVerify {
            syncWorkerManager.enqueueOneTimeAllAuthorities(any())
            pushRegistrationManager.update(service.id)
        }
    }

}