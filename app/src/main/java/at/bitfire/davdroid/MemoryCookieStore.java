/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

/**
 * Primitive cookie store that stores cookies in a (volatile) hash map.
 * Will be sufficient for session cookies.
 */
public class MemoryCookieStore implements CookieJar {

    public static final MemoryCookieStore INSTANCE = new MemoryCookieStore();

    protected final Map<HttpUrl, List<Cookie>> store = new ConcurrentHashMap<>();


    @Override
    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
        store.put(url, cookies);
    }

    @Override
    public List<Cookie> loadForRequest(HttpUrl url) {
        return store.get(url);
    }

}
