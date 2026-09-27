/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage

import android.accounts.Account
import android.content.ContentProviderClient
import android.net.Uri
import at.bitfire.synctools.storage.calendar.AndroidCalendarProvider
import at.bitfire.synctools.storage.jtx.JtxCollectionProvider
import at.bitfire.synctools.storage.tasks.DmfsTaskListProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertFailsWith

/**
 * A provider that returns no cursor must not look like one that holds no collections: callers
 * delete local collections that are missing from the result.
 */
@RunWith(RobolectricTestRunner::class)
class NoCursorTest {

    private val account = Account("test", "test.account")
    private val client = LocalStorageClient(
        mockk<ContentProviderClient> {
            every { query(any<Uri>(), any(), any(), any(), any<String>()) } returns null
        }
    )

    @Test
    fun testFindCalendars_noCursor() {
        assertFailsWith<LocalStorageException> {
            AndroidCalendarProvider(account, client).findCalendars()
        }
    }

    @Test
    fun testFindJtxCollections_noCursor() {
        assertFailsWith<LocalStorageException> {
            JtxCollectionProvider(account, client).findCollections()
        }
    }

    @Test
    fun testFindTaskLists_noCursor() {
        assertFailsWith<LocalStorageException> {
            DmfsTaskListProvider(account, client, TaskProvider.ProviderName.OpenTasks).findTaskLists()
        }
    }

}
