/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.webdav;

import android.util.Log;

import org.apache.http.client.methods.HttpEntityEnclosingRequestBaseHC4;
import org.apache.http.entity.StringEntity;

import java.io.UnsupportedEncodingException;
import java.net.URI;

public class HttpReport extends HttpEntityEnclosingRequestBaseHC4 {
    private static final String TAG = "davdroid.HttpEntityEncloseRequestBase";
	
	public final static String METHOD_NAME = "REPORT";
	
	
	HttpReport(URI uri) {
		setURI(uri);
	}

	HttpReport(URI uri, String entity) {
		this(uri);
		
		setHeader("Content-Type", "text/xml; charset=UTF-8");
		setHeader("Accept", "text/xml");
		setHeader("Depth", "1");

        try {
            setEntity(new StringEntity(entity, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            Log.wtf(TAG, "String entity doesn't support UTF-8");
        }
	}

	@Override
	public String getMethod() {
		return METHOD_NAME;
	}
}
