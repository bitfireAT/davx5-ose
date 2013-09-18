package at.bitfire.davdroid.resource;

import java.io.IOException;
import java.io.InputStream;

import junit.framework.Assert;
import android.content.res.AssetManager;
import android.test.InstrumentationTestCase;

public class ContactTest extends InstrumentationTestCase {
	AssetManager assetMgr;
	
	public void setUp() {
		assetMgr = getInstrumentation().getContext().getResources().getAssets();
	}

	
	public void testParseVcard3() throws IOException {
		String fname = "vcard3-sample1.vcf";
		InputStream in = assetMgr.open(fname, AssetManager.ACCESS_STREAMING);
		
		Contact c = new Contact(fname, null);
		c.parseEntity(in);
		
		Assert.assertEquals("Forrest Gump", c.getDisplayName());
		Assert.assertEquals("Forrest", c.getGivenName());
		Assert.assertEquals("Gump", c.getFamilyName());
		
		Assert.assertEquals(2, c.getPhoneNumbers().size());
		Assert.assertEquals("(111) 555-1212", c.getPhoneNumbers().get(0).getText());
		
		Assert.assertEquals(1, c.getEmails().size());
		Assert.assertEquals("forrestgump@example.com", c.getEmails().get(0).getValue());
	}

}
