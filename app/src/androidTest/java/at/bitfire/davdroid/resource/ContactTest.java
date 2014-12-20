/*******************************************************************************
 * Copyright (c) 2014 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.resource.test;

import java.io.IOException;
import java.io.InputStream;

import ezvcard.property.Email;
import ezvcard.property.Telephone;
import lombok.Cleanup;
import android.content.res.AssetManager;
import android.test.InstrumentationTestCase;
import at.bitfire.davdroid.resource.Contact;
import at.bitfire.davdroid.resource.InvalidResourceException;

public class ContactTest extends InstrumentationTestCase {
	AssetManager assetMgr;
	
	public void setUp() throws IOException, InvalidResourceException {
		assetMgr = getInstrumentation().getContext().getResources().getAssets();
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
	}
	
	public void testParseInvalidUnknownProperties() throws IOException, InvalidResourceException {
		Contact c = parseVCF("invalid-unknown-properties.vcf");
		assertEquals("VCard with invalid unknown properties", c.getDisplayName());
		assertNull(c.getUnknownProperties());
	}
	
	
	protected Contact parseVCF(String fname) throws IOException, InvalidResourceException {
		@Cleanup InputStream in = assetMgr.open(fname, AssetManager.ACCESS_STREAMING);
		Contact c = new Contact(fname, null);
		c.parseEntity(in);
		return c;
	}
}
