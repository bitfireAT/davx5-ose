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
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import lombok.Cleanup;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import org.apache.commons.lang.StringUtils;

import android.util.Log;
import at.bitfire.davdroid.Constants;
import ezvcard.Ezvcard;
import ezvcard.VCard;
import ezvcard.VCardException;
import ezvcard.VCardVersion;
import ezvcard.parameter.EmailType;
import ezvcard.parameter.ImageType;
import ezvcard.parameter.TelephoneType;
import ezvcard.property.Address;
import ezvcard.property.Anniversary;
import ezvcard.property.Birthday;
import ezvcard.property.Email;
import ezvcard.property.FormattedName;
import ezvcard.property.Impp;
import ezvcard.property.Nickname;
import ezvcard.property.Note;
import ezvcard.property.Organization;
import ezvcard.property.Photo;
import ezvcard.property.RawProperty;
import ezvcard.property.Revision;
import ezvcard.property.Role;
import ezvcard.property.StructuredName;
import ezvcard.property.Telephone;
import ezvcard.property.Uid;
import ezvcard.property.Url;
import ezvcard.util.IOUtils;

@ToString(callSuper = true)
public class Contact extends Resource {
	private final static String TAG = "davdroid.Contact";
	
	public final static String
		PROPERTY_STARRED = "X-DAVDROID-STARRED",
		PROPERTY_PHONETIC_FIRST_NAME = "X-PHONETIC-FIRST-NAME",
		PROPERTY_PHONETIC_MIDDLE_NAME = "X-PHONETIC-MIDDLE-NAME",
		PROPERTY_PHONETIC_LAST_NAME = "X-PHONETIC-LAST-NAME";
		
	public final static EmailType EMAIL_TYPE_MOBILE = EmailType.get("X-MOBILE");
				
	public final static TelephoneType
		PHONE_TYPE_CALLBACK = TelephoneType.get("X-CALLBACK"),
		PHONE_TYPE_COMPANY_MAIN = TelephoneType.get("X-COMPANY_MAIN"),
		PHONE_TYPE_RADIO = TelephoneType.get("X-RADIO"),
		PHONE_TYPE_ASSISTANT = TelephoneType.get("X-ASSISTANT"),
		PHONE_TYPE_MMS = TelephoneType.get("X-MMS");
	
	@Getter @Setter boolean starred;
	
	@Getter @Setter private String displayName, nickName;
	@Getter @Setter private String prefix, givenName, middleName, familyName, suffix;
	@Getter @Setter private String phoneticGivenName, phoneticMiddleName, phoneticFamilyName;
	@Getter @Setter private String note, URL;
	@Getter @Setter private String organization, role;
	
	@Getter @Setter private byte[] photo;
	
	@Getter @Setter private Anniversary anniversary;
	@Getter @Setter private Birthday birthDay;

	@Getter private List<Email> emails = new LinkedList<Email>();
	@Getter private List<Telephone> phoneNumbers = new LinkedList<Telephone>();
	@Getter private List<Address> addresses = new LinkedList<Address>();
	@Getter private List<Impp> impps = new LinkedList<Impp>();


	/* instance methods */
	
	public Contact(String name, String ETag) {
		super(name, ETag);
	}
	
	public Contact(long localID, String resourceName, String eTag) {
		super(resourceName, eTag);
		this.localID = localID;
	}

	@Override
	public void initialize() {
		uid = UUID.randomUUID().toString();
		name = uid + ".vcf";
	}

	
	/* VCard methods */

	@Override
	public void parseEntity(InputStream is) throws IOException, VCardException {
		VCard vcard = Ezvcard.parse(is).first();
		if (vcard == null)
			return;
		
		Uid uid = vcard.getUid();
		if (uid == null) {
			Log.w(TAG, "Received VCONTACT without UID, generating new one");
			uid = new Uid(UUID.randomUUID().toString());
		}
		this.uid = uid.getValue();

		RawProperty starred = vcard.getExtendedProperty(PROPERTY_STARRED);
		this.starred = starred != null && starred.getValue().equals("1");
		
		FormattedName fn = vcard.getFormattedName();
		if (fn != null)
			displayName = fn.getValue();

		StructuredName n = vcard.getStructuredName();
		if (n != null) {
			prefix = StringUtils.join(n.getPrefixes(), " ");
			givenName = n.getGiven();
			middleName = StringUtils.join(n.getAdditional(), " ");
			familyName = n.getFamily();
			suffix = StringUtils.join(n.getSuffixes(), " ");
		}
		
		RawProperty
			phoneticFirstName = vcard.getExtendedProperty(PROPERTY_PHONETIC_FIRST_NAME),
			phoneticMiddleName = vcard.getExtendedProperty(PROPERTY_PHONETIC_MIDDLE_NAME),
			phoneticLastName = vcard.getExtendedProperty(PROPERTY_PHONETIC_LAST_NAME);
		if (phoneticFirstName != null)
			phoneticGivenName = phoneticFirstName.getValue();
		if (phoneticMiddleName != null)
			this.phoneticMiddleName = phoneticMiddleName.getValue();
		if (phoneticLastName != null)
			phoneticFamilyName = phoneticLastName.getValue();
		
		phoneNumbers = vcard.getTelephoneNumbers();
		emails = vcard.getEmails();
		
		List<Photo> photos = vcard.getPhotos();
		if (!photos.isEmpty()) {
			Photo photo = photos.get(0);
			this.photo = photo.getData();
			if (this.photo == null) {
				try {
					URL url = new URL(photo.getUrl());
					@Cleanup InputStream in = url.openStream();
					this.photo = IOUtils.toByteArray(in);
				} catch(IOException ex) {
					Log.w(TAG, "Couldn't fetch photo from referenced URL", ex);
				}
			}
		}
		
		if (vcard.getOrganization() != null) {
			List<String> organizations = vcard.getOrganization().getValues();
			if (!organizations.isEmpty())
				organization = organizations.get(0);
		}
		List<Role> roles = vcard.getRoles();
		if (!roles.isEmpty())
			role = roles.get(0).getValue();
		
		impps = vcard.getImpps();
		
		Nickname nicknames = vcard.getNickname();
		if (nicknames != null && nicknames.getValues() != null)
			nickName = StringUtils.join(nicknames.getValues(), ", ");
		
		List<String> notes = new LinkedList<String>();
		for (Note note : vcard.getNotes())
			notes.add(note.getValue());
		if (!notes.isEmpty())
			note = StringUtils.join(notes, "\n---\n");

		addresses = vcard.getAddresses();
		
		for (Url url : vcard.getUrls()) {
			URL = url.getValue();
			break;
		}

		birthDay = vcard.getBirthday();
		anniversary = vcard.getAnniversary();
	}

	
	@Override
	public String toEntity() throws IOException {
		VCard vcard = new VCard();
		
		if (uid != null)
			vcard.setUid(new Uid(uid));
		
		if (starred)
			vcard.setExtendedProperty(PROPERTY_STARRED, "1");
		
		if (displayName != null)
			vcard.setFormattedName(displayName);

		if (familyName != null || middleName != null || givenName != null) {
			StructuredName n = new StructuredName();
			if (prefix != null)
				for (String p : StringUtils.split(prefix))
					n.addPrefix(p);
			n.setGiven(givenName);
			if (middleName != null)
				for (String middle : StringUtils.split(middleName))
					n.addAdditional(middle);
			n.setFamily(familyName);
			if (suffix != null)
				for (String s : StringUtils.split(suffix))
					n.addSuffix(s);
			vcard.setStructuredName(n);
		}
		
		if (phoneticGivenName != null)
			vcard.addExtendedProperty(PROPERTY_PHONETIC_FIRST_NAME, phoneticGivenName);
		if (phoneticMiddleName != null)
			vcard.addExtendedProperty(PROPERTY_PHONETIC_MIDDLE_NAME, phoneticMiddleName);
		if (phoneticFamilyName != null)
			vcard.addExtendedProperty(PROPERTY_PHONETIC_LAST_NAME, phoneticFamilyName);
		
		for (Telephone phoneNumber : phoneNumbers)
			vcard.addTelephoneNumber(phoneNumber);
		
		for (Email email : emails)
			vcard.addEmail(email);

		if (photo != null)
			vcard.addPhoto(new Photo(photo, ImageType.JPEG));
		
		if (organization != null) {
			Organization org = new Organization();
			org.addValue(organization);
			vcard.addOrganization(org);
		}
		if (role != null)
			vcard.addRole(role);
		
		for (Impp impp : impps)
			vcard.addImpp(impp);

		if (nickName != null && !nickName.isEmpty())
			vcard.setNickname(nickName);
		
		if (note != null && !note.isEmpty())
			vcard.addNote(note);
		
		for (Address address : addresses)
			vcard.addAddress(address);
		
		if (URL != null && !URL.isEmpty())
			vcard.addUrl(URL);
		
		if (anniversary != null)
			vcard.setAnniversary(anniversary);
		if (birthDay != null)
			vcard.setBirthday(birthDay);
		
		vcard.setProdId("DAVdroid/" + Constants.APP_VERSION);
		vcard.setRevision(Revision.now());
		return Ezvcard
			.write(vcard)
			.version(VCardVersion.V3_0)
			.go();
	}
}
