/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid;

import android.accounts.Account;
import android.content.Context;
import android.os.Build;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import at.bitfire.dav4android.BasicDigestAuthenticator;
import at.bitfire.davdroid.log.ExternalFileLogger;

import android.support.annotation.NonNull;
import android.text.format.DateFormat;

import lombok.RequiredArgsConstructor;
import okhttp3.Credentials;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

public class HttpClient {
    private static final OkHttpClient client = new OkHttpClient();
    private static final UserAgentInterceptor userAgentInterceptor = new UserAgentInterceptor();

    private static final String userAgent;
    static {
        String date = new SimpleDateFormat("yyyy/MM/dd", Locale.US).format(new Date(BuildConfig.buildTime));
        userAgent = "DAVdroid/" + BuildConfig.VERSION_NAME + " (" + date + "; dav4android; okhttp3) Android/" + Build.VERSION.RELEASE;
    }

    private HttpClient() {
    }

    public static OkHttpClient create(Context context, Account account) {
        OkHttpClient.Builder builder = client.newBuilder();

        // use MemorizingTrustManager to manage self-signed certificates
        if (App.getSslSocketFactoryCompat() != null)
            builder.sslSocketFactory(App.getSslSocketFactoryCompat());
        if (App.getHostnameVerifier() != null)
            builder.hostnameVerifier(App.getHostnameVerifier());

        // set timeouts
        builder.connectTimeout(30, TimeUnit.SECONDS);
        builder.writeTimeout(30, TimeUnit.SECONDS);
        builder.readTimeout(120, TimeUnit.SECONDS);

        // don't allow redirects, because it would break PROPFIND handling
        builder.followRedirects(false);

        // add User-Agent to every request
        builder.addNetworkInterceptor(userAgentInterceptor);

        // add cookie store for non-persistent cookies (some services like Horde use cookies for session tracking)
        builder.cookieJar(MemoryCookieStore.INSTANCE);

        if (context != null && account != null) {
            // use account settings for authentication and logging
            AccountSettings settings = new AccountSettings(context, account);

            if (settings.preemptiveAuth())
                builder.addNetworkInterceptor(new PreemptiveAuthenticationInterceptor(settings.username(), settings.password()));
            else
                builder.authenticator(new BasicDigestAuthenticator(null, settings.username(), settings.password()));

        }

        if (App.log.isLoggable(Level.FINEST))
            addLogger(builder, App.log);

        return builder.build();
    }

    private static OkHttpClient.Builder addAuthentication(@NonNull OkHttpClient.Builder builder, @NonNull String username, @NonNull String password, boolean preemptive) {
        if (preemptive)
            builder.addNetworkInterceptor(new PreemptiveAuthenticationInterceptor(username, password));
        else
            builder.authenticator(new BasicDigestAuthenticator(null, username, password));
        return builder;
    }

    public static OkHttpClient addAuthentication(@NonNull OkHttpClient client, @NonNull String username, @NonNull String password, boolean preemptive) {
        OkHttpClient.Builder builder = client.newBuilder();
        addAuthentication(builder, username, password, preemptive);
        return builder.build();
    }

    public static OkHttpClient addAuthentication(@NonNull OkHttpClient client, @NonNull String host, @NonNull String username, @NonNull String password) {
        return client.newBuilder()
                .authenticator(new BasicDigestAuthenticator(host, username, password))
                .build();
    }

    private static OkHttpClient.Builder addLogger(@NonNull OkHttpClient.Builder builder, @NonNull final Logger logger) {
        // trace-level logging → add network traffic interceptor
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(new HttpLoggingInterceptor.Logger() {
            @Override
            public void log(String message) {
                logger.finest(message);
            }
        });
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        return builder.addInterceptor(loggingInterceptor);
    }

    public static OkHttpClient addLogger(@NonNull OkHttpClient client, @NonNull final Logger logger) {
        return addLogger(client.newBuilder(), logger).build();
    }


    static class UserAgentInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Locale locale = Locale.getDefault();
            Request request = chain.request().newBuilder()
                    .header("User-Agent", userAgent)
                    .header("Accept-Language", locale.getLanguage() + "-" + locale.getCountry() + ", " + locale.getLanguage() + ";q=0.7, *;q=0.5")
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

}
