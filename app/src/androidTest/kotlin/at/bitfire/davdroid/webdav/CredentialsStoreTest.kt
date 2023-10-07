/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.webdav

import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.davdroid.db.Credentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CredentialsStoreTest {

    private val store = CredentialsStore(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test
    fun testSetGetDelete() {
        store.setCredentials(0, Credentials(userName = "myname", password = "12345"))
        assertEquals(Credentials(userName = "myname", password = "12345"), store.getCredentials(0))

        store.setCredentials(0, null)
        assertNull(store.getCredentials(0))
    }

}