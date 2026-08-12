/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.mapping

import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.davdroid.ProductIds
import at.bitfire.davdroid.resource.LocalTask
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.davdroid.resource.remote.WebDavCollection
import at.bitfire.synctools.storage.TaskProvider
import at.bitfire.synctools.storage.tasks.DmfsTaskList
import at.bitfire.synctools.storage.tasks.TaskAndExceptions
import io.ktor.client.engine.mock.toByteArray
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okio.Buffer
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.ConscryptMode
import java.util.logging.Logger

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class TaskMapperTest {

    private val productIds = ProductIds(RuntimeEnvironment.getApplication())
    private val localCollection = mockk<LocalTaskList>(relaxed = true) {
        every { dmfsTaskList } returns mockk<DmfsTaskList>(relaxed = true) {
            every { providerName } returns TaskProvider.ProviderName.OpenTasks
        }
    }
    private val mapper = TaskMapper(
        localCollection = localCollection,
        logger = Logger.getLogger(javaClass.name),
        productIds = productIds
    )

    @Test
    fun `generateUpload() keeps existing UID`() = runTest {
        val resource = mockk<LocalTask>(relaxed = true) {
            every { taskAndExceptions } returns TaskAndExceptions(
                main = Entity(
                    contentValuesOf(
                        Tasks._UID to "existing-uid"
                    )
                ),
                exceptions = emptyList()
            )
        }

        val result = mapper.generateUpload(resource, WebDavCollection.Capabilities())

        assertEquals("existing-uid.ics", result.suggestedFileName)
        val vToDo = Buffer().also { it.write(result.content.toByteArray()) }.readString(Charsets.UTF_8)
        assertTrue(vToDo.contains("UID:existing-uid"))
    }

}
