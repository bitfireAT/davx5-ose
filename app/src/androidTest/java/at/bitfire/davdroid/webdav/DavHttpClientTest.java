/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.webdav;

import android.test.InstrumentationTestCase;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGetHC4;
import org.apache.http.client.methods.HttpPostHC4;
import org.apache.http.client.methods.HttpRequestBaseHC4;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.IOException;
import java.net.URI;

import at.bitfire.davdroid.TestConstants;
import lombok.Cleanup;

public class DavHttpClientTest extends InstrumentationTestCase {
    final static URI testCookieURI = TestConstants.roboHydra.resolve("/dav/testCookieStore");

    CloseableHttpClient httpClient;

    @Override
    protected void setUp() throws Exception {
        httpClient = DavHttpClient.create();
    }

    @Override
    protected void tearDown() throws Exception {
        httpClient.close();
    }


    public void testCookies() throws IOException {
        CloseableHttpResponse response = null;

        HttpGetHC4 get = new HttpGetHC4(testCookieURI);
        get.setHeader("Accept", "text/xml");

        // at first, DavHttpClient doesn't send a cookie
        try {
            response = httpClient.execute(get);
            assertEquals(412, response.getStatusLine().getStatusCode());
        } finally {
            if (response != null)
                response.close();
        }

        // POST sets a cookie to DavHttpClient
        try {
            response = httpClient.execute(new HttpPostHC4(testCookieURI));
            assertEquals(200, response.getStatusLine().getStatusCode());
        } finally {
            if (response != null)
                response.close();
        }

        // and now DavHttpClient sends a cookie for GET, too
        try {
            response = httpClient.execute(get);
            assertEquals(200, response.getStatusLine().getStatusCode());
        } finally {
            if (response != null)
                response.close();
        }
    }
}
