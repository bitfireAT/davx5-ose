package at.bitfire.davdroid.test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.TimeZone;

import junit.framework.Assert;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Date;
import android.content.res.AssetManager;
import android.test.InstrumentationTestCase;
import android.text.format.Time;
import at.bitfire.davdroid.resource.Event;

public class CalendarTest extends InstrumentationTestCase {
	AssetManager assetMgr;
	
	public void setUp() {
		assetMgr = getInstrumentation().getContext().getResources().getAssets();
	}
	
	
	public void testTimeZonesByEvolution() throws IOException, ParserException {
		Event e = parseCalendar("vienna-evolution.ics");
		Assert.assertEquals("Test-Ereignis im schönen Wien", e.getSummary());		
		
		//DTSTART;TZID=/freeassociation.sourceforge.net/Tzfile/Europe/Vienna:20131009T170000
		//Assert.assertEquals(1381327200, e.getDtStartInMillis());
	}
	
	
	protected Event parseCalendar(String fname) throws IOException, ParserException {
		InputStream in = assetMgr.open(fname, AssetManager.ACCESS_STREAMING);
		Event e = new Event(fname, null);
		e.parseEntity(in);
		return e;
	}
}
