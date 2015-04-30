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
package org.apache.http.impl.conn;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Arrays;

import android.util.Log;

import org.apache.http.HttpHost;
import org.apache.http.annotation.Immutable;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Lookup;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.DnsResolver;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.conn.ManagedHttpClientConnection;
import org.apache.http.conn.SchemePortResolver;
import org.apache.http.conn.UnsupportedSchemeException;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.Args;

@Immutable
class HttpClientConnectionOperator {

    static final String SOCKET_FACTORY_REGISTRY = "http.socket-factory-registry";

    private final static String TAG = "HttpClient";

    private final Lookup<ConnectionSocketFactory> socketFactoryRegistry;
    private final SchemePortResolver schemePortResolver;
    private final DnsResolver dnsResolver;

    HttpClientConnectionOperator(
            final Lookup<ConnectionSocketFactory> socketFactoryRegistry,
            final SchemePortResolver schemePortResolver,
            final DnsResolver dnsResolver) {
        super();
        Args.notNull(socketFactoryRegistry, "Socket factory registry");
        this.socketFactoryRegistry = socketFactoryRegistry;
        this.schemePortResolver = schemePortResolver != null ? schemePortResolver :
            DefaultSchemePortResolver.INSTANCE;
        this.dnsResolver = dnsResolver != null ? dnsResolver :
            SystemDefaultDnsResolver.INSTANCE;
    }

    @SuppressWarnings("unchecked")
    private Lookup<ConnectionSocketFactory> getSocketFactoryRegistry(final HttpContext context) {
        Lookup<ConnectionSocketFactory> reg = (Lookup<ConnectionSocketFactory>) context.getAttribute(
                SOCKET_FACTORY_REGISTRY);
        if (reg == null) {
            reg = this.socketFactoryRegistry;
        }
        return reg;
    }

    public void connect(
            final ManagedHttpClientConnection conn,
            final HttpHost host,
            final InetSocketAddress localAddress,
            final int connectTimeout,
            final SocketConfig socketConfig,
            final HttpContext context) throws IOException {
        final Lookup<ConnectionSocketFactory> registry = getSocketFactoryRegistry(context);
        final ConnectionSocketFactory sf = registry.lookup(host.getSchemeName());
        if (sf == null) {
            throw new UnsupportedSchemeException(host.getSchemeName() +
                    " protocol is not supported");
        }
        final InetAddress[] addresses = this.dnsResolver.resolve(host.getHostName());
        final int port = this.schemePortResolver.resolve(host);
        for (int i = 0; i < addresses.length; i++) {
            final InetAddress address = addresses[i];
            final boolean last = i == addresses.length - 1;

            Socket sock = sf.createSocket(context);
            sock.setSoTimeout(socketConfig.getSoTimeout());
            sock.setReuseAddress(socketConfig.isSoReuseAddress());
            sock.setTcpNoDelay(socketConfig.isTcpNoDelay());
            sock.setKeepAlive(socketConfig.isSoKeepAlive());
            final int linger = socketConfig.getSoLinger();
            if (linger >= 0) {
                sock.setSoLinger(linger > 0, linger);
            }
            conn.bind(sock);

            final InetSocketAddress remoteAddress = new InetSocketAddress(address, port);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Connecting to " + remoteAddress);
            }
            try {
                sock = sf.connectSocket(
                        connectTimeout, sock, host, remoteAddress, localAddress, context);
                conn.bind(sock);
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Connection established " + conn);
                }
                return;
            } catch (final SocketTimeoutException ex) {
                if (last) {
                    final ConnectTimeoutException cex = new ConnectTimeoutException(
                            getConnectTimeoutMessage(ex, host, addresses));
                    cex.initCause(ex);
                }
            } catch (final ConnectException ex) {
                if (last) {
                    final String msg = ex.getMessage();
                    if ("Connection timed out".equals(msg)) {
                        final ConnectTimeoutException cex = new ConnectTimeoutException(
                                getConnectTimeoutMessage(ex, host, addresses));
                        cex.initCause(ex);
                        throw cex;
                    } else {
                        throw new HttpHostConnectException(host, ex);
                    }
                }
            }
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Connect to " + remoteAddress + " timed out. " +
                        "Connection will be retried using another IP address");
            }
        }
    }

    private static String getConnectTimeoutMessage(
            final IOException cause,
            final HttpHost host,
            final InetAddress... remoteAddresses) {
        return "Connect to " +
                (host != null ? host.toHostString() : "remote host") +
                (remoteAddresses != null && remoteAddresses .length > 0 ?
                        " " + Arrays.asList(remoteAddresses) : "") +
                ((cause != null && cause.getMessage() != null) ?
                        " failed: " + cause.getMessage() : " timed out");
    }

    public void upgrade(
            final ManagedHttpClientConnection conn,
            final HttpHost host,
            final HttpContext context) throws IOException {
        final HttpClientContext clientContext = HttpClientContext.adapt(context);
        final Lookup<ConnectionSocketFactory> registry = getSocketFactoryRegistry(clientContext);
        final ConnectionSocketFactory sf = registry.lookup(host.getSchemeName());
        if (sf == null) {
            throw new UnsupportedSchemeException(host.getSchemeName() +
                    " protocol is not supported");
        }
        if (!(sf instanceof LayeredConnectionSocketFactory)) {
            throw new UnsupportedSchemeException(host.getSchemeName() +
                    " protocol does not support connection upgrade");
        }
        final LayeredConnectionSocketFactory lsf = (LayeredConnectionSocketFactory) sf;
        Socket sock = conn.getSocket();
        final int port = this.schemePortResolver.resolve(host);
        sock = lsf.createLayeredSocket(sock, host.getHostName(), port, context);
        conn.bind(sock);
    }

}
