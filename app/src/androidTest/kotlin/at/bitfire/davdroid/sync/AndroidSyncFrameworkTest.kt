/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.ContentResolver
import android.content.SyncRequest
import android.os.Bundle
import android.provider.CalendarContract
import androidx.test.filters.SdkSuppress
import at.bitfire.davdroid.sync.account.TestAccount
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import junit.framework.AssertionFailedError
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.AfterClass
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
class AndroidSyncFrameworkTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

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


    /**
     * Correct behaviour of the sync framework on Android 13 and below.
     * Pending state is correctly reflected
     */
    @SdkSuppress(maxSdkVersion = 33)
    @Test
    fun testVerifySyncAlwaysPending_correctBehaviour_android13() {
        verifySyncStates(
            listOf(
                State(pending = false, active = false),                 // no sync pending or active
                State(pending = true, active = false, optional = true), // sync becomes pending
                State(pending = true, active = true),                   // ... and pending and active at the same time
                State(pending = false, active = true),                  // ... and then only active
                State(pending = false, active = false)                  // sync finished
            )
        )
    }

    /**
     * Wrong behaviour of the sync framework on Android 14+.
     * Pending state stays true forever (after initial run), active state behaves correctly
     */
    @SdkSuppress(minSdkVersion = 34 /*, maxSdkVersion = 36 */)
    @Test
    fun testVerifySyncAlwaysPending_wrongBehaviour_android14() {
        verifySyncStates(
            listOf(
                State(pending = false, active = false),                 // no sync pending or active
                State(pending = true, active = false, optional = true), // sync becomes pending
                State(pending = true, active = true),                   // ... and pending and active at the same time
                State(pending = true, active = false)                   // ... and finishes, but stays pending
            )
        )
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
    private fun verifySyncStates(expectedStates: List<State>) = runBlocking {
        // Verify that last state is non-optional.
        if (expectedStates.last().optional)
            throw IllegalArgumentException("Last expected state must not be optional")

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
                    assertStatesEqual(expectedStates, recordedStates, fullMatch = false)

                delay(500) // avoid busy-waiting
            }

            assertStatesEqual(expectedStates, recordedStates, fullMatch = true)
        }
    }

    private fun assertStatesEqual(expectedStates: List<State>, actualStates: List<State>, fullMatch: Boolean) {
        if (!statesMatch(expectedStates, actualStates, fullMatch))
            throw AssertionFailedError("Expected states=$expectedStates, actual=$actualStates")
    }

    /**
     * Checks whether [actualStates] have matching [expectedStates], under the condition
     * that expected states with the [State.optional] flag can be skipped.
     *
     * Note: When [fullMatch] is not set, this method can return _true_ even if not all expected states are used.
     *
     * @param expectedStates    expected states (can include optional states which don't have to be present in actual states)
     * @param actualStates      actual states
     * @param fullMatch         whether all non-optional expected states must be present in actual states
     */
    private fun statesMatch(expectedStates: List<State>, actualStates: List<State>, fullMatch: Boolean): Boolean {
        // iterate through entries
        val expectedIterator = expectedStates.iterator()
        for (actual in actualStates) {
            if (!expectedIterator.hasNext())
                return false
            var expected = expectedIterator.next()

            // skip optional expected entries if they don't match the actual entry
            while (!actual.stateEquals(expected) && expected.optional) {
                if (!expectedIterator.hasNext())
                    return false
                expected = expectedIterator.next()
            }

            // we now have a non-optional expected state and it must match
            if (!actual.stateEquals(expected))
                return false
        }

        // full match: all expected states must have been used
        if (fullMatch && expectedIterator.hasNext())
            return false

        return true
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