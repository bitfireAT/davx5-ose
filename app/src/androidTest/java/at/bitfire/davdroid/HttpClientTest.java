/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid;

import android.test.InstrumentationTestCase;

import com.squareup.okhttp.ConnectionSpec;
import com.squareup.okhttp.TlsVersion;
import com.squareup.okhttp.mockwebserver.MockWebServer;

import java.io.IOException;
import java.util.Collections;

public class HttpClientTest extends InstrumentationTestCase {

    final MockWebServer server = new MockWebServer();
    HttpClient httpClient;

    @Override
    public void setUp() throws IOException {
        httpClient = new HttpClient(null, getInstrumentation().getTargetContext().getApplicationContext());

        httpClient.setConnectionSpecs(Collections.singletonList(new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .tlsVersions(TlsVersion.TLS_1_2)
                .build()));

        server.start();
        server.useHttps(new SSLSocketFactoryCompat(null), false);

        assertEquals("https", server.url("/").scheme());
    }

    @Override
    public void tearDown() throws IOException {
        server.shutdown();
    }

    public void testTLSVersion() throws IOException {
        // FIXME
        /*server.enqueue(new MockResponse().setResponseCode(204));
        Response response = httpClient.newCall(new Request.Builder()
                .get().url(server.url("/"))
                .build()).execute();
        assertTrue(response.isSuccessful());*/
    }

}
