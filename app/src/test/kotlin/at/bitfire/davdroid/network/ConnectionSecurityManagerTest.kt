/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import at.bitfire.cert4android.CustomCertManager
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Optional

class ConnectionSecurityManagerTest {

    @Test
    fun `getContext(no customTrustManager, no client certificate)`() {
        val manager = ConnectionSecurityManager(
            customTrustManager = Optional.empty(),
            customHostnameVerifier = Optional.empty(),
            keyManagerFactory = mockk()
        )
        val context = manager.getContext(null)
        assertNull(context.sslSocketFactory)
        assertNull(context.trustManager)
        assertNull(context.hostnameVerifier)
        assertFalse(context.disableHttp2)
    }

    @Test
    fun `getContext(no customTrustManager, with client certificate)`() {
        val kmf: ClientCertKeyManager.Factory = mockk(relaxed = true)
        val manager = ConnectionSecurityManager(
            customTrustManager = Optional.empty(),
            customHostnameVerifier = Optional.empty(),
            keyManagerFactory = kmf
        )
        val context = manager.getContext("alias")
        assertNotNull(context.sslSocketFactory)
        assertEquals(manager.defaultTrustManager().javaClass, context.trustManager?.javaClass)
        assertNull(context.hostnameVerifier)
        assertTrue(context.disableHttp2)
        verify(exactly = 1) {
            kmf.create("alias")
        }
    }

    @Test
    fun `getContext(with customTrustManager, no client certificate)`() {
        val customTrustManager: CustomCertManager = mockk()
        val customHostnameVerifier: CustomCertManager.HostnameVerifier = mockk()
        val manager = ConnectionSecurityManager(
            customTrustManager = Optional.of(customTrustManager),
            customHostnameVerifier = Optional.of(customHostnameVerifier),
            keyManagerFactory = mockk()
        )
        val context = manager.getContext(null)
        assertNotNull(context.sslSocketFactory)
        assertEquals(customTrustManager, context.trustManager)
        assertEquals(customHostnameVerifier, context.hostnameVerifier)
        assertFalse(context.disableHttp2)
    }

    @Test
    fun `getContext(with customTrustManager, with client certificate)`() {
        val customTrustManager: CustomCertManager = mockk()
        val customHostnameVerifier: CustomCertManager.HostnameVerifier = mockk()
        val kmf: ClientCertKeyManager.Factory = mockk(relaxed = true)
        val manager = ConnectionSecurityManager(
            customTrustManager = Optional.of(customTrustManager),
            customHostnameVerifier = Optional.of(customHostnameVerifier),
            keyManagerFactory = kmf
        )
        val context = manager.getContext("alias")
        assertNotNull(context.sslSocketFactory)
        assertEquals(customTrustManager, context.trustManager)
        assertEquals(customHostnameVerifier, context.hostnameVerifier)
        assertTrue(context.disableHttp2)
        verify(exactly = 1) {
            kmf.create("alias")
        }
    }

    @Test
    fun `getSocketFactory(no customTrustManager, no client certificate)`() {
        val manager = ConnectionSecurityManager(
            customTrustManager = Optional.empty(),
            customHostnameVerifier = Optional.empty(),
            keyManagerFactory = mockk()
        )
        val socketFactory = manager.getSocketFactory(null)
        assertNotNull(socketFactory.javaClass)
    }

    @Test
    fun `getSocketFactory(no customTrustManager, with client certificate)`() {
        val kmf: ClientCertKeyManager.Factory = mockk(relaxed = true)
        val manager = ConnectionSecurityManager(
            customTrustManager = Optional.empty(),
            customHostnameVerifier = Optional.empty(),
            keyManagerFactory = kmf
        )
        val socketFactory = manager.getSocketFactory("alias")
        assertNotNull(socketFactory.javaClass)
        verify(exactly = 1) {
            kmf.create("alias")
        }
    }

    @Test
    fun `getSocketFactory(with customTrustManager, no client certificate)`() {
        val customTrustManager: CustomCertManager = mockk()
        val customHostnameVerifier: CustomCertManager.HostnameVerifier = mockk()
        val manager = ConnectionSecurityManager(
            customTrustManager = Optional.of(customTrustManager),
            customHostnameVerifier = Optional.of(customHostnameVerifier),
            keyManagerFactory = mockk()
        )
        val socketFactory = manager.getSocketFactory(null)
        assertNotNull(socketFactory.javaClass)
    }

    @Test
    fun `getSocketFactory(with customTrustManager, with client certificate)`() {
        val customTrustManager: CustomCertManager = mockk()
        val customHostnameVerifier: CustomCertManager.HostnameVerifier = mockk()
        val kmf: ClientCertKeyManager.Factory = mockk(relaxed = true)
        val manager = ConnectionSecurityManager(
            customTrustManager = Optional.of(customTrustManager),
            customHostnameVerifier = Optional.of(customHostnameVerifier),
            keyManagerFactory = kmf
        )
        val socketFactory = manager.getSocketFactory("alias")
        assertNotNull(socketFactory.javaClass)
        verify(exactly = 1) {
            kmf.create("alias")
        }
    }

}