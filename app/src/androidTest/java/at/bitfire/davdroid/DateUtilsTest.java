/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid;

import junit.framework.TestCase;

import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateList;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;
import net.fortuna.ical4j.model.parameter.Value;
import net.fortuna.ical4j.model.property.DateListProperty;
import net.fortuna.ical4j.model.property.ExDate;
import net.fortuna.ical4j.model.property.RDate;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

public class DateUtilsTest extends TestCase {
	private static final String tzIdVienna = "Europe/Vienna";

	public void testRecurrenceSetsToAndroidString() throws ParseException {
		// one entry without time zone (implicitly UTC)
		final List<RDate> list = new ArrayList<>(2);
		list.add(new RDate(new DateList("20150101T103010Z,20150102T103020Z", Value.DATE_TIME)));
		assertEquals("20150101T103010Z,20150102T103020Z", DateUtils.recurrenceSetsToAndroidString(list, false));

		// two entries (previous one + this), both with time zone Vienna
		list.add(new RDate(new DateList("20150103T113030,20150704T123040", Value.DATE_TIME)));
		final TimeZone tz = DateUtils.tzRegistry.getTimeZone(tzIdVienna);
		for (RDate rdate : list)
			rdate.setTimeZone(tz);
		assertEquals("20150101T103010Z,20150102T103020Z,20150103T103030Z,20150704T103040Z", DateUtils.recurrenceSetsToAndroidString(list, false));

		// DATEs (without time) have to be converted to <date>T000000Z for Android
		list.clear();
		list.add(new RDate(new DateList("20150101,20150702", Value.DATE)));
		assertEquals("20150101T000000Z,20150702T000000Z", DateUtils.recurrenceSetsToAndroidString(list, true));

		// DATE-TIME (floating time or UTC) recurrences for all-day events have to converted to <date>T000000Z for Android
		list.clear();
		list.add(new RDate(new DateList("20150101T000000,20150702T000000Z", Value.DATE_TIME)));
		assertEquals("20150101T000000Z,20150702T000000Z", DateUtils.recurrenceSetsToAndroidString(list, true));
	}

	public void testAndroidStringToRecurrenceSets() throws ParseException {
		// list of UTC times
		ExDate exDate = (ExDate)DateUtils.androidStringToRecurrenceSet("20150101T103010Z,20150702T103020Z", ExDate.class, false);
		DateList exDates = exDate.getDates();
		assertEquals(Value.DATE_TIME, exDates.getType());
		assertTrue(exDates.isUtc());
		assertEquals(2, exDates.size());
		assertEquals(1420108210000L, exDates.get(0).getTime());
		assertEquals(1435833020000L, exDates.get(1).getTime());

		// list of time zone times
		exDate = (ExDate)DateUtils.androidStringToRecurrenceSet(tzIdVienna + ";20150101T103010,20150702T103020", ExDate.class, false);
		exDates = exDate.getDates();
		assertEquals(Value.DATE_TIME, exDates.getType());
		assertEquals(DateUtils.tzRegistry.getTimeZone(tzIdVienna), exDates.getTimeZone());
		assertEquals(2, exDates.size());
		assertEquals(1420104610000L, exDates.get(0).getTime());
		assertEquals(1435825820000L, exDates.get(1).getTime());

		// list of dates
		exDate = (ExDate)DateUtils.androidStringToRecurrenceSet("20150101T103010Z,20150702T103020Z", ExDate.class, true);
		exDates = exDate.getDates();
		assertEquals(Value.DATE, exDates.getType());
		assertEquals(2, exDates.size());
		assertEquals("20150101", exDates.get(0).toString());
		assertEquals("20150702", exDates.get(1).toString());
	}

}
