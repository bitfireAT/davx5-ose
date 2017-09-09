/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.Socket;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import at.bitfire.cert4android.CustomCertManager;
import okhttp3.mockwebserver.MockWebServer;

import static android.support.test.InstrumentationRegistry.getInstrumentation;
import static android.support.test.InstrumentationRegistry.getTargetContext;
import static junit.framework.TestCase.assertFalse;
import static org.apache.commons.lang3.ArrayUtils.contains;
import static org.junit.Assert.assertTrue;

public class SSLSocketFactoryCompatTest {

    CustomCertManager certMgr;
    SSLSocketFactoryCompat factory;
    MockWebServer server = new MockWebServer();

    @Before
    public void startServer() throws Exception {
        certMgr = new CustomCertManager(getInstrumentation().getContext(), true, null);
        factory = new SSLSocketFactoryCompat(certMgr);
        server.start();
    }

    @After
    public void stopServer() throws Exception {
        server.shutdown();
        certMgr.close();
    }


    @Test
    public void testUpgradeTLS() throws IOException {
        Socket s = factory.createSocket(server.getHostName(), server.getPort());
        assertTrue(s instanceof SSLSocket);

        SSLSocket ssl = (SSLSocket)s;
        assertFalse(contains(ssl.getEnabledProtocols(), "SSLv3"));
        assertTrue(contains(ssl.getEnabledProtocols(), "TLSv1"));
        assertTrue(contains(ssl.getEnabledProtocols(), "TLSv1.1"));
        assertTrue(contains(ssl.getEnabledProtocols(), "TLSv1.2"));
    }

}
