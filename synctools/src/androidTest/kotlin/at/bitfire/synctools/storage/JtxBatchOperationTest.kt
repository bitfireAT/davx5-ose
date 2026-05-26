/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import androidx.core.content.contentValuesOf
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.ical4android.JtxCollection
import at.bitfire.ical4android.JtxICalObject
import at.bitfire.ical4android.TaskProvider
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter

import at.bitfire.synctools.test.BuildConfig
import at.bitfire.synctools.test.GrantPermissionOrSkipRule
import at.techbee.jtx.JtxContract
import io.mockk.mockk
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class JtxBatchOperationTest {

    @get:Rule
    val permissionRule = GrantPermissionOrSkipRule(TaskProvider.PERMISSIONS_JTX.toSet())

    private val testAccount = Account(javaClass.name, BuildConfig.APPLICATION_ID)

    lateinit var provider: ContentProviderClient

    @Before
    fun setUp() {
        provider = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
            .acquireContentProviderClient(JtxContract.AUTHORITY)!!
    }

    @After
    fun tearDown() {
        provider.close()
    }


    @Test
    fun testJtxBoard_OperationsPerYieldPoint_501() {
        val batch = JtxBatchOperation(provider)
        val uri = JtxCollection.create(testAccount, provider, contentValuesOf(
            JtxContract.JtxCollection.ACCOUNT_NAME to testAccount.name,
            JtxContract.JtxCollection.ACCOUNT_TYPE to testAccount.type,
            JtxContract.JtxCollection.DISPLAYNAME to javaClass.name
        ))
        val collectionId = ContentUris.parseId(uri)

        try {
            // 501 operations should succeed with JtxBatchOperation
            repeat(501) { idx ->
                batch += BatchOperation.CpoBuilder.newInsert(JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount))
                    .withValue(JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID, collectionId)
                    .withValue(JtxContract.JtxICalObject.SUMMARY, "Entry $idx")
            }
            batch.commit()
        } finally {
            val collection = JtxCollection<JtxICalObject>(testAccount, provider, mockk(), collectionId)
            collection.delete()
        }
    }

}