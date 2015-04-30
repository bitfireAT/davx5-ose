/*
 * Copyright (c) 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.resource;

import android.accounts.Account;

import org.apache.http.impl.client.CloseableHttpClient;

import java.net.URISyntaxException;

import at.bitfire.davdroid.syncadapter.AccountSettings;
import at.bitfire.davdroid.webdav.DavMultiget;
import ezvcard.VCardVersion;

public class CardDavAddressBook extends RemoteCollection<Contact> {
	AccountSettings accountSettings;

	@Override
	protected String memberAcceptedMimeTypes() {
		return "text/vcard;q=0.8, text/vcard;version=4.0";
	}
	
	@Override
	protected DavMultiget.Type multiGetType() {
		return accountSettings.getAddressBookVCardVersion() == VCardVersion.V4_0 ?
				DavMultiget.Type.ADDRESS_BOOK_V4 : DavMultiget.Type.ADDRESS_BOOK;
	}

	@Override
	protected Contact newResourceSkeleton(String name, String ETag) {
		return new Contact(name, ETag);
	}
	

	public CardDavAddressBook(AccountSettings settings, CloseableHttpClient httpClient, String baseURL, String user, String password, boolean preemptiveAuth) throws URISyntaxException {
		super(httpClient, baseURL, user, password, preemptiveAuth);
		accountSettings = settings;
	}
}
