/*******************************************************************************
 * Copyright (c) 2014 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Richard Hirner (bitfire web engineering) - initial API and implementation
 ******************************************************************************/
package at.bitfire.davdroid.resource;

import java.net.URISyntaxException;

import at.bitfire.davdroid.webdav.DavMultiget;

public class CardDavAddressBook extends RemoteCollection<Contact> {
	//private final static String TAG = "davdroid.CardDavAddressBook"; 
	
	@Override
	protected String memberContentType() {
		return "text/vcard";
	}
	
	@Override
	protected DavMultiget.Type multiGetType() {
		return DavMultiget.Type.ADDRESS_BOOK;
	}

	@Override
	protected Contact newResourceSkeleton(String name, String ETag) {
		return new Contact(name, ETag);
	}
	

	public CardDavAddressBook(String baseURL, String user, String password, boolean preemptiveAuth) throws URISyntaxException {
		super(baseURL, user, password, preemptiveAuth);
	}
}
