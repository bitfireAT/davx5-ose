/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid;

import android.os.Build;
import android.test.InstrumentationTestCase;

import junit.framework.TestCase;

import java.io.IOException;
import java.net.Socket;

import javax.net.ssl.SSLSocket;

import de.duenndns.ssl.MemorizingTrustManager;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockWebServer;

public class SSLSocketFactoryCompatTest extends InstrumentationTestCase {

    SSLSocketFactoryCompat factory;
    MockWebServer server = new MockWebServer();

    @Override
    protected void setUp() throws Exception {
        factory = new SSLSocketFactoryCompat(new MemorizingTrustManager(getInstrumentation().getTargetContext().getApplicationContext()));
        server.start();
    }

    @Override
    protected void tearDown() throws Exception {
        server.shutdown();
    }


    public void testUpgradeTLS() throws IOException {
        Socket s = factory.createSocket(server.getHostName(), server.getPort());
        assertTrue(s instanceof SSLSocket);

        SSLSocket ssl = (SSLSocket)s;
        assertFalse(org.apache.commons.lang3.ArrayUtils.contains(ssl.getEnabledProtocols(), "SSLv3"));
        assertTrue(org.apache.commons.lang3.ArrayUtils.contains(ssl.getEnabledProtocols(), "TLSv1"));

        if (Build.VERSION.SDK_INT >= 16) {
            assertTrue(org.apache.commons.lang3.ArrayUtils.contains(ssl.getEnabledProtocols(), "TLSv1.1"));
            assertTrue(org.apache.commons.lang3.ArrayUtils.contains(ssl.getEnabledProtocols(), "TLSv1.2"));
        }
    }

}
