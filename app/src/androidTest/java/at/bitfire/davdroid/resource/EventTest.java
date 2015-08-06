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

import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.util.TimeZones;

import java.io.IOException;
import java.io.InputStream;

import at.bitfire.davdroid.DateUtils;
import lombok.Cleanup;

public class EventTest extends InstrumentationTestCase {
	protected final TimeZone tzVienna = DateUtils.tzRegistry.getTimeZone("Europe/Vienna");

	AssetManager assetMgr;
	
	Event eOnThatDay, eAllDay1Day, eAllDay10Days, eAllDay0Sec;
	
	public void setUp() throws IOException, InvalidResourceException {
		assetMgr = getInstrumentation().getContext().getResources().getAssets();
		
		eOnThatDay = parseCalendar("event-on-that-day.ics");
		eAllDay1Day = parseCalendar("all-day-1day.ics");
		eAllDay10Days = parseCalendar("all-day-10days.ics");
		eAllDay0Sec = parseCalendar("all-day-0sec.ics");
	}


	public void testGetTzID() throws Exception {
		// DATE (without time)
		assertEquals(TimeZones.UTC_ID, Event.getTzId(new DtStart(new Date("20150101"))));

		// DATE-TIME without time zone (floating time): should be UTC (because net.fortuna.ical4j.timezone.date.floating=false)
		assertEquals(TimeZones.UTC_ID, Event.getTzId(new DtStart(new DateTime("20150101T000000"))));

		// DATE-TIME without time zone (UTC)
		assertEquals(TimeZones.UTC_ID, Event.getTzId(new DtStart(new DateTime(1438607288000L))));

		// DATE-TIME with time zone
		assertEquals(tzVienna.getID(), Event.getTzId(new DtStart(new DateTime("20150101T000000", tzVienna))));
	}


	public void testRecurringWithException() throws Exception {
		Event event = parseCalendar("recurring-with-exception1.ics");
		assertTrue(event.isAllDay());

		assertEquals(1, event.getExceptions().size());
		Event exception = event.getExceptions().get(0);
		assertEquals("20150503", exception.getRecurrenceId().getValue());
		assertEquals("Another summary for the third day", exception.getSummary());
	}
	
	public void testStartEndTimes() throws IOException, ParserException, InvalidResourceException {
		// event with start+end date-time
		Event eViennaEvolution = parseCalendar("vienna-evolution.ics");
		assertEquals(1381330800000L, eViennaEvolution.getDtStartInMillis());
		assertEquals("Europe/Vienna", eViennaEvolution.getDtStartTzID());
		assertEquals(1381334400000L, eViennaEvolution.getDtEndInMillis());
		assertEquals("Europe/Vienna", eViennaEvolution.getDtEndTzID());
	}
	
	public void testStartEndTimesAllDay() throws IOException, ParserException {
		// event with start date only
		assertEquals(868838400000L, eOnThatDay.getDtStartInMillis());
		assertEquals(TimeZones.UTC_ID, eOnThatDay.getDtStartTzID());
		// DTEND missing in VEVENT, must have been set to DTSTART+1 day
		assertEquals(868838400000L + 86400000, eOnThatDay.getDtEndInMillis());
		assertEquals(TimeZones.UTC_ID, eOnThatDay.getDtEndTzID());
		
		// event with start+end date for all-day event (one day)
		assertEquals(868838400000L, eAllDay1Day.getDtStartInMillis());
		assertEquals(TimeZones.UTC_ID, eAllDay1Day.getDtStartTzID());
		assertEquals(868838400000L + 86400000, eAllDay1Day.getDtEndInMillis());
		assertEquals(TimeZones.UTC_ID, eAllDay1Day.getDtEndTzID());
		
		// event with start+end date for all-day event (ten days)
		assertEquals(868838400000L, eAllDay10Days.getDtStartInMillis());
		assertEquals(TimeZones.UTC_ID, eAllDay10Days.getDtStartTzID());
		assertEquals(868838400000L + 10*86400000, eAllDay10Days.getDtEndInMillis());
		assertEquals(TimeZones.UTC_ID, eAllDay10Days.getDtEndTzID());
		
		// event with start+end date on some day (invalid 0 sec-event)
		assertEquals(868838400000L, eAllDay0Sec.getDtStartInMillis());
		assertEquals(TimeZones.UTC_ID, eAllDay0Sec.getDtStartTzID());
		// DTEND invalid in VEVENT, must have been set to DTSTART+1 day
		assertEquals(868838400000L + 86400000, eAllDay0Sec.getDtEndInMillis());
		assertEquals(TimeZones.UTC_ID, eAllDay0Sec.getDtEndTzID());
	}
	
	public void testUnfolding() throws IOException, InvalidResourceException {
		Event e = parseCalendar("two-line-description-without-crlf.ics");
		assertEquals("http://www.tgbornheim.de/index.php?sessionid=&page=&id=&sportcentergroup=&day=6", e.getDescription());
	}
	
	
	protected Event parseCalendar(String fname) throws IOException, InvalidResourceException {
		@Cleanup InputStream in = assetMgr.open(fname, AssetManager.ACCESS_STREAMING);
		Event e = new Event(fname, null);
		e.parseEntity(in, null, null);
		return e;
	}
}
