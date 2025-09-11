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
import junit.framework.AssertionFailedError
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import java.util.Collections
import java.util.LinkedList
import java.util.logging.Logger
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

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

    private lateinit var stateChangeListener: Any
    private val recordedStates = Collections.synchronizedList(LinkedList<State>())

    @Before
    fun setUp() {
        hiltRule.inject()

        account = TestAccount.create()

        // Enable sync globally and for the test account
        ContentResolver.setIsSyncable(account, authority, 1)

        // Remember states the sync framework reports as pairs of (sync pending, sync active).
        recordedStates.clear()
        onStatusChanged(0)      // record first entry (pending = false, active = false)
        stateChangeListener = ContentResolver.addStatusChangeListener(
            ContentResolver.SYNC_OBSERVER_TYPE_PENDING or ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE,
            ::onStatusChanged
        )
    }

    @After
    fun tearDown() {
        ContentResolver.removeStatusChangeListener(stateChangeListener)
        TestAccount.remove(account)
    }


    @SdkSuppress(minSdkVersion = 34)
    @Test
    fun testCancelsSyncAndClearsPendingState() = runBlocking {
        // Move into known forever pending state
        verifySyncStates(
            listOf(
                State(pending = false, active = false),                 // no sync pending or active
                State(pending = true, active = false, optional = true), // sync becomes pending
                State(pending = true, active = true),                   // ... and pending and active at the same time
                State(pending = true, active = false)                   // ... and finishes, but stays pending
            )
        )

        // Assert we are in the forever pending state
        assertTrue(ContentResolver.isSyncPending(account, authority))

        // Run the migration which should cancel the sync for all accounts
        migration.migrate(account)

        Thread.sleep(2000)

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

    /**
     * Verifies that the given expected states match the recorded states.
     */
    private suspend fun verifySyncStates(expectedStates: List<State>) {
        // We use runBlocking for these tests because it uses the default dispatcher
        // which does not auto-advance virtual time and we need real system time to
        // test the sync framework behavior.

        ContentResolver.requestSync(syncRequest())

        // Even though the always-pending-bug is present on Android 14+, the sync active
        // state behaves correctly, so we can record the state changes as pairs (pending,
        // active) and expect a certain sequence of state pairs to verify the presence or
        // absence of the bug on different Android versions.
        withTimeout(60.seconds) { // Usually takes less than 30 seconds
            while (recordedStates.size < expectedStates.size) {
                // verify already known states
                if (recordedStates.isNotEmpty())
                    assertStatesEqual(expectedStates.subList(0, recordedStates.size), recordedStates)

                delay(500) // avoid busy-waiting
            }

            assertStatesEqual(expectedStates, recordedStates)
        }
    }

    /**
     * Asserts whether [actualStates] and [expectedStates] are the same, under the condition
     * that expected states with the [State.optional] flag can be skipped.
     */
    private fun assertStatesEqual(expectedStates: List<State>, actualStates: List<State>) {
        fun fail() {
            throw AssertionFailedError("Expected states=$expectedStates, actual=$actualStates")
        }

        // iterate through entries
        val expectedIterator = expectedStates.iterator()
        for (actual in actualStates) {
            if (!expectedIterator.hasNext())
                fail()
            var expected = expectedIterator.next()

            // skip optional expected entries if they don't match the actual entry
            while (!actual.stateEquals(expected) && expected.optional) {
                if (!expectedIterator.hasNext())
                    fail()
                expected = expectedIterator.next()
            }

            if (!actual.stateEquals(expected))
                fail()
        }
    }


    // SyncStatusObserver implementation and data class

    fun onStatusChanged(which: Int) {
        val state = State(
            pending = ContentResolver.isSyncPending(account, authority),
            active = ContentResolver.isSyncActive(account, authority)
        )
        synchronized(recordedStates) {
            if (recordedStates.lastOrNull() != state) {
                logger.info("$account syncState = $state")
                recordedStates += state
            }
        }
    }

    data class State(
        val pending: Boolean,
        val active: Boolean,
        val optional: Boolean = false
    ) {
        fun stateEquals(other: State) =
            pending == other.pending && active == other.active
    }


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