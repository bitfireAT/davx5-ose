/*******************************************************************************
 * Copyright (c) 2013 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.resource;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;

import net.fortuna.ical4j.data.ParserException;

import org.apache.http.HttpException;

import at.bitfire.davdroid.webdav.WebDavCollection;
import at.bitfire.davdroid.webdav.WebDavCollection.MultigetType;
import at.bitfire.davdroid.webdav.WebDavResource;

public class CalDavCalendar extends RemoteCollection {
	//private final static String TAG = "davdroid.CalDavCalendar";
	
	public CalDavCalendar(String baseURL, String user, String password) throws IOException {
		try {
			collection = new WebDavCollection(new URI(baseURL), user, password);
		} catch (URISyntaxException e) {
			throw new IOException();
		}
	}
	
	
	@Override
	protected String memberContentType() {
		return "text/calendar";
	}
	

	@Override
	public Event[] getMemberETags() throws IOException, IncapableResourceException, HttpException {
		super.getMemberETags();
		
		LinkedList<Event> resources = new LinkedList<Event>();
		for (WebDavResource member : collection.getMembers()) {
			Event e = new Event(member.getName(), member.getETag());
			resources.add(e);
		}
		return resources.toArray(new Event[0]);
	}

	@Override
	public Event[] multiGet(Resource[] resources) throws IOException, IncapableResourceException, HttpException, ParserException {
		if (resources.length == 1)
			return new Event[] { (Event)get(resources[0]) };
		
		LinkedList<String> names = new LinkedList<String>();
		for (Resource c : resources)
			names.add(c.getName());
		
		collection.multiGet(names.toArray(new String[0]), MultigetType.CALENDAR);
		
		LinkedList<Event> foundEvents = new LinkedList<Event>();
		for (WebDavResource member : collection.getMembers()) {
			Event e = new Event(member.getName(), member.getETag());
			e.parseEntity(member.getContent());
			foundEvents.add(e);
		}
		return foundEvents.toArray(new Event[0]);
	}

}
