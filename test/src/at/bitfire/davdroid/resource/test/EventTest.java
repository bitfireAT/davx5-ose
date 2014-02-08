/*******************************************************************************
 * Copyright (c) 2013 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.resource.test;

import java.io.IOException;
import java.io.InputStream;

import lombok.Cleanup;
import net.fortuna.ical4j.data.ParserException;
import android.content.res.AssetManager;
import android.test.InstrumentationTestCase;
import android.text.format.Time;
import at.bitfire.davdroid.resource.Event;
import at.bitfire.davdroid.resource.InvalidResourceException;

public class EventTest extends InstrumentationTestCase {
	AssetManager assetMgr;
	
	Event	eViennaEvolution,
			eOnThatDay, eAllDay1Day, eAllDay10Days, eAllDay0Sec;
	
	public void setUp() throws IOException, InvalidResourceException {
		assetMgr = getInstrumentation().getContext().getResources().getAssets();
		
		eViennaEvolution = parseCalendar("vienna-evolution.ics");
		eOnThatDay = parseCalendar("event-on-that-day.ics");
		eAllDay1Day = parseCalendar("all-day-1day.ics");
		eAllDay10Days = parseCalendar("all-day-10days.ics");
		eAllDay0Sec = parseCalendar("all-day-0sec.ics");
		
		//assertEquals("Test-Ereignis im schönen Wien", e.getSummary());
	}
	
	
	public void testStartEndTimes() throws IOException, ParserException {
		// event with start+end date-time
		assertEquals(1381330800000L, eViennaEvolution.getDtStartInMillis());
		assertEquals("Europe/Vienna", eViennaEvolution.getDtStartTzID());
		assertEquals(1381334400000L, eViennaEvolution.getDtEndInMillis());
		assertEquals("Europe/Vienna", eViennaEvolution.getDtEndTzID());
	}
	
	public void testStartEndTimesAllDay() throws IOException, ParserException {
		// event with start date only
		assertEquals(868838400000L, eOnThatDay.getDtStartInMillis());
		assertEquals(Time.TIMEZONE_UTC, eOnThatDay.getDtStartTzID());
		// DTEND missing in VEVENT, must have been set to DTSTART+1 day
		assertEquals(868838400000L + 86400000, eOnThatDay.getDtEndInMillis());
		assertEquals(Time.TIMEZONE_UTC, eOnThatDay.getDtEndTzID());
		
		// event with start+end date for all-day event (one day)
		assertEquals(868838400000L, eAllDay1Day.getDtStartInMillis());
		assertEquals(Time.TIMEZONE_UTC, eAllDay1Day.getDtStartTzID());
		assertEquals(868838400000L + 86400000, eAllDay1Day.getDtEndInMillis());
		assertEquals(Time.TIMEZONE_UTC, eAllDay1Day.getDtEndTzID());
		
		// event with start+end date for all-day event (ten days)
		assertEquals(868838400000L, eAllDay10Days.getDtStartInMillis());
		assertEquals(Time.TIMEZONE_UTC, eAllDay10Days.getDtStartTzID());
		assertEquals(868838400000L + 10*86400000, eAllDay10Days.getDtEndInMillis());
		assertEquals(Time.TIMEZONE_UTC, eAllDay10Days.getDtEndTzID());
		
		// event with start+end date on some day (invalid 0 sec-event)
		assertEquals(868838400000L, eAllDay0Sec.getDtStartInMillis());
		assertEquals(Time.TIMEZONE_UTC, eAllDay0Sec.getDtStartTzID());
		// DTEND invalid in VEVENT, must have been set to DTSTART+1 day
		assertEquals(868838400000L + 86400000, eAllDay0Sec.getDtEndInMillis());
		assertEquals(Time.TIMEZONE_UTC, eAllDay0Sec.getDtEndTzID());
	}
	
	
	protected Event parseCalendar(String fname) throws IOException, InvalidResourceException {
		@Cleanup InputStream in = assetMgr.open(fname, AssetManager.ACCESS_STREAMING);
		Event e = new Event(fname, null);
		e.parseEntity(in);
		return e;
	}
}
