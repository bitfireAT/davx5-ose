/*******************************************************************************
 * Copyright (c) 2013 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.resource;

import java.util.Set;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentProviderOperation.Builder;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
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
import ezvcard.parameters.EmailTypeParameter;
import ezvcard.parameters.ImageTypeParameter;
import ezvcard.parameters.TelephoneTypeParameter;
import ezvcard.types.EmailType;
import ezvcard.types.NicknameType;
import ezvcard.types.NoteType;
import ezvcard.types.PhotoType;
import ezvcard.types.TelephoneType;
import ezvcard.types.UrlType;

public class LocalAddressBook extends LocalCollection<Contact> {
	//private final static String TAG = "davdroid.LocalAddressBook";
	
	protected AccountManager accountManager;
	
	
	/* database fields */
	
	@Override
	protected Uri entriesURI() {
		return syncAdapterURI(RawContacts.CONTENT_URI);
	}

	protected String entryColumnAccountType()	{ return RawContacts.ACCOUNT_TYPE; }
	protected String entryColumnAccountName()	{ return RawContacts.ACCOUNT_NAME; }
	
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
			new String[] { entryColumnUID(), RawContacts.STARRED }, null, null, null);
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
			for (String nick : contact.getNickNames().getValues())
				pendingOperations.add(buildNickName(newDataInsertBuilder(localID, backrefIdx), nick).build());
		
		for (PhotoType photo : contact.getPhotos())
			pendingOperations.add(buildPhoto(newDataInsertBuilder(localID, backrefIdx), photo).build());
		
		for (TelephoneType number : contact.getPhoneNumbers())
			pendingOperations.add(buildPhoneNumber(newDataInsertBuilder(localID, backrefIdx), number).build());
		
		for (EmailType email : contact.getEmails())
			pendingOperations.add(buildEmail(newDataInsertBuilder(localID, backrefIdx), email).build());
		
		for (UrlType url : contact.getURLs())
			pendingOperations.add(buildURL(newDataInsertBuilder(localID, backrefIdx), url).build());
		
		for (NoteType note : contact.getNotes())
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
