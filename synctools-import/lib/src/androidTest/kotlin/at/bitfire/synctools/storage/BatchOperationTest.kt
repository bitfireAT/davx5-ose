/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage

import android.content.ContentProviderClient
import android.os.TransactionTooLargeException
import androidx.core.net.toUri
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class BatchOperationTest {

    @Test
    fun testSplitLargeTransaction() {
        val provider = mockk<ContentProviderClient>(relaxed = true)

        val maxSize = 100
        every { provider.applyBatch(match { it.size > maxSize }) } throws TransactionTooLargeException()

        val batch = BatchOperation(provider, null)
        repeat(4*maxSize) {
            batch += BatchOperation.CpoBuilder.newInsert("test://".toUri())
        }
        batch.commit()

        // one too large batch (with 400 operations) +
        // then two still too large batches with 200 operations each +
        // then four batches with 100 operations each
        verify(exactly = 7) { provider.applyBatch(any()) }
    }

    @Test(expected = LocalStorageException::class)
    fun testSplitLargeTransaction_OneTooBigRow() {
        val provider = mockk<ContentProviderClient>()

        every { provider.applyBatch(any()) } throws TransactionTooLargeException()

        val batch = BatchOperation(provider, null)
        batch += BatchOperation.CpoBuilder.newInsert("test://".toUri())
        batch.commit()
    }

}