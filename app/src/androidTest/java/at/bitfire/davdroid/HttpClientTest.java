/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid;

import android.test.InstrumentationTestCase;

import java.io.IOException;
import java.net.URISyntaxException;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

public class HttpClientTest extends InstrumentationTestCase {

    MockWebServer server;
    OkHttpClient httpClient;

    @Override
    public void setUp() throws IOException {
        httpClient = HttpClient.create(getInstrumentation().getTargetContext().getApplicationContext(), null);

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

}
