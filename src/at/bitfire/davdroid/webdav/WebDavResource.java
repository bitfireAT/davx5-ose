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
import org.apache.http.client.params.ClientPNames;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicLineParser;
import org.apache.http.params.CoreProtocolPNames;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import android.util.Log;
import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.URIUtils;


@ToString
public class WebDavResource {
	private static final String TAG = "davdroid.WebDavResource";
	
	public enum Property {
		CURRENT_USER_PRINCIPAL,
		DISPLAY_NAME, DESCRIPTION,
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
	
	// list of members (only for collections)
	@Getter protected List<WebDavResource> members;

	// content (available after GET)
	@Getter protected InputStream content;

	protected DefaultHttpClient client;
	
	
	public WebDavResource(URI baseURL, boolean trailingSlash) throws URISyntaxException {
		location = baseURL.normalize();
		
		if (trailingSlash && !location.getPath().endsWith("/"))
			location = new URI(location.getScheme(), location.getSchemeSpecificPart() + "/", null);
		
		// create new HTTP client
		client = new DefaultHttpClient();
		client.getParams().setParameter(CoreProtocolPNames.USER_AGENT, "DAVdroid/" + Constants.APP_VERSION);
		
		// allow gzip compression
		GzipDecompressingEntity.enable(client);
		
		// redirections
		client.getParams().setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, false);
	}
	
	public WebDavResource(URI baseURL, String username, String password, boolean preemptive, boolean trailingSlash) throws URISyntaxException {
		this(baseURL, trailingSlash);
		
		// authenticate
		client.getCredentialsProvider().setCredentials(new AuthScope(location.getHost(), location.getPort()),
				new UsernamePasswordCredentials(username, password));
		// preemptive auth is available for Basic auth only
		if (preemptive) {
			Log.i(TAG, "Using preemptive Basic Authentication");
			client.addRequestInterceptor(new PreemptiveAuthInterceptor(), 0);
		}
	}

	protected WebDavResource(WebDavResource parent, URI uri) {
		location = uri;
		client = parent.client;
	}
	
	public WebDavResource(WebDavResource parent, String member) {
		location = parent.location.resolve(URIUtils.sanitize(member));
		client = parent.client;
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
		String[] names = StringUtils.split(location.getPath(), "/");
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
	
	public boolean propfind(HttpPropfind.Mode mode) throws IOException, InvalidDavResponseException, HttpException {
		HttpPropfind propfind = new HttpPropfind(location, mode);
		HttpResponse response = client.execute(propfind);
		checkResponse(response);
		
		if (response.getStatusLine().getStatusCode() == HttpStatus.SC_MULTI_STATUS) {
			DavMultistatus multistatus;
			try {
				Serializer serializer = new Persister();
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				InputStream is = new TeeInputStream(response.getEntity().getContent(), baos);
				multistatus = serializer.read(DavMultistatus.class, is, false);
				
				Log.d(TAG, "Received multistatus response: " + baos.toString("UTF-8"));
			} catch (Exception ex) {
				Log.w(TAG, "Invalid PROPFIND XML response", ex);
				throw new InvalidDavResponseException();
			}
			processMultiStatus(multistatus);
			return true;
			
		} else
			return false;
	}

	public boolean multiGet(String[] names, MultigetType type) throws IOException, InvalidDavResponseException, HttpException {
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
				multistatus = serializer.read(DavMultistatus.class, is, false);
				
				Log.d(TAG, "Received multistatus response: " + baos.toString("UTF-8"));
			} catch (Exception e) {
				Log.e(TAG, e.getLocalizedMessage());
				return false;
			}
			processMultiStatus(multistatus);
			
		} else
			throw new InvalidDavResponseException();
		return true;
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
			
			// about which resource is this response?
			WebDavResource referenced = null;
			if (URIUtils.isSame(location, href)) {	// -> ourselves
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

				if (prop.currentUserPrincipal != null && prop.currentUserPrincipal.getHref() != null)
					referenced.properties.put(Property.CURRENT_USER_PRINCIPAL, prop.currentUserPrincipal.getHref().href);
				
				if (prop.addressbookHomeSet != null && prop.addressbookHomeSet.getHref() != null)
					referenced.properties.put(Property.ADDRESSBOOK_HOMESET, prop.addressbookHomeSet.getHref().href);
				
				if (singlePropstat.prop.calendarHomeSet != null && prop.calendarHomeSet.getHref() != null)
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
