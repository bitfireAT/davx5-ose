/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid;

import android.os.Build;

import com.squareup.okhttp.Authenticator;
import com.squareup.okhttp.Credentials;
import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.logging.HttpLoggingInterceptor;

import java.io.IOException;
import java.net.Proxy;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

import at.bitfire.dav4android.HttpUtils;
import lombok.RequiredArgsConstructor;

public class HttpClient extends OkHttpClient {

    protected static final String HEADER_AUTHORIZATION = "Authorization";

    final static UserAgentInterceptor userAgentInterceptor = new UserAgentInterceptor();
    final static HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(new HttpLoggingInterceptor.Logger() {
        @Override
        public void log(String message) {
            Constants.log.trace(message);
        }
    });
    static {
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
    }

    static final String userAgent;
    static {
        String date = new SimpleDateFormat("yyyy/MM/dd").format(BuildConfig.buildTime);
        userAgent = "DAVdroid/" + BuildConfig.VERSION_NAME + " (" + date + "; dav4android) Android/" + Build.VERSION.RELEASE;
    }

    protected String username, password;


    public HttpClient() {
        super();
        initialize();
    }

    public HttpClient(String username, String password, boolean preemptive) {
        super();
        initialize();

        // authentication
        this.username = username;
        this.password = password;
        if (preemptive)
            networkInterceptors().add(new PreemptiveAuthenticationInterceptor(username, password));
        else
            setAuthenticator(new DavAuthenticator(null, username, password));
    }

    /**
     * Creates a new HttpClient (based on another one) which can be used to download external resources:
     * 1. it does not use preemptive authentiation
     * 2. it only authenticates against a given host
     * @param client  user name and password from this client will be used
     * @param host    authentication will be restricted to this host
     */
    public HttpClient(HttpClient client, String host) {
        super();
        initialize();

        username = client.username;
        password = client.password;
        setAuthenticator(new DavAuthenticator(host, username, password));
    }


    protected void initialize() {
        // don't follow redirects automatically because this may rewrite DAV methods to GET
        setFollowRedirects(false);

        // set timeouts
        setConnectTimeout(30, TimeUnit.SECONDS);
        setWriteTimeout(15, TimeUnit.SECONDS);
        setReadTimeout(45, TimeUnit.SECONDS);

        // add User-Agent to every request
        networkInterceptors().add(userAgentInterceptor);

        // enable logs
        enableLogs();
    }

    protected void enableLogs() {
        interceptors().add(loggingInterceptor);
    }


    static class UserAgentInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request().newBuilder()
                    .header("User-Agent", userAgent)
                    .build();
            return chain.proceed(request);
        }
    }


    @RequiredArgsConstructor
    static class PreemptiveAuthenticationInterceptor implements Interceptor {
        final String username, password;

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request().newBuilder()
                    .header("Authorization", Credentials.basic(username, password))
                    .build();
            return chain.proceed(request);
        }
    }

    @RequiredArgsConstructor
    public static class DavAuthenticator implements Authenticator {
        final String host, username, password;

        @Override
        public Request authenticate(Proxy proxy, Response response) throws IOException {
            Request request = response.request();

            if (host != null && !request.httpUrl().host().equalsIgnoreCase(host)) {
                Constants.log.warn("Not authenticating against " +  host + " for security reasons!");
                return null;
            }

            // check whether this is the first authentication try with our credentials
            Response priorResponse = response.priorResponse();
            boolean triedBefore = priorResponse != null ? priorResponse.request().header(HEADER_AUTHORIZATION) != null : false;
            if (triedBefore)
                // credentials didn't work last time, and they won't work now → stop here
                return null;

            //List<HttpUtils.AuthScheme> schemes = HttpUtils.parseWwwAuthenticate(response.headers("WWW-Authenticate"));
            // TODO Digest auth

            return request.newBuilder()
                    .header(HEADER_AUTHORIZATION, Credentials.basic(username, password))
                    .build();
        }

        @Override
        public Request authenticateProxy(Proxy proxy, Response response) throws IOException {
            return null;
        }
    }

}
