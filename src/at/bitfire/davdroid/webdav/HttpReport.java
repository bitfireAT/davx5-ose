/*******************************************************************************
 * Copyright (c) 2014 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Richard Hirner (bitfire web engineering) - initial API and implementation
 ******************************************************************************/
package at.bitfire.davdroid.webdav;


import java.io.UnsupportedEncodingException;
import java.net.URI;

import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.entity.StringEntity;

import android.util.Log;

public class HttpReport extends HttpEntityEnclosingRequestBase {
	private static final String TAG = "DavHttpReport";

	HttpReport(URI uri, String entity) {
		setURI(uri);
		
		setHeader("Content-Type", "text/xml; charset=UTF-8");
		setHeader("Depth", "0");
		
		try {
			setEntity(new StringEntity(entity, "UTF-8"));
			
			Log.d(TAG, "Prepared REPORT request for " + uri + ": " + entity);
		} catch (UnsupportedEncodingException e) {
			Log.e(TAG, e.getMessage());
		}
	}

	@Override
	public String getMethod() {
		return "REPORT";
	}
}
