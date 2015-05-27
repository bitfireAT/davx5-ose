/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.webdav;

import android.util.Log;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.RequestLine;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.protocol.HttpContext;

import java.net.URI;
import java.net.URISyntaxException;

import at.bitfire.davdroid.URIUtils;

/**
 * Custom Redirect Strategy that handles 30x for CalDAV/CardDAV-specific requests correctly
 */
public class DavRedirectStrategy implements RedirectStrategy {
	private final static String TAG = "davdroid.DavRedirectStrategy";
	public final static DavRedirectStrategy INSTANCE = new DavRedirectStrategy();
	
	protected final static String REDIRECTABLE_METHODS[] = {
		"OPTIONS", "GET", "PUT", "DELETE"
	};


	@Override
	public HttpUriRequest getRedirect(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
		RequestLine line = request.getRequestLine();

		String location = getLocation(request, response, context).toString();
		Log.i(TAG, "Following redirection: " + line.getMethod() + " " + line.getUri() + " -> " + location);

		return RequestBuilder
				.copy(request)
				.setUri(location)
				.removeHeaders("Content-Length")	// Content-Length will be set again automatically, if required;
													// remove it now to avoid duplicate header
				.build();
	}

	/**
	 * Determines whether a response indicates a redirection and if it does, whether to follow this redirection.
	 * PROPFIND and REPORT must handle redirections explicitely because multi-status processing requires knowledge of the content location.
	 * @return true for 3xx responses on OPTIONS, GET, PUT, DELETE requests that have a valid Location header; false otherwise
	 */
	@Override
	public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
		if (response.getStatusLine().getStatusCode()/100 == 3) {
			boolean redirectable = false;
			for (String method : REDIRECTABLE_METHODS)
				if (method.equalsIgnoreCase(request.getRequestLine().getMethod())) {
					redirectable = true;
					break;
				}
			return redirectable && getLocation(request, response, context) != null;
		}
		return false;
	}

	/**
	 * Gets the destination of a redirection 
	 * @return absolute URL of new location; null if not available
	 */
	static URI getLocation(HttpRequest request, HttpResponse response, HttpContext context) {
		Header locationHdr = response.getFirstHeader("Location");
		if (locationHdr == null) {
			Log.e(TAG, "Received redirection without Location header, ignoring");
			return null;
		}
		try {
			URI location = URIUtils.parseURI(locationHdr.getValue(), false);
			
			// some servers don't return absolute URLs as required by RFC 2616
			if (!location.isAbsolute()) {
				Log.w(TAG, "Received invalid redirection to relative URL, repairing");
				URI originalURI = URIUtils.parseURI(request.getRequestLine().getUri(), false);
				if (!originalURI.isAbsolute()) {
					final HttpHost target = HttpClientContext.adapt(context).getTargetHost();
					if (target != null)
						originalURI = org.apache.http.client.utils.URIUtilsHC4.rewriteURI(originalURI, target);
					else
						return null;
				}
				return originalURI.resolve(location);
			}
			return location;
		} catch (URISyntaxException e) {
			Log.e(TAG, "Received redirection from/to invalid URI, ignoring", e);
		}
		return null;
	}

}
