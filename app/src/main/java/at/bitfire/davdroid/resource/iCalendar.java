/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.resource;

import android.text.format.Time;
import android.util.Log;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.DefaultTimeZoneRegistryFactory;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.parameter.Value;
import net.fortuna.ical4j.model.property.DateProperty;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.util.CompatibilityHints;
import net.fortuna.ical4j.util.SimpleHostInfo;
import net.fortuna.ical4j.util.UidGenerator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;

import at.bitfire.davdroid.DateUtils;
import at.bitfire.davdroid.syncadapter.DavSyncAdapter;
import lombok.Getter;

public abstract class iCalendar extends Resource {
	static private final String TAG = "DAVdroid.iCal";

	// static ical4j initialization
	static {
		CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_UNFOLDING, true);
		CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_PARSING, true);
		CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_OUTLOOK_COMPATIBILITY, true);

		// disable automatic time-zone updates (causes unwanted network traffic)
		System.setProperty("net.fortuna.ical4j.timezone.update.enabled", "false");
	}

	static protected final TimeZoneRegistry tzRegistry = new DefaultTimeZoneRegistryFactory().createRegistry();


	public iCalendar(long localID, String name, String ETag) {
		super(localID, name, ETag);
	}

	public iCalendar(String name, String ETag) {
		super(name, ETag);
	}


	@Override
	public void initialize() {
		generateUID();
		name = uid + ".ics";
	}

	protected void generateUID() {
		UidGenerator generator = new UidGenerator(new SimpleHostInfo(DavSyncAdapter.getAndroidID()), String.valueOf(android.os.Process.myPid()));
		uid = generator.generateUid().getValue().replace("@", "_");
	}


	@Override
	public String getMimeType() {
		return "text/calendar";
	}


	// time zone helpers

	protected static boolean hasTime(DateProperty date) {
		return date.getDate() instanceof DateTime;
	}

	protected static String getTzId(DateProperty date) {
		if (date.isUtc() || !hasTime(date))
			return Time.TIMEZONE_UTC;
		else if (date.getTimeZone() != null)
			return date.getTimeZone().getID();
		else if (date.getParameter(Value.TZID) != null)
			return date.getParameter(Value.TZID).getValue();

		// fallback
		return Time.TIMEZONE_UTC;
	}

	/* guess matching Android timezone ID */
	protected static void validateTimeZone(DateProperty date) {
		if (date.isUtc() || !hasTime(date))
			return;

		String tzID = getTzId(date);
		if (tzID == null)
			return;

		String localTZ = DateUtils.findAndroidTimezoneID(tzID);
		date.setTimeZone(tzRegistry.getTimeZone(localTZ));
	}

	public static String TimezoneDefToTzId(String timezoneDef) throws IllegalArgumentException {
		try {
			if (timezoneDef != null) {
				CalendarBuilder builder = new CalendarBuilder();
				net.fortuna.ical4j.model.Calendar cal = builder.build(new StringReader(timezoneDef));
				VTimeZone timezone = (VTimeZone)cal.getComponent(VTimeZone.VTIMEZONE);
				return timezone.getTimeZoneId().getValue();
			}
		} catch (Exception ex) {
			Log.w(TAG, "Can't understand time zone definition, ignoring", ex);
		}
		throw new IllegalArgumentException();
	}

}
