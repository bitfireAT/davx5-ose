/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import org.conscrypt.Conscrypt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.Security

class ConscryptIntegrationTest {

    val integration = ConscryptIntegration()

    @Test
    fun testInitialize_InstallsConscrypt() {
        uninstallConscrypt()
        assertFalse(integration.conscryptInstalled())

        integration.initialize()
        assertTrue(integration.conscryptInstalled())
    }

    private fun uninstallConscrypt() {
        for (conscrypt in Security.getProviders().filter { Conscrypt.isConscrypt(it) })
            Security.removeProvider(conscrypt.name)
    }

}