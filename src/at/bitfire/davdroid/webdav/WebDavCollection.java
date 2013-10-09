/*******************************************************************************
 * Copyright (c) 2013 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.webdav;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import lombok.Getter;

import org.apache.commons.io.input.TeeInputStream;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.message.BasicLineParser;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import android.util.Log;
import at.bitfire.davdroid.resource.IncapableResourceException;

public class WebDavCollection extends WebDavResource {
	private static final String TAG = "davdroid.WebDavCollection";
	
	public enum MultigetType {
		ADDRESS_BOOK,
		CALENDAR
	}
	
	/* list of resource members, null until filled by propfind() or multiGet() */
	@Getter protected List<WebDavResource> members;

	
	public WebDavCollection(URI baseURL, String username, String password) throws IOException {
		super(baseURL, username, password);
	}
	
	public WebDavCollection(WebDavCollection parent, URI member) {
		super(parent, member);
	}

	public WebDavCollection(WebDavCollection parent, String member) {
		super(parent, member);
	}


	/* collection operations */


	public boolean propfind(HttpPropfind.Mode mode) throws IOException, IncapableResourceException, HttpException {
		HttpPropfind propfind = new HttpPropfind(location, mode);
		HttpResponse response = client.execute(propfind);
		checkResponse(response);
		
		if (response.getStatusLine().getStatusCode() == HttpStatus.SC_MULTI_STATUS) {
			DavMultistatus multistatus;
			try {
				Serializer serializer = new Persister();
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				InputStream is = new TeeInputStream(response.getEntity().getContent(), baos);
				multistatus = serializer.read(DavMultistatus.class, is);
				
				Log.d(TAG, "Received multistatus response: " + baos.toString("UTF-8"));
			} catch (Exception e) {
				Log.w(TAG, e);
				throw new IncapableResourceException();
			}
			processMultiStatus(multistatus);
			return true;
			
		} else
			return false;
	}
	
	public boolean multiGet(String[] names, MultigetType type) throws IOException, IncapableResourceException, HttpException {
		DavMultiget multiget = (type == MultigetType.ADDRESS_BOOK) ? new DavAddressbookMultiget() : new DavCalendarMultiget(); 
			
		multiget.prop = new DavProp();
		multiget.prop.getetag = new DavProp.DavPropGetETag();
		
		if (type == MultigetType.ADDRESS_BOOK)
			multiget.prop.addressData = new DavProp.DavPropAddressData();
		else if (type == MultigetType.CALENDAR)
			multiget.prop.calendarData = new DavProp.DavPropCalendarData();
		
		multiget.hrefs = new ArrayList<DavHref>(names.length);
		for (String name : names)
			multiget.hrefs.add(new DavHref(location.resolve(name).getPath()));
		
		Serializer serializer = new Persister();
		StringWriter writer = new StringWriter();
		try {
			serializer.write(multiget, writer);
		} catch (Exception e) {
			Log.e(TAG, e.getLocalizedMessage());
			return false;
		}

		HttpReport report = new HttpReport(location, writer.toString());
		HttpResponse response = client.execute(report);
		checkResponse(response);
		
		if (response.getStatusLine().getStatusCode() == HttpStatus.SC_MULTI_STATUS) {
			DavMultistatus multistatus;
			try {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				InputStream is = new TeeInputStream(response.getEntity().getContent(), baos);
				multistatus = serializer.read(DavMultistatus.class, is);
				
				Log.d(TAG, "Received multistatus response: " + baos.toString("UTF-8"));
			} catch (Exception e) {
				Log.e(TAG, e.getLocalizedMessage());
				return false;
			}
			processMultiStatus(multistatus);
			
		} else
			throw new IncapableResourceException();
		return true;
	}
	
	
	/* member operations */
	
	@Override
	public void put(byte[] data, PutMode mode) throws IOException, HttpException {
		properties.remove(Property.CTAG);
		super.put(data, mode);
	}

	@Override
	public void delete() throws IOException, HttpException {
		properties.remove(Property.CTAG);
		super.delete();
	}

	
	/* HTTP support */
	
	protected void processMultiStatus(DavMultistatus multistatus) throws HttpException {
		List<WebDavResource> members = new LinkedList<WebDavResource>();
		
		if (multistatus.response == null)	// empty response
			return;
		
		for (DavResponse singleResponse : multistatus.response) {
			String href = singleResponse.getHref().href;
			
			for (DavPropstat singlePropstat : singleResponse.getPropstat()) {
				StatusLine status = BasicLineParser.parseStatusLine(singlePropstat.status, new BasicLineParser());
				
				try {
					checkResponse(status);
				} catch(NotFoundException e) {
					continue;
				}
				
				WebDavResource referenced = null;
			
				if (location.equals(location.resolve(href))) {
					// response is about this property
					referenced = this;
					
				} else {
					// response is about a member, add it
					URI uri = location.resolve(href);
					referenced = new WebDavResource(this, uri);
					members.add(referenced);
				}
				
				DavProp prop = singlePropstat.prop;

				if (prop.currentUserPrincipal != null)
					referenced.properties.put(Property.CURRENT_USER_PRINCIPAL, prop.currentUserPrincipal.getHref().href);
				
				if (prop.addressbookHomeSet != null)
					referenced.properties.put(Property.ADDRESSBOOK_HOMESET, prop.addressbookHomeSet.getHref().href);
				
				if (singlePropstat.prop.calendarHomeSet != null)
					referenced.properties.put(Property.CALENDAR_HOMESET, prop.calendarHomeSet.getHref().href);
				
				if (prop.displayname != null)
					referenced.properties.put(Property.DISPLAY_NAME, prop.displayname.getDisplayName());
				
				if (prop.resourcetype != null) {
					if (prop.resourcetype.getAddressbook() != null) {
						referenced.properties.put(Property.IS_ADDRESSBOOK, "1");
						
						if (prop.addressbookDescription != null)
							referenced.properties.put(Property.DESCRIPTION, prop.addressbookDescription.getDescription());
					} else
						referenced.properties.remove(Property.IS_ADDRESSBOOK);
					
					if (prop.resourcetype.getCalendar() != null) {
						referenced.properties.put(Property.IS_CALENDAR, "1");
						
						if (prop.calendarDescription != null)
							referenced.properties.put(Property.DESCRIPTION, prop.calendarDescription.getDescription());
					} else
						referenced.properties.remove(Property.IS_CALENDAR);
				}
				
				if (prop.getctag != null)
					referenced.properties.put(Property.CTAG, prop.getctag.getCTag());

				if (prop.getetag != null)
					referenced.properties.put(Property.ETAG, prop.getetag.getETag());
				
				if (prop.calendarData != null)
					referenced.content = new ByteArrayInputStream(prop.calendarData.ical.getBytes());
				else if (prop.addressData != null)
					referenced.content = new ByteArrayInputStream(prop.addressData.vcard.getBytes());
			}
		}
		this.members = members;
	}
}
