/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.resource;

import android.accounts.Account;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentProviderOperation.Builder;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Entity;
import android.content.EntityIterator;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.Build;
import android.os.RemoteException;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Reminders;
import android.util.Log;

import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.ParameterList;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.component.VAlarm;
import net.fortuna.ical4j.model.parameter.Cn;
import net.fortuna.ical4j.model.parameter.CuType;
import net.fortuna.ical4j.model.parameter.PartStat;
import net.fortuna.ical4j.model.parameter.Role;
import net.fortuna.ical4j.model.parameter.Rsvp;
import net.fortuna.ical4j.model.property.Action;
import net.fortuna.ical4j.model.property.Attendee;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.Duration;
import net.fortuna.ical4j.model.property.ExDate;
import net.fortuna.ical4j.model.property.ExRule;
import net.fortuna.ical4j.model.property.Organizer;
import net.fortuna.ical4j.model.property.RDate;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.RecurrenceId;
import net.fortuna.ical4j.model.property.Status;

import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.LinkedList;
import java.util.List;

import at.bitfire.davdroid.DAVUtils;
import at.bitfire.davdroid.DateUtils;
import at.bitfire.davdroid.webdav.WebDavResource;
import lombok.Cleanup;
import lombok.Getter;

/**
 * Represents a locally stored calendar, containing Events.
 * Communicates with the Android Contacts Provider which uses an SQLite
 * database to store the contacts.
 */
public class LocalCalendar extends LocalCollection<Event> {
	private static final String TAG = "davdroid.LocalCalendar";

	@Getter protected String url;
	@Getter protected long id;
	
	protected static final String COLLECTION_COLUMN_CTAG = Calendars.CAL_SYNC1;

	/* database fields */
	
	@Override protected Uri entriesURI()                { return syncAdapterURI(Events.CONTENT_URI); }
	@Override protected String entryColumnAccountType()	{ return Events.ACCOUNT_TYPE; }
	@Override protected String entryColumnAccountName()	{ return Events.ACCOUNT_NAME; }
	@Override protected String entryColumnParentID()	{ return Events.CALENDAR_ID; }
	@Override protected String entryColumnID()			{ return Events._ID; }
	@Override protected String entryColumnRemoteName()	{ return Events._SYNC_ID; }
	@Override protected String entryColumnETag()		{ return Events.SYNC_DATA1; }
	@Override protected String entryColumnDirty()		{ return Events.DIRTY; }
	@Override protected String entryColumnDeleted()		{ return Events.DELETED; }

	@Override
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	protected String entryColumnUID() {
		return (android.os.Build.VERSION.SDK_INT >= 17) ?
			Events.UID_2445 : Events.SYNC_DATA2;
	}

	
	/* class methods, constructor */

	@SuppressLint("InlinedApi")
	public static Uri create(Account account, ContentResolver resolver, ServerInfo.ResourceInfo info) throws LocalStorageException {
		@Cleanup("release") final ContentProviderClient client = resolver.acquireContentProviderClient(CalendarContract.AUTHORITY);
		if (client == null)
			throw new LocalStorageException("No Calendar Provider found (Calendar app disabled?)");

		ContentValues values = new ContentValues();
		values.put(Calendars.ACCOUNT_NAME, account.name);
		values.put(Calendars.ACCOUNT_TYPE, account.type);
		values.put(Calendars.NAME, info.getURL());
		values.put(Calendars.CALENDAR_DISPLAY_NAME, info.getTitle());
		values.put(Calendars.CALENDAR_COLOR, info.getColor() != null ? info.getColor() : DAVUtils.calendarGreen);
		values.put(Calendars.OWNER_ACCOUNT, account.name);
		values.put(Calendars.SYNC_EVENTS, 1);
		values.put(Calendars.VISIBLE, 1);
		values.put(Calendars.ALLOWED_REMINDERS, Reminders.METHOD_ALERT);
		
		if (info.isReadOnly())
			values.put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_READ);
		else {
			values.put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER);
			values.put(Calendars.CAN_ORGANIZER_RESPOND, 1);
			values.put(Calendars.CAN_MODIFY_TIME_ZONE, 1);
		}
		
		if (android.os.Build.VERSION.SDK_INT >= 15) {
			values.put(Calendars.ALLOWED_AVAILABILITY, Events.AVAILABILITY_BUSY + "," + Events.AVAILABILITY_FREE + "," + Events.AVAILABILITY_TENTATIVE);
			values.put(Calendars.ALLOWED_ATTENDEE_TYPES, Attendees.TYPE_NONE + "," + Attendees.TYPE_OPTIONAL + "," + Attendees.TYPE_REQUIRED + "," + Attendees.TYPE_RESOURCE);
		}
		
		if (info.getTimezone() != null)
			values.put(Calendars.CALENDAR_TIME_ZONE, DateUtils.findAndroidTimezoneID(info.getTimezone()));
		
		Log.i(TAG, "Inserting calendar: " + values.toString());
		try {
			return client.insert(calendarsURI(account), values);
		} catch (RemoteException e) {
			throw new LocalStorageException(e);
		}
	}
	
	public static LocalCalendar[] findAll(Account account, ContentProviderClient providerClient) throws RemoteException {
		@Cleanup Cursor cursor = providerClient.query(calendarsURI(account),
				new String[] { Calendars._ID, Calendars.NAME },
				Calendars.DELETED + "=0 AND " + Calendars.SYNC_EVENTS + "=1", null, null);

		LinkedList<LocalCalendar> calendars = new LinkedList<>();
		while (cursor != null && cursor.moveToNext())
			calendars.add(new LocalCalendar(account, providerClient, cursor.getInt(0), cursor.getString(1)));
		return calendars.toArray(new LocalCalendar[calendars.size()]);
	}

	public LocalCalendar(Account account, ContentProviderClient providerClient, long id, String url) {
		super(account, providerClient);
		this.id = id;
		this.url = url;
		sqlFilter = "ORIGINAL_ID IS NULL";
	}

	
	/* collection operations */
	
	@Override
	public String getCTag() throws LocalStorageException {
		try {
			@Cleanup Cursor c = providerClient.query(ContentUris.withAppendedId(calendarsURI(), id),
					new String[] { COLLECTION_COLUMN_CTAG }, null, null, null);
			if (c != null && c.moveToFirst())
				return c.getString(0);
			else
				throw new LocalStorageException("Couldn't query calendar CTag");
		} catch(RemoteException e) {
			throw new LocalStorageException(e);
		}
	}
	
	@Override
	public void setCTag(String cTag) throws LocalStorageException {
		ContentValues values = new ContentValues(1);
		values.put(COLLECTION_COLUMN_CTAG, cTag);
		try {
			providerClient.update(ContentUris.withAppendedId(calendarsURI(), id), values, null, null);
		} catch(RemoteException e) {
			throw new LocalStorageException(e);
		}
	}

	@Override
	public void updateMetaData(WebDavResource.Properties properties) throws LocalStorageException {
		ContentValues values = new ContentValues();

		final String displayName = properties.getDisplayName();
		if (displayName != null)
			values.put(Calendars.CALENDAR_DISPLAY_NAME, displayName);

		final Integer color = properties.getColor();
		if (color != null)
			values.put(Calendars.CALENDAR_COLOR, color);

		try {
			if (values.size() > 0)
				providerClient.update(ContentUris.withAppendedId(calendarsURI(), id), values, null, null);
		} catch(RemoteException e) {
			throw new LocalStorageException(e);
		}
	}

	@Override
	public long[] findUpdated() throws LocalStorageException {
		// mark (recurring) events with changed/deleted exceptions as dirty
		String where = entryColumnID() + " IN (SELECT DISTINCT " + Events.ORIGINAL_ID + " FROM events WHERE " +
			Events.ORIGINAL_ID + " IS NOT NULL AND (" + Events.DIRTY + "=1 OR " + Events.DELETED + "=1))";
		ContentValues dirty = new ContentValues(1);
		dirty.put(CalendarContract.Events.DIRTY, 1);
		try {
			int rows = providerClient.update(entriesURI(), dirty, where, null);
			if (rows > 0)
				Log.d(TAG, rows + " event(s) marked as dirty because of dirty/deleted exceptions");
		} catch (RemoteException e) {
			Log.e(TAG, "Couldn't mark events with updated exceptions as dirty", e);
		}

		// new find and return updated (master) events
		return super.findUpdated();
	}


	/* create/update/delete */
	
	public Event newResource(long localID, String resourceName, String eTag) {
		return new Event(localID, resourceName, eTag);
	}
	
	public int deleteAllExceptRemoteNames(Resource[] remoteResources) throws LocalStorageException {
		List<String> sqlFileNames = new LinkedList<>();
		for (Resource res : remoteResources)
			sqlFileNames.add(DatabaseUtils.sqlEscapeString(res.getName()));

		// delete master events
		String where = entryColumnParentID() + "=?";
		where += sqlFileNames.isEmpty() ?
				" AND " + entryColumnRemoteName() + " IS NOT NULL"  :   // don't retain anything (delete all)
				" AND " + entryColumnRemoteName() + " NOT IN (" + StringUtils.join(sqlFileNames, ",") + ")";    // retain by remote file name
		if (sqlFilter != null)
			where += " AND (" + sqlFilter + ")";
		pendingOperations.add(ContentProviderOperation.newDelete(entriesURI())
				.withSelection(where, new String[] { String.valueOf(id) })
				.build());

		// delete exceptions, too
		where = entryColumnParentID() + "=?";
		where += sqlFileNames.isEmpty() ?
				" AND " + Events.ORIGINAL_SYNC_ID + " IS NOT NULL"  :   // don't retain anything (delete all)
				" AND " + Events.ORIGINAL_SYNC_ID + " NOT IN (" + StringUtils.join(sqlFileNames, ",") + ")";    // retain by remote file name
		pendingOperations.add(ContentProviderOperation
				.newDelete(entriesURI())
				.withSelection(where, new String[]{String.valueOf(id)})
				.withYieldAllowed(true)
				.build()
		);
        return commit();
	}

	@Override
	public void delete(Resource resource) {
		super.delete(resource);

		// delete all exceptions of this event, too
		pendingOperations.add(ContentProviderOperation
				.newDelete(entriesURI())
				.withSelection(Events.ORIGINAL_ID + "=?", new String[] { String.valueOf(resource.getLocalID()) })
				.build()
		);
	}

	@Override
	public void clearDirty(Resource resource) {
		super.clearDirty(resource);

		// clear dirty flag of all exceptions of this event
		pendingOperations.add(ContentProviderOperation
				.newUpdate(entriesURI())
				.withValue(Events.DIRTY, 0)
				.withSelection(Events.ORIGINAL_ID + "=?", new String[]{String.valueOf(resource.getLocalID())})
				.build()
		);
	}
	
	
	/* methods for populating the data object from the content provider */

	@Override
	public void populate(Resource resource) throws LocalStorageException {
		Event event = (Event)resource;

		try {
			@Cleanup EntityIterator iterEvents = CalendarContract.EventsEntity.newEntityIterator(
					providerClient.query(
							syncAdapterURI(CalendarContract.EventsEntity.CONTENT_URI),
							null, Events._ID + "=" + event.getLocalID(),
							null, null),
					providerClient
			);
			while (iterEvents.hasNext()) {
				Entity e = iterEvents.next();

				ContentValues values = e.getEntityValues();
				populateEvent(event, values);

				List<Entity.NamedContentValues> subValues = e.getSubValues();
				for (Entity.NamedContentValues subValue : subValues) {
					values = subValue.values;
					if (Attendees.CONTENT_URI.equals(subValue.uri))
						populateAttendee(event, values);
					if (Reminders.CONTENT_URI.equals(subValue.uri))
						populateReminder(event, values);
				}

				populateExceptions(event);
			}
		} catch (RemoteException ex) {
			throw new LocalStorageException("Couldn't process locally stored event", ex);
		}
	}

	protected void populateEvent(Event e, ContentValues values) {
		e.setUid(values.getAsString(entryColumnUID()));

		e.summary = values.getAsString(Events.TITLE);
		e.location = values.getAsString(Events.EVENT_LOCATION);
		e.description = values.getAsString(Events.DESCRIPTION);

		final boolean allDay = values.getAsInteger(Events.ALL_DAY) != 0;
		final long tsStart = values.getAsLong(Events.DTSTART);
		final String duration = values.getAsString(Events.DURATION);

		String tzId;
		Long tsEnd = values.getAsLong(Events.DTEND);
		if (allDay) {
			e.setDtStart(tsStart, null);
			if (tsEnd == null) {
				Dur dur = new Dur(duration);
				java.util.Date dEnd = dur.getTime(new java.util.Date(tsStart));
				tsEnd = dEnd.getTime();
			}
			e.setDtEnd(tsEnd, null);

		} else {
			// use the start time zone for the end time, too
			// because apps like Samsung Planner allow the user to change "the" time zone but change the start time zone only
			tzId = values.getAsString(Events.EVENT_TIMEZONE);
			e.setDtStart(tsStart, tzId);
			if (tsEnd != null)
				e.setDtEnd(tsEnd, tzId);
			else if (!StringUtils.isEmpty(duration))
				e.duration = new Duration(new Dur(duration));
		}

		// recurrence
		try {
			String strRRule = values.getAsString(Events.RRULE);
			if (!StringUtils.isEmpty(strRRule))
				e.rrule = new RRule(strRRule);

			String strRDate = values.getAsString(Events.RDATE);
			if (!StringUtils.isEmpty(strRDate)) {
				RDate rDate = (RDate)DateUtils.androidStringToRecurrenceSet(strRDate, RDate.class, allDay);
				e.getRdates().add(rDate);
			}

			String strExRule = values.getAsString(Events.EXRULE);
			if (!StringUtils.isEmpty(strExRule)) {
				ExRule exRule = new ExRule();
				exRule.setValue(strExRule);
				e.exrule = exRule;
			}

			String strExDate = values.getAsString(Events.EXDATE);
			if (!StringUtils.isEmpty(strExDate)) {
				ExDate exDate = (ExDate)DateUtils.androidStringToRecurrenceSet(strExDate, ExDate.class, allDay);
				e.getExdates().add(exDate);
			}
		} catch (ParseException ex) {
			Log.w(TAG, "Couldn't parse recurrence rules, ignoring", ex);
		} catch (IllegalArgumentException ex) {
			Log.w(TAG, "Invalid recurrence rules, ignoring", ex);
		}

		if (values.containsKey(Events.ORIGINAL_INSTANCE_TIME)) {
			// this event is an exception of a recurring event
			long originalInstanceTime = values.getAsLong(Events.ORIGINAL_INSTANCE_TIME);

			boolean originalAllDay = false;
			if (values.containsKey(Events.ORIGINAL_ALL_DAY))
				originalAllDay = values.getAsInteger(Events.ORIGINAL_ALL_DAY) != 0;

			Date originalDate = originalAllDay ?
					new Date(originalInstanceTime) :
					new DateTime(originalInstanceTime);
			if (originalDate instanceof DateTime)
				((DateTime)originalDate).setUtc(true);
			e.recurrenceId = new RecurrenceId(originalDate);
		}

		// status
		if (values.containsKey(Events.STATUS))
			switch (values.getAsInteger(Events.STATUS)) {
				case Events.STATUS_CONFIRMED:
					e.status = Status.VEVENT_CONFIRMED;
					break;
				case Events.STATUS_TENTATIVE:
					e.status = Status.VEVENT_TENTATIVE;
					break;
				case Events.STATUS_CANCELED:
					e.status = Status.VEVENT_CANCELLED;
			}

		// availability
		e.opaque = values.getAsInteger(Events.AVAILABILITY) != Events.AVAILABILITY_FREE;

		// set ORGANIZER
		try {
			e.organizer = new Organizer(new URI("mailto", values.getAsString(Events.ORGANIZER), null));
		} catch (URISyntaxException ex) {
			Log.e(TAG, "Error when creating ORGANIZER mailto URI, ignoring", ex);
		}

		// classification
		switch (values.getAsInteger(Events.ACCESS_LEVEL)) {
			case Events.ACCESS_CONFIDENTIAL:
			case Events.ACCESS_PRIVATE:
				e.forPublic = false;
				break;
			case Events.ACCESS_PUBLIC:
				e.forPublic = true;
		}
	}

	void populateExceptions(Event e) throws RemoteException {
		@Cleanup Cursor c = providerClient.query(syncAdapterURI(Events.CONTENT_URI),
				new String[]{Events._ID, entryColumnRemoteName()},
				Events.ORIGINAL_ID + "=?", new String[]{ String.valueOf(e.getLocalID()) }, null);
		while (c != null && c.moveToNext()) {
			long exceptionId = c.getLong(0);
			String exceptionRemoteName = c.getString(1);
			try {
				Event exception = new Event(exceptionId, exceptionRemoteName, null);
				populate(exception);
				e.getExceptions().add(exception);
			} catch (LocalStorageException ex) {
				Log.e(TAG, "Couldn't find exception details, ignoring");
			}
		}
	}

	void populateAttendee(Event event, ContentValues values) {
		try {
			final Attendee attendee;
			final String
					email = values.getAsString(Attendees.ATTENDEE_EMAIL),
					idNS = values.getAsString(Attendees.ATTENDEE_ID_NAMESPACE),
					id = values.getAsString(Attendees.ATTENDEE_IDENTITY);
			if (idNS != null || id != null) {
				// attendee identified by namespace and ID
				attendee = new Attendee(new URI(idNS, id, null));
				if (email != null)
					attendee.getParameters().add(new iCalendar.Email(email));
			} else
				// attendee identified by email address
				attendee = new Attendee(new URI("mailto", email, null));
			final ParameterList params = attendee.getParameters();

			String cn = values.getAsString(Attendees.ATTENDEE_NAME);
			if (cn != null)
				params.add(new Cn(cn));

			// type
			int type = values.getAsInteger(Attendees.ATTENDEE_TYPE);
			params.add((type == Attendees.TYPE_RESOURCE) ? CuType.RESOURCE : CuType.INDIVIDUAL);

			// role
			int relationship = values.getAsInteger(Attendees.ATTENDEE_RELATIONSHIP);
			switch (relationship) {
				case Attendees.RELATIONSHIP_ORGANIZER:
				case Attendees.RELATIONSHIP_ATTENDEE:
				case Attendees.RELATIONSHIP_PERFORMER:
				case Attendees.RELATIONSHIP_SPEAKER:
					params.add((type == Attendees.TYPE_REQUIRED) ? Role.REQ_PARTICIPANT : Role.OPT_PARTICIPANT);
					params.add(new Rsvp(true));
					break;
				case Attendees.RELATIONSHIP_NONE:
					params.add(Role.NON_PARTICIPANT);
			}

			// status
			switch (values.getAsInteger(Attendees.ATTENDEE_STATUS)) {
				case Attendees.ATTENDEE_STATUS_INVITED:
					params.add(PartStat.NEEDS_ACTION);
					break;
				case Attendees.ATTENDEE_STATUS_ACCEPTED:
					params.add(PartStat.ACCEPTED);
					break;
				case Attendees.ATTENDEE_STATUS_DECLINED:
					params.add(PartStat.DECLINED);
					break;
				case Attendees.ATTENDEE_STATUS_TENTATIVE:
					params.add(PartStat.TENTATIVE);
					break;
			}

			event.getAttendees().add(attendee);
		} catch (URISyntaxException ex) {
			Log.e(TAG, "Couldn't parse attendee information, ignoring", ex);
		}
	}

	void populateReminder(Event event, ContentValues row) {
		VAlarm alarm = new VAlarm(new Dur(0, 0, -row.getAsInteger(Reminders.MINUTES), 0));

		PropertyList props = alarm.getProperties();
		props.add(Action.DISPLAY);
		props.add(new Description(event.summary));
		event.getAlarms().add(alarm);
	}

	
	/* content builder methods */

	@Override
	protected Builder buildEntry(Builder builder, Resource resource, boolean update) {
		final Event event = (Event)resource;

		builder	.withValue(Events.CALENDAR_ID, id)
				.withValue(Events.ALL_DAY, event.isAllDay() ? 1 : 0)
				.withValue(Events.DTSTART, event.getDtStartInMillis())
				.withValue(Events.EVENT_TIMEZONE, event.getDtStartTzID())
				.withValue(Events.HAS_ALARM, event.getAlarms().isEmpty() ? 0 : 1)
				.withValue(Events.HAS_ATTENDEE_DATA, event.getAttendees().isEmpty() ? 0 : 1)
				.withValue(Events.GUESTS_CAN_INVITE_OTHERS, 1)
				.withValue(Events.GUESTS_CAN_MODIFY, 1)
				.withValue(Events.GUESTS_CAN_SEE_GUESTS, 1);

		if (event.recurrenceId == null) {
			// this event is a "master event" (not an exception)
			builder	.withValue(entryColumnRemoteName(), event.getName())
					.withValue(entryColumnETag(), event.getETag())
					.withValue(entryColumnUID(), event.getUid());
		} else {
			builder.withValue(Events.ORIGINAL_SYNC_ID, event.getName());

			// ORIGINAL_INSTANCE_TIME and ORIGINAL_ALL_DAY is set in buildExceptions.
			// It's not possible to use only the RECURRENCE-ID to calculate
			// ORIGINAL_INSTANCE_TIME and ORIGINAL_ALL_DAY because iCloud sends DATE-TIME
			// RECURRENCE-IDs even if the original event is an all-day event.
		}

		boolean recurring = false;
		if (event.rrule != null) {
			recurring = true;
			builder.withValue(Events.RRULE, event.rrule.getValue());
		}
		if (!event.getRdates().isEmpty()) {
			recurring = true;
			try {
				builder.withValue(Events.RDATE, DateUtils.recurrenceSetsToAndroidString(event.getRdates(), event.isAllDay()));
			} catch (ParseException e) {
				Log.e(TAG, "Couldn't parse RDate(s)", e);
			}
		}
		if (event.exrule != null)
			 builder.withValue(Events.EXRULE, event.exrule.getValue());
		if (!event.getExceptions().isEmpty())
			try {
				builder.withValue(Events.EXDATE, DateUtils.recurrenceSetsToAndroidString(event.getExdates(), event.isAllDay()));
			} catch (ParseException e) {
				Log.e(TAG, "Couldn't parse ExDate(s)", e);
			}

		// set either DTEND for single-time events or DURATION for recurring events
		// because that's the way Android likes it (see docs)
		if (recurring) {
			// calculate DURATION from start and end date
			Duration duration = new Duration(event.dtStart.getDate(), event.dtEnd.getDate());
			builder.withValue(Events.DURATION, duration.getValue());
		} else
			builder	.withValue(Events.DTEND, event.getDtEndInMillis())
					.withValue(Events.EVENT_END_TIMEZONE, event.getDtEndTzID());

		if (event.summary != null)
			builder.withValue(Events.TITLE, event.summary);
		if (event.location != null)
			builder.withValue(Events.EVENT_LOCATION, event.location);
		if (event.description != null)
			builder.withValue(Events.DESCRIPTION, event.description);

		if (event.organizer != null) {
			final URI uri = event.organizer.getCalAddress();

			String email = null;
			if (uri != null && "mailto".equalsIgnoreCase(uri.getScheme()))
				email = uri.getSchemeSpecificPart();
			else {
				iCalendar.Email emailParam = (iCalendar.Email)event.organizer.getParameter(iCalendar.Email.PARAMETER_NAME);
				if (emailParam != null)
					email = emailParam.getValue();
				else
					Log.w(TAG, "Got ORGANIZER without email address, using given URI instead (may cause Android to behave unexpectedly)");
			}
			builder.withValue(Events.ORGANIZER, email != null ? email : uri.toString());
		}

		//Status status = event.getStatus();
		if (event.status!= null) {
			int statusCode = Events.STATUS_TENTATIVE;
			if (event.status == Status.VEVENT_CONFIRMED)
				statusCode = Events.STATUS_CONFIRMED;
			else if (event.status == Status.VEVENT_CANCELLED)
				statusCode = Events.STATUS_CANCELED;
			builder.withValue(Events.STATUS, statusCode);
		}
		
		builder.withValue(Events.AVAILABILITY, event.opaque ? Events.AVAILABILITY_BUSY : Events.AVAILABILITY_FREE);
		
		if (event.forPublic != null)
			builder.withValue(Events.ACCESS_LEVEL, event.forPublic ? Events.ACCESS_PUBLIC : Events.ACCESS_PRIVATE);

		return builder;
	}

	
	@Override
	protected void addDataRows(Resource resource, long localID, int backrefIdx) {
		final Event event = (Event)resource;

		// add exceptions
		for (Event exception : event.getExceptions())
			pendingOperations.add(buildException(newDataInsertBuilder(Events.CONTENT_URI, Events.ORIGINAL_ID, localID, backrefIdx), event, exception).build());
		// add attendees
		for (Attendee attendee : event.getAttendees())
			pendingOperations.add(buildAttendee(newDataInsertBuilder(Attendees.CONTENT_URI, Attendees.EVENT_ID, localID, backrefIdx), attendee).build());
		// add reminders
		for (VAlarm alarm : event.getAlarms())
			pendingOperations.add(buildReminder(newDataInsertBuilder(Reminders.CONTENT_URI, Reminders.EVENT_ID, localID, backrefIdx), alarm).build());
	}
	
	@Override
	protected void removeDataRows(Resource resource) {
		final Event event = (Event)resource;

		// delete exceptions
		pendingOperations.add(ContentProviderOperation.newDelete(syncAdapterURI(Events.CONTENT_URI))
				.withSelection(Events.ORIGINAL_ID + "=?", new String[] { String.valueOf(event.getLocalID())}).build());
		// delete attendees
		pendingOperations.add(ContentProviderOperation.newDelete(syncAdapterURI(Attendees.CONTENT_URI))
				.withSelection(Attendees.EVENT_ID + "=?", new String[]{String.valueOf(event.getLocalID())}).build());
		// delete reminders
		pendingOperations.add(ContentProviderOperation.newDelete(syncAdapterURI(Reminders.CONTENT_URI))
				.withSelection(Reminders.EVENT_ID + "=?", new String[]{String.valueOf(event.getLocalID())}).build());
	}


	protected Builder buildException(Builder builder, Event master, Event exception) {
		buildEntry(builder, exception, false);
		builder.withValue(Events.ORIGINAL_SYNC_ID, exception.getName());

		final boolean originalAllDay = master.isAllDay();

		Date date = exception.recurrenceId.getDate();
		if (originalAllDay && date instanceof DateTime) {       // correct VALUE=DATE-TIME RECURRENCE-IDs to VALUE=DATE
			final DateFormat dateFormatDate = new SimpleDateFormat("yyyyMMdd");
			final String dateString = dateFormatDate.format(exception.recurrenceId.getDate());
			try {
				date = new Date(dateString);
			} catch (ParseException e) {
				Log.e(TAG, "Couldn't parse DATE part of DATE-TIME RECURRENCE-ID", e);
			}
		}

		builder.withValue(Events.ORIGINAL_INSTANCE_TIME, date.getTime());
		builder.withValue(Events.ORIGINAL_ALL_DAY, originalAllDay ? 1 : 0);
		return builder;
	}
	
	@SuppressLint("InlinedApi")
	protected Builder buildAttendee(Builder builder, Attendee attendee) {
		final Uri member = Uri.parse(attendee.getValue());
		if ("mailto".equalsIgnoreCase(member.getScheme()))
			// attendee identified by email
			builder = builder.withValue(Attendees.ATTENDEE_EMAIL, member.getSchemeSpecificPart());
		else {
			// attendee identified by other URI
			builder = builder
					.withValue(Attendees.ATTENDEE_ID_NAMESPACE, member.getScheme())
					.withValue(Attendees.ATTENDEE_IDENTITY, member.getSchemeSpecificPart());
			iCalendar.Email email = (iCalendar.Email)attendee.getParameter(iCalendar.Email.PARAMETER_NAME);
			if (email != null)
				builder = builder.withValue(Attendees.ATTENDEE_EMAIL, email.getValue());
		}

		final Cn cn = (Cn)attendee.getParameter(Parameter.CN);
		if (cn != null)
			builder.withValue(Attendees.ATTENDEE_NAME, cn.getValue());

		int type = Attendees.TYPE_NONE;
		
		CuType cutype = (CuType)attendee.getParameter(Parameter.CUTYPE);
		if (cutype == CuType.RESOURCE || cutype == CuType.ROOM)
			// "attendee" is a (physical) resource
			type = Attendees.TYPE_RESOURCE;
		else {
			// attendee is not a (physical) resource
			Role role = (Role)attendee.getParameter(Parameter.ROLE);
			int relationship;
			if (role == Role.CHAIR)
				relationship = Attendees.RELATIONSHIP_ORGANIZER;
			else {
				relationship = Attendees.RELATIONSHIP_ATTENDEE;
				if (role == Role.OPT_PARTICIPANT)
					type = Attendees.TYPE_OPTIONAL;
				else if (role == Role.REQ_PARTICIPANT)
					type = Attendees.TYPE_REQUIRED;
			}
			builder.withValue(Attendees.ATTENDEE_RELATIONSHIP, relationship);
		}
		
		int status = Attendees.ATTENDEE_STATUS_NONE;
		PartStat partStat = (PartStat)attendee.getParameter(Parameter.PARTSTAT);
		if (partStat == null || partStat == PartStat.NEEDS_ACTION)
			status = Attendees.ATTENDEE_STATUS_INVITED;
		else if (partStat == PartStat.ACCEPTED)
			status = Attendees.ATTENDEE_STATUS_ACCEPTED;
		else if (partStat == PartStat.DECLINED)
			status = Attendees.ATTENDEE_STATUS_DECLINED;
		else if (partStat == PartStat.TENTATIVE)
			status = Attendees.ATTENDEE_STATUS_TENTATIVE;
		
		return builder
			.withValue(Attendees.ATTENDEE_TYPE, type)
			.withValue(Attendees.ATTENDEE_STATUS, status);
	}
	
	protected Builder buildReminder(Builder builder, VAlarm alarm) {
		int minutes = 0;
		
		if (alarm.getTrigger() != null) {
			Dur duration = alarm.getTrigger().getDuration();
			if (duration != null) {
				// negative value in TRIGGER means positive value in Reminders.MINUTES and vice versa
				minutes = -(((duration.getWeeks() * 7 + duration.getDays()) * 24 + duration.getHours()) * 60 + duration.getMinutes());
				if (duration.isNegative())
					minutes *= -1;
			}
		}

		Log.d(TAG, "Adding alarm " + minutes + " minutes before");

		return builder
				.withValue(Reminders.METHOD, Reminders.METHOD_ALERT)
				.withValue(Reminders.MINUTES, minutes);
	}
	
	
	
	/* private helper methods */
	
	protected static Uri calendarsURI(Account account) {
		return Calendars.CONTENT_URI.buildUpon()
				.appendQueryParameter(Calendars.ACCOUNT_NAME, account.name)
				.appendQueryParameter(Calendars.ACCOUNT_TYPE, account.type)
				.appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
				.build();
	}

	protected Uri calendarsURI() {
		return calendarsURI(account);
	}

}
