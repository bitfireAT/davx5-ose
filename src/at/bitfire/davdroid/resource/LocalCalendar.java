/*******************************************************************************
 * Copyright (c) 2013 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.resource;

import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import lombok.Getter;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.ParameterList;
import net.fortuna.ical4j.model.parameter.Cn;
import net.fortuna.ical4j.model.parameter.CuType;
import net.fortuna.ical4j.model.parameter.PartStat;
import net.fortuna.ical4j.model.parameter.Role;
import net.fortuna.ical4j.model.property.Attendee;
import net.fortuna.ical4j.model.property.ExDate;
import net.fortuna.ical4j.model.property.ExRule;
import net.fortuna.ical4j.model.property.Organizer;
import net.fortuna.ical4j.model.property.RDate;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.Status;
import android.accounts.Account;
import android.annotation.SuppressLint;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentProviderOperation.Builder;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract;
import android.util.Log;
import at.bitfire.davdroid.syncadapter.ServerInfo;

import com.google.common.base.Joiner;

public class LocalCalendar extends LocalCollection {
	private static final String TAG = "davdroid.LocalCalendar";

	protected final static String
		CALENDARS_COLUMN_CTAG = Calendars.CAL_SYNC1,
		EVENTS_COLUMN_REMOTE_NAME = Events._SYNC_ID,
		EVENTS_COLUMN_ETAG = Events.SYNC_DATA1;

	protected long id;
	@Getter protected String path, cTag;
	
	
	/* class methods */

	@SuppressLint("InlinedApi")
	public static void create(Account account, ContentResolver resolver, ServerInfo.ResourceInfo info) throws RemoteException {
		ContentProviderClient client = resolver.acquireContentProviderClient(CalendarContract.AUTHORITY);
		
		ContentValues values = new ContentValues();
		values.put(Calendars.ACCOUNT_NAME, account.name);
		values.put(Calendars.ACCOUNT_TYPE, account.type);
		values.put(Calendars.NAME, info.getPath());
		values.put(Calendars.CALENDAR_DISPLAY_NAME, info.getTitle());
		values.put(Calendars.CALENDAR_COLOR, 0xC3EA6E);
		values.put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER);
		values.put(Calendars.ALLOWED_AVAILABILITY, Events.AVAILABILITY_BUSY + "," + Events.AVAILABILITY_FREE + "," + Events.AVAILABILITY_TENTATIVE);
		values.put(Calendars.ALLOWED_ATTENDEE_TYPES, Attendees.TYPE_NONE + "," + Attendees.TYPE_REQUIRED + "," + Attendees.TYPE_OPTIONAL + "," + Attendees.TYPE_RESOURCE);
		values.put(Calendars.OWNER_ACCOUNT, account.name);
		values.put(Calendars.SYNC_EVENTS, 1);
		values.put(Calendars.VISIBLE, 1);
		Log.i(TAG, "Inserting calendar: " + values.toString() + " -> " + calendarsURI(account).toString());
		client.insert(calendarsURI(account), values);
	}
	
	public static LocalCalendar[] findAll(Account account, ContentProviderClient providerClient) throws RemoteException {
		Cursor cursor = providerClient.query(calendarsURI(account),
				new String[] { Calendars._ID, Calendars.NAME, CALENDARS_COLUMN_CTAG },
				Calendars.DELETED + "=0 AND " + Calendars.SYNC_EVENTS + "=1", null, null);
		LinkedList<LocalCalendar> calendars = new LinkedList<LocalCalendar>();
		while (cursor.moveToNext())
			calendars.add(new LocalCalendar(account, providerClient, cursor.getInt(0), cursor.getString(1), cursor.getString(2)));
		return calendars.toArray(new LocalCalendar[0]);
	}

	public LocalCalendar(Account account, ContentProviderClient providerClient, int id, String path, String cTag) throws RemoteException {
		super(account, providerClient);
		this.id = id;
		this.path = path;
		this.cTag = cTag;
	}
	
	
	/* find multiple rows */

	@Override
	public Resource[] findDeleted() throws RemoteException {
		Cursor cursor = providerClient.query(entriesURI(),
				new String[] { Events._ID, EVENTS_COLUMN_REMOTE_NAME, EVENTS_COLUMN_ETAG },
				Events.CALENDAR_ID + "=? AND " + Events.DELETED + "=1", new String[] { String.valueOf(id) }, null);
		LinkedList<Event> events = new LinkedList<Event>();
		while (cursor.moveToNext())
			events.add(new Event(cursor.getLong(0), cursor.getString(1), cursor.getString(2)));
		return events.toArray(new Event[0]);
	}

	@Override
	public Resource[] findDirty() throws RemoteException {
		Cursor cursor = providerClient.query(entriesURI(),
				new String[] { Events._ID, EVENTS_COLUMN_REMOTE_NAME, EVENTS_COLUMN_ETAG },
				Events.DIRTY + "=1", null, null);
		LinkedList<Event> events = new LinkedList<Event>();
		while (cursor.moveToNext()) {
			Event e = new Event(cursor.getLong(0), cursor.getString(1), cursor.getString(2));
			populate(e);
			events.add(e);
		}
		return events.toArray(new Event[0]);
	}

	@Override
	public Resource[] findNew() throws RemoteException {
		Cursor cursor = providerClient.query(entriesURI(), new String[] { Events._ID },
				Events.DIRTY + "=1 AND " + EVENTS_COLUMN_REMOTE_NAME + " IS NULL", null, null);
		LinkedList<Event> events = new LinkedList<Event>();
		while (cursor.moveToNext()) {
			String uid = UUID.randomUUID().toString(),
				   resourceName = uid + ".ics";
			Event e = new Event(cursor.getLong(0), resourceName, null);
			e.setUid(uid);
			populate(e);

			// new record: set resource name and UID in database
			pendingOperations.add(ContentProviderOperation
					.newUpdate(ContentUris.withAppendedId(entriesURI(), e.getLocalID()))
					.withValue(EVENTS_COLUMN_REMOTE_NAME, resourceName)
					.build());
			
			events.add(e);
		}
		return events.toArray(new Event[0]);
	}
	
	@Override
	public void setCTag(String cTag) {
		pendingOperations.add(ContentProviderOperation.newUpdate(ContentUris.withAppendedId(calendarsURI(), id))
			.withValue(CALENDARS_COLUMN_CTAG, cTag)
			.build());
	}

	
	/* get data */
	
	@Override
	public Event getByRemoteName(String remoteName) throws RemoteException {
		Cursor cursor = providerClient.query(entriesURI(), new String[] { Events._ID, EVENTS_COLUMN_REMOTE_NAME, EVENTS_COLUMN_ETAG },
				Events.CALENDAR_ID + "=? AND " + EVENTS_COLUMN_REMOTE_NAME + "=?", new String[] { String.valueOf(id), remoteName }, null);
		if (cursor.moveToNext())
			return new Event(cursor.getLong(0), cursor.getString(1), cursor.getString(2));
		return null;
	}

	@Override
	public void populate(Resource resource) throws RemoteException {
		Event e = (Event)resource;
		if (e.isPopulated())
			return;
		
		Cursor cursor = providerClient.query(ContentUris.withAppendedId(entriesURI(), e.getLocalID()),
			new String[] {
				/*  0 */ Events.TITLE, Events.EVENT_LOCATION, Events.DESCRIPTION,
				/*  3 */ Events.DTSTART, Events.DTEND, Events.EVENT_TIMEZONE, Events.EVENT_END_TIMEZONE, Events.ALL_DAY,
				/*  8 */ Events.STATUS, Events.ACCESS_LEVEL,
				/* 10 */ Events.RRULE, Events.RDATE, Events.EXRULE, Events.EXDATE,
				/* 14 */ Events.HAS_ATTENDEE_DATA, Events.ORGANIZER, Events.SELF_ATTENDEE_STATUS
			}, null, null, null);
		if (cursor.moveToNext()) {
			e.setSummary(cursor.getString(0));
			e.setLocation(cursor.getString(1));
			e.setDescription(cursor.getString(2));
			
			long tsStart = cursor.getLong(3),
				 tsEnd = cursor.getLong(4);
			if (cursor.getInt(7) != 0) {	// all-day, UTC
				e.setDtStart(tsStart, null);
				e.setDtEnd(tsEnd, null);
			} else {
				// use the start time zone for the end time, too
				// because the Samsung Planner UI allows the user to change the time zone
				// but it will change the start time zone only
				
				String	tzIdStart = cursor.getString(5);
						//tzIdEnd = cursor.getString(6);
				
				e.setDtStart(tsStart, tzIdStart);
				e.setDtEnd(tsEnd, tzIdStart /*(tzIdEnd != null) ? tzIdEnd : tzIdStart*/);
			}
			
			// recurrence
			try {
				String strRRule = cursor.getString(10);
				if (strRRule != null)
					e.setRrule(new RRule(strRRule));
				
				String strRDate = cursor.getString(11);
				if (strRDate != null) {
					RDate rDate = new RDate();
					rDate.setValue(strRDate);
					e.setRdate(rDate);
				}
			
				String strExRule = cursor.getString(12);
				if (strExRule != null) {
					ExRule exRule = new ExRule();
					exRule.setValue(strExRule);
					e.setExrule(exRule);
				}
				
				String strExDate = cursor.getString(13);
				if (strExDate != null) {
					// ignored, see https://code.google.com/p/android/issues/detail?id=21426
					ExDate exDate = new ExDate();
					exDate.setValue(strExDate);
					e.setExdate(exDate);
				}
				
			} catch (ParseException ex) {
				Log.e(TAG, "Couldn't parse recurrence rules, ignoring");
			}

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
			
			// attendees
			if (cursor.getInt(14) != 0) {	// has attendees
				try {
					e.setOrganizer(new Organizer("mailto:" + cursor.getString(15)));
				} catch (URISyntaxException ex) {
					Log.e(TAG, "Error parsing organizer email address, ignoring");
				}
				
				Uri attendeesUri = Attendees.CONTENT_URI.buildUpon()
						.appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
						.build();
				Cursor c = providerClient.query(attendeesUri, new String[] {
						/* 0 */ Attendees.ATTENDEE_EMAIL, Attendees.ATTENDEE_NAME, Attendees.ATTENDEE_TYPE,
						/* 3 */ Attendees.ATTENDEE_RELATIONSHIP, Attendees.STATUS
					}, Attendees.EVENT_ID + "=?", new String[] { String.valueOf(e.getLocalID()) }, null);
				while (c.moveToNext()) {
					try {
						Attendee attendee = new Attendee("mailto:" + c.getString(0));
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
						int status = Attendees.ATTENDEE_STATUS_NONE;
						if (relationship == Attendees.RELATIONSHIP_ORGANIZER)	// we are organizer
							status = cursor.getInt(16);
						else
							status = c.getInt(4);
						
						switch (status) {
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
						
						e.addAttendee(attendee);
					} catch (URISyntaxException ex) {
						Log.e(TAG, "Couldn't parse attendee member URI, ignoring member");
					}
				}
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
		}
	}


	/* create/update */

	@Override
	public void add(Resource resource) {
		Event event = (Event) resource;

		int idx = pendingOperations.size();
		pendingOperations.add(buildEvent(ContentProviderOperation.newInsert(entriesURI()), event)
				.withYieldAllowed(true)
				.build());
		
		addDataRows(event, -1, idx);
	}

	@Override
	public void updateByRemoteName(Resource remoteResource) throws RemoteException {
		Event remoteEvent = (Event) remoteResource,
			localEvent = (Event) getByRemoteName(remoteResource.getName());

		pendingOperations.add(buildEvent(
				ContentProviderOperation.newUpdate(ContentUris.withAppendedId(entriesURI(), localEvent.getLocalID())), remoteEvent)
				.withYieldAllowed(true).build());
		
		pendingOperations.add(ContentProviderOperation.newDelete(syncAdapterURI(Attendees.CONTENT_URI))
			.withSelection(Attendees.EVENT_ID + "=?",
			new String[] { String.valueOf(localEvent.getLocalID()) }).build());
		
		addDataRows(remoteEvent, localEvent.getLocalID(), -1);
	}

	@Override
	public void delete(Resource event) {
		pendingOperations.add(ContentProviderOperation.newDelete(
				ContentUris.withAppendedId(entriesURI(), event.getLocalID())).build());
	}

	@Override
	public void deleteAllExceptRemoteNames(Resource[] remoteResources) {
		Builder builder = ContentProviderOperation.newDelete(entriesURI());
		
		if (remoteResources.length != 0) {
			List<String> terms = new LinkedList<String>();
			for (Resource res : remoteResources)
				terms.add(EVENTS_COLUMN_REMOTE_NAME + "<>" + DatabaseUtils.sqlEscapeString(res.getName()));
			String where = Joiner.on(" AND ").join(terms);
			builder = builder.withSelection(where, null);
		} else
			builder = builder.withSelection(EVENTS_COLUMN_REMOTE_NAME + " IS NOT NULL", null);
		
		pendingOperations.add(builder.build());
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
	
	@Override
	protected Uri syncAdapterURI(Uri baseURI) {
		return baseURI.buildUpon()
				.appendQueryParameter(Events.ACCOUNT_NAME, account.name)
				.appendQueryParameter(Events.ACCOUNT_TYPE, account.type)
				.appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
				.build();
	}
	
	@Override
	protected Uri entriesURI() {
		return syncAdapterURI(Events.CONTENT_URI);
	}

	@Override
	public void clearDirty(Resource resource) {
		pendingOperations.add(ContentProviderOperation
				.newUpdate(ContentUris.withAppendedId(entriesURI(), resource.getLocalID()))
				.withValue(Events.DIRTY, 0).build());
	}
	
	private Builder newInsertBuilder(Uri dataUri, String refFieldName, long raw_ref_id, Integer backrefIdx) {
		Builder builder = ContentProviderOperation.newInsert(syncAdapterURI(dataUri));
		if (backrefIdx != -1)
			return builder.withValueBackReference(refFieldName, backrefIdx);
		else
			return builder.withValue(refFieldName, raw_ref_id);
	}
	
	protected void addDataRows(Event event, long localID, int backrefIdx) {
		for (Attendee attendee : event.getAttendees())
			pendingOperations.add(buildAttendee(newInsertBuilder(Attendees.CONTENT_URI, Attendees.EVENT_ID, localID, backrefIdx), attendee).build());
	}
	
	
	/* content builder methods */

	protected Builder buildEvent(Builder builder, Event event) {
		builder = builder.withValue(Events.CALENDAR_ID, id)
				.withValue(EVENTS_COLUMN_REMOTE_NAME, event.getName())
				.withValue(EVENTS_COLUMN_ETAG, event.getETag())
				.withValue(Events.ALL_DAY, event.isAllDay() ? 1 : 0)
				.withValue(Events.DTSTART, event.getDtStartInMillis())
				.withValue(Events.DTEND, event.getDtEndInMillis())
				.withValue(Events.EVENT_TIMEZONE, event.getDtStartTzID())
				.withValue(Events.HAS_ATTENDEE_DATA, event.getAttendees().isEmpty() ? 0 : 1);
		
		if (event.getDtEndTzID() != null)
			builder = builder.withValue(Events.EVENT_END_TIMEZONE, event.getDtEndTzID());
		
		if (event.getRrule() != null)
			builder = builder.withValue(Events.RRULE, event.getRrule().getValue());
		if (event.getRdate() != null)
			builder = builder.withValue(Events.RDATE, event.getRdate().getValue());
		if (event.getExrule() != null)
			builder = builder.withValue(Events.EXRULE, event.getExrule().getValue());
		if (event.getExdate() != null)
			builder = builder.withValue(Events.EXDATE, event.getExdate().getValue());
		
		if (event.getSummary() != null)
			builder = builder.withValue(Events.TITLE, event.getSummary());
		if (event.getLocation() != null)
			builder = builder.withValue(Events.EVENT_LOCATION, event.getLocation());
		if (event.getDescription() != null)
			builder = builder.withValue(Events.DESCRIPTION, event.getDescription());
		
		Status status = event.getStatus();
		if (status != null) {
			int statusCode = Events.STATUS_TENTATIVE;
			if (status == Status.VEVENT_CONFIRMED)
				statusCode = Events.STATUS_CONFIRMED;
			else if (status == Status.VEVENT_CANCELLED)
				statusCode = Events.STATUS_CANCELED;
			builder = builder.withValue(Events.STATUS, statusCode);
		}
		
		if (event.getForPublic() != null)
			builder = builder.withValue(Events.ACCESS_LEVEL, event.getForPublic() ? Events.ACCESS_PUBLIC : Events.ACCESS_PRIVATE);

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
		if (partStat == PartStat.NEEDS_ACTION)
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
}
