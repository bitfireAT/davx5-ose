/*******************************************************************************
 * Copyright (c) 2013 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.resource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

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
import ezvcard.property.Title;
import ezvcard.property.Uid;
import ezvcard.property.Url;

@ToString(callSuper = true)
public class Contact extends Resource {
	private final static String TAG = "davdroid.Contact";
	
	public final static String
		PROPERTY_STARRED = "X-DAVDROID-STARRED",
		PROPERTY_PHONETIC_FIRST_NAME = "X-PHONETIC-FIRST-NAME",
		PROPERTY_PHONETIC_MIDDLE_NAME = "X-PHONETIC-MIDDLE-NAME",
		PROPERTY_PHONETIC_LAST_NAME = "X-PHONETIC-LAST-NAME",
		PROPERTY_SIP = "X-SIP";
		
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
	@Getter @Setter private String note;
	@Getter @Setter private Organization organization;
	@Getter @Setter private String jobTitle, jobDescription;
	
	@Getter @Setter private byte[] photo;
	
	@Getter @Setter private Anniversary anniversary;
	@Getter @Setter private Birthday birthDay;

	@Getter private List<Telephone> phoneNumbers = new LinkedList<Telephone>();
	@Getter private List<Email> emails = new LinkedList<Email>();
	@Getter private List<Impp> impps = new LinkedList<Impp>();
	@Getter private List<Address> addresses = new LinkedList<Address>();
	@Getter private List<String> URLs = new LinkedList<String>();


	/* instance methods */
	
	public Contact(String name, String ETag) {
		super(name, ETag);
	}
	
	public Contact(long localID, String resourceName, String eTag) {
		super(localID, resourceName, eTag);
	}

	
	@Override
	public void generateUID() {
		uid = UUID.randomUUID().toString();
	}
	
	@Override
	public void generateName() {
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
			Log.w(TAG, "Received VCard without UID, generating new one");
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
		
		for (Photo photo : vcard.getPhotos()) {
			this.photo = photo.getData();
			break;
		}

		organization = vcard.getOrganization();
		for (Title title : vcard.getTitles()) {
			jobTitle = title.getValue();
			break;
		}
		for (Role role : vcard.getRoles()) {
			this.jobDescription = role.getValue();
			break;
		}
	
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
		
		for (Url url : vcard.getUrls())
			URLs.add(url.getValue());

		birthDay = vcard.getBirthday();
		anniversary = vcard.getAnniversary();
		
		// get X-SIP and import as IMPP
		for (RawProperty sip : vcard.getExtendedProperties(PROPERTY_SIP))
			impps.add(new Impp("sip", sip.getValue()));
	}

	
	@Override
	public ByteArrayOutputStream toEntity() throws IOException {
		VCard vcard = new VCard();
		vcard.setProdId("DAVdroid/" + Constants.APP_VERSION + " (ez-vcard/" + Ezvcard.VERSION + ")");
		
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

		if (organization != null)
			vcard.addOrganization(organization);
		if (jobTitle != null)
			vcard.addTitle(jobTitle);
		if (jobDescription != null)
			vcard.addRole(jobDescription);
		
		for (Impp impp : impps)
			vcard.addImpp(impp);

		if (nickName != null && !nickName.isEmpty())
			vcard.setNickname(nickName);
		
		if (note != null && !note.isEmpty())
			vcard.addNote(note);
		
		for (Address address : addresses)
			vcard.addAddress(address);
		
		for (String url : URLs)
			vcard.addUrl(url);
		
		if (anniversary != null)
			vcard.setAnniversary(anniversary);
		if (birthDay != null)
			vcard.setBirthday(birthDay);
		
		if (photo != null)
			vcard.addPhoto(new Photo(photo, ImageType.JPEG));
		
		vcard.setRevision(Revision.now());
		
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		Ezvcard
			.write(vcard)
			.version(VCardVersion.V3_0)
			.versionStrict(false)
			.prodId(false)		// we provide our own PRODID
			.go(os);
		return os;
	}
}
