/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.algorithm

import at.bitfire.davdroid.resource.SyncState
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)      // required because main project uses Conscrypt, but unit tests do not
class PropfindReportAlgorithmTest {

    @Test
    fun `local modifications trigger a re-queried sync state, full listing, cleanup and post-processing`() = runTest {
        val oldRemoteSyncState = SyncState(SyncState.Type.CTAG, "ctag1")
        val queriedRemoteSyncState = SyncState(SyncState.Type.CTAG, "ctag2")

        var lastSyncState: SyncState? = null

        var callSequence = 0
        var resetPresentRemotelyCalled: Int? = null
        var syncRemoteCalled: Int? = null
        var listAllRemoteCalled: Int? = null
        var deleteNotPresentRemotelyCalled: Int? = null
        var postProcessCalled: Int? = null

        val algorithm = PropfindReportAlgorithm(
            PropfindReportAlgorithm.Context(
                resetPresentRemotely = { resetPresentRemotelyCalled = ++callSequence },
                querySyncState = { queriedRemoteSyncState },
                syncRemote = { listRemote ->
                    syncRemoteCalled = ++callSequence
                    listRemote { _, _ -> }
                },
                listAllRemote = {
                    listAllRemoteCalled = ++callSequence
                    emptyFlow()
                },
                deleteNotPresentRemotely = { deleteNotPresentRemotelyCalled = ++callSequence },
                postProcess = { postProcessCalled = ++callSequence },
                setLastSyncState = { lastSyncState = it }
            )
        )

        algorithm(modificationsPresent = true, remoteSyncState = oldRemoteSyncState)

        // all local resources were marked for potential deletion before the full listing
        assertEquals(1, resetPresentRemotelyCalled)
        // the full remote listing was downloaded through the batching/download machinery
        assertEquals(2, syncRemoteCalled)
        // remote resources were listed (PROPFIND/REPORT)
        assertEquals(3, listAllRemoteCalled)
        // local resources no longer present remotely were removed
        assertEquals(4, deleteNotPresentRemotelyCalled)
        // post-processing was run after the sync
        assertEquals(5, postProcessCalled)
        // since local modifications were present, the freshly queried state was saved (not the stale one passed in)
        assertEquals(queriedRemoteSyncState, lastSyncState)
    }

}
