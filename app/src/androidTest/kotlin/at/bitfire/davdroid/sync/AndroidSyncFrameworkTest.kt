/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

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
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
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

    @get:Rule
    val mockkRule = MockKRule(this)

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


    /**
     * Correct behaviour of the sync framework on Android 13 and below.
     * Pending state is correctly reflected
     */
    @SdkSuppress(maxSdkVersion = 33)
    @Test
    fun testVerifySyncAlwaysPending_correctBehaviour_android13() {
        assertSyncStates(
            listOf(
                State(pending = false, active = false),     // no sync pending or active
                State(pending = true, active = false),      // sync becomes pending
                State(pending = true, active = true),       // ... and pending and active at the same time
                State(pending = false, active = true),      // ... and then only active
                State(pending = false, active = false)      // sync finished
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
        assertSyncStates(
            listOf(
                State(pending = false, active = false),     // no sync pending or active
                State(pending = true, active = false),      // sync becomes pending
                State(pending = true, active = true),       // ... and pending and active at the same time
                State(pending = true, active = false)       // ... and finishes, but stays pending
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
    private fun assertSyncStates(expectedStates: List<State>) = runBlocking {
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
                    assertEquals(expectedStates.subList(0, recordedStates.size), recordedStates)

                delay(500) // avoid busy-waiting
            }

            assertEquals(expectedStates, recordedStates)
        }
    }


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
        val active: Boolean
    )


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