/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.resource;

import android.content.res.AssetManager;
import android.test.InstrumentationTestCase;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

import at.bitfire.davdroid.webdav.DavException;
import at.bitfire.davdroid.webdav.HttpException;
import ezvcard.VCardVersion;
import ezvcard.property.Email;
import ezvcard.property.Telephone;
import lombok.Cleanup;

public class ContactTest extends InstrumentationTestCase {
	AssetManager assetMgr;
	
	public void setUp() throws IOException, InvalidResourceException {
		assetMgr = getInstrumentation().getContext().getResources().getAssets();
	}

	public void testGenerateDifferentVersions() throws Exception {
		Contact c = new Contact("test.vcf", null);

		// should generate VCard 3.0 by default
		assertEquals("text/vcard", c.getMimeType());
		assertTrue(new String(c.toEntity().toByteArray()).contains("VERSION:3.0"));

		// now let's generate VCard 4.0
		c.setVCardVersion(VCardVersion.V4_0);
		assertEquals("text/vcard;version=4.0", c.getMimeType());
		assertTrue(new String(c.toEntity().toByteArray()).contains("VERSION:4.0"));
	}
	
	public void testReferenceVCard() throws IOException, InvalidResourceException {
		Contact c = parseVCF("reference.vcf");
		assertEquals("Gump", c.getFamilyName());
		assertEquals("Forrest", c.getGivenName());
		assertEquals("Forrest Gump", c.getDisplayName());
		assertEquals("Bubba Gump Shrimp Co.", c.getOrganization().getValues().get(0));
		assertEquals("Shrimp Man", c.getJobTitle());
		
		Telephone phone1 = c.getPhoneNumbers().get(0);
		assertEquals("(111) 555-1212", phone1.getText());
		assertEquals("WORK", phone1.getParameters("TYPE").get(0));
		assertEquals("VOICE", phone1.getParameters("TYPE").get(1));
		
		Telephone phone2 = c.getPhoneNumbers().get(1);
		assertEquals("(404) 555-1212", phone2.getText());
		assertEquals("HOME", phone2.getParameters("TYPE").get(0));
		assertEquals("VOICE", phone2.getParameters("TYPE").get(1));
		
		Email email = c.getEmails().get(0);
		assertEquals("forrestgump@example.com", email.getValue());
		assertEquals("PREF", email.getParameters("TYPE").get(0));
		assertEquals("INTERNET", email.getParameters("TYPE").get(1));

		@Cleanup InputStream photoStream = assetMgr.open("davdroid-logo-192.png", AssetManager.ACCESS_STREAMING);
		byte[] expectedPhoto = IOUtils.toByteArray(photoStream);
		assertTrue(Arrays.equals(c.getPhoto(), expectedPhoto));
	}

	public void testParseInvalidUnknownProperties() throws IOException {
		Contact c = parseVCF("invalid-unknown-properties.vcf");
		assertEquals("VCard with invalid unknown properties", c.getDisplayName());
		assertNull(c.getUnknownProperties());
	}
	
	
	protected Contact parseVCF(String fname) throws IOException {
		@Cleanup InputStream in = assetMgr.open(fname, AssetManager.ACCESS_STREAMING);
		Contact c = new Contact(fname, null);
		c.parseEntity(in, new Resource.AssetDownloader() {
            @Override
            public byte[] download(URI uri) throws URISyntaxException, IOException, HttpException, DavException {
                return IOUtils.toByteArray(uri);
            }
        });
		return c;
	}
}
