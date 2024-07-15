/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.contactrow

import android.Manifest
import android.content.ContentProviderClient
import android.content.ContentValues
import android.provider.ContactsContract
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.davdroid.resource.LocalContact
import at.bitfire.davdroid.resource.LocalTestAddressBook
import at.bitfire.vcard4android.CachedGroupMembership
import at.bitfire.vcard4android.Contact
import at.bitfire.vcard4android.GroupMethod
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.AfterClass
import org.junit.Assert
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class CachedGroupMembershipHandlerTest {

    companion object {

        val context = InstrumentationRegistry.getInstrumentation().context

        @JvmField
        @ClassRule
        val permissionRule = GrantPermissionRule.grant(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)!!

        private lateinit var provider: ContentProviderClient

        @BeforeClass
        @JvmStatic
        fun connect() {
            provider = context.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)!!
            Assert.assertNotNull(provider)
        }

        @AfterClass
        @JvmStatic
        fun disconnect() {
            provider.close()
        }

    }

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private lateinit var addressBook: LocalTestAddressBook

    @Before
    fun setup() {
        hiltRule.inject()

        addressBook = LocalTestAddressBook(context, provider, GroupMethod.GROUP_VCARDS)
    }


    @Test
    fun testMembership() {
        val contact = Contact()
        val localContact = LocalContact(addressBook, contact, null, null, 0)
        CachedGroupMembershipHandler(localContact).handle(ContentValues().apply {
            put(CachedGroupMembership.GROUP_ID, 123456)
            put(CachedGroupMembership.RAW_CONTACT_ID, 789)
        }, contact)
        assertArrayEquals(arrayOf(123456L), localContact.cachedGroupMemberships.toArray())
    }

}