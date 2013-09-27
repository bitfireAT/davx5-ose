/*******************************************************************************
 * Copyright (c) 2013 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.resource;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentProviderOperation.Builder;
import android.content.ContentUris;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import at.bitfire.davdroid.Constants;

import com.google.common.base.Joiner;

import ezvcard.parameters.EmailTypeParameter;
import ezvcard.parameters.ImageTypeParameter;
import ezvcard.parameters.TelephoneTypeParameter;
import ezvcard.types.EmailType;
import ezvcard.types.NicknameType;
import ezvcard.types.NoteType;
import ezvcard.types.PhotoType;
import ezvcard.types.TelephoneType;
import ezvcard.types.UrlType;

public class LocalAddressBook extends LocalCollection {
	//private final static String TAG = "davdroid.LocalAddressBook";

	protected final static String
		COLUMN_REMOTE_NAME = RawContacts.SOURCE_ID,
		COLUMN_UID = RawContacts.SYNC1,
		COLUMN_ETAG = RawContacts.SYNC2;
	
	protected AccountManager accountManager;


	public LocalAddressBook(Account account, ContentProviderClient providerClient, AccountManager accountManager) {
		super(account, providerClient);
		this.accountManager = accountManager;
	}
	
	
	/* find multiple rows */

	@Override
	public Contact[] findDeleted() throws RemoteException {
		Cursor cursor = providerClient.query(entriesURI(),
				new String[] { RawContacts._ID, COLUMN_REMOTE_NAME, COLUMN_ETAG },
				RawContacts.DELETED + "=1", null, null);
		LinkedList<Contact> contacts = new LinkedList<Contact>();
		while (cursor.moveToNext())
			contacts.add(new Contact(cursor.getLong(0), cursor.getString(1), cursor.getString(2)));
		return contacts.toArray(new Contact[0]);
	}

	@Override
	public Contact[] findDirty() throws RemoteException {
		Cursor cursor = providerClient.query(entriesURI(),
				new String[] { RawContacts._ID, COLUMN_REMOTE_NAME, COLUMN_ETAG },
				RawContacts.DIRTY + "=1", null, null);
		LinkedList<Contact> contacts = new LinkedList<Contact>();
		while (cursor.moveToNext()) {
			Contact c = new Contact(cursor.getLong(0), cursor.getString(1), cursor.getString(2));
			populate(c);
			contacts.add(c);
		}
		return contacts.toArray(new Contact[0]);
	}
	
	@Override
	public Contact[] findNew() throws RemoteException {
		Cursor cursor = providerClient.query(entriesURI(), new String[] { RawContacts._ID },
				RawContacts.DIRTY + "=1 AND " + COLUMN_REMOTE_NAME + " IS NULL", null, null);
		LinkedList<Contact> contacts = new LinkedList<Contact>();
		while (cursor.moveToNext()) {
			String uid = UUID.randomUUID().toString(),
				   resourceName = uid + ".vcf";
			Contact c = new Contact(cursor.getLong(0), resourceName, null);
			c.setUid(uid);
			populate(c);

			// new record: set resource name and UID in database
			pendingOperations.add(ContentProviderOperation
					.newUpdate(ContentUris.withAppendedId(entriesURI(), c.getLocalID()))
					.withValue(COLUMN_REMOTE_NAME, resourceName)
					.build());
			
			contacts.add(c);
		}
		return contacts.toArray(new Contact[0]);
	}
	
	@Override
	public String getCTag() {
		return accountManager.getUserData(account, Constants.ACCOUNT_KEY_ADDRESSBOOK_CTAG);
	}

	@Override
	public void setCTag(String cTag) {
		accountManager.setUserData(account, Constants.ACCOUNT_KEY_ADDRESSBOOK_CTAG, cTag);
	}
	
	
	/* get data */

	@Override
	public Contact getByRemoteName(String remoteName) throws RemoteException {
		Cursor cursor = providerClient.query(entriesURI(),
				new String[] { RawContacts._ID, COLUMN_REMOTE_NAME, COLUMN_ETAG },
				COLUMN_REMOTE_NAME + "=?", new String[] { remoteName }, null);
		if (cursor.moveToNext())
			return new Contact(cursor.getLong(0), cursor.getString(1), cursor.getString(2));
		return null;
	}

	@Override
	public void populate(Resource res) throws RemoteException {
		Contact c = (Contact)res;
		if (c.isPopulated())
			return;
		
		Cursor cursor = providerClient.query(ContentUris.withAppendedId(entriesURI(), c.getLocalID()),
			new String[] { COLUMN_UID, RawContacts.STARRED }, null, null, null);
		if (cursor.moveToNext()) {
			c.setUid(cursor.getString(0));
			c.setStarred(cursor.getInt(1) != 0);
		}
		
		// structured name
		cursor = providerClient.query(dataURI(), new String[] {
				StructuredName.DISPLAY_NAME, StructuredName.PREFIX, StructuredName.GIVEN_NAME,
				StructuredName.MIDDLE_NAME,	StructuredName.FAMILY_NAME, StructuredName.SUFFIX
			}, StructuredName.RAW_CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
			new String[] { String.valueOf(res.getLocalID()), StructuredName.CONTENT_ITEM_TYPE }, null);
		if (cursor.moveToNext()) {
			c.setDisplayName(cursor.getString(0));
			
			c.setPrefix(cursor.getString(1));
			c.setGivenName(cursor.getString(2));
			c.setMiddleName(cursor.getString(3));
			c.setFamilyName(cursor.getString(4));
			c.setSuffix(cursor.getString(5));
		}
		
		// nick names
		cursor = providerClient.query(dataURI(), new String[] { Nickname.NAME },
				Nickname.RAW_CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
				new String[] { String.valueOf(c.getLocalID()), Nickname.CONTENT_ITEM_TYPE }, null);
		NicknameType nickNames = new NicknameType();
		while (cursor.moveToNext())
			nickNames.addValue(cursor.getString(0));
		if (!nickNames.getValues().isEmpty())
			c.setNickNames(nickNames);
		
		// photos
		cursor = providerClient.query(dataURI(), new String[] { Photo.PHOTO },
			Photo.RAW_CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
			new String[] { String.valueOf(c.getLocalID()), Photo.CONTENT_ITEM_TYPE }, null);
		while (cursor.moveToNext())
			c.addPhoto(new PhotoType(cursor.getBlob(0), ImageTypeParameter.JPEG));
		
		// phone numbers
		cursor = providerClient.query(dataURI(), new String[] { Phone.TYPE, Phone.NUMBER },
				Phone.RAW_CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
				new String[] { String.valueOf(c.getLocalID()), Phone.CONTENT_ITEM_TYPE }, null);
		while (cursor.moveToNext()) {
			TelephoneType number = new TelephoneType(cursor.getString(1));
			switch (cursor.getInt(0)) {
			case Phone.TYPE_FAX_HOME:
				number.addType(TelephoneTypeParameter.FAX);
				number.addType(TelephoneTypeParameter.HOME);
				break;
			case Phone.TYPE_FAX_WORK:
				number.addType(TelephoneTypeParameter.FAX);
				number.addType(TelephoneTypeParameter.WORK);
				break;
			case Phone.TYPE_HOME:
				number.addType(TelephoneTypeParameter.HOME);
				break;
			case Phone.TYPE_MOBILE:
				number.addType(TelephoneTypeParameter.CELL);
				break;
			case Phone.TYPE_OTHER_FAX:
				number.addType(TelephoneTypeParameter.FAX);
				break;
			case Phone.TYPE_PAGER:
				number.addType(TelephoneTypeParameter.PAGER);
				break;
			case Phone.TYPE_WORK:
				number.addType(TelephoneTypeParameter.WORK);
				break;
			case Phone.TYPE_WORK_MOBILE:
				number.addType(TelephoneTypeParameter.CELL);
				number.addType(TelephoneTypeParameter.WORK);
				break;
			case Phone.TYPE_WORK_PAGER:
				number.addType(TelephoneTypeParameter.PAGER);
				number.addType(TelephoneTypeParameter.WORK);
				break;
			}
			c.addPhoneNumber(number);
		}
		
		// email addresses
		cursor = providerClient.query(dataURI(), new String[] { Email.TYPE, Email.ADDRESS },
				Email.RAW_CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
				new String[] { String.valueOf(c.getLocalID()), Email.CONTENT_ITEM_TYPE }, null);
		while (cursor.moveToNext()) {
			EmailType email = new EmailType(cursor.getString(1));
			switch (cursor.getInt(0)) {
			case Email.TYPE_HOME:
				email.addType(EmailTypeParameter.HOME);
				break;
			case Email.TYPE_WORK:
				email.addType(EmailTypeParameter.WORK);
				break;
			}
			c.addEmail(email);
		}
		
		// URLs
		cursor = providerClient.query(dataURI(), new String[] { Website.URL },
				Website.RAW_CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
				new String[] { String.valueOf(c.getLocalID()), Website.CONTENT_ITEM_TYPE }, null);
		while (cursor.moveToNext())
			c.getURLs().add(new UrlType(cursor.getString(0)));
		
		// notes
		cursor = providerClient.query(dataURI(), new String[] { Note.NOTE },
				Website.RAW_CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
				new String[] { String.valueOf(c.getLocalID()), Note.CONTENT_ITEM_TYPE }, null);
		while (cursor.moveToNext())
			c.addNote(new NoteType(cursor.getString(0)));
		
		c.populated = true;
		return;
	}
	
	
	/* create/update */	
	
	@Override
	public void add(Resource resource) {
		Contact contact = (Contact)resource;
		
		int idx = pendingOperations.size();
		pendingOperations.add(ContentProviderOperation.newInsert(entriesURI())
				.withValue(RawContacts.ACCOUNT_NAME, account.name)
				.withValue(RawContacts.ACCOUNT_TYPE, account.type)
				.withValue(COLUMN_REMOTE_NAME, contact.getName())
				.withValue(COLUMN_UID, contact.getUid())
				.withValue(COLUMN_ETAG, contact.getETag())
				.withValue(RawContacts.STARRED, contact.isStarred())
				.withYieldAllowed(true)
				.build());

		addDataRows(contact, -1, idx);
	}
	
	@Override
	public void updateByRemoteName(Resource resource) throws RemoteException {
		Contact remoteContact = (Contact)resource,
				localContact = getByRemoteName(remoteContact.getName());

		pendingOperations.add(ContentProviderOperation
				.newUpdate(ContentUris.withAppendedId(entriesURI(), localContact.getLocalID()))
				.withValue(COLUMN_ETAG, remoteContact.getETag())
				.withValue(RawContacts.STARRED, remoteContact.isStarred())
				.withYieldAllowed(true)
				.build());
		
		// remove all data rows ...
		pendingOperations.add(ContentProviderOperation.newDelete(dataURI())
				.withSelection(Data.RAW_CONTACT_ID + "=?",
				new String[] { String.valueOf(localContact.getLocalID()) }).build());
		
		// ... and insert new ones
		addDataRows(remoteContact, localContact.getLocalID(), -1);
	}
	
	@Override
	public void delete(Resource contact) {
		pendingOperations.add(ContentProviderOperation.newDelete(
				ContentUris.withAppendedId(entriesURI(), contact.getLocalID())).build());
	}

	@Override
	public void deleteAllExceptRemoteNames(Resource[] remoteResources) {
		Builder builder = ContentProviderOperation.newDelete(entriesURI());
		
		if (remoteResources.length != 0) {
			List<String> terms = new LinkedList<String>();
			for (Resource res : remoteResources)
				terms.add(COLUMN_REMOTE_NAME + "<>" + DatabaseUtils.sqlEscapeString(res.getName()));
			String where = Joiner.on(" AND ").join(terms);
			builder = builder.withSelection(where, new String[] {});
		} else
			builder = builder.withSelection(COLUMN_REMOTE_NAME + " IS NOT NULL", null);
		
		pendingOperations.add(builder.build());
	}
	
	public void clearDirty(Resource resource) {
		pendingOperations.add(ContentProviderOperation
				.newUpdate(ContentUris.withAppendedId(entriesURI(), resource.getLocalID()))
				.withValue(RawContacts.DIRTY, 0).build());
	}
	
	
	/* private helper methods */
	
	protected Uri dataURI() {
		return Data.CONTENT_URI.buildUpon()
				.appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
				.build();
	}

	@Override
	protected Uri entriesURI() {
		return RawContacts.CONTENT_URI.buildUpon()
				.appendQueryParameter(RawContacts.ACCOUNT_NAME, account.name)
				.appendQueryParameter(RawContacts.ACCOUNT_TYPE, account.type)
				.appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
				.build();
	}
	
	private Builder newInsertBuilder(long raw_contact_id, Integer backrefIdx) {
		Builder builder = ContentProviderOperation.newInsert(dataURI());
		if (backrefIdx != -1)
			return builder.withValueBackReference(Data.RAW_CONTACT_ID, backrefIdx);
		else
			return builder.withValue(Data.RAW_CONTACT_ID, raw_contact_id);
	}
	
	protected void addDataRows(Contact contact, long localID, int backrefIdx) {
		pendingOperations.add(buildStructuredName(newInsertBuilder(localID, backrefIdx), contact).build());
		
		if (contact.getNickNames() != null)
			for (String nick : contact.getNickNames().getValues())
				pendingOperations.add(buildNickName(newInsertBuilder(localID, backrefIdx), nick).build());
		
		for (PhotoType photo : contact.getPhotos())
			pendingOperations.add(buildPhoto(newInsertBuilder(localID, backrefIdx), photo).build());
		
		for (TelephoneType number : contact.getPhoneNumbers())
			pendingOperations.add(buildPhoneNumber(newInsertBuilder(localID, backrefIdx), number).build());
		
		for (EmailType email : contact.getEmails())
			pendingOperations.add(buildEmail(newInsertBuilder(localID, backrefIdx), email).build());
		
		for (UrlType url : contact.getURLs())
			pendingOperations.add(buildURL(newInsertBuilder(localID, backrefIdx), url).build());
		
		for (NoteType note : contact.getNotes())
			pendingOperations.add(buildNote(newInsertBuilder(localID, backrefIdx), note).build());
	}

	
	
	/* content builder methods */
	
	protected Builder buildStructuredName(Builder builder, Contact contact) {
		return builder
			.withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
			.withValue(StructuredName.PREFIX, contact.getPrefix())
			.withValue(StructuredName.DISPLAY_NAME, contact.getDisplayName())
			.withValue(StructuredName.GIVEN_NAME, contact.getGivenName())
			.withValue(StructuredName.MIDDLE_NAME, contact.getMiddleName())
			.withValue(StructuredName.FAMILY_NAME, contact.getFamilyName())
			.withValue(StructuredName.SUFFIX, contact.getSuffix());
	}
	
	protected Builder buildNickName(Builder builder, String nickName) {
		return builder
			.withValue(Data.MIMETYPE, Nickname.CONTENT_ITEM_TYPE)
			.withValue(Nickname.NAME, nickName);
	}
	
	protected Builder buildPhoto(Builder builder, PhotoType photo) {
		return builder
			.withValue(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
			.withValue(Photo.PHOTO, photo.getData());
	}
	
	protected Builder buildPhoneNumber(Builder builder, TelephoneType number) {
		Set<TelephoneTypeParameter> types = number.getTypes();
		int type;
		if (types.contains(TelephoneTypeParameter.FAX)) {
			if (types.contains(TelephoneTypeParameter.HOME))
				type = Phone.TYPE_FAX_HOME;
			else if (types.contains(TelephoneTypeParameter.WORK))
				type = Phone.TYPE_FAX_WORK;
			else
				type = Phone.TYPE_OTHER_FAX;
			
		} else if (types.contains(TelephoneTypeParameter.CELL)) {
			if (types.contains(TelephoneTypeParameter.WORK))
				type = Phone.TYPE_WORK_MOBILE;
			else
				type = Phone.TYPE_MOBILE;
			
		} else if (types.contains(TelephoneTypeParameter.PAGER)) {
			if (types.contains(TelephoneTypeParameter.WORK))
				type = Phone.TYPE_WORK_PAGER;
			else
				type = Phone.TYPE_PAGER;
			
		} else if (types.contains(TelephoneTypeParameter.HOME)) {
			type = Phone.TYPE_HOME;
		} else if (types.contains(TelephoneTypeParameter.WORK)) {
			type = Phone.TYPE_WORK;
		} else
			type = Phone.TYPE_OTHER;
		
		return builder
			.withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
			.withValue(Phone.NUMBER, number.getText())
			.withValue(Email.TYPE, type);
	}
	
	protected Builder buildEmail(Builder builder, EmailType email) {
		builder = builder
			.withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
			.withValue(Email.ADDRESS, email.getValue());
		
		int type = 0;
		for (EmailTypeParameter emailType : email.getTypes()) {
			if (emailType.equals(EmailTypeParameter.HOME))
				type = Email.TYPE_HOME;
			else if (emailType.equals(EmailTypeParameter.WORK))
				type = Email.TYPE_WORK;
		}
		if (type != 0)
			builder = builder.withValue(Email.TYPE, type);
		return builder;
	}

	protected Builder buildURL(Builder builder, UrlType url) {
		return builder
			.withValue(Data.MIMETYPE, Website.CONTENT_ITEM_TYPE)
			.withValue(Website.URL, url.getValue());
	}
	
	protected Builder buildNote(Builder builder, NoteType note) {
		return builder
			.withValue(Data.MIMETYPE, Note.CONTENT_ITEM_TYPE)
			.withValue(Note.NOTE, note.getValue());
	}
}
