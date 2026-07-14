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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.util.Optional

class CategoriesStrategyTest {

    @get:Rule
    val mockKRule = MockKRule(this)

    @MockK(relaxed = true)
    lateinit var addressBook: LocalAddressBook

    private val strategy by lazy { CategoriesStrategy(addressBook) }


    // resolveLocalGroupChanges

    @Test
    fun `resolveLocalGroupChanges() with deleted group marks members dirty and removes group`() = runTest {
        val group = mockk<LocalGroup>(relaxed = true)
        every { addressBook.findDeletedGroups() } returns flowOf(group)
        every { addressBook.findDirtyGroups() } returns flowOf()

        // must not depend on whether the address book is read-only: this is pure local housekeeping
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
