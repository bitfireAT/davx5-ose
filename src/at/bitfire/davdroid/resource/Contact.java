/*******************************************************************************
 * Copyright (c) 2013 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import com.google.common.base.Joiner;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import ezvcard.Ezvcard;
import ezvcard.VCard;
import ezvcard.VCardVersion;
import ezvcard.types.EmailType;
import ezvcard.types.NicknameType;
import ezvcard.types.NoteType;
import ezvcard.types.PhotoType;
import ezvcard.types.RawType;
import ezvcard.types.StructuredNameType;
import ezvcard.types.TelephoneType;
import ezvcard.types.UidType;
import ezvcard.types.UrlType;

@ToString(callSuper=true)
public class Contact extends Resource {
	public final String VCARD_STARRED = "X-DAVDROID-STARRED";
	
	@Getter @Setter private String uid;
	
	// VCard data
	@Getter @Setter boolean starred;
	
	@Getter @Setter private String displayName;
	@Getter @Setter private String prefix, givenName, middleName, familyName, suffix;
	@Getter @Setter private NicknameType nickNames = null;

	
	public Contact(String name, String ETag) {
		super(name, ETag);
	}
	
	public Contact(long localID, String resourceName, String eTag) {
		super(resourceName, eTag);
		this.localID = localID;
	}

	
	/* multiple-record fields */
	
	@Getter private List<PhotoType> photos = new LinkedList<PhotoType>();
	public void addPhoto(PhotoType photo) {
		photos.add(photo);
	}
	
	@Getter private List<TelephoneType> phoneNumbers = new LinkedList<TelephoneType>();
	public void addPhoneNumber(TelephoneType number) {
		phoneNumbers.add(number);
	}
	
	@Getter private List<EmailType> emails = new LinkedList<EmailType>();
	public void addEmail(EmailType email) {
		emails.add(email);
	}
	
	@Getter private List<UrlType> URLs = new LinkedList<UrlType>();
	public void addURL(UrlType url) {
		URLs.add(url);
	}
	
	@Getter private List<NoteType> notes = new LinkedList<NoteType>();
	public void addNote(NoteType note) {
		notes.add(note);
	}
	

	
	/* VCard methods */

	@Override
	public void parseEntity(InputStream is) throws IOException {
		VCard vcard = Ezvcard.parse(is).first();
		if (vcard == null)
			return;
		
		if (vcard.getUid() != null)
			uid = vcard.getUid().getValue();
		
		starred = false;
		for (RawType rawStarred : vcard.getExtendedType(VCARD_STARRED))
			starred = Boolean.parseBoolean(rawStarred.getValue());
		
		if (vcard.getFormattedName() != null)
			displayName = vcard.getFormattedName().getValue();
		else
			displayName = null;
		
		nickNames = vcard.getNickname();
		
		photos = vcard.getPhotos();
		
		if (vcard.getStructuredName() != null) {
			prefix = Joiner.on(" ").join(vcard.getStructuredName().getPrefixes());
			givenName = vcard.getStructuredName().getGiven();
			middleName = Joiner.on(" ").join(vcard.getStructuredName().getAdditional());
			familyName = vcard.getStructuredName().getFamily();
			prefix = Joiner.on(" ").join(vcard.getStructuredName().getSuffixes());
		} else {
			prefix = givenName = middleName = familyName = suffix = null;
		}
		
		phoneNumbers = vcard.getTelephoneNumbers();
		emails = vcard.getEmails();
		URLs = vcard.getUrls();
		notes = vcard.getNotes();
	}

	
	@Override
	public String toEntity() {
		VCard vcard = new VCard();
		
		if (uid != null)
			vcard.setUid(new UidType(uid));
		
		vcard.addExtendedType(VCARD_STARRED, Boolean.toString(starred));
		
		if (displayName != null)
			vcard.setFormattedName(displayName);
		
		if (nickNames != null)
			vcard.setNickname(nickNames);

		for (PhotoType photo : photos)
			vcard.addPhoto(photo);
		
		StructuredNameType n = new StructuredNameType();
		if (prefix != null) n.addPrefix(prefix);
		if (givenName != null) n.setGiven(givenName);
		if (middleName != null) n.addAdditional(middleName);
		if (familyName != null) n.setFamily(familyName);
		if (suffix != null) n.addSuffix(suffix);
		vcard.setStructuredName(n);
		
		for (TelephoneType number : phoneNumbers)
			vcard.addTelephoneNumber(number);
		
		for (EmailType email : emails)
			vcard.addEmail(email);
		
		for (UrlType url : URLs)
			vcard.addUrl(url);
		
		for (NoteType note : notes)
			vcard.addNote(note);
		
		return Ezvcard.write(vcard).version(VCardVersion.V4_0).go();
	}
}
