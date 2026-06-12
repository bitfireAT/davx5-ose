/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.jtx

import android.accounts.Account
import android.content.ContentProviderClient
import androidx.core.content.contentValuesOf
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.ical4android.TaskProvider
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter
import at.bitfire.synctools.storage.BatchOperation
import at.bitfire.synctools.test.BuildConfig
import at.bitfire.synctools.test.GrantPermissionOrSkipRule
import at.techbee.jtx.JtxContract
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
        val jtxProvider = JtxCollectionProvider(testAccount, provider)
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