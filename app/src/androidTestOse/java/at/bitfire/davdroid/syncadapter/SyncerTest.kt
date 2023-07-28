// SPDX-FileCopyrightText: 2023 DAVx⁵ contributors <https://github.com/bitfireAT/davx5-ose/graphs/contributors>
//
// SPDX-License-Identifier: GPL-3.0-only

/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.Context
import android.content.SyncResult
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.R
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@HiltAndroidTest
class SyncerTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** use our WebDAV provider as a mock provider because it's our own and we don't need any permissions for it */
    val mockAuthority = context.getString(R.string.webdav_authority)
    val mockProvider = context.contentResolver!!.acquireContentProviderClient(mockAuthority)!!

    val account = Account(javaClass.canonicalName, context.getString(R.string.account_type))


    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun testOnPerformSync_runsSyncAndSetsClassLoader() {
        val syncer = TestSyncer(context)
        syncer.onPerformSync(account, arrayOf(), mockAuthority, mockProvider, SyncResult())

        // check whether onPerformSync() actually calls sync()
        assertEquals(1, syncer.syncCalled.get())

        // check whether contextClassLoader is set
        assertEquals(context.classLoader, Thread.currentThread().contextClassLoader)
    }


    class TestSyncer(context: Context) : Syncer(context) {

        val syncCalled = AtomicInteger()

        override fun sync(
            account: Account,
            extras: Array<String>,
            authority: String,
            httpClient: Lazy<HttpClient>,
            provider: ContentProviderClient,
            syncResult: SyncResult
        ) {
            Thread.sleep(1000)
            syncCalled.incrementAndGet()
        }

    }

}