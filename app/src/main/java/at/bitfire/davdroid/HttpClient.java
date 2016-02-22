/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid;

import android.content.Context;
import android.os.Build;

import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import at.bitfire.dav4android.BasicDigestAuthenticator;
import at.bitfire.davdroid.syncadapter.AccountSettings;
import de.duenndns.ssl.MemorizingTrustManager;
import android.support.annotation.NonNull;
import lombok.RequiredArgsConstructor;
import okhttp3.Credentials;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.internal.tls.OkHostnameVerifier;
import okhttp3.logging.HttpLoggingInterceptor;

public class HttpClient {
    private static final int MAX_LOG_LINE_LENGTH = 85;

    private static final OkHttpClient client = new OkHttpClient();
    private static final UserAgentInterceptor userAgentInterceptor = new UserAgentInterceptor();

    private static final String userAgent;
    static {
        String date = new SimpleDateFormat("yyyy/MM/dd", Locale.US).format(new Date(BuildConfig.buildTime));
        userAgent = "DAVdroid/" + BuildConfig.VERSION_NAME + " (" + date + "; dav4android; okhttp3) Android/" + Build.VERSION.RELEASE;
    }

    private HttpClient() {
    }

    public static OkHttpClient create(Context context) {
        OkHttpClient.Builder builder = client.newBuilder();

        if (context != null) {
            // use MemorizingTrustManager to manage self-signed certificates
            MemorizingTrustManager mtm = new MemorizingTrustManager(context);
            builder.sslSocketFactory(new SSLSocketFactoryCompat(mtm));
            builder.hostnameVerifier(mtm.wrapHostnameVerifier(OkHostnameVerifier.INSTANCE));
        }

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

        return builder.build();
    }

    public static OkHttpClient addAuthentication(@NonNull OkHttpClient httpClient, @NonNull String username, @NonNull String password, boolean preemptive) {
        OkHttpClient.Builder builder = httpClient.newBuilder();
        if (preemptive)
            builder.addNetworkInterceptor(new PreemptiveAuthenticationInterceptor(username, password));
        else
            builder.authenticator(new BasicDigestAuthenticator(null, username, password));
        return builder.build();
    }

    public static OkHttpClient addAuthentication(@NonNull OkHttpClient httpClient, @NonNull AccountSettings accountSettings) {
        return addAuthentication(httpClient, accountSettings.username(), accountSettings.password(), accountSettings.preemptiveAuth());
    }

    public static OkHttpClient addAuthentication(@NonNull OkHttpClient httpClient, @NonNull String host, @NonNull String username, @NonNull String password) {
        return httpClient.newBuilder()
                .authenticator(new BasicDigestAuthenticator(host, username, password))
                .build();
    }

    public static OkHttpClient addLogger(@NonNull OkHttpClient httpClient, @NonNull final Logger logger) {
        // enable verbose logs, if requested
        if (logger.isTraceEnabled()) {
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(new HttpLoggingInterceptor.Logger() {
                @Override
                public void log(String message) {
                    BufferedReader reader = new BufferedReader(new StringReader(message));
                    String line;
                    try {
                        while ((line = reader.readLine()) != null) {
                            int len = line.length();
                            for (int pos = 0; pos < len; pos += MAX_LOG_LINE_LENGTH)
                                if (pos < len - MAX_LOG_LINE_LENGTH)
                                    logger.trace(line.substring(pos, pos + MAX_LOG_LINE_LENGTH) + "\\");
                                else
                                    logger.trace(line.substring(pos));
                        }
                    } catch(IOException e) {
                        // for some reason, we couldn't split our message
                        logger.trace(message);
                    }
                }
            });
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

            return httpClient.newBuilder()
                    .addInterceptor(loggingInterceptor)
                    .build();
        } else
            return httpClient;
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
