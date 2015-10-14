/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.resource;

import com.squareup.okhttp.HttpUrl;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.LinkedList;
import java.util.List;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(suppressConstructorProperties=true)
@Data
public class ServerInfo implements Serializable {
	final private URI baseURI;
	final private String userName, password;
	final boolean authPreemptive;
	
	private String errorMessage;
	
	private List<ResourceInfo>
		addressBooks = new LinkedList<>(),
		calendars  = new LinkedList<>(),
		taskLists = new LinkedList<>();
	
	
	public boolean hasEnabledCalendars() {
		for (ResourceInfo calendar : calendars)
			if (calendar.enabled)
				return true;
		return false;
	}


	@RequiredArgsConstructor(suppressConstructorProperties=true)
	@Data
	public static class ResourceInfo implements Serializable {
		public enum Type {
			ADDRESS_BOOK,
			CALENDAR
		}
		
		boolean enabled = false;
		
		final Type type;
		final boolean readOnly;

		final String URL,       // absolute URL of resource
			  title,
			  description;
		final Integer color;

        /** full VTIMEZONE definition (not the TZ ID) */
		String timezone;


		// copy constructor
		public ResourceInfo(ResourceInfo src) {
			enabled = src.enabled;
			type = src.type;
			readOnly = src.readOnly;

			URL = src.URL;
			title = src.title;
			description = src.description;
			color = src.color;

			timezone = src.timezone;
		}


		// some logic

		public String getTitle() {
			if (title == null) {
                HttpUrl url = HttpUrl.parse(URL);
                return url != null ? url.toString() : "–";
            } else
				return title;
		}
	}
}
