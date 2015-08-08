/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.resource;

import android.util.Log;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.CalendarParserFactory;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.ParameterFactory;
import net.fortuna.ical4j.model.ParameterFactoryImpl;
import net.fortuna.ical4j.model.ParameterFactoryRegistry;
import net.fortuna.ical4j.model.PropertyFactoryRegistry;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.property.DateProperty;
import net.fortuna.ical4j.util.CompatibilityHints;
import net.fortuna.ical4j.util.SimpleHostInfo;
import net.fortuna.ical4j.util.Strings;
import net.fortuna.ical4j.util.UidGenerator;

import org.apache.commons.codec.CharEncoding;
import org.apache.http.entity.ContentType;

import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.util.TimeZone;

import at.bitfire.davdroid.DateUtils;
import at.bitfire.davdroid.syncadapter.DavSyncAdapter;
import lombok.Getter;
import lombok.NonNull;

public abstract class iCalendar extends Resource {
	private static final String TAG = "DAVdroid.iCal";

	// static ical4j initialization
	static {
		CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_UNFOLDING, true);
		CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_PARSING, true);
		CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_OUTLOOK_COMPATIBILITY, true);
	}

	public static final ContentType MIME_ICALENDAR = ContentType.create("text/calendar", CharEncoding.UTF_8);


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
	public ContentType getContentType() {
		return MIME_ICALENDAR;
	}


	// time zone helpers

	protected static boolean isDateTime(DateProperty date) {
		return date.getDate() instanceof DateTime;
	}

	/**
	 * Ensures that a given DateProperty has a time zone with an ID that is available in Android.
	 * @param date   DateProperty to validate. Values which are not DATE-TIME will be ignored.
	 */
	protected static void validateTimeZone(DateProperty date) {
		if (isDateTime(date)) {
			final TimeZone tz = date.getTimeZone();
			if (tz == null)
				return;
			final String tzID = tz.getID();
			if (tzID == null)
				return;

			String deviceTzID = DateUtils.findAndroidTimezoneID(tzID);
			if (!tzID.equals(deviceTzID))
				date.setTimeZone(DateUtils.tzRegistry.getTimeZone(deviceTzID));
		}
	}

	/**
	 * Takes a string with a timezone definition and returns the time zone ID.
	 * @param timezoneDef   time zone definition (VCALENDAR with VTIMEZONE component)
	 * @return              time zone id (TZID)  if VTIMEZONE contains a TZID,
	 *                      null                 otherwise
	 */
	public static String TimezoneDefToTzId(@NonNull String timezoneDef) {
		try {
			CalendarBuilder builder = new CalendarBuilder();
			net.fortuna.ical4j.model.Calendar cal = builder.build(new StringReader(timezoneDef));
			VTimeZone timezone = (VTimeZone)cal.getComponent(VTimeZone.VTIMEZONE);
			if (timezone != null && timezone.getTimeZoneId() != null)
				return timezone.getTimeZoneId().getValue();
		} catch (IOException|ParserException e) {
			Log.e(TAG, "Can't understand time zone definition", e);
		}
		return null;
	}


	// ical4j helpers and extensions

	private static final ParameterFactoryRegistry parameterFactoryRegistry = new ParameterFactoryRegistry();
	static {
		parameterFactoryRegistry.register(Email.PARAMETER_NAME, Email.FACTORY);
	}
	protected static final CalendarBuilder calendarBuilder = new CalendarBuilder(
			CalendarParserFactory.getInstance().createParser(),
			new PropertyFactoryRegistry(), parameterFactoryRegistry, DateUtils.tzRegistry);

	public static class Email extends Parameter {
		/* EMAIL property for ATTENDEE properties, as used by iCloud:
		   ATTENDEE;EMAIL=bla@domain.tld;/path/to/principal
		*/
		public static final ParameterFactory FACTORY = new Factory();

		public static final String PARAMETER_NAME = "EMAIL";
		@Getter	private String value;

		protected Email() {
			super(PARAMETER_NAME, ParameterFactoryImpl.getInstance());
		}

		public Email(String aValue) {
			super(PARAMETER_NAME, ParameterFactoryImpl.getInstance());
			value = Strings.unquote(aValue);
		}

		public static class Factory implements ParameterFactory {
			@Override
			public Parameter createParameter(String name, String value) throws URISyntaxException {
				return new Email(value);
			}
		}
	}


}
