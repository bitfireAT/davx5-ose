/*******************************************************************************
 * Copyright (c) 2014 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.webdav;

import android.util.Log;

import org.apache.http.client.methods.HttpEntityEnclosingRequestBaseHC4;
import org.apache.http.entity.StringEntity;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import java.io.StringWriter;
import java.net.URI;
import java.util.LinkedList;

public class HttpPropfind extends HttpEntityEnclosingRequestBaseHC4 {
	private static final String TAG = "davdroid.HttpPropfind";
	
	public final static String METHOD_NAME = "PROPFIND";
	
	public enum Mode {
		CURRENT_USER_PRINCIPAL,
		HOME_SETS,
		CARDDAV_COLLECTIONS,
		CALDAV_COLLECTIONS,
		COLLECTION_CTAG,
		MEMBERS_ETAG
	}

	
	HttpPropfind(URI uri) {
		setURI(uri);
	}

	HttpPropfind(URI uri, Mode mode) {
		this(uri);

		DavPropfind propfind = new DavPropfind();
		propfind.prop = new DavProp();
		
		int depth = 0;
		switch (mode) {
		case CURRENT_USER_PRINCIPAL:
			propfind.prop.currentUserPrincipal = new DavProp.CurrentUserPrincipal();
			break;
		case HOME_SETS:
			propfind.prop.addressbookHomeSet = new DavProp.AddressbookHomeSet();
			propfind.prop.calendarHomeSet = new DavProp.CalendarHomeSet();
			break;
		case CARDDAV_COLLECTIONS:
			depth = 1;
			propfind.prop.displayname = new DavProp.DisplayName();
			propfind.prop.resourcetype = new DavProp.ResourceType();
			propfind.prop.currentUserPrivilegeSet = new LinkedList<DavProp.Privilege>();
			propfind.prop.addressbookDescription = new DavProp.AddressbookDescription();
			propfind.prop.supportedAddressData = new LinkedList<DavProp.AddressDataType>();
			break;
		case CALDAV_COLLECTIONS:
			depth = 1;
			propfind.prop.displayname = new DavProp.DisplayName();
			propfind.prop.resourcetype = new DavProp.ResourceType();
			propfind.prop.currentUserPrivilegeSet = new LinkedList<DavProp.Privilege>();
			propfind.prop.calendarDescription = new DavProp.CalendarDescription();
			propfind.prop.calendarColor = new DavProp.CalendarColor();
			propfind.prop.calendarTimezone = new DavProp.CalendarTimezone();
			propfind.prop.supportedCalendarComponentSet = new LinkedList<DavProp.Comp>();
			break;
		case COLLECTION_CTAG:
			propfind.prop.getctag = new DavProp.GetCTag(); 
			break;
		case MEMBERS_ETAG:
			depth = 1;
			propfind.prop.getctag = new DavProp.GetCTag();
			propfind.prop.getetag = new DavProp.GetETag();
			break;
		}
		
		try {
			Serializer serializer = new Persister();
			StringWriter writer = new StringWriter();
			serializer.write(propfind, writer);
		
			setHeader("Content-Type", "text/xml; charset=UTF-8");
			setHeader("Accept", "text/xml");
			setHeader("Depth", String.valueOf(depth));
			setEntity(new StringEntity(writer.toString(), "UTF-8"));
		} catch(Exception ex) {
			Log.e(TAG, "Couldn't prepare PROPFIND request for " + uri, ex);
			abort();
		}
	}

	@Override
	public String getMethod() {
		return METHOD_NAME;
	}
}
