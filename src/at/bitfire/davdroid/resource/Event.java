/*******************************************************************************
 * Copyright (c) 2013 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.resource;

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
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.Organizer;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.RDate;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.Status;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Version;
import net.fortuna.ical4j.util.UidGenerator;
import android.text.format.Time;
import android.util.Log;
import at.bitfire.davdroid.Constants;


public class Event extends Resource {
	private final static String TAG = "davdroid.Event";
	
	private TimeZoneRegistry tzRegistry;
	
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
		
		DefaultTimeZoneRegistryFactory factory = new DefaultTimeZoneRegistryFactory();
		tzRegistry = factory.createRegistry();
	}
	
	public Event(long localID, String name, String ETag) {
		this(name, ETag);
		this.localID = localID;
	}


	@Override
	@SuppressWarnings("unchecked")
	public void parseEntity(@NonNull InputStream entity) throws IOException, ParserException {
		CalendarBuilder builder = new CalendarBuilder();
		net.fortuna.ical4j.model.Calendar ical = builder.build(entity);
		if (ical == null)
			return;
		
		Log.d(TAG, "Parsing iCal: " + ical.toString());
		
		// event
		ComponentList events = ical.getComponents(Component.VEVENT);
		if (events == null || events.isEmpty())
			return;
		VEvent event = (VEvent)events.get(0); 
		
		if (event.getUid() != null)
			uid = event.getUid().getValue();
		else {
			Log.w(TAG, "Received VEVENT without UID, generating new one");
			UidGenerator uidGenerator = new UidGenerator(Integer.toString(android.os.Process.myPid()));
			uid = uidGenerator.generateUid().getValue();
		}
		
		dtStart = event.getStartDate();	validateTimeZone(dtStart);
		dtEnd = event.getEndDate(); validateTimeZone(dtEnd);
		
		duration = event.getDuration();
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
	public String toEntity() {
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
		
		if (summary != null)
			props.add(new Summary(summary));
		if (location != null)
			props.add(new Location(location));
		if (description != null)
			props.add(new Description(description));
		
		if (status != null)
			props.add(status);
		
		if (organizer != null)
			props.add(organizer);
		props.addAll(attendees);
		
		if (forPublic != null)
			event.getProperties().add(forPublic ? Clazz.PUBLIC : Clazz.PRIVATE);
		
		event.getAlarms().addAll(alarms);
		
		ical.getComponents().add(event);

		// add VTIMEZONE components
		net.fortuna.ical4j.model.TimeZone
			tzStart = (dtStart == null ? null : dtStart.getTimeZone()),
			tzEnd = (dtEnd == null ? null : dtEnd.getTimeZone());
		if (tzStart != null)
			ical.getComponents().add(tzStart.getVTimeZone());
		if (tzEnd != null && tzEnd != tzStart)
			ical.getComponents().add(tzEnd.getVTimeZone());
			
		return ical.toString();
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
	
	
	public Long getDtEndInMillis() {
		if (hasNoTime(dtStart) && dtEnd == null) {		// "event on that day"
			// dtEnd = dtStart + 1 day
			Calendar c = Calendar.getInstance(TimeZone.getTimeZone(Time.TIMEZONE_UTC));
			c.setTime(dtStart.getDate());
			c.add(Calendar.DATE, 1);
			return c.getTimeInMillis();
			
		} else if (dtEnd == null || dtEnd.getDate() == null) {	// no DTEND provided (maybe DURATION instead)
			return null;
		}
		
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
		if (hasNoTime(dtStart)) {
			// events on that day
			if (dtEnd == null)
				return true;
			
			// all-day events
			if (hasNoTime(dtEnd))
				return true;
		}
		return false;
	}

	protected boolean hasNoTime(DateProperty date) {
		return !(date.getDate() instanceof DateTime);
	}

	String getTzId(DateProperty date) {
		if (date == null)
			return null;
		
		if (hasNoTime(date) || date.isUtc())
			return Time.TIMEZONE_UTC;
		else if (date.getTimeZone() != null)
			return date.getTimeZone().getID();
		else if (date.getParameter(Value.TZID) != null)
			return date.getParameter(Value.TZID).getValue();
		return null;
	}

	/* guess matching Android timezone ID */
	protected void validateTimeZone(DateProperty date) {
		if (date == null || date.isUtc() || hasNoTime(date))
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


	@Override
	public void validate() throws ValidationException {
		super.validate();
		
		if (dtStart == null)
			throw new ValidationException("dtStart must not be empty");
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
