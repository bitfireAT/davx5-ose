/*******************************************************************************
 * Copyright (c) 2013 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.resource;

import java.io.IOException;
import java.net.URISyntaxException;

import at.bitfire.davdroid.webdav.WebDavCollection.MultigetType;

public class CalDavCalendar extends RemoteCollection<Event> { 
	//private final static String TAG = "davdroid.CalDavCalendar";
	
	@Override
	protected String memberContentType() {
		return "text/calendar";
	}

	@Override
	protected MultigetType multiGetType() {
		return MultigetType.CALENDAR;
	}
	
	@Override
	protected Event newResourceSkeleton(String name, String ETag) {
		return new Event(name, ETag);
	}
	
	
	public CalDavCalendar(String baseURL, String user, String password, boolean preemptiveAuth) throws IOException, URISyntaxException {
		super(baseURL, user, password, preemptiveAuth);
	}
}
