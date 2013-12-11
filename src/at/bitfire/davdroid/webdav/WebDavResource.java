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
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import lombok.Getter;
import lombok.ToString;

import org.apache.commons.io.input.TeeInputStream;
import org.apache.commons.lang.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicLineParser;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import android.util.Log;
import at.bitfire.davdroid.URIUtils;
import at.bitfire.davdroid.resource.Event;
import at.bitfire.davdroid.webdav.DavProp.DavPropComp;


@ToString
public class WebDavResource {
	private static final String TAG = "davdroid.WebDavResource";
	
	public enum Property {
		CURRENT_USER_PRINCIPAL,
		DISPLAY_NAME, DESCRIPTION, COLOR,
		TIMEZONE, SUPPORTED_COMPONENTS,
		ADDRESSBOOK_HOMESET, CALENDAR_HOMESET,
		IS_ADDRESSBOOK, IS_CALENDAR,
		CTAG, ETAG,
		CONTENT_TYPE
	}
	public enum MultigetType {
		ADDRESS_BOOK,
		CALENDAR
	}
	public enum PutMode {
		ADD_DONT_OVERWRITE,
		UPDATE_DONT_OVERWRITE
	}

	// location of this resource
	@Getter protected URI location;
	
	// DAV capabilities (DAV: header) and allowed DAV methods (set for OPTIONS request)
	protected Set<String>	capabilities = new HashSet<String>(),
							methods = new HashSet<String>();
	
	// DAV properties
	protected HashMap<Property, String> properties = new HashMap<Property, String>();
	@Getter protected List<String> supportedComponents;
	
	// list of members (only for collections)
	@Getter protected List<WebDavResource> members;

	// content (available after GET)
	@Getter protected InputStream content;

	protected DefaultHttpClient client;
	
	
	public WebDavResource(URI baseURL, boolean trailingSlash) throws URISyntaxException {
		location = baseURL.normalize();
		
		if (trailingSlash && !location.getRawPath().endsWith("/"))
			location = new URI(location.getScheme(), location.getSchemeSpecificPart() + "/", null);
		
		client = DavHttpClient.getDefault();
	}
	
	public WebDavResource(URI baseURL, String username, String password, boolean preemptive, boolean trailingSlash) throws URISyntaxException {
		this(baseURL, trailingSlash);
		
		// authenticate
		client.getCredentialsProvider().setCredentials(
			new AuthScope(location.getHost(), location.getPort()),
			new UsernamePasswordCredentials(username, password)
		);
		if (preemptive) {
			Log.i(TAG, "Using preemptive authentication (not compatible with Digest auth)");
			client.addRequestInterceptor(new PreemptiveAuthInterceptor(), 0);
		}
	}

	protected WebDavResource(WebDavResource parent, URI uri) {
		location = uri;
		client = parent.client;
	}
	
	public WebDavResource(WebDavResource parent, String member) {
		this(parent, parent.location.resolve(URIUtils.sanitize(member)));
	}
	
	public WebDavResource(WebDavResource parent, String member, boolean trailingSlash) {
		this(parent, (trailingSlash && !member.endsWith("/")) ? (member + "/") : member);
	}
	
	public WebDavResource(WebDavResource parent, String member, String ETag) {
		this(parent, member);
		properties.put(Property.ETAG, ETag);
	}

	
	protected void checkResponse(HttpResponse response) throws HttpException {
		checkResponse(response.getStatusLine());
	}
	
	protected void checkResponse(StatusLine statusLine) throws HttpException {
		int code = statusLine.getStatusCode();
		
		if (code/100 == 1 || code/100 == 2)		// everything OK
			return;
		
		String reason = code + " " + statusLine.getReasonPhrase();
		switch (code) {
		case HttpStatus.SC_UNAUTHORIZED:
			throw new AuthenticationException(reason);
		case HttpStatus.SC_NOT_FOUND:
			throw new NotFoundException(reason);
		case HttpStatus.SC_PRECONDITION_FAILED:
			throw new PreconditionFailedException(reason);
		default:
			throw new HttpException(reason);
		}
	}
	

	/* feature detection */

	public void options() throws IOException, HttpException {
		HttpOptions options = new HttpOptions(location);
		HttpResponse response = client.execute(options);
		checkResponse(response);

		Header[] allowHeaders = response.getHeaders("Allow");
		for (Header allowHeader : allowHeaders)
			methods.addAll(Arrays.asList(allowHeader.getValue().split(", ?")));

		Header[] capHeaders = response.getHeaders("DAV");
		for (Header capHeader : capHeaders)
			capabilities.addAll(Arrays.asList(capHeader.getValue().split(", ?")));
	}

	public boolean supportsDAV(String capability) {
		return capabilities.contains(capability);
	}

	public boolean supportsMethod(String method) {
		return methods.contains(method);
	}
	
	
	/* file hierarchy methods */
	
	public String getName() {
		String[] names = StringUtils.split(location.getRawPath(), "/");
		return names[names.length - 1];
	}
	
	
	/* property methods */
	
	public String getCurrentUserPrincipal() {
		return properties.get(Property.CURRENT_USER_PRINCIPAL);
	}
	
	public String getDisplayName() {
		return properties.get(Property.DISPLAY_NAME);
	}
	
	public String getDescription() {
		return properties.get(Property.DESCRIPTION);
	}
	
	public String getColor() {
		return properties.get(Property.COLOR);
	}
	
	public String getTimezone() {
		return properties.get(Property.TIMEZONE);
	}
	
	public String getAddressbookHomeSet() {
		return properties.get(Property.ADDRESSBOOK_HOMESET);
	}
	
	public String getCalendarHomeSet() {
		return properties.get(Property.CALENDAR_HOMESET);
	}

	public String getCTag() {
		return properties.get(Property.CTAG);
	}
	public void invalidateCTag() {
		properties.remove(Property.CTAG);
	}
	
	public String getETag() {
		return properties.get(Property.ETAG);
	}
	
	public String getContentType() {
		return properties.get(Property.CONTENT_TYPE);
	}
	
	public void setContentType(String mimeType) {
		properties.put(Property.CONTENT_TYPE, mimeType);
	}
	
	public boolean isAddressBook() {
		return properties.containsKey(Property.IS_ADDRESSBOOK);
	}
	
	public boolean isCalendar() {
		return properties.containsKey(Property.IS_CALENDAR);
	}
	
	
	/* collection operations */
	
	public void propfind(HttpPropfind.Mode mode) throws IOException, InvalidDavResponseException, HttpException {
		HttpPropfind propfind = new HttpPropfind(location, mode);
		HttpResponse response = client.execute(propfind);
		checkResponse(response);

		if (response.getStatusLine().getStatusCode() == HttpStatus.SC_MULTI_STATUS) {
			InputStream content = response.getEntity().getContent();
			if (content == null)
				throw new InvalidDavResponseException("Multistatus response without content");

			// duplicate content for logging
			ByteArrayOutputStream logStream = new ByteArrayOutputStream();
			InputStream is = new TeeInputStream(content, logStream);

			DavMultistatus multistatus;
			try {
				Serializer serializer = new Persister();
				multistatus = serializer.read(DavMultistatus.class, is, false);
			} catch (Exception ex) {
				Log.w(TAG, "Invalid PROPFIND XML response", ex);
				throw new InvalidDavResponseException("Invalid PROPFIND response");
			} finally {
				Log.d(TAG, "Received multistatus response:\n" + logStream.toString("UTF-8"));
				is.close();
				content.close();
			}
			processMultiStatus(multistatus);
			
		} else
			throw new InvalidDavResponseException("Multistatus response expected");
	}

	public void multiGet(String[] names, MultigetType type) throws IOException, InvalidDavResponseException, HttpException {
		DavMultiget multiget = (type == MultigetType.ADDRESS_BOOK) ? new DavAddressbookMultiget() : new DavCalendarMultiget(); 
			
		multiget.prop = new DavProp();
		multiget.prop.getetag = new DavProp.DavPropGetETag();
		
		if (type == MultigetType.ADDRESS_BOOK)
			multiget.prop.addressData = new DavProp.DavPropAddressData();
		else if (type == MultigetType.CALENDAR)
			multiget.prop.calendarData = new DavProp.DavPropCalendarData();
		
		multiget.hrefs = new ArrayList<DavHref>(names.length);
		for (String name : names)
			multiget.hrefs.add(new DavHref(location.resolve(name).getRawPath()));
		
		Serializer serializer = new Persister();
		StringWriter writer = new StringWriter();
		try {
			serializer.write(multiget, writer);
		} catch (Exception ex) {
			Log.e(TAG, "Couldn't create XML multi-get request", ex);
			throw new InvalidDavResponseException("Couldn't create multi-get request");
		}

		HttpReport report = new HttpReport(location, writer.toString());
		HttpResponse response = client.execute(report);
		checkResponse(response);
		
		if (response.getStatusLine().getStatusCode() == HttpStatus.SC_MULTI_STATUS) {
			InputStream content = response.getEntity().getContent();
			if (content == null)
				throw new InvalidDavResponseException("Multistatus response without content");
			
			DavMultistatus multistatus;
			
			// duplicate content for logging
			ByteArrayOutputStream logStream = new ByteArrayOutputStream();
			InputStream is = new TeeInputStream(content, logStream, true);
			
			try {
				multistatus = serializer.read(DavMultistatus.class, is, false);
			} catch (Exception ex) {
				Log.e(TAG, "Couldn't parse multi-get response", ex);
				throw new InvalidDavResponseException("Invalid multi-get response");
			} finally {
				Log.d(TAG, "Received multistatus response:\n" + logStream.toString("UTF-8"));
				is.close();
				content.close();
			}
			processMultiStatus(multistatus);
			
		} else
			throw new InvalidDavResponseException("Multistatus response expected");
	}

	
	/* resource operations */
	
	public void get() throws IOException, HttpException {
		HttpGet get = new HttpGet(location);
		HttpResponse response = client.execute(get);
		checkResponse(response);
		
		content = response.getEntity().getContent();
	}
	
	public void put(byte[] data, PutMode mode) throws IOException, HttpException {
		HttpPut put = new HttpPut(location);
		Log.d(TAG, "Sending PUT request: " + new String(data, "UTF-8"));
		put.setEntity(new ByteArrayEntity(data));

		switch (mode) {
		case ADD_DONT_OVERWRITE:
			put.addHeader("If-None-Match", "*");
			break;
		case UPDATE_DONT_OVERWRITE:
			put.addHeader("If-Match", (getETag() != null) ? getETag() : "*");
			break;
		}
		
		if (getContentType() != null)
			put.addHeader("Content-Type", getContentType());

		checkResponse(client.execute(put));
	}
	
	public void delete() throws IOException, HttpException {
		HttpDelete delete = new HttpDelete(location);
		
		if (getETag() != null)
			delete.addHeader("If-Match", getETag());
		
		checkResponse(client.execute(delete));
	}
	

	/* helpers */
	
	protected void processMultiStatus(DavMultistatus multistatus) throws HttpException {
		if (multistatus.response == null)	// empty response
			return;
		
		// member list will be built from response
		List<WebDavResource> members = new LinkedList<WebDavResource>();
		
		for (DavResponse singleResponse : multistatus.response) {
			URI href;
			try {
				href = location.resolve(URIUtils.sanitize(singleResponse.getHref().href));
			} catch(IllegalArgumentException ex) {
				Log.w(TAG, "Ignoring illegal member URI in multi-status response", ex);
				continue;
			}
			Log.d(TAG, "Processing multi-status element: " + href);
			
			// about which resource is this response?
			WebDavResource referenced = null;
			if (location.equals(href)) {	// -> ourselves
				referenced = this;
				
			} else {						// -> about a member
				referenced = new WebDavResource(this, href);
				members.add(referenced);
			}
			
			for (DavPropstat singlePropstat : singleResponse.getPropstat()) {
				StatusLine status = BasicLineParser.parseStatusLine(singlePropstat.status, new BasicLineParser());
				
				// ignore information about missing properties etc.
				if (status.getStatusCode()/100 != 1 && status.getStatusCode()/100 != 2)
					continue;
				
				DavProp prop = singlePropstat.prop;
				HashMap<Property, String> properties = referenced.properties;

				if (prop.currentUserPrincipal != null && prop.currentUserPrincipal.getHref() != null)
					properties.put(Property.CURRENT_USER_PRINCIPAL, prop.currentUserPrincipal.getHref().href);
				
				if (prop.addressbookHomeSet != null && prop.addressbookHomeSet.getHref() != null)
					properties.put(Property.ADDRESSBOOK_HOMESET, prop.addressbookHomeSet.getHref().href);
				
				if (singlePropstat.prop.calendarHomeSet != null && prop.calendarHomeSet.getHref() != null)
					properties.put(Property.CALENDAR_HOMESET, prop.calendarHomeSet.getHref().href);
				
				if (prop.displayname != null)
					properties.put(Property.DISPLAY_NAME, prop.displayname.getDisplayName());
				
				if (prop.resourcetype != null) {
					if (prop.resourcetype.getAddressbook() != null) {
						properties.put(Property.IS_ADDRESSBOOK, "1");
						
						if (prop.addressbookDescription != null)
							properties.put(Property.DESCRIPTION, prop.addressbookDescription.getDescription());
					}
					if (prop.resourcetype.getCalendar() != null) {
						properties.put(Property.IS_CALENDAR, "1");
						
						if (prop.calendarDescription != null)
							properties.put(Property.DESCRIPTION, prop.calendarDescription.getDescription());
						
						if (prop.calendarColor != null)
							properties.put(Property.COLOR, prop.calendarColor.getColor());
						
						if (prop.calendarTimezone != null)
							properties.put(Property.TIMEZONE, Event.TimezoneDefToTzId(prop.calendarTimezone.getTimezone()));
						
						if (prop.supportedCalendarComponentSet != null && prop.supportedCalendarComponentSet.components != null) {
							referenced.supportedComponents = new LinkedList<String>();
							for (DavPropComp component : prop.supportedCalendarComponentSet.components)
								referenced.supportedComponents.add(component.getName());
						}
					}
				}
				
				if (prop.getctag != null)
					properties.put(Property.CTAG, prop.getctag.getCTag());

				if (prop.getetag != null)
					properties.put(Property.ETAG, prop.getetag.getETag());
				
				if (prop.calendarData != null && prop.calendarData.ical != null)
					referenced.content = new ByteArrayInputStream(prop.calendarData.ical.getBytes());
				else if (prop.addressData != null && prop.addressData.vcard != null)
					referenced.content = new ByteArrayInputStream(prop.addressData.vcard.getBytes());
			}
		}
		
		this.members = members;
	}
}
