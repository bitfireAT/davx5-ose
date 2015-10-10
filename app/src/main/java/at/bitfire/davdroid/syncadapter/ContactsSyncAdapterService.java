/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.syncadapter;

import android.accounts.Account;
import android.app.Service;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.ResponseBody;

import org.apache.commons.io.Charsets;

import at.bitfire.dav4android.DavAddressBook;
import at.bitfire.dav4android.DavResource;
import at.bitfire.dav4android.property.SupportedAddressData;
import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.HttpClient;
import at.bitfire.davdroid.resource.LocalAddressBook;
import at.bitfire.davdroid.resource.LocalContact;
import at.bitfire.vcard4android.Contact;

public class ContactsSyncAdapterService extends Service {
	private static ContactsSyncAdapter syncAdapter;

	@Override
	public void onCreate() {
		if (syncAdapter == null)
			syncAdapter = new ContactsSyncAdapter(getApplicationContext());
	}

	@Override
	public void onDestroy() {
		syncAdapter = null;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return syncAdapter.getSyncAdapterBinder();
	}
	

	private static class ContactsSyncAdapter extends AbstractThreadedSyncAdapter {

        public ContactsSyncAdapter(Context context) {
            super(context, false);
        }

        @Override
        public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
            Constants.log.info("Starting sync for authority " + authority);

            AccountSettings settings = new AccountSettings(getContext(), account);
            HttpClient httpClient = new HttpClient(settings.getUserName(), settings.getPassword(), settings.getPreemptiveAuth());

            DavAddressBook dav = new DavAddressBook(httpClient, HttpUrl.parse(settings.getAddressBookURL()));
            try {
                boolean hasVCard4 = false;
                dav.propfind(0, SupportedAddressData.NAME);
                SupportedAddressData supportedAddressData = (SupportedAddressData)dav.properties.get(SupportedAddressData.NAME);
                if (supportedAddressData != null)
                    for (MediaType type : supportedAddressData.types)
                        if ("text/vcard; version=4.0".equalsIgnoreCase(type.toString()))
                            hasVCard4 = true;
                Constants.log.info("Server advertises VCard/4 support: " + hasVCard4);

                LocalAddressBook addressBook = new LocalAddressBook(account, provider);

                dav.queryMemberETags();
                for (DavResource vCard : dav.members) {
                    Constants.log.info("Found remote VCard: " + vCard.location);
                    ResponseBody body = vCard.get("text/vcard;q=0.8, text/vcard;version=4.0");

                    Contact contacts[] = Contact.fromStream(body.byteStream(), body.contentType().charset(Charsets.UTF_8));
                    if (contacts.length == 1) {
                        Contact contact = contacts[0];
                        Constants.log.info(contact.toString());

                        LocalContact localContact = new LocalContact(addressBook, contact);
                        localContact.add();
                    } else
                        Constants.log.error("Received VCard with not exactly one VCARD");
                }

            } catch (Exception e) {
                Log.e("davdroid", "querying member etags", e);
            }

            Constants.log.info("Sync complete for authority " + authority);
        }
    }
}
