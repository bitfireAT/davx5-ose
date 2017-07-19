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
import java.net.URISyntaxException;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class HttpClientTest {

    MockWebServer server;
    OkHttpClient httpClient;

    @Before
    public void setUp() throws IOException {
        httpClient = HttpClient.create(null);

        server = new MockWebServer();
        server.start(30000);
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }


    @Test
    public void testCookies() throws IOException, InterruptedException, URISyntaxException {
        HttpUrl url = server.url("/test");

        // set cookie for root path (/) and /test path in first response
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "cookie1=1; path=/")
                .addHeader("Set-Cookie", "cookie2=2")
                .setBody("Cookie set"));
        httpClient.newCall(new Request.Builder()
                .get().url(url)
                .build()).execute();
        assertNull(server.takeRequest().getHeader("Cookie"));

        // cookie should be sent with second request
        // second response lets first cookie expire and overwrites second cookie
        server.enqueue(new MockResponse()
                .addHeader("Set-Cookie", "cookie1=1a; path=/; Max-Age=0")
                .addHeader("Set-Cookie", "cookie2=2a")
                .setResponseCode(200));
        httpClient.newCall(new Request.Builder()
                .get().url(url)
                .build()).execute();
        assertEquals("cookie2=2; cookie1=1", server.takeRequest().getHeader("Cookie"));

        server.enqueue(new MockResponse()
                .setResponseCode(200));
        httpClient.newCall(new Request.Builder()
                .get().url(url)
                .build()).execute();
        assertEquals("cookie2=2a", server.takeRequest().getHeader("Cookie"));
    }

}
