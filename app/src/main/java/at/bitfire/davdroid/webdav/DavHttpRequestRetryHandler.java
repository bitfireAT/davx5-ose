/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.webdav;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.http.HttpRequest;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandlerHC4;

import java.util.Locale;

public class DavHttpRequestRetryHandler extends DefaultHttpRequestRetryHandlerHC4 {
	final static DavHttpRequestRetryHandler INSTANCE = new DavHttpRequestRetryHandler();
	
	// see http://www.iana.org/assignments/http-methods/http-methods.xhtml
	private final static String idempotentMethods[] = {
		"DELETE", "GET", "HEAD", "MKCALENDAR", "MKCOL", "OPTIONS", "PROPFIND", "PROPPATCH",
		"PUT", "REPORT", "SEARCH", "TRACE"
	};

    public DavHttpRequestRetryHandler() {
        super(/* retry count */ 3, /* retry already sent requests? */ false);
    }

    @Override
    protected boolean handleAsIdempotent(final HttpRequest request) {
        final String method = request.getRequestLine().getMethod().toUpperCase(Locale.ROOT);
        return ArrayUtils.contains(idempotentMethods, method);
    }
}
