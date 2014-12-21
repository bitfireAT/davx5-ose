/*******************************************************************************
 * Copyright (c) 2014 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid;

import android.util.Log;

import java.net.URI;
import java.net.URISyntaxException;

public class URIUtils {
	private static final String TAG = "davdroid.URIUtils";

	
	public static String ensureTrailingSlash(String href) {
		if (!href.endsWith("/")) {
			Log.d(TAG, "Implicitly appending trailing slash to collection " + href);
			return href + "/";
		} else
			return href;
	}
	
	public static URI ensureTrailingSlash(URI href) {
		if (!href.getPath().endsWith("/")) {
			try {
				URI newURI = new URI(href.getScheme(), href.getAuthority(), href.getPath() + "/", null, null);
				Log.d(TAG, "Appended trailing slash to collection " + href + " -> " + newURI);
				href = newURI;
			} catch (URISyntaxException e) {
			}
		}
		return href;
	}


	/**
	 * Parse a received absolute/relative URL and generate a normalized URI that can be compared.
	 * @param original	URI to be parsed, may be absolute or relative 
	 * @return			normalized URI
	 * @throws URISyntaxException
	 */
	public static URI parseURI(String original) throws URISyntaxException {
		URI raw = URI.create(original);
		URI uri = new URI(raw.getScheme(), raw.getAuthority(), raw.getPath(), raw.getQuery(), raw.getFragment());
		Log.v(TAG, "Normalized URL " + original + " -> " + uri.toASCIIString());
		return uri;
	}

}
