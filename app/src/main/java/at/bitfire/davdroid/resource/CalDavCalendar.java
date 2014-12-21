/*******************************************************************************
 * Copyright (c) 2014 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.resource;

import org.apache.http.impl.client.CloseableHttpClient;

import java.net.URISyntaxException;

import at.bitfire.davdroid.webdav.DavMultiget;

public class CalDavCalendar extends RemoteCollection<Event> { 
	//private final static String TAG = "davdroid.CalDavCalendar";
	
	@Override
	protected String memberContentType() {
		return "text/calendar";
	}

	@Override
	protected DavMultiget.Type multiGetType() {
		return DavMultiget.Type.CALENDAR;
	}
	
	@Override
	protected Event newResourceSkeleton(String name, String ETag) {
		return new Event(name, ETag);
	}
	
	
	public CalDavCalendar(CloseableHttpClient httpClient, String baseURL, String user, String password, boolean preemptiveAuth) throws URISyntaxException {
		super(httpClient, baseURL, user, password, preemptiveAuth);
	}
}
