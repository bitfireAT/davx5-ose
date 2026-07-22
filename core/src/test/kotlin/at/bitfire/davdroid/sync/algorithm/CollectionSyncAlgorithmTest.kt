/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.algorithm

import at.bitfire.dav4jvm.property.webdav.SyncToken
import at.bitfire.davdroid.resource.SyncState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)      // required because main project uses Conscrypt, but unit tests do not
class CollectionSyncAlgorithmTest {

    @Test
    fun `no previous sync state triggers an initial sync`() = runTest {
        var lastSyncState: SyncState? = null

        var callSequence = 0
        var resetPresentRemotelyCalled: Int? = null
        var syncRemoteCalled: Int? = null
        var listRemoteChangesCalled: Int? = null
        var deleteNotPresentRemotelyCalled: Int? = null
        var postProcessCalled: Int? = null

        val algorithm = CollectionSyncAlgorithm(
            CollectionSyncAlgorithm.Context(
                getLastSyncState = { lastSyncState },
                setLastSyncState = { lastSyncState = it },
                resetPresentRemotely = { resetPresentRemotelyCalled = ++callSequence },
                syncRemote = { listRemote ->
                    syncRemoteCalled = ++callSequence
                    listRemote { _, _ -> }
                },
                listRemoteChanges = { _, _ ->
                    listRemoteChangesCalled = ++callSequence
                    SyncToken("token1") to false
                },
                deleteNotPresentRemotely = { deleteNotPresentRemotelyCalled = ++callSequence },
                postProcess = { postProcessCalled = ++callSequence }
            )
        )

        algorithm(modificationsPresent = false, remoteSyncState = null)

        // all "present remotely" flags were reset locally, since there was no previous sync state (initial sync)
        assertEquals(1, resetPresentRemotelyCalled)
        // changes were downloaded through the batching/download machinery
        assertEquals(2, syncRemoteCalled)
        // changes were listed since the (nonexistent) last sync state
        assertEquals(3, listRemoteChangesCalled)
        // resources not listed by the server were removed, since the initial sync completed
        assertEquals(4, deleteNotPresentRemotelyCalled)
        // post-processing was run after the sync
        assertEquals(5, postProcessCalled)
        // the new sync token was saved, with the initial-sync flag cleared
        assertEquals(SyncState(SyncState.Type.SYNC_TOKEN, "token1", initialSync = false), lastSyncState)
    }

    @Test
    fun `existing sync token triggers an incremental sync`() = runTest {
        var lastSyncState: SyncState? = SyncState(SyncState.Type.SYNC_TOKEN, "old-token", initialSync = false)

        var callSequence = 0
        var resetPresentRemotelyCalled: Int? = null
        var syncRemoteCalled: Int? = null
        var listRemoteChangesCalled: Int? = null
        var deleteNotPresentRemotelyCalled: Int? = null
        var postProcessCalled: Int? = null

        val algorithm = CollectionSyncAlgorithm(
            CollectionSyncAlgorithm.Context(
                getLastSyncState = { lastSyncState },
                setLastSyncState = { lastSyncState = it },
                resetPresentRemotely = { resetPresentRemotelyCalled = ++callSequence },
                syncRemote = { listRemote ->
                    syncRemoteCalled = ++callSequence
                    listRemote { _, _ -> }
                },
                listRemoteChanges = { _, _ ->
                    listRemoteChangesCalled = ++callSequence
                    SyncToken("new-token") to false
                },
                deleteNotPresentRemotely = { deleteNotPresentRemotelyCalled = ++callSequence },
                postProcess = { postProcessCalled = ++callSequence }
            )
        )

        algorithm(modificationsPresent = true, remoteSyncState = null)

        // changes were downloaded through the batching/download machinery
        assertEquals(1, syncRemoteCalled)
        // changes were listed since the existing sync token
        assertEquals(2, listRemoteChangesCalled)
        // post-processing was run after the sync
        assertEquals(3, postProcessCalled)
        // "present remotely" flags were not reset, since this wasn't an initial sync
        assertNull(resetPresentRemotelyCalled)
        // resources not present remotely were not cleaned up, since this wasn't an initial sync
        assertNull(deleteNotPresentRemotelyCalled)
        // the new sync token was saved
        assertEquals(SyncState(SyncState.Type.SYNC_TOKEN, "new-token", initialSync = false), lastSyncState)
    }

}
