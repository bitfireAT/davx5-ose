package at.bitfire.davdroid.resource

import at.bitfire.davdroid.db.Collection
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAddressBookStoreTest {

    /**
     * Tests the calculation of read only state is correct
     */
    @Test
    fun test_shouldBeReadOnly() {
        val collectionReadOnly = mockk<Collection> { every { readOnly() } returns true }
        assertTrue(LocalAddressBookStore.shouldBeReadOnly(collectionReadOnly, false))
        assertTrue(LocalAddressBookStore.shouldBeReadOnly(collectionReadOnly, true))

        val collectionNotReadOnly = mockk<Collection> { every { readOnly() } returns false }
        assertFalse(LocalAddressBookStore.shouldBeReadOnly(collectionNotReadOnly, false))
        assertTrue(LocalAddressBookStore.shouldBeReadOnly(collectionNotReadOnly, true))
    }

}