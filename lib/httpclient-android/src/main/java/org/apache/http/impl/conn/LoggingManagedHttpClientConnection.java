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

import android.util.Log;
import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.config.MessageConstraints;
import org.apache.http.entity.ContentLengthStrategy;
import org.apache.http.io.HttpMessageParserFactory;
import org.apache.http.io.HttpMessageWriterFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

@NotThreadSafe
class LoggingManagedHttpClientConnection extends DefaultManagedHttpClientConnection {

    private final static String TAG = "HttpClient";
    private final static String HEADER_TAG = "Headers";
    private final WireHC4 wire;

    public LoggingManagedHttpClientConnection(
            final String id,
            final int buffersize,
            final int fragmentSizeHint,
            final CharsetDecoder chardecoder,
            final CharsetEncoder charencoder,
            final MessageConstraints constraints,
            final ContentLengthStrategy incomingContentStrategy,
            final ContentLengthStrategy outgoingContentStrategy,
            final HttpMessageWriterFactory<HttpRequest> requestWriterFactory,
            final HttpMessageParserFactory<HttpResponse> responseParserFactory) {
        super(id, buffersize, fragmentSizeHint, chardecoder, charencoder,
                constraints, incomingContentStrategy, outgoingContentStrategy,
                requestWriterFactory, responseParserFactory);
        this.wire = new WireHC4(id);
    }

    @Override
    public void close() throws IOException {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, getId() + ": Close connection");
        }
        super.close();
    }

    @Override
    public void shutdown() throws IOException {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, getId() + ": Shutdown connection");
        }
        super.shutdown();
    }

    @Override
    protected InputStream getSocketInputStream(final Socket socket) throws IOException {
        InputStream in = super.getSocketInputStream(socket);
        if (this.wire.enabled()) {
            in = new LoggingInputStream(in, this.wire);
        }
        return in;
    }

    @Override
    protected OutputStream getSocketOutputStream(final Socket socket) throws IOException {
        OutputStream out = super.getSocketOutputStream(socket);
        if (this.wire.enabled()) {
            out = new LoggingOutputStream(out, this.wire);
        }
        return out;
    }

    @Override
    protected void onResponseReceived(final HttpResponse response) {
        if (response != null && Log.isLoggable(HEADER_TAG, Log.DEBUG)) {
            Log.d(HEADER_TAG, getId() + " << " + response.getStatusLine().toString());
            final Header[] headers = response.getAllHeaders();
            for (final Header header : headers) {
                Log.d(HEADER_TAG, getId() + " << " + header.toString());
            }
        }
    }

    @Override
    protected void onRequestSubmitted(final HttpRequest request) {
        if (request != null && Log.isLoggable(HEADER_TAG, Log.DEBUG)) {
            Log.d(HEADER_TAG, getId() + " >> " + request.getRequestLine().toString());
            final Header[] headers = request.getAllHeaders();
            for (final Header header : headers) {
                Log.d(HEADER_TAG, getId() + " >> " + header.toString());
            }
        }
    }

}
