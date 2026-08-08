/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowUtilsTest {

    /** Maps each item to two values, to verify flattening. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun expand(batch: List<Int>): Flow<String> =
        batch.asFlow().flatMapConcat { i -> flowOf("item$i-a", "item$i-b") }

    @Test
    fun `batchMap() with below-batch-size flow downloads remainder as one batch`() = runTest {
        val batches = mutableListOf<List<Int>>()

        val result = flowOf(1, 2)
            .batchMap(batchSize = 3) { batch ->
                batches += batch
                expand(batch)
            }.toList()

        assertEquals(listOf(listOf(1, 2)), batches)
        assertEquals(listOf("item1-a", "item1-b", "item2-a", "item2-b"), result)
    }

    @Test
    fun `batchMap() with multiple batches downloads each full batch plus remainder`() = runTest {
        val batches = mutableListOf<List<Int>>()

        val result = flowOf(1, 2, 3, 4, 5)
            .batchMap(batchSize = 2) { batch ->
                batches += batch
                expand(batch)
            }.toList()

        // batch formation order is preserved
        assertEquals(
            listOf(
                listOf(1, 2),
                listOf(3, 4),
                listOf(5)
            ),
            batches
        )
        // batches process concurrently, so result order isn't guaranteed
        assertEquals(
            setOf(
                "item1-a", "item1-b", "item2-a", "item2-b",
                "item3-a", "item3-b", "item4-a", "item4-b",
                "item5-a", "item5-b"
            ),
            result.toSet()
        )
    }

    @Test
    fun `batchMap() with batch size 1 downloads each item separately`() = runTest {
        val batches = mutableListOf<List<Int>>()

        val result = flowOf(1)
            .batchMap(batchSize = 1) { batch ->
                batches += batch
                expand(batch)
            }.toList()

        assertEquals(listOf(listOf(1)), batches)
        assertEquals(listOf("item1-a", "item1-b"), result)
    }

    @Test
    fun `batchMap() with empty flow does not download`() = runTest {
        val batches = mutableListOf<List<Int>>()

        emptyFlow<Int>()
            .batchMap(batchSize = 10) { batch ->
                batches += batch
                emptyFlow<String>()
            }.toList()

        assertTrue(batches.isEmpty())
    }

}
