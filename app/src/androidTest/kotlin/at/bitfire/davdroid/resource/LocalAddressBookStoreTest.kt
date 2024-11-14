package at.bitfire.davdroid.resource

import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.SpyK
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LocalAddressBookStoreTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @SpyK
    @InjectMockKs
    var localAddressBookStore = LocalAddressBookStore(
        addressBookFactory = mockk(relaxed = true),
        collectionRepository = mockk(relaxed = true),
        context = mockk(relaxed = true),
        localAddressBookFactory = mockk(relaxed = true),
        logger = mockk(relaxed = true),
        serviceRepository = mockk(relaxed = true) {
            every { get(any<Long>()) } returns null
            every { get(200) } returns mockk<Service> {
                every { accountName } returns "MrRobert@example.com"
            }
        },
        settings = mockk(relaxed = true)
    )


    @Test
    fun test_accountName_missingService() {
        val collection = mockk<Collection> {
            every { id } returns 42
            every { url } returns "https://example.com/addressbook/funnyfriends".toHttpUrl()
            every { displayName } returns null
            every { serviceId } returns 404
        }
        assertEquals("funnyfriends #42", localAddressBookStore.accountName(collection))
    }

    @Test
    fun test_accountName_missingDisplayName() {
        val collection = mockk<Collection> {
            every { id } returns 42
            every { url } returns "https://example.com/addressbook/funnyfriends".toHttpUrl()
            every { displayName } returns null
            every { serviceId } returns 200
        }
        val accountName = localAddressBookStore.accountName(collection)
        assertEquals("funnyfriends (MrRobert@example.com) #42", accountName)
    }

    @Test
    fun test_accountName_missingDisplayNameAndService() {
        val collection = mockk<Collection> {
            every { id } returns 1
            every { url } returns "https://example.com/addressbook/funnyfriends".toHttpUrl()
            every { displayName } returns null
            every { serviceId } returns 404 // missing service
        }
        assertEquals("funnyfriends #1", localAddressBookStore.accountName(collection))
    }


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