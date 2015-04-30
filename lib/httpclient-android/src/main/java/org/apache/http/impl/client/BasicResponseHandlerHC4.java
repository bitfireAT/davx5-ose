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

import java.io.IOException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.annotation.Immutable;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.util.EntityUtilsHC4;

/**
 * A {@link ResponseHandler} that returns the response body as a String
 * for successful (2xx) responses. If the response code was >= 300, the response
 * body is consumed and an {@link HttpResponseException} is thrown.
 * <p/>
 * If this is used with
 * {@link org.apache.http.client.HttpClient#execute(
 *  org.apache.http.client.methods.HttpUriRequest, ResponseHandler)},
 * HttpClient may handle redirects (3xx responses) internally.
 *
 * @since 4.0
 */
@Immutable
public class BasicResponseHandlerHC4 implements ResponseHandler<String> {

    /**
     * Returns the response body as a String if the response was successful (a
     * 2xx status code). If no response body exists, this returns null. If the
     * response was unsuccessful (>= 300 status code), throws an
     * {@link HttpResponseException}.
     */
    public String handleResponse(final HttpResponse response)
            throws HttpResponseException, IOException {
        final StatusLine statusLine = response.getStatusLine();
        final HttpEntity entity = response.getEntity();
        if (statusLine.getStatusCode() >= 300) {
            EntityUtilsHC4.consume(entity);
            throw new HttpResponseException(statusLine.getStatusCode(),
                    statusLine.getReasonPhrase());
        }
        return entity == null ? null : EntityUtilsHC4.toString(entity);
    }

}
