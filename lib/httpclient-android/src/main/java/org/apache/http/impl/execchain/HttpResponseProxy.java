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

package org.apache.http.impl.execchain;

import java.io.IOException;
import java.util.Locale;

import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.params.HttpParams;

/**
 * A proxy class for {@link org.apache.http.HttpResponse} that can be used to release client connection
 * associated with the original response.
 *
 * @since 4.3
 */
@NotThreadSafe
class HttpResponseProxy implements CloseableHttpResponse {

    private final HttpResponse original;
    private final ConnectionHolder connHolder;

    public HttpResponseProxy(final HttpResponse original, final ConnectionHolder connHolder) {
        this.original = original;
        this.connHolder = connHolder;
        ResponseEntityProxy.enchance(original, connHolder);
    }

    public void close() throws IOException {
        if (this.connHolder != null) {
            this.connHolder.abortConnection();
        }
    }

    public StatusLine getStatusLine() {
        return original.getStatusLine();
    }

    public void setStatusLine(final StatusLine statusline) {
        original.setStatusLine(statusline);
    }

    public void setStatusLine(final ProtocolVersion ver, final int code) {
        original.setStatusLine(ver, code);
    }

    public void setStatusLine(final ProtocolVersion ver, final int code, final String reason) {
        original.setStatusLine(ver, code, reason);
    }

    public void setStatusCode(final int code) throws IllegalStateException {
        original.setStatusCode(code);
    }

    public void setReasonPhrase(final String reason) throws IllegalStateException {
        original.setReasonPhrase(reason);
    }

    public HttpEntity getEntity() {
        return original.getEntity();
    }

    public void setEntity(final HttpEntity entity) {
        original.setEntity(entity);
    }

    public Locale getLocale() {
        return original.getLocale();
    }

    public void setLocale(final Locale loc) {
        original.setLocale(loc);
    }

    public ProtocolVersion getProtocolVersion() {
        return original.getProtocolVersion();
    }

    public boolean containsHeader(final String name) {
        return original.containsHeader(name);
    }

    public Header[] getHeaders(final String name) {
        return original.getHeaders(name);
    }

    public Header getFirstHeader(final String name) {
        return original.getFirstHeader(name);
    }

    public Header getLastHeader(final String name) {
        return original.getLastHeader(name);
    }

    public Header[] getAllHeaders() {
        return original.getAllHeaders();
    }

    public void addHeader(final Header header) {
        original.addHeader(header);
    }

    public void addHeader(final String name, final String value) {
        original.addHeader(name, value);
    }

    public void setHeader(final Header header) {
        original.setHeader(header);
    }

    public void setHeader(final String name, final String value) {
        original.setHeader(name, value);
    }

    public void setHeaders(final Header[] headers) {
        original.setHeaders(headers);
    }

    public void removeHeader(final Header header) {
        original.removeHeader(header);
    }

    public void removeHeaders(final String name) {
        original.removeHeaders(name);
    }

    public HeaderIterator headerIterator() {
        return original.headerIterator();
    }

    public HeaderIterator headerIterator(final String name) {
        return original.headerIterator(name);
    }

    @Deprecated
    public HttpParams getParams() {
        return original.getParams();
    }

    @Deprecated
    public void setParams(final HttpParams params) {
        original.setParams(params);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("HttpResponseProxy{");
        sb.append(original);
        sb.append('}');
        return sb.toString();
    }

}
