/*
 * Copyright (c) 2013 – 2015 Ricki Hirner (bitfire web engineering).
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
import android.provider.ContactsContract;
import android.util.Log;

import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.ParameterList;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;
import net.fortuna.ical4j.model.component.VAlarm;
import net.fortuna.ical4j.model.parameter.Cn;
import net.fortuna.ical4j.model.parameter.CuType;
import net.fortuna.ical4j.model.parameter.PartStat;
import net.fortuna.ical4j.model.parameter.Role;
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
import net.fortuna.ical4j.util.TimeZones;

import org.apache.commons.lang.StringUtils;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Cleanup;
import lombok.Getter;

/**
 * Represents a locally stored calendar, containing Events.
 * Communicates with the Android Contacts Provider which uses an SQLite
 * database to store the contacts.
 */
public class LocalCalendar extends LocalCollection<Event> {
	private static final String TAG = "davdroid.LocalCalendar";

	@Getter protected long id;
	@Getter protected String url;
	
	protected static String COLLECTION_COLUMN_CTAG = Calendars.CAL_SYNC1;

	
	/* database fields */
	
	@Override
	protected Uri entriesURI() {
		return syncAdapterURI(Events.CONTENT_URI);
	}

	protected String entryColumnAccountType()	{ return Events.ACCOUNT_TYPE; }
	protected String entryColumnAccountName()	{ return Events.ACCOUNT_NAME; }
	
	protected String entryColumnParentID()		{ return Events.CALENDAR_ID; }
	protected String entryColumnID()			{ return Events._ID; }
	protected String entryColumnRemoteName()	{ return Events._SYNC_ID; }
	protected String entryColumnETag()			{ return Events.SYNC_DATA1; }

	protected String entryColumnDirty()			{ return Events.DIRTY; }
	protected String entryColumnDeleted()		{ return Events.DELETED; }
	
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	protected String entryColumnUID() {
		return (android.os.Build.VERSION.SDK_INT >= 17) ?
			Events.UID_2445 : Events.SYNC_DATA2;
	}

	
	/* class methods, constructor */

	@SuppressLint("InlinedApi")
	public static void create(Account account, ContentResolver resolver, ServerInfo.ResourceInfo info) throws LocalStorageException {
		ContentProviderClient client = resolver.acquireContentProviderClient(CalendarContract.AUTHORITY);
		if (client == null)
			throw new LocalStorageException("No Calendar Provider found (Calendar app disabled?)");
		
		int color = 0xFFC3EA6E;		// fallback: "DAVdroid green"
		if (info.getColor() != null) {
			Pattern p = Pattern.compile("#?(\\p{XDigit}{6})(\\p{XDigit}{2})?");
			Matcher m = p.matcher(info.getColor());
			if (m.find()) {
				int color_rgb = Integer.parseInt(m.group(1), 16);
				int color_alpha = m.group(2) != null ? (Integer.parseInt(m.group(2), 16) & 0xFF) : 0xFF;
				color = (color_alpha << 24) | color_rgb;
			}
		}

		ContentValues values = new ContentValues();
		values.put(Calendars.ACCOUNT_NAME, account.name);
		values.put(Calendars.ACCOUNT_TYPE, account.type);
		values.put(Calendars.NAME, info.getURL());
		values.put(Calendars.CALENDAR_DISPLAY_NAME, info.getTitle());
		values.put(Calendars.CALENDAR_COLOR, color);
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
			values.put(Calendars.CALENDAR_TIME_ZONE, info.getTimezone());
		
		Log.i(TAG, "Inserting calendar: " + values.toString() + " -> " + calendarsURI(account).toString());
		try {
			client.insert(calendarsURI(account), values);
		} catch(RemoteException e) {
			throw new LocalStorageException(e);
		}
	}
	
	public static LocalCalendar[] findAll(Account account, ContentProviderClient providerClient) throws RemoteException {
		@Cleanup Cursor cursor = providerClient.query(calendarsURI(account),
				new String[] { Calendars._ID, Calendars.NAME },
				Calendars.DELETED + "=0 AND " + Calendars.SYNC_EVENTS + "=1", null, null);
		
		LinkedList<LocalCalendar> calendars = new LinkedList<LocalCalendar>();
		while (cursor != null && cursor.moveToNext())
			calendars.add(new LocalCalendar(account, providerClient, cursor.getInt(0), cursor.getString(1)));
		return calendars.toArray(new LocalCalendar[0]);
	}

	public LocalCalendar(Account account, ContentProviderClient providerClient, long id, String url) throws RemoteException {
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
			if (c.moveToFirst()) {
				return c.getString(0);
			} else
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
	
	public void deleteAllExceptRemoteNames(Resource[] remoteResources) {
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
				.withSelection(where, new String[] { String.valueOf(id) })
				.withYieldAllowed(true)
				.build()
		);
	}

	@Override
	public void delete(Resource resource) {
		super.delete(resource);

		// delete all exceptions of this event, too
		pendingOperations.add(ContentProviderOperation
				.newDelete(entriesURI())
				.withSelection(Events.ORIGINAL_ID+"=?", new String[] { Long.toString(resource.getLocalID()) })
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
				.withSelection(Events.ORIGINAL_ID+"=?", new String[] { Long.toString(resource.getLocalID()) })
				.build()
		);
	}
	
	
	/* methods for populating the data object from the content provider */

	@Override
	public void populate(Resource resource) throws LocalStorageException {
		Event e = (Event)resource;
		
		try {
			@Cleanup Cursor cursor = providerClient.query(ContentUris.withAppendedId(entriesURI(), e.getLocalID()),
				new String[] {
					/*  0 */ Events.TITLE, Events.EVENT_LOCATION, Events.DESCRIPTION,
					/*  3 */ Events.DTSTART, Events.DTEND, Events.EVENT_TIMEZONE, Events.EVENT_END_TIMEZONE, Events.ALL_DAY,
					/*  8 */ Events.STATUS, Events.ACCESS_LEVEL,
					/* 10 */ Events.RRULE, Events.RDATE, Events.EXRULE, Events.EXDATE,
					/* 14 */ Events.HAS_ATTENDEE_DATA, Events.ORGANIZER, Events.SELF_ATTENDEE_STATUS,
					/* 17 */ entryColumnUID(), Events.DURATION, Events.AVAILABILITY,
					/* 20 */ Events.ORIGINAL_ALL_DAY, Events.ORIGINAL_INSTANCE_TIME
				}, null, null, null);
			if (cursor != null && cursor.moveToNext()) {
				e.setUid(cursor.getString(17));
				
				e.setSummary(cursor.getString(0));
				e.setLocation(cursor.getString(1));
				e.setDescription(cursor.getString(2));
				
				boolean allDay = cursor.getInt(7) != 0;
				long tsStart = cursor.getLong(3),
					 tsEnd = cursor.getLong(4);
				String duration = cursor.getString(18);
				
				String tzId = null;
				if (allDay) {
					e.setDtStart(tsStart, null);
					// provide only DTEND and not DURATION for all-day events
					if (tsEnd == 0) {
						Dur dur = new Dur(duration);
						java.util.Date dEnd = dur.getTime(new java.util.Date(tsStart));
						tsEnd = dEnd.getTime();
					}
					e.setDtEnd(tsEnd, null);
					
				} else {
					// use the start time zone for the end time, too
					// because apps like Samsung Planner allow the user to change "the" time zone but change the start time zone only
					tzId = cursor.getString(5);
					e.setDtStart(tsStart, tzId);
					if (tsEnd != 0)
						e.setDtEnd(tsEnd, tzId);
					else if (!StringUtils.isEmpty(duration))
						e.setDuration(new Duration(new Dur(duration)));
				}
					
				// recurrence
				try {
					String strRRule = cursor.getString(10);
					if (!StringUtils.isEmpty(strRRule))
						e.setRrule(new RRule(strRRule));
					
					String strRDate = cursor.getString(11);
					if (!StringUtils.isEmpty(strRDate)) {
						RDate rDate = new RDate();
						rDate.setValue(strRDate);
						e.setRdate(rDate);
					}
				
					String strExRule = cursor.getString(12);
					if (!StringUtils.isEmpty(strExRule)) {
						ExRule exRule = new ExRule();
						exRule.setValue(strExRule);
						e.setExrule(exRule);
					}
					
					String strExDate = cursor.getString(13);
					if (!StringUtils.isEmpty(strExDate)) {
						// ignored, see https://code.google.com/p/android/issues/detail?id=21426
						ExDate exDate = new ExDate();
						exDate.setValue(strExDate);
						e.setExdate(exDate);
					}
				} catch (ParseException ex) {
					Log.w(TAG, "Couldn't parse recurrence rules, ignoring", ex);
				} catch (IllegalArgumentException ex) {
					Log.w(TAG, "Invalid recurrence rules, ignoring", ex);
				}

				// recurrence exceptions
				if (!cursor.isNull(21)) {
					long originalInstanceTime = cursor.getLong(21);
					boolean originalAllDay = cursor.getInt(20) != 0;
					Date originalDate = originalAllDay ?
							new Date(originalInstanceTime) :
							new DateTime(originalInstanceTime);
					if (originalDate instanceof DateTime)
						((DateTime)originalDate).setUtc(true);
					e.setRecurrenceId(new RecurrenceId(originalDate));
				} else
					// this event may have exceptions
					populateExceptions(e);

				// status
				switch (cursor.getInt(8)) {
				case Events.STATUS_CONFIRMED:
					e.setStatus(Status.VEVENT_CONFIRMED);
					break;
				case Events.STATUS_TENTATIVE:
					e.setStatus(Status.VEVENT_TENTATIVE);
					break;
				case Events.STATUS_CANCELED:
					e.setStatus(Status.VEVENT_CANCELLED);
				}
				
				// availability
				e.setOpaque(cursor.getInt(19) != Events.AVAILABILITY_FREE);
					
				// attendees
				if (cursor.getInt(14) != 0) {	// has attendees
					try {
						e.setOrganizer(new Organizer(new URI("mailto", cursor.getString(15), null)));
					} catch (URISyntaxException ex) {
						Log.e(TAG, "Error when creating ORGANIZER URI, ignoring", ex);
					}
					populateAttendees(e);
				}
				
				// classification
				switch (cursor.getInt(9)) {
				case Events.ACCESS_CONFIDENTIAL:
				case Events.ACCESS_PRIVATE:
					e.setForPublic(false);
					break;
				case Events.ACCESS_PUBLIC:
					e.setForPublic(true);
				}
				
				populateReminders(e);
			} else
				throw new RecordNotFoundException();
		} catch(RemoteException ex) {
			throw new LocalStorageException(ex);
		}
	}

	void populateExceptions(Event e) throws RemoteException {
		Uri exceptionsUri = Events.CONTENT_URI.buildUpon()
				.appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
				.build();
		@Cleanup Cursor c = providerClient.query(exceptionsUri, new String[] {
				/* 0 */ Events._ID, entryColumnRemoteName()
		}, Events.ORIGINAL_ID + "=?", new String[] { String.valueOf(e.getLocalID()) }, null);
		while (c != null && c.moveToNext()) {
			long exceptionId = c.getLong(0);
			String exceptionRemoteName = c.getString(1);
			Log.i(TAG, "Found exception ID " + exceptionId + " of original ID " + e.getLocalID());
			try {
				Event exception = new Event(exceptionId, exceptionRemoteName, null);
				populate(exception);
				e.getExceptions().add(exception);
			} catch (LocalStorageException ex) {
				Log.e(TAG, "Couldn't find exception details, ignoring");
			}
		}
	}

	void populateAttendees(Event e) throws RemoteException {
		Uri attendeesUri = Attendees.CONTENT_URI.buildUpon()
				.appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
				.build();
		@Cleanup Cursor c = providerClient.query(attendeesUri, new String[]{
				/* 0 */ Attendees.ATTENDEE_EMAIL, Attendees.ATTENDEE_NAME, Attendees.ATTENDEE_TYPE,
				/* 3 */ Attendees.ATTENDEE_RELATIONSHIP, Attendees.STATUS
		}, Attendees.EVENT_ID + "=?", new String[]{String.valueOf(e.getLocalID())}, null);
		while (c != null && c.moveToNext()) {
			try {
				Attendee attendee = new Attendee(new URI("mailto", c.getString(0), null));
				ParameterList params = attendee.getParameters();
				
				String cn = c.getString(1);
				if (cn != null)
					params.add(new Cn(cn));
				
				// type
				int type = c.getInt(2);
				params.add((type == Attendees.TYPE_RESOURCE) ? CuType.RESOURCE : CuType.INDIVIDUAL);
				
				// role
				int relationship = c.getInt(3); 
				switch (relationship) {
				case Attendees.RELATIONSHIP_ORGANIZER:
					params.add(Role.CHAIR);
					break;
				case Attendees.RELATIONSHIP_ATTENDEE:
				case Attendees.RELATIONSHIP_PERFORMER:
				case Attendees.RELATIONSHIP_SPEAKER:
					params.add((type == Attendees.TYPE_REQUIRED) ? Role.REQ_PARTICIPANT : Role.OPT_PARTICIPANT);
					break;
				case Attendees.RELATIONSHIP_NONE:
					params.add(Role.NON_PARTICIPANT);
				}
	
				// status
				switch (c.getInt(4)) {
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
				
				e.getAttendees().add(attendee);
			} catch (URISyntaxException ex) {
				Log.e(TAG, "Couldn't parse attendee information, ignoring", ex);
			}
		}
	}

	void populateReminders(Event e) throws RemoteException {
		// reminders
		Uri remindersUri = Reminders.CONTENT_URI.buildUpon()
				.appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
				.build();
		@Cleanup Cursor c = providerClient.query(remindersUri, new String[] {
				/* 0 */ Reminders.MINUTES, Reminders.METHOD
			}, Reminders.EVENT_ID + "=?", new String[] { String.valueOf(e.getLocalID()) }, null);
		while (c != null && c.moveToNext()) {
			VAlarm alarm = new VAlarm(new Dur(0, 0, -c.getInt(0), 0));
			
			PropertyList props = alarm.getProperties();
			switch (c.getInt(1)) {
			/*case Reminders.METHOD_EMAIL:
				props.add(Action.EMAIL);
				break;*/
			default:
				props.add(Action.DISPLAY);
				props.add(new Description(e.getSummary()));
			}
			e.getAlarms().add(alarm);
		}
	}

	
	/* content builder methods */

	@Override
	protected Builder buildEntry(Builder builder, Resource resource) {
		Event event = (Event)resource;

		builder = builder
				.withValue(Events.CALENDAR_ID, id)
				.withValue(Events.ALL_DAY, event.isAllDay() ? 1 : 0)
				.withValue(Events.DTSTART, event.getDtStartInMillis())
				.withValue(Events.EVENT_TIMEZONE, event.getDtStartTzID())
				.withValue(Events.HAS_ATTENDEE_DATA, event.getAttendees().isEmpty() ? 0 : 1)
				.withValue(Events.GUESTS_CAN_INVITE_OTHERS, 1)
				.withValue(Events.GUESTS_CAN_MODIFY, 1)
				.withValue(Events.GUESTS_CAN_SEE_GUESTS, 1);

		RecurrenceId recurrenceId = event.getRecurrenceId();
		if (recurrenceId == null) {
			// this event is a "master event" (not an exception)
			builder = builder
					.withValue(entryColumnRemoteName(), event.getName())
					.withValue(entryColumnETag(), event.getETag())
					.withValue(entryColumnUID(), event.getUid());
		} else {
			builder = builder.withValue(Events.ORIGINAL_SYNC_ID, event.getName());

			// ORIGINAL_INSTANCE_TIME and ORIGINAL_ALL_DAY is set in buildExceptions.
			// It's not possible to use only the RECURRENCE-ID to calculate
			// ORIGINAL_INSTANCE_TIME and ORIGINAL_ALL_DAY because iCloud sends DATE-TIME
			// RECURRENCE-IDs even if the original event is an all-day event.
		}

		boolean recurring = false;
		if (event.getRrule() != null) {
			recurring = true;
			builder = builder.withValue(Events.RRULE, event.getRrule().getValue());
		}
		if (event.getRdate() != null) {
			recurring = true;
			builder = builder.withValue(Events.RDATE, event.getRdate().getValue());
		}
		if (event.getExrule() != null)
			builder = builder.withValue(Events.EXRULE, event.getExrule().getValue());
		if (event.getExdate() != null)
			builder = builder.withValue(Events.EXDATE, event.getExdate().getValue());
		
		// set either DTEND for single-time events or DURATION for recurring events
		// because that's the way Android likes it (see docs)
		if (recurring) {
			// calculate DURATION from start and end date
			Duration duration = new Duration(event.getDtStart().getDate(), event.getDtEnd().getDate());
			builder = builder.withValue(Events.DURATION, duration.getValue());
		} else {
			builder = builder
					.withValue(Events.DTEND, event.getDtEndInMillis())
					.withValue(Events.EVENT_END_TIMEZONE, event.getDtEndTzID());
		}
		
		if (event.getSummary() != null)
			builder = builder.withValue(Events.TITLE, event.getSummary());
		if (event.getLocation() != null)
			builder = builder.withValue(Events.EVENT_LOCATION, event.getLocation());
		if (event.getDescription() != null)
			builder = builder.withValue(Events.DESCRIPTION, event.getDescription());
		
		if (event.getOrganizer() != null && event.getOrganizer().getCalAddress() != null) {
			URI organizer = event.getOrganizer().getCalAddress();
			if (organizer.getScheme() != null && organizer.getScheme().equalsIgnoreCase("mailto"))
				builder = builder.withValue(Events.ORGANIZER, organizer.getSchemeSpecificPart());
		}
		
		Status status = event.getStatus();
		if (status != null) {
			int statusCode = Events.STATUS_TENTATIVE;
			if (status == Status.VEVENT_CONFIRMED)
				statusCode = Events.STATUS_CONFIRMED;
			else if (status == Status.VEVENT_CANCELLED)
				statusCode = Events.STATUS_CANCELED;
			builder = builder.withValue(Events.STATUS, statusCode);
		}
		
		builder = builder.withValue(Events.AVAILABILITY, event.isOpaque() ? Events.AVAILABILITY_BUSY : Events.AVAILABILITY_FREE);
		
		if (event.getForPublic() != null)
			builder = builder.withValue(Events.ACCESS_LEVEL, event.getForPublic() ? Events.ACCESS_PUBLIC : Events.ACCESS_PRIVATE);

		return builder;
	}

	
	@Override
	protected void addDataRows(Resource resource, long localID, int backrefIdx) {
		Event event = (Event)resource;
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
		Event event = (Event)resource;
		// delete exceptions
		pendingOperations.add(ContentProviderOperation.newDelete(entriesURI())
				.withSelection(Events.ORIGINAL_ID + "=?",
				new String[] { String.valueOf(event.getLocalID()) }).build());
		// delete attendees
		pendingOperations.add(ContentProviderOperation.newDelete(syncAdapterURI(Attendees.CONTENT_URI))
				.withSelection(Attendees.EVENT_ID + "=?",
				new String[] { String.valueOf(event.getLocalID()) }).build());
		// delete reminders
		pendingOperations.add(ContentProviderOperation.newDelete(syncAdapterURI(Reminders.CONTENT_URI))
				.withSelection(Reminders.EVENT_ID + "=?",
				new String[] { String.valueOf(event.getLocalID()) }).build());
	}


	protected Builder buildException(Builder builder, Event master, Event exception) {
		buildEntry(builder, exception);
		builder.withValue(Events.ORIGINAL_SYNC_ID, exception.getName());

		// Some servers (iCloud, for instance) return RECURRENCE-ID with DATE-TIME even if
		// the original event is an all-day event. Workaround: determine value of ORIGINAL_ALL_DAY
		// by original event type (all-day or not) and not by whether RECURRENCE-ID is DATE or DATE-TIME.

		RecurrenceId recurrenceId = exception.getRecurrenceId();
		Date date = recurrenceId.getDate();

		boolean originalAllDay = master.isAllDay();
		if (originalAllDay && date instanceof DateTime) {
			String value = recurrenceId.getValue();
			if (value.matches("^\\d{8}T\\d{6}$"))
				try {
					// no "Z" at the end indicates "local" time
					// so this is a "local" time, but it should be a ical4j Date without time
					date = new Date(value.substring(0, 8));
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
		Uri member = Uri.parse(attendee.getValue());
		String email = member.getSchemeSpecificPart();
		
		Cn cn = (Cn)attendee.getParameter(Parameter.CN);
		if (cn != null)
			builder = builder.withValue(Attendees.ATTENDEE_NAME, cn.getValue());
		
		int type = Attendees.TYPE_NONE;
		
		CuType cutype = (CuType)attendee.getParameter(Parameter.CUTYPE);
		if (cutype == CuType.RESOURCE)
			type = Attendees.TYPE_RESOURCE;
		else {
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
			builder = builder.withValue(Attendees.ATTENDEE_RELATIONSHIP, relationship);
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
			.withValue(Attendees.ATTENDEE_EMAIL, email)
			.withValue(Attendees.ATTENDEE_TYPE, type)
			.withValue(Attendees.ATTENDEE_STATUS, status);
	}
	
	protected Builder buildReminder(Builder builder, VAlarm alarm) {
		int minutes = 0;
		
		Dur duration;
		if (alarm.getTrigger() != null && (duration = alarm.getTrigger().getDuration()) != null)
			minutes = duration.getDays() * 24*60 + duration.getHours()*60 + duration.getMinutes();
		
		Log.d(TAG, "Adding alarm " + minutes + " min before");
		
		return builder
				.withValue(Reminders.METHOD, Reminders.METHOD_ALERT)
				.withValue(Reminders.MINUTES, minutes);
	}
	
	
	
	/* private helper methods */
	
	protected static Uri calendarsURI(Account account) {
		return Calendars.CONTENT_URI.buildUpon().appendQueryParameter(Calendars.ACCOUNT_NAME, account.name)
				.appendQueryParameter(Calendars.ACCOUNT_TYPE, account.type)
				.appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true").build();
	}

	protected Uri calendarsURI() {
		return calendarsURI(account);
	}

}
