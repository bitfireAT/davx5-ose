/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.Context
import android.content.SyncResult
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.settings.AccountSettings
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
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

    val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Inject
    lateinit var accountSettingsFactory: AccountSettings.Factory

    @Inject
    lateinit var db: AppDatabase

    /** use our WebDAV provider as a mock provider because it's our own and we don't need any permissions for it */
    private val mockAuthority = context.getString(R.string.webdav_authority)

    val account = Account(javaClass.canonicalName, context.getString(R.string.account_type))


    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun testOnPerformSync_runsSyncAndSetsClassLoader() {
        val syncer = TestSyncer(accountSettingsFactory, context, db)
        syncer.onPerformSync(account, arrayOf(), mockAuthority, SyncResult())

        // check whether onPerformSync() actually calls sync()
        assertEquals(1, syncer.syncCalled.get())

        // check whether contextClassLoader is set
        assertEquals(context.classLoader, Thread.currentThread().contextClassLoader)
    }


    class TestSyncer(
        accountSettingsFactory: AccountSettings.Factory,
        context: Context,
        db: AppDatabase
    ) : Syncer(accountSettingsFactory, context, db) {

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