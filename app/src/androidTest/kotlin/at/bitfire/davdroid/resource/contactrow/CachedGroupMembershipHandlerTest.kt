/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.contactrow

import android.Manifest
import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentValues
import android.content.Context
import android.provider.ContactsContract
import androidx.test.rule.GrantPermissionRule
import at.bitfire.davdroid.resource.LocalContact
import at.bitfire.davdroid.resource.LocalTestAddressBook
import at.bitfire.vcard4android.CachedGroupMembership
import at.bitfire.vcard4android.Contact
import at.bitfire.vcard4android.GroupMethod
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class CachedGroupMembershipHandlerTest {

    @Inject @ApplicationContext
    lateinit var context: Context

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val permissionRule = GrantPermissionRule.grant(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)

    val account = Account("Test Account", "Test Account Type")
    private lateinit var provider: ContentProviderClient

    @Before
    fun setUp() {
        hiltRule.inject()

        provider = context.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)!!
    }

    @After
    fun tearDown() {
        provider.close()
    }


    @Test
    fun testMembership() {
        val addressBook = LocalTestAddressBook.create(context, account, provider, GroupMethod.GROUP_VCARDS)
        try {
            val contact = Contact()
            val localContact = LocalContact(addressBook, contact, null, null, 0)
            CachedGroupMembershipHandler(localContact).handle(ContentValues().apply {
                put(CachedGroupMembership.GROUP_ID, 123456)
                put(CachedGroupMembership.RAW_CONTACT_ID, 789)
            }, contact)
            assertArrayEquals(arrayOf(123456L), localContact.cachedGroupMemberships.toArray())
        } finally {
            addressBook.remove()
        }
    }

}