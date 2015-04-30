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
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.annotation.Immutable;
import org.apache.http.auth.AuthProtocolState;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthStateHC4;
import org.apache.http.auth.Credentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.routing.RouteInfo;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.Args;

/**
 * Request interceptor that can preemptively authenticate against known hosts,
 * if there is a cached {@link AuthScheme} instance in the local
 * {@link AuthCache} associated with the given target or proxy host.
 *
 * @since 4.1
 */
@Immutable
public class RequestAuthCache implements HttpRequestInterceptor {

    private final static String TAG = "HttpClient";

    public RequestAuthCache() {
        super();
    }

    public void process(final HttpRequest request, final HttpContext context)
            throws HttpException, IOException {
        Args.notNull(request, "HTTP request");
        Args.notNull(context, "HTTP context");

        final HttpClientContext clientContext = HttpClientContext.adapt(context);

        final AuthCache authCache = clientContext.getAuthCache();
        if (authCache == null) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Auth cache not set in the context");
            }
            return;
        }

        final CredentialsProvider credsProvider = clientContext.getCredentialsProvider();
        if (credsProvider == null) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Credentials provider not set in the context");
            }
            return;
        }

        final RouteInfo route = clientContext.getHttpRoute();
        if (route == null) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Route info not set in the context");
            }
            return;
        }

        HttpHost target = clientContext.getTargetHost();
        if (target == null) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Target host not set in the context");
            }
            return;
        }

        if (target.getPort() < 0) {
            target = new HttpHost(
                    target.getHostName(),
                    route.getTargetHost().getPort(),
                    target.getSchemeName());
        }

        final AuthStateHC4 targetState = clientContext.getTargetAuthState();
        if (targetState != null && targetState.getState() == AuthProtocolState.UNCHALLENGED) {
            final AuthScheme authScheme = authCache.get(target);
            if (authScheme != null) {
                doPreemptiveAuth(target, authScheme, targetState, credsProvider);
            }
        }

        final HttpHost proxy = route.getProxyHost();
        final AuthStateHC4 proxyState = clientContext.getProxyAuthState();
        if (proxy != null && proxyState != null && proxyState.getState() == AuthProtocolState.UNCHALLENGED) {
            final AuthScheme authScheme = authCache.get(proxy);
            if (authScheme != null) {
                doPreemptiveAuth(proxy, authScheme, proxyState, credsProvider);
            }
        }
    }

    private void doPreemptiveAuth(
            final HttpHost host,
            final AuthScheme authScheme,
            final AuthStateHC4 authState,
            final CredentialsProvider credsProvider) {
        final String schemeName = authScheme.getSchemeName();
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Re-using cached '" + schemeName + "' auth scheme for " + host);
        }

        final AuthScope authScope = new AuthScope(host.getHostName(), host.getPort(), AuthScope.ANY_REALM, schemeName);
        final Credentials creds = credsProvider.getCredentials(authScope);

        if (creds != null) {
            if ("BASIC".equalsIgnoreCase(authScheme.getSchemeName())) {
                authState.setState(AuthProtocolState.CHALLENGED);
            } else {
                authState.setState(AuthProtocolState.SUCCESS);
            }
            authState.update(authScheme, creds);
        } else {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "No credentials for preemptive authentication");
            }
        }
    }

}
