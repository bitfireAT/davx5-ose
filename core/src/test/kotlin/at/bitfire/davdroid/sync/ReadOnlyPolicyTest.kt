/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import at.bitfire.davdroid.resource.LocalCollection
import at.bitfire.davdroid.resource.LocalResource
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Optional
import java.util.logging.Logger

class ReadOnlyPolicyTest {

    @get:Rule
    val mockKRule = MockKRule(this)

    private val logger = Logger.getLogger(javaClass.name)
    private val policy = ReadOnlyPolicy(logger)

    @MockK(relaxed = true)
    lateinit var collection: LocalCollection<LocalResource>


    @Test
    fun `resetDeleted() with no deleted resources returns false and keeps sync state`() = runTest {
        every { collection.findDeleted() } returns flowOf()

        assertFalse(policy.resetDeleted(collection))
        verify(exactly = 0) { collection.lastSyncState = any() }
    }

    @Test
    fun `resetDeleted() with deleted resources resets each and clears sync state`() = runTest {
        val resource1 = mockk<LocalResource>(relaxed = true)
        val resource2 = mockk<LocalResource>(relaxed = true)
        every { collection.findDeleted() } returns flowOf(resource1, resource2)

        assertTrue(policy.resetDeleted(collection))

        verify { resource1.resetDeleted() }
        verify { resource2.resetDeleted() }
        verify { collection.lastSyncState = null }
    }


    @Test
    fun `resetDirty() with no dirty resources returns false and keeps sync state`() = runTest {
        every { collection.findDirty() } returns flowOf()

        assertFalse(policy.resetDirty(collection))
        verify(exactly = 0) { collection.lastSyncState = any() }
    }

    @Test
    fun `resetDirty() with dirty resources clears dirty on each and clears sync state`() = runTest {
        val resource = mockk<LocalResource>(relaxed = true)
        every { collection.findDirty() } returns flowOf(resource)

        assertTrue(policy.resetDirty(collection))

        verify { resource.clearDirty(Optional.empty(), null, null) }
        verify { collection.lastSyncState = null }
    }

}
