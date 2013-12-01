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

import net.fortuna.ical4j.data.ParserException;
import android.content.res.AssetManager;
import android.test.InstrumentationTestCase;
import at.bitfire.davdroid.resource.Event;

public class CalendarTest extends InstrumentationTestCase {
	AssetManager assetMgr;
	
	public void setUp() {
		assetMgr = getInstrumentation().getContext().getResources().getAssets();
	}
	
	
	public void testTimeZonesByEvolution() throws IOException, ParserException {
		Event e = parseCalendar("vienna-evolution.ics");
		assertEquals("Test-Ereignis im schönen Wien", e.getSummary());		
		
		//DTSTART;TZID=/freeassociation.sourceforge.net/Tzfile/Europe/Vienna:20131009T170000
		/*assertEquals(1381330800000L, e.getDtStartInMillis());
		assertEquals(1381334400000L, (long)e.getDtEndInMillis());*/
	}
	
	
	protected Event parseCalendar(String fname) throws IOException, ParserException {
		InputStream in = assetMgr.open(fname, AssetManager.ACCESS_STREAMING);
		Event e = new Event(fname, null);
		e.parseEntity(in);
		return e;
	}
}
