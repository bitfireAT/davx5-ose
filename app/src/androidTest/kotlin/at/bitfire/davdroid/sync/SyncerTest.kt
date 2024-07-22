/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.Context
import android.content.SyncResult
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.internal.Provider
import okhttp3.HttpUrl
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@HiltAndroidTest
class SyncerTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    @ApplicationContext
    lateinit var context: Context

    val account by lazy { Account(javaClass.canonicalName, context.getString(R.string.account_type)) }

    @Before
    fun setUp() {
        hiltRule.inject()
    }


    @Test
    fun testOnPerformSync_runsSyncAndSetsClassLoader() {
        val syncer = TestSyncer()
        syncer.onPerformSync()

        // check whether onPerformSync() actually calls sync()
        assertEquals(1, syncer.syncCalled.get())

        // check whether contextClassLoader is set
        assertEquals(context.classLoader, Thread.currentThread().contextClassLoader)
    }


    inner class TestSyncer : Syncer<LocalTestCollection>(account, arrayOf(), SyncResult()) {

        val syncCalled = AtomicInteger()
        override val serviceType: String
            get() = TODO("Not yet implemented")
        override val authority: String
            get() = TODO("Not yet implemented")
        override val localCollections: List<LocalTestCollection>
            get() = TODO("Not yet implemented")
        override val localSyncCollections: List<LocalTestCollection>
            get() = TODO("Not yet implemented")

        override fun beforeSync() {
            syncCalled.incrementAndGet()
        }

        override fun create(remoteCollection: Collection) {
            TODO("Not yet implemented")
        }

        override fun afterSync() {
            TODO("Not yet implemented")
        }

        override fun syncCollection(
            localCollection: LocalTestCollection,
            remoteCollection: Collection
        ) {
            TODO("Not yet implemented")
        }

        override fun update(localCollection: LocalTestCollection, remoteCollection: Collection) {
            TODO("Not yet implemented")
        }

        override fun delete(localCollection: LocalTestCollection) {
            TODO("Not yet implemented")
        }

        override fun getUrl(localCollection: LocalTestCollection): HttpUrl? {
            TODO("Not yet implemented")
        }

    }

}