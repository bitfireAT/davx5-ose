/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.mapping

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.davdroid.ProductIds
import at.bitfire.davdroid.resource.LocalJtxObject
import at.bitfire.davdroid.resource.remote.WebDavCollection
import at.bitfire.synctools.storage.jtx.JtxCollection
import at.bitfire.synctools.storage.jtx.JtxObjectAndExceptions
import at.techbee.jtx.JtxContract
import io.ktor.client.engine.mock.toByteArray
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okio.Buffer
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
class JtxMapperTest {

    private val productIds = ProductIds(RuntimeEnvironment.getApplication())
    private val mapper = JtxMapper(
        logger = Logger.getLogger(javaClass.name),
        productIds = productIds
    )

    @Test
    fun `generateUpload() keeps existing UID`() = runTest {
        val resource = mockk<LocalJtxObject>(relaxed = true) {
            every { jtxObjectAndExceptions } returns JtxObjectAndExceptions(
                main = Entity(
                    contentValuesOf(
                        JtxContract.JtxICalObject.COMPONENT to "VTODO",
                        JtxContract.JtxICalObject.UID to "existing-uid"
                    )
                ),
                exceptions = emptyList()
            )
            every { collection } returns mockk<JtxCollection>(relaxed = true) {
                every { client } returns mockk<ContentProviderClient>(relaxed = true)
                every { account } returns Account("test@example.com", "test")
            }
        }

        val result = mapper.generateUpload(resource, WebDavCollection.Capabilities())

        assertEquals("existing-uid.ics", result.suggestedFileName)
        val vToDo = Buffer().also { it.write(result.content.toByteArray()) }.readString(Charsets.UTF_8)
        assertTrue(vToDo.contains("UID:existing-uid"))
    }

}
