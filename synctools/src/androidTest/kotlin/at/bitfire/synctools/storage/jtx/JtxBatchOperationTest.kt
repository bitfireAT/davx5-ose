/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.jtx

import android.accounts.Account
import androidx.core.content.contentValuesOf
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.synctools.storage.BatchOperation
import at.bitfire.synctools.storage.LocalStorageClient
import at.bitfire.synctools.storage.TaskProvider
import at.bitfire.synctools.test.BuildConfig
import at.bitfire.synctools.test.GrantPermissionOrSkipRule
import at.techbee.jtx.JtxContract
import at.techbee.jtx.JtxContract.asSyncAdapter
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class JtxBatchOperationTest {

    @get:Rule
    val permissionRule = GrantPermissionOrSkipRule(TaskProvider.ProviderName.JtxBoard.permissions.toSet())

    private val testAccount = Account(javaClass.name, BuildConfig.APPLICATION_ID)

    lateinit var client: LocalStorageClient

    @Before
    fun setUp() {
        val provider = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
            .acquireContentProviderClient(JtxContract.AUTHORITY)!!
        client = LocalStorageClient(provider)
    }

    @After
    fun tearDown() {
        client.close()
    }


    @Test
    fun testJtxBoard_OperationsPerYieldPoint_501() {
        val batch = JtxBatchOperation(client)
        val jtxProvider = JtxCollectionProvider(testAccount, client)
        val collectionId = jtxProvider.createCollection(
            contentValuesOf(
            JtxContract.JtxCollection.DISPLAYNAME to javaClass.name
        ))

        try {
            // 501 operations should succeed with JtxBatchOperation
            repeat(501) { idx ->
                batch += BatchOperation.CpoBuilder.newInsert(JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount))
                    .withValue(JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID, collectionId)
                    .withValue(JtxContract.JtxICalObject.SUMMARY, "Entry $idx")
            }
            batch.commit()
        } finally {
            jtxProvider.deleteCollection(collectionId)
        }
    }

}