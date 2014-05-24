/*******************************************************************************
 * Copyright (c) 2014 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.resource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.SimpleTimeZone;
import java.util.TimeZone;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.DefaultTimeZoneRegistryFactory;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.ValidationException;
import net.fortuna.ical4j.model.component.VAlarm;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.parameter.Value;
import net.fortuna.ical4j.model.property.Attendee;
import net.fortuna.ical4j.model.property.Clazz;
import net.fortuna.ical4j.model.property.DateProperty;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Duration;
import net.fortuna.ical4j.model.property.ExDate;
import net.fortuna.ical4j.model.property.ExRule;
import net.fortuna.ical4j.model.property.LastModified;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.Organizer;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.RDate;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.Status;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Transp;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Version;
import net.fortuna.ical4j.util.SimpleHostInfo;
import net.fortuna.ical4j.util.UidGenerator;
import android.text.format.Time;
import android.util.Log;
import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.syncadapter.DavSyncAdapter;


public class Event extends Resource {
	private final static String TAG = "davdroid.Event";
	
	private final static TimeZoneRegistry tzRegistry = new DefaultTimeZoneRegistryFactory().createRegistry();
	
	@Getter @Setter private String summary, location, description;
	
	@Getter private DtStart dtStart;
	@Getter private DtEnd dtEnd;
	@Getter @Setter private Duration duration;
	@Getter @Setter private RDate rdate;
	@Getter @Setter private RRule rrule;
	@Getter @Setter private ExDate exdate;
	@Getter @Setter private ExRule exrule;
	
	@Getter @Setter private Boolean forPublic;
	@Getter @Setter private Status status;
	
	@Getter @Setter private boolean opaque;	
	
	@Getter @Setter private Organizer organizer;
	@Getter private List<Attendee> attendees = new LinkedList<Attendee>();
	public void addAttendee(Attendee attendee) {
		attendees.add(attendee);
	}
	
	@Getter private List<VAlarm> alarms = new LinkedList<VAlarm>();
	public void addAlarm(VAlarm alarm) {
		alarms.add(alarm);
	}
	

	public Event(String name, String ETag) {
		super(name, ETag);
	}
	
	public Event(long localID, String name, String ETag) {
		super(localID, name, ETag);
	}

	
	@Override
	public void initialize() {
		generateUID();
		name = uid.replace("@", "_") + ".ics";
	}
	
	protected void generateUID() {
		UidGenerator generator = new UidGenerator(new SimpleHostInfo(DavSyncAdapter.getAndroidID()), String.valueOf(android.os.Process.myPid()));
		uid = generator.generateUid().getValue();
	}


	@Override
	@SuppressWarnings("unchecked")
	public void parseEntity(@NonNull InputStream entity) throws IOException, InvalidResourceException {
		net.fortuna.ical4j.model.Calendar ical;
		try {
			CalendarBuilder builder = new CalendarBuilder();
			ical = builder.build(entity);

			if (ical == null)
				throw new InvalidResourceException("No iCalendar found");
		} catch (ParserException e) {
			throw new InvalidResourceException(e);
		}
		
		// event
		ComponentList events = ical.getComponents(Component.VEVENT);
		if (events == null || events.isEmpty())
			throw new InvalidResourceException("No VEVENT found");
		VEvent event = (VEvent)events.get(0);
		
		if (event.getUid() != null)
			uid = event.getUid().getValue();
		else {
			Log.w(TAG, "Received VEVENT without UID, generating new one");
			generateUID();
		}
		
		if ((dtStart = event.getStartDate()) == null || (dtEnd = event.getEndDate()) == null)
			throw new InvalidResourceException("Invalid start time/end time/duration");

		if (hasTime(dtStart)) {
			validateTimeZone(dtStart);
			validateTimeZone(dtEnd);
		}
		
		// all-day events and "events on that day":
		// * related UNIX times must be in UTC
		// * must have a duration (set to one day if missing)
		if (!hasTime(dtStart) && !dtEnd.getDate().after(dtStart.getDate())) {
			Log.i(TAG, "Repairing iCal: DTEND := DTSTART+1");
			Calendar c = Calendar.getInstance(TimeZone.getTimeZone(Time.TIMEZONE_UTC));
			c.setTime(dtStart.getDate());
			c.add(Calendar.DATE, 1);
			dtEnd.setDate(new Date(c.getTimeInMillis()));
		}
		
		rrule = (RRule)event.getProperty(Property.RRULE);
		rdate = (RDate)event.getProperty(Property.RDATE);
		exrule = (ExRule)event.getProperty(Property.EXRULE);
		exdate = (ExDate)event.getProperty(Property.EXDATE);
		
		if (event.getSummary() != null)
			summary = event.getSummary().getValue();
		if (event.getLocation() != null)
			location = event.getLocation().getValue();
		if (event.getDescription() != null)
			description = event.getDescription().getValue();
		
		status = event.getStatus();
		
		opaque = true;
		if (event.getTransparency() == Transp.TRANSPARENT)
			opaque = false;
		
		organizer = event.getOrganizer();
		for (Object o : event.getProperties(Property.ATTENDEE))
			attendees.add((Attendee)o);
		
		Clazz classification = event.getClassification();
		if (classification != null) {
			if (classification == Clazz.PUBLIC)
				forPublic = true;
			else if (classification == Clazz.CONFIDENTIAL || classification == Clazz.PRIVATE)
				forPublic = false;
		}
		
		this.alarms = event.getAlarms();
	}

	@Override
	@SuppressWarnings("unchecked")
	public ByteArrayOutputStream toEntity() throws IOException {
		net.fortuna.ical4j.model.Calendar ical = new net.fortuna.ical4j.model.Calendar();
		ical.getProperties().add(Version.VERSION_2_0);
		ical.getProperties().add(new ProdId("-//bitfire web engineering//DAVdroid " + Constants.APP_VERSION + "//EN"));
		
		VEvent event = new VEvent();
		PropertyList props = event.getProperties();
		
		if (uid != null)
			props.add(new Uid(uid));
		
		props.add(dtStart);
		if (dtEnd != null)
			props.add(dtEnd);
		if (duration != null)
			props.add(duration);
		
		if (rrule != null)
			props.add(rrule);
		if (rdate != null)
			props.add(rdate);
		if (exrule != null)
			props.add(exrule);
		if (exdate != null)
			props.add(exdate);
		
		if (summary != null && !summary.isEmpty())
			props.add(new Summary(summary));
		if (location != null && !location.isEmpty())
			props.add(new Location(location));
		if (description != null && !description.isEmpty())
			props.add(new Description(description));
		
		if (status != null)
			props.add(status);
		if (!opaque)
			props.add(Transp.TRANSPARENT);
		
		if (organizer != null)
			props.add(organizer);
		props.addAll(attendees);
		
		if (forPublic != null)
			event.getProperties().add(forPublic ? Clazz.PUBLIC : Clazz.PRIVATE);
		
		event.getAlarms().addAll(alarms);
		
		props.add(new LastModified());
		ical.getComponents().add(event);

		// add VTIMEZONE components
		net.fortuna.ical4j.model.TimeZone
			tzStart = (dtStart == null ? null : dtStart.getTimeZone()),
			tzEnd = (dtEnd == null ? null : dtEnd.getTimeZone());
		if (tzStart != null)
			ical.getComponents().add(tzStart.getVTimeZone());
		if (tzEnd != null && tzEnd != tzStart)
			ical.getComponents().add(tzEnd.getVTimeZone());

		CalendarOutputter output = new CalendarOutputter(false);
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		try {
			output.output(ical, os);
		} catch (ValidationException e) {
			Log.e(TAG, "Generated invalid iCalendar");
		}
		return os;
	}

	
	public long getDtStartInMillis() {
		return dtStart.getDate().getTime();
	}
	
	public String getDtStartTzID() {
		return getTzId(dtStart);
	}
	
	public void setDtStart(long tsStart, String tzID) {
		if (tzID == null) { 	// all-day
			dtStart = new DtStart(new Date(tsStart));
		} else {
			DateTime start = new DateTime(tsStart);
			start.setTimeZone(tzRegistry.getTimeZone(tzID));
			dtStart = new DtStart(start);
		}
	}
	
	
	public long getDtEndInMillis() {
		return dtEnd.getDate().getTime();
	}
	
	public String getDtEndTzID() {
		return getTzId(dtEnd);
	}
	
	public void setDtEnd(long tsEnd, String tzID) {
		if (tzID == null) { 	// all-day
			dtEnd = new DtEnd(new Date(tsEnd));
		} else {
			DateTime end = new DateTime(tsEnd);
			end.setTimeZone(tzRegistry.getTimeZone(tzID));
			dtEnd = new DtEnd(end);
		}
	}
	
	
	// helpers
	
	public boolean isAllDay() {
		return !hasTime(dtStart);
	}

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
		
		String localTZ = Time.TIMEZONE_UTC;
		
		String availableTZs[] = SimpleTimeZone.getAvailableIDs();
		for (String availableTZ : availableTZs)
			if (tzID.indexOf(availableTZ, 0) != -1) {
				localTZ = availableTZ;
				break;
			}
		
		Log.d(TAG, "Assuming time zone " + localTZ + " for " + tzID);
		date.setTimeZone(tzRegistry.getTimeZone(localTZ));
	}

	public static String TimezoneDefToTzId(String timezoneDef) {
		try {
			CalendarBuilder builder = new CalendarBuilder();
			net.fortuna.ical4j.model.Calendar cal = builder.build(new StringReader(timezoneDef));
			VTimeZone timezone = (VTimeZone)cal.getComponent(VTimeZone.VTIMEZONE);
			return timezone.getTimeZoneId().getValue();
		} catch (Exception ex) {
			Log.w(TAG, "Can't understand time zone definition", ex);
		}
		return null;
	}
}
