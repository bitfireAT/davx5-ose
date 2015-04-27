/*
 * Copyright (c) 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.resource;

import android.text.format.Time;
import android.util.Log;

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
import net.fortuna.ical4j.model.property.RecurrenceId;
import net.fortuna.ical4j.model.property.Status;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Transp;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Version;
import net.fortuna.ical4j.util.CompatibilityHints;
import net.fortuna.ical4j.util.SimpleHostInfo;
import net.fortuna.ical4j.util.UidGenerator;

import org.apache.commons.lang.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.SimpleTimeZone;
import java.util.TimeZone;

import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.syncadapter.DavSyncAdapter;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;


public class Event extends Resource {
	private final static String TAG = "davdroid.Event";
	
	public final static String MIME_TYPE = "text/calendar";
	
	private final static TimeZoneRegistry tzRegistry = new DefaultTimeZoneRegistryFactory().createRegistry();

	@Getter @Setter protected RecurrenceId recurrenceId;

	@Getter @Setter protected String summary, location, description;
	
	@Getter protected DtStart dtStart;
	@Getter protected DtEnd dtEnd;
	@Getter @Setter protected Duration duration;
	@Getter @Setter protected RDate rdate;
	@Getter @Setter protected RRule rrule;
	@Getter @Setter protected ExDate exdate;
	@Getter @Setter protected ExRule exrule;
	@Getter protected List<Event> exceptions = new LinkedList<>();

	@Getter @Setter protected Boolean forPublic;
	@Getter @Setter protected Status status;
	
	@Getter @Setter protected boolean opaque;
	
	@Getter @Setter protected Organizer organizer;
	@Getter protected List<Attendee> attendees = new LinkedList<Attendee>();

	@Getter protected List<VAlarm> alarms = new LinkedList<VAlarm>();

	static {
		CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_UNFOLDING, true);
		CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_PARSING, true);
		CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_OUTLOOK_COMPATIBILITY, true);

		// disable automatic time-zone updates (causes unnecessary network traffic for most people)
		System.setProperty("net.fortuna.ical4j.timezone.update.enabled", "false");
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
	public void parseEntity(@NonNull InputStream entity, AssetDownloader downloader) throws IOException, InvalidResourceException {
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
        opaque = event.getTransparency() != Transp.TRANSPARENT;
		
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
		ical.getProperties().add(new ProdId("-//bitfire web engineering//DAVdroid " + Constants.APP_VERSION + " (ical4j 1.0.x)//EN"));

		// "main event" (without exceptions)
		ComponentList components = ical.getComponents();
		VEvent mainEvent = toVEvent(this);
		components.add(mainEvent);

		// recurrence exceptions
		for (Event exception : exceptions) {
			VEvent vException = toVEvent(exception);
			vException.getProperties().add(mainEvent.getProperty(Property.UID));
			components.add(vException);
		}

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

	protected static VEvent toVEvent(Event e) {
		VEvent event = new VEvent();
		PropertyList props = event.getProperties();

		if (e.uid != null)
			props.add(new Uid(e.uid));
		if (e.recurrenceId != null)
			props.add(e.recurrenceId);

		props.add(e.dtStart);
		if (e.dtEnd != null)
			props.add(e.dtEnd);
		if (e.duration != null)
			props.add(e.duration);

		if (e.rrule != null)
			props.add(e.rrule);
		if (e.rdate != null)
			props.add(e.rdate);
		if (e.exrule != null)
			props.add(e.exrule);
		if (e.exdate != null)
			props.add(e.exdate);

		if (e.summary != null && !e.summary.isEmpty())
			props.add(new Summary(e.summary));
		if (e.location != null && !e.location.isEmpty())
			props.add(new Location(e.location));
		if (e.description != null && !e.description.isEmpty())
			props.add(new Description(e.description));

		if (e.status != null)
			props.add(e.status);
		if (!e.opaque)
			props.add(Transp.TRANSPARENT);

		if (e.organizer != null)
			props.add(e.organizer);
		props.addAll(e.attendees);

		if (e.forPublic != null)
			event.getProperties().add(e.forPublic ? Clazz.PUBLIC : Clazz.PRIVATE);

		event.getAlarms().addAll(e.alarms);

		props.add(new LastModified());
		return event;
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

        String localTZ = null;
		String availableTZs[] = SimpleTimeZone.getAvailableIDs();

        // first, try to find an exact match (case insensitive)
        for (String availableTZ : availableTZs)
            if (tzID.equalsIgnoreCase(availableTZ)) {
                localTZ = availableTZ;
                break;
            }

		// if that doesn't work, try to find something else that matches
        if (localTZ == null) {
	        Log.w(TAG, "Coulnd't find time zone with matching identifiers, trying to guess");
	        for (String availableTZ : availableTZs)
		        if (StringUtils.indexOfIgnoreCase(tzID, availableTZ) != -1) {
			        localTZ = availableTZ;
			        break;
		        }
        }

		// if that doesn't work, use UTC as fallback
		if (localTZ == null) {
			Log.e(TAG, "Couldn't identify time zone, using UTC as fallback");
			localTZ = Time.TIMEZONE_UTC;
		}

        Log.d(TAG, "Assuming time zone " + localTZ + " for " + tzID);
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
