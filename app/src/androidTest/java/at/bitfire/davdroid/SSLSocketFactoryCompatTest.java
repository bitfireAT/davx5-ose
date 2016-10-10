/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid;

import android.os.Build;
import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.Socket;

import javax.net.ssl.SSLSocket;

import at.bitfire.cert4android.CustomCertManager;
import okhttp3.mockwebserver.MockWebServer;

import static android.support.test.InstrumentationRegistry.getTargetContext;
import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertTrue;

public class SSLSocketFactoryCompatTest {

    SSLSocketFactoryCompat factory;
    MockWebServer server = new MockWebServer();

    @Before
    public void startServer() throws Exception {
        factory = new SSLSocketFactoryCompat(new CustomCertManager(getTargetContext().getApplicationContext(), true));
        server.start();
    }

    @After
    public void stopServer() throws Exception {
        server.shutdown();
    }


    @Test
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
