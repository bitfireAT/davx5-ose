/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.groups

import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.resource.LocalGroup
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import io.mockk.verify
import junit.framework.AssertionFailedError
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Optional

class CategoriesStrategyTest {

    @get:Rule
    val mockKRule = MockKRule(this)

    @MockK(relaxed = true)
    lateinit var addressBook: LocalAddressBook

    private val strategy by lazy { CategoriesStrategy(addressBook) }

    @Before
    fun setUp() {
        /* CategoriesStrategy is called by orchestrator depending on whether the collection is read-only;
        but it doesn't check itself. */
        every { addressBook.readOnly } throws AssertionFailedError()
    }


    // resolveLocalGroupChanges

    @Test
    fun `resolveLocalGroupChanges() with deleted group marks members dirty and removes group`() = runTest {
        val group = mockk<LocalGroup>(relaxed = true)
        every { addressBook.findDeletedGroups() } returns flowOf(group)
        every { addressBook.findDirtyGroups() } returns flowOf()

        strategy.resolveLocalGroupChanges()

        verify { group.markMembersDirty() }
        verify { group.androidGroup.delete() }
    }

    @Test
    fun `resolveLocalGroupChanges() with dirty group marks members dirty and clears dirty flag`() = runTest {
        val group = mockk<LocalGroup>(relaxed = true)
        every { addressBook.findDeletedGroups() } returns flowOf()
        every { addressBook.findDirtyGroups() } returns flowOf(group)

        strategy.resolveLocalGroupChanges()

        verify { group.markMembersDirty() }
        verify { group.clearDirty(Optional.empty(), null) }
    }

}
