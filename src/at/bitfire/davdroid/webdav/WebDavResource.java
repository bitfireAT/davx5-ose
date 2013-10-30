/*******************************************************************************
 * Copyright (c) 2013 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.webdav;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import lombok.Getter;
import lombok.ToString;

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
import org.apache.http.impl.EnglishReasonPhraseCatalog;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreProtocolPNames;

import android.util.Log;


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
	public enum PutMode {
		ADD_DONT_OVERWRITE,
		UPDATE_DONT_OVERWRITE
	}

	@Getter protected URI location;
	protected Set<String> capabilities = new HashSet<String>(), methods = new HashSet<String>();
	protected HashMap<Property, String> properties = new HashMap<Property, String>();
	@Getter protected InputStream content;

	protected DefaultHttpClient client;
	
	
	public WebDavResource(URI baseURL, String username, String password, boolean preemptive, boolean isCollection) throws URISyntaxException {
		location = baseURL.normalize();
		
		if (isCollection && !location.getPath().endsWith("/"))
			location = new URI(location.getScheme(), location.getSchemeSpecificPart() + "/", null);
		
		client = new DefaultHttpClient();
		client.getCredentialsProvider().setCredentials(new AuthScope(location.getHost(), location.getPort()),
				new UsernamePasswordCredentials(username, password));
		
		// preemptive auth is available for Basic auth only
		if (preemptive) {
			Log.i(TAG, "Using preemptive Basic Authentication");
			client.addRequestInterceptor(new PreemptiveAuthInterceptor(), 0);
		}
		
		client.getParams().setParameter(CoreProtocolPNames.USER_AGENT, "DAVdroid");
		GzipDecompressingEntity.enable(client);
	}

	protected WebDavResource(WebDavCollection parent, URI uri) {
		location = uri;
		client = parent.client;
	}
	
	public WebDavResource(WebDavCollection parent, String member) {
		location = parent.location.resolve(member);
		client = parent.client;
	}
	
	public WebDavResource(WebDavCollection parent, String member, String ETag) {
		location = parent.location.resolve(member);
		properties.put(Property.ETAG, ETag);
		client = parent.client;
	}
	
	protected void checkResponse(HttpResponse response) throws HttpException {
		checkResponse(response.getStatusLine());
	}
	
	protected void checkResponse(StatusLine statusLine) throws HttpException {
		int code = statusLine.getStatusCode();
		
		if (code/100 == 1 || code/100 == 2)
			return;
		
		// handle known codes
		EnglishReasonPhraseCatalog catalog = EnglishReasonPhraseCatalog.INSTANCE;
		String reason = catalog.getReason(code, null);
		
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
	
	
	/* resource operations */
	
	public boolean get() throws IOException, HttpException {
		HttpGet get = new HttpGet(location);
		HttpResponse response = client.execute(get);
		checkResponse(response);
		
		content = response.getEntity().getContent();
		return true;
	}
	
	public void put(byte[] data, PutMode mode) throws IOException, HttpException {
		HttpPut put = new HttpPut(location);
		put.setEntity(new ByteArrayEntity(data));

		switch (mode) {
		case ADD_DONT_OVERWRITE:
			put.addHeader("If-None-Match", "*");
			break;
		case UPDATE_DONT_OVERWRITE:
			put.addHeader("If-Match", getETag());
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
	
}
