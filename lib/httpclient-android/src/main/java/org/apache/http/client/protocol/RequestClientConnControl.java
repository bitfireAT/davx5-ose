/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.client.protocol;

import java.io.IOException;

import android.util.Log;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.annotation.Immutable;
import org.apache.http.conn.routing.RouteInfo;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.Args;

/**
 * This protocol interceptor is responsible for adding <code>Connection</code>
 * or <code>Proxy-Connection</code> headers to the outgoing requests, which
 * is essential for managing persistence of <code>HTTP/1.0</code> connections.
 *
 * @since 4.0
 */
@Immutable
public class RequestClientConnControl implements HttpRequestInterceptor {

    private final static String TAG = "HttpClient";

    private static final String PROXY_CONN_DIRECTIVE = "Proxy-Connection";

    public RequestClientConnControl() {
        super();
    }

    public void process(final HttpRequest request, final HttpContext context)
            throws HttpException, IOException {
        Args.notNull(request, "HTTP request");

        final String method = request.getRequestLine().getMethod();
        if (method.equalsIgnoreCase("CONNECT")) {
            request.setHeader(PROXY_CONN_DIRECTIVE, HTTP.CONN_KEEP_ALIVE);
            return;
        }

        final HttpClientContext clientContext = HttpClientContext.adapt(context);

        // Obtain the client connection (required)
        final RouteInfo route = clientContext.getHttpRoute();
        if (route == null) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Connection route not set in the context");
            }
            return;
        }

        if (route.getHopCount() == 1 || route.isTunnelled()) {
            if (!request.containsHeader(HTTP.CONN_DIRECTIVE)) {
                request.addHeader(HTTP.CONN_DIRECTIVE, HTTP.CONN_KEEP_ALIVE);
            }
        }
        if (route.getHopCount() == 2 && !route.isTunnelled()) {
            if (!request.containsHeader(PROXY_CONN_DIRECTIVE)) {
                request.addHeader(PROXY_CONN_DIRECTIVE, HTTP.CONN_KEEP_ALIVE);
            }
        }
    }

}
