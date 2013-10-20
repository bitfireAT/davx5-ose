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

public class CardDavAddressBook extends RemoteCollection<Contact> {
	//private final static String TAG = "davdroid.CardDavAddressBook"; 
	
	@Override
	protected String memberContentType() {
		return "text/vcard";
	}
	
	@Override
	protected MultigetType multiGetType() {
		return MultigetType.ADDRESS_BOOK;
	}

	@Override
	protected Contact newResourceSkeleton(String name, String ETag) {
		return new Contact(name, ETag);
	}
	

	public CardDavAddressBook(String baseURL, String user, String password, boolean preemptiveAuth) throws URISyntaxException {
		super(baseURL, user, password, preemptiveAuth);
	}
}
