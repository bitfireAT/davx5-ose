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
import android.util.Log;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.CharEncoding;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import at.bitfire.davdroid.ArrayUtils;
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
		assertEquals("text/vcard; charset=utf-8", c.getContentType().toString().toLowerCase());
		assertTrue(new String(c.toEntity().toByteArray()).contains("VERSION:3.0"));

		// now let's generate VCard 4.0
		c.vCardVersion = VCardVersion.V4_0;
		assertEquals("text/vcard; version=4.0", c.getContentType().toString());
		assertTrue(new String(c.toEntity().toByteArray()).contains("VERSION:4.0"));
	}
	
	public void testReferenceVCard3() throws IOException, InvalidResourceException {
		Contact c = parseVCF("reference-vcard3.vcf", Charset.forName(CharEncoding.UTF_8));

		assertEquals("Gümp", c.familyName);
		assertEquals("Förrest", c.givenName);
		assertEquals("Förrest Gümp", c.displayName);
		assertEquals("Bubba Gump Shrimpß Co.", c.organization.getValues().get(0));
		assertEquals("Shrimp Man", c.jobTitle);
		
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
		assertTrue(Arrays.equals(c.photo, expectedPhoto));
	}

	public void testParseInvalidUnknownProperties() throws IOException {
		Contact c = parseVCF("invalid-unknown-properties.vcf");
		assertEquals("VCard with invalid unknown properties", c.displayName);
		assertNull(c.unknownProperties);
	}

	public void testParseLatin1() throws IOException {
		Contact c = parseVCF("latin1.vcf", Charset.forName(CharEncoding.ISO_8859_1));
		assertEquals("Özkan Äuçek", c.displayName);
		assertEquals("Özkan", c.givenName);
		assertEquals("Äuçek", c.familyName);
		assertNull(c.unknownProperties);
	}
	
	
	protected Contact parseVCF(String fname, Charset charset) throws IOException {
		@Cleanup InputStream in = assetMgr.open(fname, AssetManager.ACCESS_STREAMING);
		Contact c = new Contact(fname, null);
		c.parseEntity(in, charset, new Resource.AssetDownloader() {
            @Override
            public byte[] download(URI uri) throws URISyntaxException, IOException, HttpException, DavException {
                return IOUtils.toByteArray(uri);
            }
        });
		return c;
	}

	protected Contact parseVCF(String fname) throws IOException {
		return parseVCF(fname, null);
	}
}
