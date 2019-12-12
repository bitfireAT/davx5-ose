package at.bitfire.davdroid.syncadapter

import android.os.Bundle
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncAdapterServiceTest {

    @Test
    fun testPriorityCollections() {
        val extras = Bundle(1)
        assertTrue(SyncAdapterService.priorityCollections(extras).isEmpty())

        extras.putString(SyncAdapterService.SYNC_EXTRAS_PRIORITY_COLLECTIONS, "800")
        assertArrayEquals(longArrayOf(800), SyncAdapterService.priorityCollections(extras).toLongArray())

        extras.putString(SyncAdapterService.SYNC_EXTRAS_PRIORITY_COLLECTIONS, "1,2,3")
        assertArrayEquals(longArrayOf(1,2,3), SyncAdapterService.priorityCollections(extras).toLongArray())

        extras.putString(SyncAdapterService.SYNC_EXTRAS_PRIORITY_COLLECTIONS, "1,INVALID,3")
        assertArrayEquals(longArrayOf(), SyncAdapterService.priorityCollections(extras).toLongArray())
    }

}