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
import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.TlsVersion;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;

public class HttpClientTest extends InstrumentationTestCase {

    MockWebServer server;
    HttpClient httpClient;

    @Override
    public void setUp() throws IOException {
        httpClient = new HttpClient(null, getInstrumentation().getTargetContext().getApplicationContext());

        server = new MockWebServer();
        server.start();
    }

    @Override
    public void tearDown() throws IOException {
        server.shutdown();
    }

    public void testCookies() throws IOException, InterruptedException, URISyntaxException {
        HttpUrl url = server.url("/");

        // set cookie in first response
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Set-Cookie", "theme=light; path=/")
                .setBody("Cookie set"));
        httpClient.newCall(new Request.Builder()
                .get().url(url)
                .build()).execute();
        assertNull(server.takeRequest().getHeader("Cookie"));

        // cookie should be sent with second request
        server.enqueue(new MockResponse()
                .setResponseCode(200));
        httpClient.newCall(new Request.Builder()
                .get().url(url)
                .build()).execute();
        //assertEquals("theme=light", server.takeRequest().getHeader("Cookie"));

        // doesn't work for URLs with ports, see https://code.google.com/p/android/issues/detail?id=193475
    }

    public void testTLSVersion() throws IOException {
        server.useHttps(new SSLSocketFactoryCompat(null), false);
        assertEquals("https", server.url("/").scheme());

        httpClient.setConnectionSpecs(Collections.singletonList(new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .tlsVersions(TlsVersion.TLS_1_2)
                .build()));

        // FIXME
        /*server.enqueue(new MockResponse().setResponseCode(204));
        Response response = httpClient.newCall(new Request.Builder()
                .get().url(server.url("/"))
                .build()).execute();
        assertTrue(response.isSuccessful());*/
    }

}
