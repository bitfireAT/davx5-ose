/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid;

import com.squareup.okhttp.Authenticator;
import com.squareup.okhttp.Credentials;
import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.logging.HttpLoggingInterceptor;

import java.io.IOException;
import java.net.Proxy;
import java.util.List;
import java.util.concurrent.TimeUnit;

import at.bitfire.dav4android.HttpUtils;
import lombok.RequiredArgsConstructor;

public class HttpClient extends OkHttpClient {

    protected static final String
            HEADER_AUTHORIZATION = "Authorization";


    public HttpClient() {
        super();
        initialize();
        enableLogs();
    }

    public HttpClient(String username, String password, boolean preemptive) {
        super();
        initialize();

        // authentication
        if (preemptive)
            networkInterceptors().add(new PreemptiveAuthenticationInterceptor(username, password));
        else
            setAuthenticator(new DavAuthenticator(username, password));

        enableLogs();
    }


    protected void initialize() {
        // don't follow redirects automatically because this may rewrite DAV methods to GET
        setFollowRedirects(false);

        // timeouts
        setConnectTimeout(20, TimeUnit.SECONDS);
        setWriteTimeout(15, TimeUnit.SECONDS);
        setReadTimeout(45, TimeUnit.SECONDS);
    }

    protected void enableLogs() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor(new HttpLoggingInterceptor.Logger() {
            @Override
            public void log(String message) {
                at.bitfire.dav4android.Constants.log.trace(message);
            }
        });
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);
        networkInterceptors().add(logging);
    }


    @RequiredArgsConstructor
    static class PreemptiveAuthenticationInterceptor implements Interceptor {
        final String username, password;

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            request = request.newBuilder()
                    .header("Authorization", Credentials.basic(username, password))
                    .build();
            return chain.proceed(request);
        }
    }

    @RequiredArgsConstructor
    static class DavAuthenticator implements Authenticator {
        final String username, password;

        @Override
        public Request authenticate(Proxy proxy, Response response) throws IOException {
            // check whether this is the first authentication try with our credentials
            Response priorResponse = response.priorResponse();
            boolean triedBefore = priorResponse != null ? priorResponse.request().header(HEADER_AUTHORIZATION) != null : false;
            if (triedBefore)
                // credentials didn't work last time, and they won't work now → stop here
                return null;

            //List<HttpUtils.AuthScheme> schemes = HttpUtils.parseWwwAuthenticate(response.headers("WWW-Authenticate"));
            // TODO Digest auth

            return response.request().newBuilder()
                    .header(HEADER_AUTHORIZATION, Credentials.basic(username, password))
                    .build();
        }

        @Override
        public Request authenticateProxy(Proxy proxy, Response response) throws IOException {
            return null;
        }
    }

}
