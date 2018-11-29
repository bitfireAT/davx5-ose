/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid

import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import at.bitfire.cert4android.CustomCertManager
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockWebServer
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.ArrayUtils.contains
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.Socket
import java.security.KeyFactory
import java.security.KeyStore
import java.security.Principal
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager

class CustomTlsSocketFactoryTest {

    private lateinit var certMgr: CustomCertManager
    private lateinit var factory: CustomTlsSocketFactory
    private val server = MockWebServer()

    @Before
    fun startServer() {
        certMgr = CustomCertManager(getInstrumentation().context, false, true)
        factory = CustomTlsSocketFactory(null, certMgr)
        server.start()
    }

    @After
    fun stopServer() {
        server.shutdown()
        certMgr.close()
    }


    @Test
    fun testSendClientCertificate() {
        var public: X509Certificate? = null
        javaClass.classLoader!!.getResourceAsStream("sample.crt").use {
            public = CertificateFactory.getInstance("X509").generateCertificate(it) as? X509Certificate
        }
        assertNotNull(public)

        val keyFactory = KeyFactory.getInstance("RSA")
        val private = keyFactory.generatePrivate(PKCS8EncodedKeySpec(readResource("sample.key")))
        assertNotNull(private)

        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val alias = "sample"
        keyStore.setKeyEntry(alias, private, null, arrayOf(public))
        assertTrue(keyStore.containsAlias(alias))

        val trustManagerFactory = TrustManagerFactory.getInstance("X509")
        trustManagerFactory.init(null as KeyStore?)
        val trustManager = trustManagerFactory.trustManagers.first() as X509TrustManager

        val factory = CustomTlsSocketFactory(object: X509ExtendedKeyManager() {
            override fun getServerAliases(p0: String?, p1: Array<out Principal>?): Array<String>? = null
            override fun chooseServerAlias(p0: String?, p1: Array<out Principal>?, p2: Socket?) = null

            override fun getClientAliases(p0: String?, p1: Array<out Principal>?) =
                    arrayOf(alias)

            override fun chooseClientAlias(p0: Array<out String>?, p1: Array<out Principal>?, p2: Socket?) =
                    alias

            override fun getCertificateChain(forAlias: String?) =
                    arrayOf(public).takeIf { forAlias == alias }

            override fun getPrivateKey(forAlias: String?) =
                    private.takeIf { forAlias == alias }
        }, trustManager)

        /* known client cert test URLs (thanks!):
        *  - https://prod.idrix.eu/secure/
        *  - https://server.cryptomix.com/secure/
        */
        val client = OkHttpClient.Builder()
                .sslSocketFactory(factory, trustManager)
                .build()
        client.newCall(Request.Builder()
                .get()
                .url("https://prod.idrix.eu/secure/")
                .build()).execute().use { response ->
            assertTrue(response.isSuccessful)
            assertTrue(response.body()!!.string().contains("CN=User Cert,O=Internet Widgits Pty Ltd,ST=Some-State,C=CA"))
        }
    }

    @Test
    fun testUpgradeTLS() {
        val s = factory.createSocket(server.hostName, server.port)
        assertTrue(s is SSLSocket)
        val ssl = s as SSLSocket

        assertFalse(contains(ssl.enabledProtocols, "SSLv3"))
        assertTrue(contains(ssl.enabledProtocols, "TLSv1"))
        assertTrue(contains(ssl.enabledProtocols, "TLSv1.1"))
        assertTrue(contains(ssl.enabledProtocols, "TLSv1.2"))
    }


    private fun readResource(name: String): ByteArray {
        this.javaClass.classLoader.getResourceAsStream(name).use {
            return IOUtils.toByteArray(it)
        }
    }

}
