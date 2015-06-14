/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
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
	 * @param original	    URI to be parsed, may be absolute or relative
     * @param mustBePath    true if it's known that original is a path (may contain ":") and not an URI, i.e. ":" is not the scheme separator
	 * @return			    normalized URI
	 * @throws URISyntaxException
	 */
	public static URI parseURI(String original, boolean mustBePath) throws URISyntaxException {
        if (mustBePath) {
            // may contain ":"
            // case 1: "my:file"        won't be parsed by URI correctly because it would consider "my" as URI scheme
            // case 2: "path/my:file"   will be parsed by URI correctly
            // case 3: "my:path/file"   won't be parsed by URI correctly because it would consider "my" as URI scheme
            int idxSlash = original.indexOf('/'),
                idxColon = original.indexOf(':');
            if (idxColon != -1) {
                // colon present
                if ((idxSlash != -1) && idxSlash < idxColon)     // There's a slash, and it's before the colon → everything OK
                    ;
                else    // No slash before the colon; we have to put it there
                    original = "./" + original;
            }
        }

        // escape some common invalid characters – servers keep sending unescaped crap like "my calendar.ics" or "{guid}.vcf"
        // this is only a hack, because for instance, "[" may be valid in URLs (IPv6 literal in host name)
        String repaired = original
                .replaceAll(" ", "%20")
                .replaceAll("\\{", "%7B")
                .replaceAll("\\}", "%7D");
        if (!repaired.equals(original))
            Log.w(TAG, "Repaired invalid URL: " + original + " -> " + repaired);

		URI uri = new URI(repaired);
		URI normalized = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), uri.getQuery(), uri.getFragment());
		Log.v(TAG, "Normalized URL " + original + " -> " + normalized.toASCIIString());
		return normalized;
	}

}
