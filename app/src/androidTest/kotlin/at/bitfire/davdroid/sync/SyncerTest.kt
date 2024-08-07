/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.SyncResult
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.sync.account.TestAccountAuthenticator
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class SyncerTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var testSyncer: TestSyncer.Factory

    lateinit var account: Account

    @Before
    fun setUp() {
        hiltRule.inject()

        account = TestAccountAuthenticator.create()
    }

    @After
    fun tearDown() {
        TestAccountAuthenticator.remove(account)
    }


    @Test
    fun testSync_prepare_fails() {
        val syncer = spyk(testSyncer.create(account, arrayOf(), SyncResult()))
        val provider = mockk<ContentProviderClient>(relaxed = true)
        every { syncer.prepare(provider) } returns false
        every { syncer.getSyncCollections(any()) } returns emptyList()

        syncer.sync(provider)

        verify(exactly = 1) { syncer.prepare(provider) }
        verify(exactly = 0) { syncer.getSyncCollections(any()) }
    }

    @Test
    fun testSync_prepare_succeeds() {
        val syncer = spyk(testSyncer.create(account, arrayOf(), SyncResult()))
        val provider = mockk<ContentProviderClient>(relaxed = true)
        every { syncer.prepare(provider) } returns true
        every { syncer.getSyncCollections(any()) } returns emptyList()
        every { syncer.localSyncCollections(any()) } returns emptyList()

        syncer.sync(provider)

        verify(exactly = 1) { syncer.prepare(provider) }
//        verify(exactly = 1) { syncer.getSyncCollections(any()) }
        verify(exactly = 1) { syncer.localSyncCollections(any()) }
    }


    class TestSyncer @AssistedInject constructor(
        @Assisted account: Account,
        @Assisted extras: Array<String>,
        @Assisted syncResult: SyncResult
    ) : Syncer<LocalTestCollection>(account, extras, syncResult) {

        @AssistedFactory
        interface Factory {
            fun create(account: Account, extras: Array<String>, syncResult: SyncResult): TestSyncer
        }

        override val authority: String
            get() = ""
        override val serviceType: String
            get() = ""

        override fun prepare(provider: ContentProviderClient): Boolean = true

        override fun localSyncCollections(provider: ContentProviderClient): List<LocalTestCollection> = emptyList()

        override fun getSyncCollections(serviceId: Long): List<Collection> = emptyList()

        override fun create(provider: ContentProviderClient, remoteCollection: Collection) {}

        override fun syncCollection(
            provider: ContentProviderClient,
            localCollection: LocalTestCollection,
            remoteCollection: Collection
        ) {}

        override fun update(localCollection: LocalTestCollection, remoteCollection: Collection) {}

    }

}