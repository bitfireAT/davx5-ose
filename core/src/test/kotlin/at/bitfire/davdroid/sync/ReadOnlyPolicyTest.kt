/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import at.bitfire.davdroid.resource.LocalCollection
import at.bitfire.davdroid.resource.LocalResource
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Optional
import java.util.logging.Logger

class ReadOnlyPolicyTest {

    private val logger = Logger.getLogger(javaClass.name)
    private val policy = ReadOnlyPolicy(logger)

    private fun localCollection(): LocalCollection<LocalResource> {
        val collection = mockk<LocalCollection<LocalResource>>(relaxed = true)
        return collection
    }


    // resetDeleted

    @Test
    fun testResetDeleted_NoDeletedResources_ReturnsFalseAndKeepsSyncState() = runTest {
        val collection = localCollection()
        every { collection.findDeleted() } returns flowOf()

        assertFalse(policy.resetDeleted(collection))
        verify(exactly = 0) { collection.lastSyncState = any() }
    }

    @Test
    fun testResetDeleted_DeletedResources_ResetsEachAndClearsSyncState() = runTest {
        val collection = localCollection()
        val resource1 = mockk<LocalResource>(relaxed = true)
        val resource2 = mockk<LocalResource>(relaxed = true)
        every { collection.findDeleted() } returns flowOf(resource1, resource2)

        assertTrue(policy.resetDeleted(collection))

        verify { resource1.resetDeleted() }
        verify { resource2.resetDeleted() }
        verify { collection.lastSyncState = null }
    }


    // resetDirty

    @Test
    fun testResetDirty_NoDirtyResources_ReturnsFalseAndKeepsSyncState() = runTest {
        val collection = localCollection()
        every { collection.findDirty() } returns flowOf()

        assertFalse(policy.resetDirty(collection))
        verify(exactly = 0) { collection.lastSyncState = any() }
    }

    @Test
    fun testResetDirty_DirtyResources_ClearsDirtyOnEachAndClearsSyncState() = runTest {
        val collection = localCollection()
        val resource = mockk<LocalResource>(relaxed = true)
        every { collection.findDirty() } returns flowOf(resource)

        assertTrue(policy.resetDirty(collection))

        verify { resource.clearDirty(Optional.empty(), null, null) }
        verify { collection.lastSyncState = null }
    }

}
