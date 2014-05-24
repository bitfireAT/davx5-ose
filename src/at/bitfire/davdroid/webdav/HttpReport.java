/*******************************************************************************
 * Copyright (c) 2014 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.webdav;

import java.net.URI;

import ch.boye.httpclientandroidlib.client.methods.HttpEntityEnclosingRequestBase;
import ch.boye.httpclientandroidlib.entity.StringEntity;

public class HttpReport extends HttpEntityEnclosingRequestBase {
	
	public final static String METHOD_NAME = "REPORT";
	
	
	HttpReport(URI uri) {
		setURI(uri);
	}

	HttpReport(URI uri, String entity) {
		this(uri);
		
		setHeader("Content-Type", "text/xml; charset=UTF-8");
		setHeader("Depth", "0");
		
		setEntity(new StringEntity(entity, "UTF-8"));
	}

	@Override
	public String getMethod() {
		return METHOD_NAME;
	}
}
