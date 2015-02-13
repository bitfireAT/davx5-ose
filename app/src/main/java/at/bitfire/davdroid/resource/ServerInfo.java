/*******************************************************************************
 * Copyright (c) 2014 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.resource;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.LinkedList;
import java.util.List;

import ezvcard.VCardVersion;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(suppressConstructorProperties=true)
@Data
public class ServerInfo implements Serializable {
	private static final long serialVersionUID = 6744847358282980437L;
	
	enum Scheme {
		HTTP, HTTPS, MAILTO
	}
	
	final private URI baseURI;
	final private String userName, password;
	final boolean authPreemptive;
	
	private String errorMessage;
	
	private boolean calDAV = false, cardDAV = false;
	private List<ResourceInfo>
		addressBooks = new LinkedList<ResourceInfo>(),
		calendars  = new LinkedList<ResourceInfo>();
	
	
	public boolean hasEnabledCalendars() {
		for (ResourceInfo calendar : calendars)
			if (calendar.enabled)
				return true;
		return false;
	}
	
	
	@RequiredArgsConstructor(suppressConstructorProperties=true)
	@Data
	public static class ResourceInfo implements Serializable {
		private static final long serialVersionUID = -5516934508229552112L;
		
		public enum Type {
			ADDRESS_BOOK,
			CALENDAR
		}
		
		boolean enabled = false;
		
		final Type type;
		final boolean readOnly;

		final String URL,       // absolute URL of resource
			  title,
			  description,
			  color;

		VCardVersion vCardVersion;

		String timezone;


		public String getTitle() {
			if (title == null) {
				try {
					java.net.URL url = new java.net.URL(URL);
					return url.getPath();
				} catch (MalformedURLException e) {
					return URL;
				}
			} else
				return title;
		}
	}
}
