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
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.WordUtils;

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
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import at.bitfire.davdroid.Constants;
import ezvcard.parameter.AddressType;
import ezvcard.parameter.EmailType;
import ezvcard.parameter.TelephoneType;
import ezvcard.property.Address;
import ezvcard.property.Anniversary;
import ezvcard.property.Birthday;
import ezvcard.property.DateOrTimeProperty;
import ezvcard.property.Telephone;


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
		
		// phone numbers
		cursor = providerClient.query(dataURI(), new String[] { Phone.TYPE, Phone.LABEL, Phone.NUMBER },
				Phone.RAW_CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
				new String[] { String.valueOf(c.getLocalID()), Phone.CONTENT_ITEM_TYPE }, null);
		while (cursor != null && cursor.moveToNext()) {
			ezvcard.property.Telephone number = new ezvcard.property.Telephone(cursor.getString(2));
			switch (cursor.getInt(0)) {
			case Phone.TYPE_HOME:
				number.addType(TelephoneType.HOME);
				break;
			case Phone.TYPE_MOBILE:
				number.addType(TelephoneType.CELL);
				break;
			case Phone.TYPE_WORK:
				number.addType(TelephoneType.WORK);
				break;
			case Phone.TYPE_FAX_WORK:
				number.addType(TelephoneType.FAX);
				number.addType(TelephoneType.WORK);
				break;
			case Phone.TYPE_FAX_HOME:
				number.addType(TelephoneType.FAX);
				number.addType(TelephoneType.HOME);
				break;
			case Phone.TYPE_PAGER:
				number.addType(TelephoneType.PAGER);
				break;
			case Phone.TYPE_CALLBACK:
				number.addType(Contact.PHONE_TYPE_CALLBACK);
				break;
			case Phone.TYPE_CAR:
				number.addType(TelephoneType.CAR);
				break;
			case Phone.TYPE_COMPANY_MAIN:
				number.addType(Contact.PHONE_TYPE_COMPANY_MAIN);
				break;
			case Phone.TYPE_ISDN:
				number.addType(TelephoneType.ISDN);
				break;
			case Phone.TYPE_MAIN:
				number.addType(TelephoneType.PREF);
				break;
			case Phone.TYPE_OTHER_FAX:
				number.addType(TelephoneType.FAX);
				break;
			case Phone.TYPE_RADIO:
				number.addType(Contact.PHONE_TYPE_RADIO);
				break;
			case Phone.TYPE_TELEX:
				number.addType(TelephoneType.TEXTPHONE);
				break;
			case Phone.TYPE_TTY_TDD:
				number.addType(TelephoneType.TEXT);
				break;
			case Phone.TYPE_WORK_MOBILE:
				number.addType(TelephoneType.CELL);
				number.addType(TelephoneType.WORK);
				break;
			case Phone.TYPE_WORK_PAGER:
				number.addType(TelephoneType.PAGER);
				number.addType(TelephoneType.WORK);
				break;
			case Phone.TYPE_ASSISTANT:
				number.addType(Contact.PHONE_TYPE_ASSISTANT);
				break;
			case Phone.TYPE_MMS:
				number.addType(Contact.PHONE_TYPE_MMS);
				break;
			case Phone.TYPE_CUSTOM:
				number.addType(TelephoneType.get(labelToXName(cursor.getString(1))));
			}
			c.getPhoneNumbers().add(number);
		}
		
		// email addresses
		cursor = providerClient.query(dataURI(), new String[] { Email.TYPE, Email.ADDRESS, Email.LABEL },
				Email.RAW_CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
				new String[] { String.valueOf(c.getLocalID()), Email.CONTENT_ITEM_TYPE }, null);
		while (cursor != null && cursor.moveToNext()) {
			ezvcard.property.Email email = new ezvcard.property.Email(cursor.getString(1));
			switch (cursor.getInt(0)) {
			case Email.TYPE_HOME:
				email.addType(EmailType.HOME);
				break;
			case Email.TYPE_WORK:
				email.addType(EmailType.WORK);
				break;
			case Email.TYPE_MOBILE:
				email.addType(Contact.EMAIL_TYPE_MOBILE);
				break;
			case Email.TYPE_CUSTOM:
				email.addType(EmailType.get(labelToXName(cursor.getString(2))));
				break;
			}
			c.getEmails().add(email);
		}
		
		// photo
		cursor = providerClient.query(dataURI(), new String[] { Photo.PHOTO },
			Photo.RAW_CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
			new String[] { String.valueOf(c.getLocalID()), Photo.CONTENT_ITEM_TYPE }, null);
		if (cursor != null && cursor.moveToNext())
			c.setPhoto(cursor.getBlob(0));
		
		// nick name (max. 1)
		cursor = providerClient.query(dataURI(), new String[] { Nickname.NAME },
				Nickname.RAW_CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
				new String[] { String.valueOf(c.getLocalID()), Nickname.CONTENT_ITEM_TYPE }, null);
		if (cursor != null && cursor.moveToNext())
			c.setNickName(cursor.getString(0));
		
		// note (max. 1)
		cursor = providerClient.query(dataURI(), new String[] { Note.NOTE },
				Website.RAW_CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
				new String[] { String.valueOf(c.getLocalID()), Note.CONTENT_ITEM_TYPE }, null);
		if (cursor != null && cursor.moveToNext())
			c.setNote(cursor.getString(0));

		// postal addresses
		cursor = providerClient.query(dataURI(), new String[] {
				/* 0 */ StructuredPostal.FORMATTED_ADDRESS, StructuredPostal.TYPE, StructuredPostal.LABEL,
				/* 3 */ StructuredPostal.STREET, StructuredPostal.POBOX, StructuredPostal.NEIGHBORHOOD,
				/* 6 */ StructuredPostal.CITY, StructuredPostal.REGION, StructuredPostal.POSTCODE,
				/* 9 */ StructuredPostal.COUNTRY
			}, StructuredPostal.RAW_CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
			new String[] { String.valueOf(c.getLocalID()), StructuredPostal.CONTENT_ITEM_TYPE }, null);
		while (cursor != null && cursor.moveToNext()) {
			Address address = new Address();

			address.setLabel(cursor.getString(0));
			switch (cursor.getInt(1)) {
			case StructuredPostal.TYPE_HOME:
				address.addType(AddressType.HOME);
				break;
			case StructuredPostal.TYPE_WORK:
				address.addType(AddressType.WORK);
				break;
			case StructuredPostal.TYPE_CUSTOM:
				address.addType(AddressType.get(labelToXName(cursor.getString(2))));
				break;
			}
			address.setStreetAddress(cursor.getString(3));
			address.setPoBox(cursor.getString(4));
			address.setExtendedAddress(cursor.getString(5));
			address.setLocality(cursor.getString(6));
			address.setRegion(cursor.getString(7));
			address.setPostalCode(cursor.getString(8));
			address.setCountry(cursor.getString(9));
			c.getAddresses().add(address);
		}
		
		// URL
		cursor = providerClient.query(dataURI(), new String[] { Website.URL },
				Website.RAW_CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
				new String[] { String.valueOf(c.getLocalID()), Website.CONTENT_ITEM_TYPE }, null);
		if (cursor != null && cursor.moveToNext())
			c.setURL(cursor.getString(0));

		// events (birthday)
		cursor = providerClient.query(dataURI(), new String[] { CommonDataKinds.Event.TYPE, CommonDataKinds.Event.START_DATE },
				Photo.RAW_CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
				new String[] { String.valueOf(c.getLocalID()), CommonDataKinds.Event.CONTENT_ITEM_TYPE }, null);
		while (cursor != null && cursor.moveToNext())
			switch (cursor.getInt(0)) {
			case CommonDataKinds.Event.TYPE_ANNIVERSARY:
				c.setAnniversary(new Anniversary(cursor.getString(1)));
				break;
			case CommonDataKinds.Event.TYPE_BIRTHDAY:
				c.setBirthDay(new Birthday(cursor.getString(1)));
				break;
			}
		
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
	
	@Override
	protected String randomUID() {
		return UUID.randomUUID().toString();
	}

	protected Uri dataURI() {
		return syncAdapterURI(Data.CONTENT_URI);
	}
	
	protected String labelToXName(String label) {
		String xName = "X-" + label.replaceAll(" ","_").replaceAll("[^\\p{L}\\p{Nd}\\-_]", "").toUpperCase(Locale.US);
		return xName;
	}
	
	private Builder newDataInsertBuilder(long raw_contact_id, Integer backrefIdx) {
		return newDataInsertBuilder(dataURI(), Data.RAW_CONTACT_ID, raw_contact_id, backrefIdx);
	}

	protected String xNameToLabel(String xname) {
		// "x-my_property"
		// 1. ensure lower case -> "x-my_property"
		// 2. remove x- from beginning -> "my_property"
		// 3. replace "_" by " " -> "my property"
		// 4. capitalize -> "My Property"
		return WordUtils.capitalize(StringUtils.removeStart(xname.toLowerCase(Locale.US), "x-").replaceAll("_"," "));
	}
	
	
	/* content builder methods */
	
	@Override
	protected Builder buildEntry(Builder builder, Resource resource) {
		Contact contact = (Contact)resource;
		
		return builder
			.withValue(RawContacts.ACCOUNT_NAME, account.name)
			.withValue(RawContacts.ACCOUNT_TYPE, account.type)
			.withValue(entryColumnRemoteName(), contact.getName())
			.withValue(entryColumnUID(), contact.getUid())
			.withValue(entryColumnETag(), contact.getETag())
			.withValue(RawContacts.STARRED, contact.isStarred());
	}
	
	
	@Override
	protected void addDataRows(Resource resource, long localID, int backrefIdx) {
		Contact contact = (Contact)resource;
		
		pendingOperations.add(buildStructuredName(newDataInsertBuilder(localID, backrefIdx), contact).build());
		
		for (Telephone number : contact.getPhoneNumbers())
			pendingOperations.add(buildPhoneNumber(newDataInsertBuilder(localID, backrefIdx), number).build());
		
		for (ezvcard.property.Email email : contact.getEmails())
			pendingOperations.add(buildEmail(newDataInsertBuilder(localID, backrefIdx), email).build());

		if (contact.getPhoto() != null)
			pendingOperations.add(buildPhoto(newDataInsertBuilder(localID, backrefIdx), contact.getPhoto()).build());
		
		// TODO organization
		// TODO im
		
		if (contact.getNickName() != null)
			pendingOperations.add(buildNickName(newDataInsertBuilder(localID, backrefIdx), contact.getNickName()).build());
		
		if (contact.getNote() != null)
			pendingOperations.add(buildNote(newDataInsertBuilder(localID, backrefIdx), contact.getNote()).build());
		
		for (Address address : contact.getAddresses())
			pendingOperations.add(buildAddress(newDataInsertBuilder(localID, backrefIdx), address).build());
		
		// TODO group membership
		
		if (contact.getURL() != null)
			pendingOperations.add(buildURL(newDataInsertBuilder(localID, backrefIdx), contact.getURL()).build());
		
		// events
		if (contact.getAnniversary() != null)
			pendingOperations.add(buildEvent(newDataInsertBuilder(localID, backrefIdx),
				contact.getAnniversary(), CommonDataKinds.Event.TYPE_ANNIVERSARY).build());
		if (contact.getBirthDay() != null)
			pendingOperations.add(buildEvent(newDataInsertBuilder(localID, backrefIdx),
					contact.getBirthDay(), CommonDataKinds.Event.TYPE_BIRTHDAY).build());
		
		// TODO relation
		// TODO SIP address
	}
	
	@Override
	protected void removeDataRows(Resource resource) {
		pendingOperations.add(ContentProviderOperation.newDelete(dataURI())
				.withSelection(Data.RAW_CONTACT_ID + "=?",
				new String[] { String.valueOf(resource.getLocalID()) }).build());
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
	
	protected Builder buildPhoneNumber(Builder builder, Telephone number) {
		int typeCode = Phone.TYPE_OTHER;
		String typeLabel = null;
		Set<TelephoneType> types = number.getTypes();
		// 1 Android type <-> 2 VCard types: fax, cell, pager
		if (types.contains(TelephoneType.FAX)) {
			if (types.contains(TelephoneType.HOME))
				typeCode = Phone.TYPE_FAX_HOME;
			else if (types.contains(TelephoneType.WORK))
				typeCode = Phone.TYPE_FAX_WORK;
			else
				typeCode = Phone.TYPE_OTHER_FAX;
		} else if (types.contains(TelephoneType.CELL)) {
			if (types.contains(TelephoneType.WORK))
				typeCode = Phone.TYPE_WORK_MOBILE;
			else
				typeCode = Phone.TYPE_MOBILE;
		} else if (types.contains(TelephoneType.PAGER)) {
			if (types.contains(TelephoneType.WORK))
				typeCode = Phone.TYPE_WORK_PAGER;
			else
				typeCode = Phone.TYPE_PAGER;
		// types with 1:1 translation
		} else if (types.contains(TelephoneType.HOME)) {
			typeCode = Phone.TYPE_HOME;
		} else if (types.contains(TelephoneType.WORK)) {
			typeCode = Phone.TYPE_WORK;
		} else if (types.contains(Contact.PHONE_TYPE_CALLBACK)) {
			typeCode = Phone.TYPE_CALLBACK;
		} else if (types.contains(TelephoneType.CAR)) {
			typeCode = Phone.TYPE_CAR;
		} else if (types.contains(Contact.PHONE_TYPE_COMPANY_MAIN)) {
			typeCode = Phone.TYPE_COMPANY_MAIN;
		} else if (types.contains(TelephoneType.ISDN)) {
			typeCode = Phone.TYPE_ISDN;
		} else if (types.contains(TelephoneType.PREF)) {
			typeCode = Phone.TYPE_MAIN;
		} else if (types.contains(Contact.PHONE_TYPE_RADIO)) {
			typeCode = Phone.TYPE_RADIO;
		} else if (types.contains(TelephoneType.TEXTPHONE)) {
			typeCode = Phone.TYPE_TELEX;
		} else if (types.contains(TelephoneType.TEXT)) {
			typeCode = Phone.TYPE_TTY_TDD;
		} else if (types.contains(Contact.PHONE_TYPE_ASSISTANT)) {
			typeCode = Phone.TYPE_ASSISTANT;
		} else if (types.contains(Contact.PHONE_TYPE_MMS)) {
			typeCode = Phone.TYPE_MMS;
		} else if (!types.isEmpty()) {
			TelephoneType type = types.iterator().next();
			typeCode = Phone.TYPE_CUSTOM;
			typeLabel = xNameToLabel(type.getValue());
		}
		
		builder = builder
			.withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
			.withValue(Phone.NUMBER, number.getText())
			.withValue(Phone.TYPE, typeCode);
		if (typeLabel != null)
			builder = builder.withValue(Phone.LABEL, typeLabel);
		return builder;
	}
	
	protected Builder buildEmail(Builder builder, ezvcard.property.Email email) {
		int typeCode = Email.TYPE_OTHER;
		String typeLabel = null;
		
		for (EmailType type : email.getTypes())
			if (type == EmailType.HOME)
				typeCode = Email.TYPE_HOME;
			else if (type == EmailType.WORK)
				typeCode = Email.TYPE_WORK;
			else if (type == Contact.EMAIL_TYPE_MOBILE)
				typeCode = Email.TYPE_MOBILE;
			else {
				typeCode = Email.TYPE_CUSTOM;
				typeLabel = xNameToLabel(type.getValue());
			}
		
		builder = builder
				.withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
				.withValue(Email.ADDRESS, email.getValue())
				.withValue(Email.TYPE, typeCode);
		if (typeLabel != null)
			builder = builder.withValue(Email.LABEL, typeLabel);
		return builder;
	}
	
	protected Builder buildPhoto(Builder builder, byte[] photo) {
		return builder
			.withValue(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
			.withValue(Photo.PHOTO, photo);
	}
	
	protected Builder buildNickName(Builder builder, String nickName) {
		return builder
			.withValue(Data.MIMETYPE, Nickname.CONTENT_ITEM_TYPE)
			.withValue(Nickname.NAME, nickName);
	}
	
	protected Builder buildNote(Builder builder, String note) {
		return builder
			.withValue(Data.MIMETYPE, Note.CONTENT_ITEM_TYPE)
			.withValue(Note.NOTE, note);
	}

	protected Builder buildAddress(Builder builder, Address address) {
		/*	street po.box (extended)
		 *	region
		 *	postal code city
		 *	country
		 */
		String formattedAddress = address.getLabel();
		if (formattedAddress == null || formattedAddress.isEmpty()) {
			String	lineStreet = StringUtils.join(new String[] { address.getStreetAddress(), address.getPoBox(), address.getExtendedAddress() }, " "),
					lineLocality = StringUtils.join(new String[] { address.getPostalCode(), address.getLocality() }, " ");
			
			List<String> lines = new LinkedList<String>();
			if (lineStreet != null)
				lines.add(lineStreet);
			if (address.getRegion() != null && !address.getRegion().isEmpty())
				lines.add(address.getRegion());
			if (lineLocality != null)
				lines.add(lineLocality);
			
			formattedAddress = StringUtils.join(lines, "\n");
		}
			
		int typeCode = StructuredPostal.TYPE_OTHER;
		String typeLabel = null;
		for (AddressType type : address.getTypes())
			if (type == AddressType.HOME)
				typeCode = StructuredPostal.TYPE_HOME;
			else if (type == AddressType.WORK)
				typeCode = StructuredPostal.TYPE_WORK;
			else {
				typeCode = StructuredPostal.TYPE_CUSTOM;
				typeLabel = xNameToLabel(type.getValue());
			}
		
		builder = builder
			.withValue(Data.MIMETYPE, StructuredPostal.CONTENT_ITEM_TYPE)
			.withValue(StructuredPostal.FORMATTED_ADDRESS, formattedAddress)
			.withValue(StructuredPostal.TYPE, typeCode)
			.withValue(StructuredPostal.STREET, address.getStreetAddress())
			.withValue(StructuredPostal.POBOX, address.getPoBox())
			.withValue(StructuredPostal.NEIGHBORHOOD, address.getExtendedAddress())
			.withValue(StructuredPostal.CITY, address.getLocality())
			.withValue(StructuredPostal.REGION, address.getRegion())
			.withValue(StructuredPostal.POSTCODE, address.getPostalCode())
			.withValue(StructuredPostal.COUNTRY, address.getCountry());
		if (typeLabel != null)
			builder = builder.withValue(StructuredPostal.LABEL, typeLabel);
		return builder;
	}

	protected Builder buildURL(Builder builder, String url) {
		return builder
			.withValue(Data.MIMETYPE, Website.CONTENT_ITEM_TYPE)
			.withValue(Website.URL, url);
	}
	
	protected Builder buildEvent(Builder builder, DateOrTimeProperty date, int type) {
		return builder
			.withValue(Data.MIMETYPE, CommonDataKinds.Event.CONTENT_ITEM_TYPE)
			.withValue(CommonDataKinds.Event.TYPE, type) 
			.withValue(CommonDataKinds.Event.START_DATE, date.getText());
	}
}
