/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.mapping

import at.bitfire.davdroid.ProductIds
import at.bitfire.davdroid.resource.LocalContact
import at.bitfire.davdroid.resource.remote.WebDavCollection
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.storage.contacts.AndroidContact
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
class ContactMapperTest {

    private val productIds = ProductIds(RuntimeEnvironment.getApplication())
    private val mapper = ContactMapper(
        logger = Logger.getLogger(javaClass.name),
        productIds = productIds
    )

    @Test
    fun `generateUpload() keeps existing UID and writes vCard 3 by default`() = runTest {
        val resource = mockk<LocalContact>(relaxed = true) {
            every { androidContact } returns mockk<AndroidContact>(relaxed = true) {
                every { getContact() } returns Contact(uid = "existing-uid", displayName = "Test Contact")
            }
        }

        val result = mapper.generateUpload(resource, WebDavCollection.Capabilities())

        assertEquals("existing-uid.vcf", result.suggestedFileName)
        val vCard = Buffer().also { it.write(result.content.toByteArray()) }.readString(Charsets.UTF_8)
        assertTrue(vCard.contains("VERSION:3.0"))
        assertTrue(vCard.contains("UID:existing-uid"))
    }

}
