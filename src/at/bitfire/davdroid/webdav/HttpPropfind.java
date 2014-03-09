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

import java.io.StringWriter;
import java.net.URI;
import java.util.LinkedList;

import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.entity.StringEntity;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import android.util.Log;

public class HttpPropfind extends HttpEntityEnclosingRequestBase {
	private static final String TAG = "davdroid.HttpPropfind";
	
	public enum Mode {
		CURRENT_USER_PRINCIPAL,
		HOME_SETS,
		MEMBERS_COLLECTIONS,
		COLLECTION_CTAG,
		MEMBERS_ETAG
	}

	HttpPropfind(URI uri, Mode mode) {
		setURI(uri);
		
		setHeader("Content-Type", "text/xml; charset=\"utf-8\"");

		DavPropfind propfind = new DavPropfind();
		propfind.prop = new DavProp();
		
		int depth = 0;
		switch (mode) {
		case CURRENT_USER_PRINCIPAL:
			propfind.prop.currentUserPrincipal = new DavProp.DavCurrentUserPrincipal();
			break;
		case HOME_SETS:
			propfind.prop.addressbookHomeSet = new DavProp.DavAddressbookHomeSet();
			propfind.prop.calendarHomeSet = new DavProp.DavCalendarHomeSet();
			break;
		case MEMBERS_COLLECTIONS:
			depth = 1;
			propfind.prop.displayname = new DavProp.DavPropDisplayName();
			propfind.prop.resourcetype = new DavProp.DavPropResourceType();
			propfind.prop.currentUserPrivilegeSet = new LinkedList<DavProp.DavPropPrivilege>();
			propfind.prop.addressbookDescription = new DavProp.DavPropAddressbookDescription();
			propfind.prop.calendarDescription = new DavProp.DavPropCalendarDescription();
			propfind.prop.calendarColor = new DavProp.DavPropCalendarColor();
			propfind.prop.calendarTimezone = new DavProp.DavPropCalendarTimezone();
			propfind.prop.supportedCalendarComponentSet = new LinkedList<DavProp.DavPropComp>();
			break;
		case COLLECTION_CTAG:
			propfind.prop.getctag = new DavProp.DavPropGetCTag(); 
			break;
		case MEMBERS_ETAG:
			depth = 1;
			propfind.prop.getctag = new DavProp.DavPropGetCTag();
			propfind.prop.getetag = new DavProp.DavPropGetETag();
			break;
		}
		
		try {
			Serializer serializer = new Persister();
			StringWriter writer = new StringWriter();
			serializer.write(propfind, writer);
		
			setHeader("Depth", String.valueOf(depth));
			setEntity(new StringEntity(writer.toString(), "UTF-8"));
			
			Log.d(TAG, "Prepared PROPFIND request for " + uri + ": " + writer.toString());
		} catch(Exception ex) {
			Log.e(TAG, "Couldn't prepare PROPFIND request for " + uri, ex);
			abort();
		}
	}

	@Override
	public String getMethod() {
		return "PROPFIND";
	}
}
