/*******************************************************************************
 * Copyright (c) 2013 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.resource;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.LinkedList;
import java.util.List;

import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.vcard.Parameter.Id;
import net.fortuna.ical4j.vcard.parameter.Type;
import net.fortuna.ical4j.vcard.property.Telephone;

import org.apache.commons.lang.StringUtils;

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
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;
import at.bitfire.davdroid.Constants;


public class LocalAddressBook extends LocalCollection<Contact> {
	private final static String TAG = "davdroid.LocalAddressBook";
	
	protected AccountManager accountManager;
	
	
	/* database fields */
	
	@Override
	protected Uri entriesURI() {
		return syncAdapterURI(RawContacts.CONTENT_URI);
	}

	protected String entryColumnAccountType()	{ return RawContacts.ACCOUNT_TYPE; }
	protected String entryColumnAccountName()	{ return RawContacts.ACCOUNT_NAME; }
	
	protected String entryColumnParentID()		{ return null;	/* maybe use RawContacts.DATA_SET some day? */ }
	protected String entryColumnID()			{ return RawContacts._ID; }
	protected String entryColumnRemoteName()	{ return RawContacts.SOURCE_ID; }
	protected String entryColumnETag()			{ return RawContacts.SYNC2; }
	
	protected String entryColumnDirty()			{ return RawContacts.DIRTY; }
	protected String entryColumnDeleted()		{ return RawContacts.DELETED; }

	protected String entryColumnUID()			{ return RawContacts.SYNC1; }



	public LocalAddressBook(Account account, ContentProviderClient providerClient, AccountManager accountManager) {
		super(account, providerClient);
		this.accountManager = accountManager;
	}
	
	
	/* collection operations */
	
	@Override
	public long getId() {
		return -1;
	}
	
	@Override
	public String getCTag() {
		return accountManager.getUserData(account, Constants.ACCOUNT_KEY_ADDRESSBOOK_CTAG);
	}

	@Override
	public void setCTag(String cTag) {
		accountManager.setUserData(account, Constants.ACCOUNT_KEY_ADDRESSBOOK_CTAG, cTag);
	}
	
	
	/* content provider (= database) querying */
	
	@Override
	public Contact findById(long localID, String remoteName, String eTag, boolean populate) throws RemoteException {
		Contact c = new Contact(localID, remoteName, eTag);
		if (populate)
			populate(c);
		return c;
	}

	@Override
	public Contact findByRemoteName(String remoteName) throws RemoteException {
		Cursor cursor = providerClient.query(entriesURI(),
				new String[] { RawContacts._ID, entryColumnRemoteName(), entryColumnETag() },
				entryColumnRemoteName() + "=?", new String[] { remoteName }, null);
		if (cursor != null && cursor.moveToNext())
			return new Contact(cursor.getLong(0), cursor.getString(1), cursor.getString(2));
		else
			return null;
	}

	@Override
	public void populate(Resource res) throws RemoteException {
		Contact c = (Contact)res;
		if (c.isPopulated())
			return;
		
		Cursor cursor = providerClient.query(ContentUris.withAppendedId(entriesURI(), c.getLocalID()),
			new String[] { entryColumnUID(), RawContacts.STARRED }, null, null, null);
		if (cursor != null && cursor.moveToNext()) {
			c.setUid(cursor.getString(0));
			c.setStarred(cursor.getInt(1) != 0);
		}
		
		// structured name
		cursor = providerClient.query(dataURI(), new String[] {
				/* 0 */ StructuredName.DISPLAY_NAME, StructuredName.PREFIX, StructuredName.GIVEN_NAME,
				/* 3 */ StructuredName.MIDDLE_NAME,	StructuredName.FAMILY_NAME, StructuredName.SUFFIX,
				/* 6 */ StructuredName.PHONETIC_GIVEN_NAME, StructuredName.PHONETIC_MIDDLE_NAME, StructuredName.PHONETIC_FAMILY_NAME
			}, StructuredName.RAW_CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
			new String[] { String.valueOf(res.getLocalID()), StructuredName.CONTENT_ITEM_TYPE }, null);
		if (cursor != null && cursor.moveToNext()) {
			c.setDisplayName(cursor.getString(0));
			
			c.setPrefix(cursor.getString(1));
			c.setGivenName(cursor.getString(2));
			c.setMiddleName(cursor.getString(3));
			c.setFamilyName(cursor.getString(4));
			c.setSuffix(cursor.getString(5));
			
			c.setPhoneticGivenName(cursor.getString(6));
			c.setPhoneticMiddleName(cursor.getString(7));
			c.setPhoneticFamilyName(cursor.getString(8));
		}
		
		// nick names
		cursor = providerClient.query(dataURI(), new String[] { Nickname.NAME },
				Nickname.RAW_CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
				new String[] { String.valueOf(c.getLocalID()), Nickname.CONTENT_ITEM_TYPE }, null);
		List<String> nickNames = new LinkedList<String>();
		while (cursor != null && cursor.moveToNext())
			nickNames.add(cursor.getString(0));
		if (!nickNames.isEmpty())
			c.setNickNames(nickNames.toArray(new String[0]));
		
		// email addresses
		cursor = providerClient.query(dataURI(), new String[] { Email.TYPE, Email.ADDRESS },
				Email.RAW_CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
				new String[] { String.valueOf(c.getLocalID()), Email.CONTENT_ITEM_TYPE }, null);
		while (cursor != null && cursor.moveToNext()) {
			net.fortuna.ical4j.vcard.property.Email email = new net.fortuna.ical4j.vcard.property.Email(cursor.getString(1));
			switch (cursor.getInt(0)) {
			case Email.TYPE_HOME:
				email.getParameters().add(Type.HOME);
				break;
			case Email.TYPE_WORK:
				email.getParameters().add(Type.WORK);
				break;
			}
			c.addEmail(email);
		}

		// phone numbers
		cursor = providerClient.query(dataURI(), new String[] { Phone.TYPE, Phone.NUMBER },
				Phone.RAW_CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
				new String[] { String.valueOf(c.getLocalID()), Phone.CONTENT_ITEM_TYPE }, null);
		while (cursor != null && cursor.moveToNext()) {
			Telephone number = new Telephone(cursor.getString(1));
			List<String> types = new LinkedList<String>();
			
			switch (cursor.getInt(0)) {
			case Phone.TYPE_FAX_HOME:
				types.add("fax");
				types.add("home");
				break;
			case Phone.TYPE_FAX_WORK:
				types.add("fax");
				types.add("work");
				break;
			case Phone.TYPE_HOME:
				types.add("home");
				break;
			case Phone.TYPE_MOBILE:
				types.add("cell");
				break;
			case Phone.TYPE_OTHER_FAX:
				types.add("fax");
				break;
			case Phone.TYPE_PAGER:
				types.add("pager");
				break;
			case Phone.TYPE_WORK:
				types.add("work");
				break;
			case Phone.TYPE_WORK_MOBILE:
				types.add("cell");
				types.add("work");
				break;
			case Phone.TYPE_WORK_PAGER:
				types.add("pager");
				types.add("work");
				break;
			}
			number.getParameters().add(new Type(types.toArray(new String[0])));
			c.addPhoneNumber(number);
		}
		
		// photo
		cursor = providerClient.query(dataURI(), new String[] { Photo.PHOTO },
			Photo.RAW_CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
			new String[] { String.valueOf(c.getLocalID()), Photo.CONTENT_ITEM_TYPE }, null);
		if (cursor != null && cursor.moveToNext())
			c.setPhoto(cursor.getBlob(0));
		
		// events (birthday)
		cursor = providerClient.query(dataURI(), new String[] { CommonDataKinds.Event.TYPE, CommonDataKinds.Event.START_DATE },
				Photo.RAW_CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
				new String[] { String.valueOf(c.getLocalID()), CommonDataKinds.Event.CONTENT_ITEM_TYPE }, null);
		while (cursor != null && cursor.moveToNext())
			try {
				switch (cursor.getInt(0)) {
				case CommonDataKinds.Event.TYPE_BIRTHDAY:
					c.setBirthDay(new Date(cursor.getString(1)));
					break;
				}
			} catch (ParseException e) {
				Log.w(TAG, "Ignoring event with unknown date format: " + cursor.getString(0));
			}
		
		// URLs
		cursor = providerClient.query(dataURI(), new String[] { Website.URL },
				Website.RAW_CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
				new String[] { String.valueOf(c.getLocalID()), Website.CONTENT_ITEM_TYPE }, null);
		while (cursor != null && cursor.moveToNext())
			try {
				c.addURL(new URI(cursor.getString(0)));
			} catch (URISyntaxException ex) {
				Log.w(TAG, "Found invalid contact URL in database: " + ex.toString());
			}
		
		// notes
		cursor = providerClient.query(dataURI(), new String[] { Note.NOTE },
				Website.RAW_CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
				new String[] { String.valueOf(c.getLocalID()), Note.CONTENT_ITEM_TYPE }, null);
		while (cursor != null && cursor.moveToNext())
			c.addNote(new String(cursor.getString(0)));
		
		c.populated = true;
		return;
	}
	
	
	public void deleteAllExceptRemoteNames(Resource[] remoteResources) {
		String where;
		
		if (remoteResources.length != 0) {
			List<String> sqlFileNames = new LinkedList<String>();
			for (Resource res : remoteResources)
				sqlFileNames.add(DatabaseUtils.sqlEscapeString(res.getName()));
			where = entryColumnRemoteName() + " NOT IN (" + StringUtils.join(sqlFileNames, ",") + ")";
		} else
			where = entryColumnRemoteName() + " IS NOT NULL";
			
		Builder builder = ContentProviderOperation.newDelete(entriesURI()).withSelection(where, null);
		pendingOperations.add(builder
				.withYieldAllowed(true)
				.build());
	}

	
	/* private helper methods */
	
	@Override
	protected String fileExtension() {
		return ".vcf";
	}
	
	protected Uri dataURI() {
		return syncAdapterURI(Data.CONTENT_URI);
	}
	
	private Builder newDataInsertBuilder(long raw_contact_id, Integer backrefIdx) {
		return newDataInsertBuilder(dataURI(), Data.RAW_CONTACT_ID, raw_contact_id, backrefIdx);
	}
	
	
	/* content builder methods */
	
	@Override
	protected Builder buildEntry(Builder builder, Contact contact) {
		return builder
			.withValue(RawContacts.ACCOUNT_NAME, account.name)
			.withValue(RawContacts.ACCOUNT_TYPE, account.type)
			.withValue(entryColumnRemoteName(), contact.getName())
			.withValue(entryColumnUID(), contact.getUid())
			.withValue(entryColumnETag(), contact.getETag())
			.withValue(RawContacts.STARRED, contact.isStarred());
	}
	
	
	@Override
	protected void addDataRows(Contact contact, long localID, int backrefIdx) {
		pendingOperations.add(buildStructuredName(newDataInsertBuilder(localID, backrefIdx), contact).build());
		
		if (contact.getNickNames() != null)
			for (String nick : contact.getNickNames())
				pendingOperations.add(buildNickName(newDataInsertBuilder(localID, backrefIdx), nick).build());
		
		for (net.fortuna.ical4j.vcard.property.Email email : contact.getEmails())
			pendingOperations.add(buildEmail(newDataInsertBuilder(localID, backrefIdx), email).build());
		
		for (Telephone number : contact.getPhoneNumbers())
			pendingOperations.add(buildPhoneNumber(newDataInsertBuilder(localID, backrefIdx), number).build());

		if (contact.getPhoto() != null)
			pendingOperations.add(buildPhoto(newDataInsertBuilder(localID, backrefIdx), contact.getPhoto()).build());
		
		if (contact.getBirthDay() != null)
			pendingOperations.add(buildBirthDay(newDataInsertBuilder(localID, backrefIdx), contact.getBirthDay()).build());
		
		for (URI uri : contact.getURLs())
			pendingOperations.add(buildURL(newDataInsertBuilder(localID, backrefIdx), uri).build());
		
		for (String note : contact.getNotes())
			pendingOperations.add(buildNote(newDataInsertBuilder(localID, backrefIdx), note).build());
	}
	
	@Override
	protected void removeDataRows(Contact contact) {
		pendingOperations.add(ContentProviderOperation.newDelete(dataURI())
				.withSelection(Data.RAW_CONTACT_ID + "=?",
				new String[] { String.valueOf(contact.getLocalID()) }).build());
	}


	protected Builder buildStructuredName(Builder builder, Contact contact) {
		return builder
			.withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
			.withValue(StructuredName.PREFIX, contact.getPrefix())
			.withValue(StructuredName.DISPLAY_NAME, contact.getDisplayName())
			.withValue(StructuredName.GIVEN_NAME, contact.getGivenName())
			.withValue(StructuredName.MIDDLE_NAME, contact.getMiddleName())
			.withValue(StructuredName.FAMILY_NAME, contact.getFamilyName())
			.withValue(StructuredName.SUFFIX, contact.getSuffix())
			.withValue(StructuredName.PHONETIC_GIVEN_NAME, contact.getPhoneticGivenName())
			.withValue(StructuredName.PHONETIC_MIDDLE_NAME, contact.getPhoneticMiddleName())
			.withValue(StructuredName.PHONETIC_FAMILY_NAME, contact.getPhoneticFamilyName());
	}
	
	protected Builder buildNickName(Builder builder, String nickName) {
		return builder
			.withValue(Data.MIMETYPE, Nickname.CONTENT_ITEM_TYPE)
			.withValue(Nickname.NAME, nickName);
	}
	
	protected Builder buildEmail(Builder builder, net.fortuna.ical4j.vcard.property.Email email) {
		builder = builder
			.withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
			.withValue(Email.ADDRESS, email.getValue());
		
		int type = 0;
		
		Type emailType = (Type)email.getParameter(Id.TYPE);
		if (emailType == Type.HOME)
			type = Email.TYPE_HOME;
		else if (emailType == Type.WORK)
			type = Email.TYPE_WORK;
		
		if (type != 0)
			builder = builder.withValue(Email.TYPE, type);
		
		return builder;
	}
	
	protected Builder buildPhoneNumber(Builder builder, Telephone number) {
		boolean	fax = false,
				cell = false,
				pager = false,
				home = false,
				work = false;
		
		Type phoneType = (Type)number.getParameter(Id.TYPE);
		if (phoneType != null)			
			for (String strType : phoneType.getTypes())
				if (strType.equalsIgnoreCase("fax"))
					fax = true;
				else if (strType.equalsIgnoreCase("cell"))
					cell = true;
				else if (strType.equalsIgnoreCase("pager"))
					pager = true;
				else if (strType.equalsIgnoreCase("home"))
					home = true;
				else if (strType.equalsIgnoreCase("work"))
					work = true;
		
		int type = Phone.TYPE_OTHER;
		if (fax) {
			if (home)
				type = Phone.TYPE_FAX_HOME;
			else if (work)
				type = Phone.TYPE_FAX_WORK;
			else
				type = Phone.TYPE_OTHER_FAX;
			
		} else if (cell) {
			if (work)
				type = Phone.TYPE_WORK_MOBILE;
			else
				type = Phone.TYPE_MOBILE;
			
		} else if (pager) {
			if (work)
				type = Phone.TYPE_WORK_PAGER;
			else
				type = Phone.TYPE_PAGER;
			
		} else if (home) {
			type = Phone.TYPE_HOME;
		} else if (work) {
			type = Phone.TYPE_WORK;
		}
		
		return builder
			.withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
			.withValue(Phone.NUMBER, number.getValue())
			.withValue(Phone.TYPE, type);
	}
	
	protected Builder buildPhoto(Builder builder, byte[] photo) {
		return builder
			.withValue(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
			.withValue(Photo.PHOTO, photo);
	}
	
	protected Builder buildBirthDay(Builder builder, Date birthDay) {
		return builder
			.withValue(Data.MIMETYPE, CommonDataKinds.Event.CONTENT_ITEM_TYPE)
			.withValue(CommonDataKinds.Event.TYPE, CommonDataKinds.Event.TYPE_BIRTHDAY) 
			.withValue(CommonDataKinds.Event.START_DATE, birthDay.toString());
	}
	
	protected Builder buildURL(Builder builder, URI uri) {
		return builder
			.withValue(Data.MIMETYPE, Website.CONTENT_ITEM_TYPE)
			.withValue(Website.URL, uri.toString());
	}
	
	protected Builder buildNote(Builder builder, String note) {
		return builder
			.withValue(Data.MIMETYPE, Note.CONTENT_ITEM_TYPE)
			.withValue(Note.NOTE, note);
	}
}
