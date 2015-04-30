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
package org.apache.http.impl.client;

import java.security.Principal;

import javax.net.ssl.SSLSession;

import org.apache.http.HttpConnection;
import org.apache.http.annotation.Immutable;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthStateHC4;
import org.apache.http.auth.Credentials;
import org.apache.http.client.UserTokenHandler;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ManagedHttpClientConnection;
import org.apache.http.protocol.HttpContext;

/**
 * Default implementation of {@link UserTokenHandler}. This class will use
 * an instance of {@link Principal} as a state object for HTTP connections,
 * if it can be obtained from the given execution context. This helps ensure
 * persistent connections created with a particular user identity within
 * a particular security context can be reused by the same user only.
 * <p>
 * DefaultUserTokenHandlerHC4 will use the user principle of connection
 * based authentication schemes such as NTLM or that of the SSL session
 * with the client authentication turned on. If both are unavailable,
 * <code>null</code> token will be returned.
 *
 * @since 4.0
 */
@Immutable
public class DefaultUserTokenHandlerHC4 implements UserTokenHandler {

    public static final DefaultUserTokenHandlerHC4 INSTANCE = new DefaultUserTokenHandlerHC4();

    public Object getUserToken(final HttpContext context) {

        final HttpClientContext clientContext = HttpClientContext.adapt(context);

        Principal userPrincipal = null;

        final AuthStateHC4 targetAuthState = clientContext.getTargetAuthState();
        if (targetAuthState != null) {
            userPrincipal = getAuthPrincipal(targetAuthState);
            if (userPrincipal == null) {
                final AuthStateHC4 proxyAuthState = clientContext.getProxyAuthState();
                userPrincipal = getAuthPrincipal(proxyAuthState);
            }
        }

        if (userPrincipal == null) {
            final HttpConnection conn = clientContext.getConnection();
            if (conn.isOpen() && conn instanceof ManagedHttpClientConnection) {
                final SSLSession sslsession = ((ManagedHttpClientConnection) conn).getSSLSession();
                if (sslsession != null) {
                    userPrincipal = sslsession.getLocalPrincipal();
                }
            }
        }

        return userPrincipal;
    }

    private static Principal getAuthPrincipal(final AuthStateHC4 authState) {
        final AuthScheme scheme = authState.getAuthScheme();
        if (scheme != null && scheme.isComplete() && scheme.isConnectionBased()) {
            final Credentials creds = authState.getCredentials();
            if (creds != null) {
                return creds.getUserPrincipal();
            }
        }
        return null;
    }

}
