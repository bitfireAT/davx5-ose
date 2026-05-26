/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.tasks

import android.Manifest
import android.accounts.Account
import android.content.ContentProviderClient
import android.provider.ContactsContract
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule

import at.bitfire.synctools.storage.BatchOperation
import at.bitfire.synctools.storage.ContactsBatchOperation
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.test.BuildConfig
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ContactsBatchOperationTest {

    @get:Rule
    val permissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS
    )

    private val testAccount = Account(javaClass.name, BuildConfig.APPLICATION_ID)

    lateinit var provider: ContentProviderClient

    @Before
    fun setUp() {
        provider = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
            .acquireContentProviderClient(ContactsContract.AUTHORITY)!!
    }

    @After
    fun tearDown() {
        // delete all contacts in test account
        provider.delete(
            ContactsContract.RawContacts.CONTENT_URI,
            "${ContactsContract.RawContacts.ACCOUNT_TYPE}=? AND ${ContactsContract.RawContacts.ACCOUNT_NAME}=?",
            arrayOf(testAccount.type, testAccount.name)
        )
        provider.close()
    }


    @Test(expected = LocalStorageException::class)
    fun testContactsProvider_OperationsPerYieldPoint_500_WithoutMax() {
        val batch = BatchOperation(provider, maxOperationsPerYieldPoint = null)

        // 500 operations should fail with BatchOperation(maxOperationsPerYieldPoint = null) (max. 499)
        repeat(500) { idx ->
            batch += BatchOperation.CpoBuilder.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, testAccount.type)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, testAccount.name)
        }
        batch.commit()
    }

    @Test
    fun testContactsProvider_OperationsPerYieldPoint_501() {
        val batch = ContactsBatchOperation(provider)

        // 501 operations should succeed with ContactsBatchOperation
        repeat(501) { idx ->
            batch += BatchOperation.CpoBuilder.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, testAccount.type)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, testAccount.name)
        }
        batch.commit()
    }

}