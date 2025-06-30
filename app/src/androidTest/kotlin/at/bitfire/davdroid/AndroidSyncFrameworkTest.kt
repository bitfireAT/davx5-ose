/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid

import android.accounts.Account
import android.content.ContentResolver
import android.content.Context
import android.content.SyncRequest
import android.os.Bundle
import android.provider.CalendarContract
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import at.bitfire.davdroid.sync.account.TestAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.junit4.MockKRule
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.runs
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltAndroidTest
class AndroidSyncFrameworkTest {

    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var logger: Logger

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mockkRule = MockKRule(this)

    lateinit var account: Account
    lateinit var syncRequest: SyncRequest
    private var stateChangeListener: Any? = null
    private val recordedStates = CopyOnWriteArrayList<States>()

    private var masterSyncStateBeforeTest = ContentResolver.getMasterSyncAutomatically()

    @Before
    fun setUp() {
        hiltRule.inject()

        account = TestAccount.create()

        // Enable sync globally and for the test account
        ContentResolver.setMasterSyncAutomatically(false)
        ContentResolver.setMasterSyncAutomatically(true)
        ContentResolver.setSyncAutomatically(account, CalendarContract.AUTHORITY, true)
        ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 1)
        ContentResolver.removePeriodicSync(account, CalendarContract.AUTHORITY, Bundle())

        // Sync request to be used in the tests
        syncRequest = SyncRequest.Builder()
            .setSyncAdapter(account, CalendarContract.AUTHORITY)
            .syncOnce()
            .setExtras(Bundle()) // Needed for Android 9
            .build()

        // Remember states the sync framework reports as pairs of (sync pending, sync active).
        stateChangeListener = ContentResolver.addStatusChangeListener(
            ContentResolver.SYNC_OBSERVER_TYPE_PENDING or ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE
        ) {
            val syncPending = ContentResolver.isSyncPending(account, CalendarContract.AUTHORITY)
            val syncActive = ContentResolver.isSyncActive(account, CalendarContract.AUTHORITY)
            logger.log(
                Level.INFO, "$account", arrayOf(
                    "sync pending = $syncPending",
                    "sync active = $syncActive"
                )
            )
            addStateIfChanged(recordedStates, States(syncPending, syncActive))
        }

        // Disable the workaround we put in place for Android 14+ present in
        // [SyncAdapterService.SyncAdapter.onPerformSync]
        mockkStatic(ContentResolver::class)
        every { ContentResolver.cancelSync(syncRequest) } just runs
    }

    @After
    fun tearDown() {
        TestAccount.remove(account)
        ContentResolver.setMasterSyncAutomatically(false)
        ContentResolver.setMasterSyncAutomatically(masterSyncStateBeforeTest)
        stateChangeListener?.let { ContentResolver.removeStatusChangeListener(it) }
        recordedStates.clear()
        ContentResolver.cancelSync(null, null) // Cancel any pending or ongoing syncs
        ContentResolver.setSyncAutomatically(account, CalendarContract.AUTHORITY, false)
        ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 0)
        ContentResolver.removePeriodicSync(account, CalendarContract.AUTHORITY, Bundle())
    }


    // correct behaviour of the sync framework on Android 13 and below
    // Pending state is correctly reflected

    @SdkSuppress(minSdkVersion = 28, maxSdkVersion = 28)
    @LargeTest
    @Test
    fun testVerifySyncAlwaysPending_correctBehaviour_android9() =
        verifyRecordedStatesMatchWith(
            listOf(
                States(pending = true, active = false),
                States(pending = true, active = true),
                States(pending = false, active = true),
                States(pending = false, active = false)
            )
        )

    @SdkSuppress(minSdkVersion = 29, maxSdkVersion = 29)
    @LargeTest
    @Test
    fun testVerifySyncAlwaysPending_correctBehaviour_android10() =
        verifyRecordedStatesMatchWith(
            listOf(
                States(pending = true, active = false),
                States(pending = true, active = true),
                States(pending = false, active = true),
                States(pending = false, active = false)
            )
        )

    @SdkSuppress(minSdkVersion = 30, maxSdkVersion = 30)
    @LargeTest
    @Test
    fun testVerifySyncAlwaysPending_correctBehaviour_android11() =
        verifyRecordedStatesMatchWith(
            listOf(
                States(pending = true, active = false),
                States(pending = true, active = true),
                States(pending = false, active = true),
                States(pending = false, active = false),
            )
        )

    @SdkSuppress(minSdkVersion = 31, maxSdkVersion = 32)
    @LargeTest
    @Test
    fun testVerifySyncAlwaysPending_correctBehaviour_android12() =
        verifyRecordedStatesMatchWith(
            listOf(
                States(pending = true, active = false),
                States(pending = true, active = true),
                States(pending = false, active = true),
                States(pending = false, active = false)
            )
        )

    @SdkSuppress(minSdkVersion = 33, maxSdkVersion = 33)
    @LargeTest
    @Test
    fun testVerifySyncAlwaysPending_correctBehaviour_android13() =
        verifyRecordedStatesMatchWith(
            listOf(
                States(pending = true, active = false),
                States(pending = true, active = true),
                States(pending = false, active = true),
                States(pending = false, active = false)
            )
        )


    // Wrong behaviour of the sync framework on Android 14+
    // Pending state stays true forever, active state behaves correctly

    @SdkSuppress(minSdkVersion = 34, maxSdkVersion = 34)
    @LargeTest
    @Test
    fun testVerifySyncAlwaysPending_wrongBehaviour_android14() =
        verifyRecordedStatesMatchWith(
            listOf(
                States(pending = true, active = false),
                States(pending = true, active = true),
                States(pending = true, active = false)
            )
        )

    @SdkSuppress(minSdkVersion = 35, maxSdkVersion = 35)
    @LargeTest
    @Test
    fun testVerifySyncAlwaysPending_wrongBehaviour_android15() =
        verifyRecordedStatesMatchWith(
            listOf(
                States(pending = true, active = false),
                States(pending = false, active = false),
                States(pending = true, active = true),
                States(pending = true, active = false)
            )
        )

    @SdkSuppress(minSdkVersion = 36, maxSdkVersion = 36)
    @LargeTest
    @Test
    fun testVerifySyncAlwaysPending_wrongBehaviour_android16() =
        verifyRecordedStatesMatchWith(
            listOf(
                States(pending = true, active = false),
                States(pending = true, active = true),
                States(pending = true, active = false)
            )
        )


    @Ignore // takes way too long to run in CI
    @SdkSuppress(minSdkVersion = 34)
    @LargeTest
    @Test(expected = TimeoutCancellationException::class)
    fun testVerifySyncAlwaysPending() = runBlocking {
        // Different from the previous tests. This test checks the pending state stays
        // "forever" (65 seconds in this test)

        // The test is expected to fail on Android 13 (API lvl 33) and below. It
        // succeeds on Android 14+ (API lvl 34), however, where the sync-framework-
        // always-pending-bug is present and hopefully fails as soon as the bug is
        // fixed in a future android version.
        // See https://github.com/bitfireAT/davx5-ose/issues/1458

        // Based on my observations the sync framework usually takes 40-63 seconds
        // to start the sync and change the sync pending state on Android 13 and
        // below (but could take up to several minutes).
        // On Android 14+ it will run the sync at some point, but report a pending
        // sync state forever. To verify this, we can wait for around a minute and
        // if the sync is still pending, we can assume that the bug is still present.
        withTimeout(65.seconds) {
            // Request calendar sync
            ContentResolver.requestSync(syncRequest)

            // Verify the sync keeps being pending "forever" (65 seconds in this test)
            while (true) {
                assertTrue(ContentResolver.isSyncPending(account, CalendarContract.AUTHORITY))
                delay(2000) // Check every 2 seconds
            }
        }
    }


    // helpers

    /**
     * Verifies that the given expected states match the recorded states.
     */
    private fun verifyRecordedStatesMatchWith(expectedStatesLists: List<States>) =
        runBlocking {
            // We use runBlocking for these tests because it uses the default dispatcher
            // which does not auto-advance virtual time and we need real system time to
            // test the sync framework behavior.

            // Even though the always-pending-bug is present on Android 14+, the sync active
            // state behaves correctly, so we can record the state changes as pairs (pending,
            // active) and expect a certain sequence of state pairs to verify the presence or
            // absence of the bug on different Android versions.
            withTimeout(15.seconds) { // Usually takes less than 10 seconds
                ContentResolver.requestSync(syncRequest)
                while (true) {
                    if (recordedStates == expectedStatesLists)
                        break
                    delay(500) // avoid busy-waiting
                }
            }
        }

    /**
     * Add a new state to the list if it is different from the last recorded state.
     * Avoids adding duplicate states in the list.
     */
    fun addStateIfChanged(list: MutableList<States>, new: States) =
        synchronized(list) {
            if (list.lastOrNull() != new)
                list.add(new)
        }

    data class States(
        val pending: Boolean,
        val active: Boolean
    )

}