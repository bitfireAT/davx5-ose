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
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.ValidationException;
import net.fortuna.ical4j.vcard.GroupRegistry;
import net.fortuna.ical4j.vcard.ParameterFactoryRegistry;
import net.fortuna.ical4j.vcard.Property;
import net.fortuna.ical4j.vcard.Property.Id;
import net.fortuna.ical4j.vcard.PropertyFactoryRegistry;
import net.fortuna.ical4j.vcard.VCard;
import net.fortuna.ical4j.vcard.VCardBuilder;
import net.fortuna.ical4j.vcard.VCardOutputter;
import net.fortuna.ical4j.vcard.property.BDay;
import net.fortuna.ical4j.vcard.property.Email;
import net.fortuna.ical4j.vcard.property.Fn;
import net.fortuna.ical4j.vcard.property.N;
import net.fortuna.ical4j.vcard.property.Nickname;
import net.fortuna.ical4j.vcard.property.Note;
import net.fortuna.ical4j.vcard.property.Photo;
import net.fortuna.ical4j.vcard.property.Telephone;
import net.fortuna.ical4j.vcard.property.Uid;
import net.fortuna.ical4j.vcard.property.Url;
import net.fortuna.ical4j.vcard.property.Version;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import android.util.Base64;
import android.util.Log;
import at.bitfire.davdroid.ical4j.PhoneticFirstName;
import at.bitfire.davdroid.ical4j.PhoneticLastName;
import at.bitfire.davdroid.ical4j.PhoneticMiddleName;
import at.bitfire.davdroid.ical4j.Starred;

@ToString(callSuper = true)
public class Contact extends Resource {
	private final static String TAG = "davdroid.Contact";
	
	@Getter @Setter boolean starred;
	
	@Getter @Setter private String displayName;
	@Getter @Setter private String prefix, givenName, middleName, familyName, suffix;
	@Getter @Setter private String phoneticGivenName, phoneticMiddleName, phoneticFamilyName;
	@Getter @Setter private String[] nickNames;

	@Getter @Setter private byte[] photo;
	
	@Getter @Setter private Date birthDay;

	
	public Contact(String name, String ETag) {
		super(name, ETag);
	}
	
	public Contact(long localID, String resourceName, String eTag) {
		super(resourceName, eTag);
		this.localID = localID;
	}

	
	/* multiple-record fields */
	
	@Getter private List<Email> emails = new LinkedList<Email>();
	public void addEmail(Email email) {
		emails.add(email);
	}
	
	@Getter private List<Telephone> phoneNumbers = new LinkedList<Telephone>();
	public void addPhoneNumber(Telephone number) {
		phoneNumbers.add(number);
	}
	
	@Getter private List<URI> URLs = new LinkedList<URI>();
	public void addURL(URI url) {
		URLs.add(url);
	}
	
	@Getter private List<String> notes = new LinkedList<String>();
	public void addNote(String note) {
		notes.add(note);
	}
	

	
	/* VCard methods */

	@Override
	public void parseEntity(InputStream is) throws IOException, ParserException {
		PropertyFactoryRegistry propertyFactoryRegistry = new PropertyFactoryRegistry();

		// add support for X-DAVDROID-STARRED
		propertyFactoryRegistry.register("X-" + Starred.PROPERTY_NAME, new Starred.Factory());
		
		// add support for phonetic names
		propertyFactoryRegistry.register("X-" + PhoneticFirstName.PROPERTY_NAME, new PhoneticFirstName.Factory());
		propertyFactoryRegistry.register("X-" + PhoneticMiddleName.PROPERTY_NAME, new PhoneticMiddleName.Factory());
		propertyFactoryRegistry.register("X-" + PhoneticLastName.PROPERTY_NAME, new PhoneticLastName.Factory());

		VCardBuilder builder = new VCardBuilder(
				new InputStreamReader(is),
				new GroupRegistry(),
				propertyFactoryRegistry,
				new ParameterFactoryRegistry()
		);
		
		VCard vcard;
		try {
			vcard = builder.build();
			if (vcard == null)
				return;
		} catch(Exception ex) {
			Log.e(TAG, "VCard parser exception", ex);
			throw new ParserException("VCard parser crashed", -1, ex);
		}
		
		Uid uid = (Uid)vcard.getProperty(Id.UID);
		if (uid != null)
			this.uid = uid.getValue();
		else {
			Log.w(TAG, "Received VCONTACT without UID, generating new one");
			this.uid = UUID.randomUUID().toString();
		}

		Starred starred = (Starred)vcard.getExtendedProperty(Starred.PROPERTY_NAME);
		this.starred = starred != null && starred.getValue().equals("1");
		
		Fn fn = (Fn)vcard.getProperty(Id.FN);
		displayName = (fn != null) ? fn.getValue() : null;

		Nickname nickname = (Nickname)vcard.getProperty(Id.NICKNAME);
		if (nickname != null)
			nickNames = nickname.getNames();
		
		// structured name
		N n = (N)vcard.getProperty(Id.N);
		if (n != null) {
			prefix = StringUtils.join(n.getPrefixes(), " ");
			givenName = n.getGivenName();
			middleName = StringUtils.join(n.getAdditionalNames(), " ");
			familyName = n.getFamilyName();
			suffix = StringUtils.join(n.getSuffixes(), " ");
		}
		
		// phonetic name
		PhoneticFirstName phoneticFirstName = (PhoneticFirstName)vcard.getExtendedProperty(PhoneticFirstName.PROPERTY_NAME);
		if (phoneticFirstName != null)
			phoneticGivenName = phoneticFirstName.getValue();
		PhoneticMiddleName phoneticMiddleName = (PhoneticMiddleName)vcard.getExtendedProperty(PhoneticMiddleName.PROPERTY_NAME);
		if (phoneticMiddleName != null)
			this.phoneticMiddleName = phoneticMiddleName.getValue();
		PhoneticLastName phoneticLastName = (PhoneticLastName)vcard.getExtendedProperty(PhoneticLastName.PROPERTY_NAME);
		if (phoneticLastName != null)
			phoneticFamilyName = phoneticLastName.getValue();
		
		for (Property p : vcard.getProperties(Id.EMAIL))
			emails.add((Email)p);
		
		for (Property p : vcard.getProperties(Id.TEL))
			phoneNumbers.add((Telephone)p);
		
		Photo photo = (Photo)vcard.getProperty(Id.PHOTO);
		if (photo != null) {
			if (photo.getBinary() != null)
				this.photo = photo.getBinary();
			else if (photo.getUri() != null) {
				URI uri = photo.getUri();
				try {
					if (uri.getScheme().equalsIgnoreCase("data"))
						this.photo = parseDataURI(uri);
					else {
						URL url = photo.getUri().toURL();
						HttpURLConnection urlConnection = (HttpURLConnection)url.openConnection();
						try {
							this.photo = IOUtils.toByteArray(urlConnection.getInputStream());
						} finally {
					    	urlConnection.disconnect();
					    }
					}
				} catch(MalformedURLException ex) {
					Log.w(TAG, "Malformed photo URL given in VCard");
				} catch(IOException ex) {
					Log.w(TAG, "Couldn't download photo from URL given in VCard");
				}
			}
		}
		
		for (Property p : vcard.getProperties(Id.BDAY))
			birthDay = ((BDay)p).getDate();
		
		for (Property p : vcard.getProperties(Id.URL))
			URLs.add(((Url)p).getUri());
		
		for (Property p : vcard.getProperties(Id.NOTE))
			notes.add(((Note)p).getValue());
	}

	
	@Override
	public String toEntity() throws IOException, ValidationException {
		VCard vcard = new VCard();
		List<Property> properties = vcard.getProperties();
		
		properties.add(Version.VERSION_4_0);
		
		try {
			if (uid != null)
				properties.add(new Uid(new URI(uid)));
		} catch (URISyntaxException e) {
			Log.e(TAG, "Couldn't write UID to VCard");
		}
		
		if (starred)
			properties.add(new Starred("1"));
		
		if (displayName != null)
			properties.add(new Fn(displayName));
		
		if (nickNames != null)
			properties.add(new Nickname(nickNames));

		if (photo != null) {
			try {
				String base64 = Base64.encodeToString(photo, Base64.NO_WRAP);
				properties.add(new Photo(new URI("data", "image/jpeg;base64," + base64, null)));
			} catch (URISyntaxException e) {
				Log.w(TAG, "Couldn't encode photo");
			}
		}
		
		if (birthDay != null)
			properties.add(new BDay(birthDay));
		
		if (familyName != null || middleName != null || givenName != null)
			properties.add(new N(familyName, givenName, StringUtils.split(middleName),
					StringUtils.split(prefix), StringUtils.split(suffix)));
		
		if (phoneticGivenName != null)
			properties.add(new PhoneticFirstName(phoneticGivenName));
		if (phoneticMiddleName != null)
			properties.add(new PhoneticMiddleName(phoneticMiddleName));
		if (phoneticFamilyName != null)
			properties.add(new PhoneticLastName(phoneticFamilyName));
		
		for (Email email : emails)
			properties.add(email);
		
		for (Telephone number : phoneNumbers)
			properties.add(number);
		
		for (URI uri : URLs)
			properties.add(new Url(uri));
		
		for (String note : notes)
			properties.add(new Note(note));
		
		StringWriter writer = new StringWriter();
		new VCardOutputter(true).output(vcard, writer);
		return writer.toString();
	}
	
	
	protected byte[] parseDataURI(URI uri) throws MalformedURLException {
		String content = uri.getSchemeSpecificPart();
		
		int commaPos = content.indexOf(',');
		if (commaPos == -1)
			throw new MalformedURLException("Malformed data: URL");
		
		String header = content.substring(0, commaPos);
		String data = content.substring(commaPos + 1);
		
		if (header.endsWith(";base64"))
			return Base64.decode(data, Base64.DEFAULT);
		
		return null;
	}

	@Override
	public void validate() throws ValidationException {
	}
}
