/*******************************************************************************
 * Copyright (c) 2013 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.test;

import java.io.IOException;
import java.io.InputStream;

import lombok.Cleanup;
import net.fortuna.ical4j.data.ParserException;
import android.content.res.AssetManager;
import android.test.InstrumentationTestCase;
import at.bitfire.davdroid.resource.Contact;
import ezvcard.VCardException;
import ezvcard.property.Impp;

public class ContactTest extends InstrumentationTestCase {
	AssetManager assetMgr;
	
	public void setUp() {
		assetMgr = getInstrumentation().getContext().getResources().getAssets();
	}
	
	public void testIMPP() throws VCardException, IOException {
		Contact c = parseVCard("impp.vcf");
		assertEquals("test mctest", c.getDisplayName());
		
		Impp jabber = c.getImpps().get(0);
		assertNull(jabber.getProtocol());
		assertEquals("test-without-valid-scheme@test.tld", jabber.getHandle());
	}

	
	public void testParseVcard3() throws IOException, ParserException {
		Contact c = parseVCard("vcard3-sample1.vcf");
		
		assertEquals("Forrest Gump", c.getDisplayName());
		assertEquals("Forrest", c.getGivenName());
		assertEquals("Gump", c.getFamilyName());
		
		assertEquals(2, c.getPhoneNumbers().size());
		assertEquals("(111) 555-1212", c.getPhoneNumbers().get(0).getText());
		
		assertEquals(1, c.getEmails().size());
		assertEquals("forrestgump@example.com", c.getEmails().get(0).getValue());
		
		assertFalse(c.isStarred());
	}

	
	private Contact parseVCard(String fileName) throws VCardException, IOException {
		@Cleanup InputStream in = assetMgr.open(fileName, AssetManager.ACCESS_STREAMING);
		
		Contact c = new Contact(fileName, null);
		c.parseEntity(in);
		
		return c;
	}
}
