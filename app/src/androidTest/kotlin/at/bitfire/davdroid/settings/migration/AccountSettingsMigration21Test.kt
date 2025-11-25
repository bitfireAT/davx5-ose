/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings.migration

import android.accounts.Account
import android.content.ContentResolver
import android.content.Context
import android.content.SyncRequest
import android.os.Bundle
import android.provider.CalendarContract
import androidx.test.filters.SdkSuppress
import at.bitfire.davdroid.sync.account.TestAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.util.logging.Logger
import javax.inject.Inject

@HiltAndroidTest
class AccountSettingsMigration21Test {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var migration: AccountSettingsMigration21

    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var logger: Logger

    lateinit var account: Account
    val authority = CalendarContract.AUTHORITY

    private val inPendingState = callbackFlow {
        val stateChangeListener = ContentResolver.addStatusChangeListener(
            ContentResolver.SYNC_OBSERVER_TYPE_PENDING or ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE
        ) {
            trySend(ContentResolver.isSyncPending(account, authority))
        }
        trySend(ContentResolver.isSyncPending(account, authority))
        awaitClose {
            ContentResolver.removeStatusChangeListener(stateChangeListener)
        }
    }

    @Before
    fun setUp() {
        hiltRule.inject()

        account = TestAccount.create()

        // Enable sync globally and for the test account
        ContentResolver.setIsSyncable(account, authority, 1)
    }

    @After
    fun tearDown() {
        TestAccount.remove(account)
    }


    @Ignore("Sometimes failing, see https://github.com/bitfireAT/davx5-ose/issues/1835")
    @SdkSuppress(minSdkVersion = 34)
    @Test
    fun testCancelsSyncAndClearsPendingState() = runBlocking {
        // Move into forever pending state
        ContentResolver.requestSync(syncRequest())

        // Wait until we are in forever pending state (with timeout)
        withTimeout(10_000) {
            inPendingState.first { it }
        }

        // Assert again that we are now in the forever pending state
        assertTrue(ContentResolver.isSyncPending(account, authority))

        // Run the migration which should cancel the forever pending sync for all accounts
        migration.migrate(account)

        // Wait for the state to change (with timeout)
        withTimeout(10_000) {
            inPendingState.first { !it }
        }

        // Check the sync is now not pending anymore
        assertFalse(ContentResolver.isSyncPending(account, authority))
    }


    // helpers

    private fun syncRequest() = SyncRequest.Builder()
        .setSyncAdapter(account, authority)
        .syncOnce()
        .setExtras(Bundle())    // needed for Android 9
        .setExpedited(true)     // sync request will be scheduled at the front of the sync request queue
        .setManual(true)        // equivalent of setting both SYNC_EXTRAS_IGNORE_SETTINGS and SYNC_EXTRAS_IGNORE_BACKOFF
        .build()

    companion object {

        var globalAutoSyncBeforeTest = false

        @BeforeClass
        @JvmStatic
        fun before() {
            globalAutoSyncBeforeTest = ContentResolver.getMasterSyncAutomatically()

            // We'll request syncs explicitly and with SYNC_EXTRAS_IGNORE_SETTINGS
            ContentResolver.setMasterSyncAutomatically(false)
        }

        @AfterClass
        @JvmStatic
        fun after() {
            ContentResolver.setMasterSyncAutomatically(globalAutoSyncBeforeTest)
        }

    }

}