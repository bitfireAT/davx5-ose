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

import org.apache.http.HttpException;

import at.bitfire.davdroid.webdav.WebDavCollection;
import at.bitfire.davdroid.webdav.WebDavCollection.MultigetType;
import at.bitfire.davdroid.webdav.WebDavResource;

public class CardDavAddressBook extends RemoteCollection {
	//private final static String TAG = "davdroid.CardDavAddressBook"; 
	
	public CardDavAddressBook(String baseURL, String user, String password) throws IOException {
		try {
			collection = new WebDavCollection(new URI(baseURL), user, password);
		} catch (URISyntaxException e) {
			throw new IOException();
		}
	}
	
	@Override
	protected String memberContentType() {
		return "text/vcard";
	}
	
	@Override
	public Contact[] getMemberETags() throws IOException, IncapableResourceException, HttpException {
		super.getMemberETags();
		
		LinkedList<Contact> resources = new LinkedList<Contact>();
		for (WebDavResource member : collection.getMembers()) {
			Contact c = new Contact(member.getName(), member.getETag());
			resources.add(c);
		}
		
		return resources.toArray(new Contact[0]);
	}

	@Override
	public Contact[] multiGet(Resource[] resources) throws IOException, IncapableResourceException, HttpException {
		if (resources.length == 1) {
			Resource resource = get(resources[0]);
			if (resource != null)
				return new Contact[] { (Contact)resource };
			else
				return null;
		}
		
		LinkedList<String> names = new LinkedList<String>();
		for (Resource c : resources)
			names.add(c.getName());
		
		collection.multiGet(names.toArray(new String[0]), MultigetType.ADDRESS_BOOK);
		
		LinkedList<Contact> foundContacts = new LinkedList<Contact>();
		for (WebDavResource member : collection.getMembers()) {
			Contact c = new Contact(member.getName(), member.getETag());
			c.parseEntity(member.getContent());
			foundContacts.add(c);
		}
		return foundContacts.toArray(new Contact[0]);
	}

}
