/*******************************************************************************
 * Copyright (c) 2013 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.resource;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import biweekly.property.DateEnd;
import biweekly.property.DateStart;
import biweekly.util.DateTimeComponents;

import com.google.common.base.Joiner;

import lombok.Getter;
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
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.ContactsContract.RawContacts;
import android.text.format.Time;
import android.util.Log;
import at.bitfire.davdroid.syncadapter.ServerInfo;

public class LocalCalendar extends LocalCollection {
	private static final String TAG = "davdroid.LocalCalendar";

	protected final static String
		CALENDARS_COLUMN_CTAG = Calendars.CAL_SYNC1,
		EVENTS_COLUMN_REMOTE_NAME = Events._SYNC_ID,
		EVENTS_COLUMN_ETAG = Events.SYNC_DATA1;

	protected long id;
	@Getter protected String path, cTag;
	
	
	/* class methods */
	
	public static void create(Account account, ContentResolver resolver, ServerInfo.ResourceInfo info) throws RemoteException {
		ContentProviderClient client = resolver.acquireContentProviderClient(CalendarContract.AUTHORITY);
		
		ContentValues values = new ContentValues();
		values.put(Calendars.ACCOUNT_NAME, account.name);
		values.put(Calendars.ACCOUNT_TYPE, account.type);
		values.put(Calendars.NAME, info.getPath());
		values.put(Calendars.CALENDAR_DISPLAY_NAME, info.getTitle());
		values.put(Calendars.CALENDAR_COLOR, 0xC3EA6E);
		values.put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER);
		values.put(Calendars.OWNER_ACCOUNT, account.name);
		values.put(Calendars.CALENDAR_TIME_ZONE, Time.TIMEZONE_UTC);
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
				Events.TITLE, Events.EVENT_LOCATION, Events.DESCRIPTION,
				Events.DTSTART, Events.DTEND, Events.ALL_DAY
			}, null, null, null);
		if (cursor.moveToNext()) {
			e.setSummary(cursor.getString(0));
			e.setLocation(cursor.getString(1));
			e.setDescription(cursor.getString(2));
			
			Date 	dateStart = new Date(cursor.getLong(3)),
					dateEnd = new Date(cursor.getLong(4));
			if (cursor.getInt(5) != 0) {
				e.setDtStart(new DateStart(dateStart, false));
				e.setDtEnd(new DateEnd(dateEnd, false));
			} else {
				e.setDtStart(new DateStart(dateStart));
				e.setDtEnd(new DateEnd(dateEnd));
			}
		}
	}


	/* create/update */	

	@Override
	public void add(Resource resource) {
		Event event = (Event) resource;

		pendingOperations.add(buildEvent(ContentProviderOperation.newInsert(entriesURI()), event)
				.withYieldAllowed(true)
				.build());
	}

	@Override
	public void updateByRemoteName(Resource remoteResource) throws RemoteException {
		Event remoteEvent = (Event) remoteResource,
			localEvent = (Event) getByRemoteName(remoteResource.getName());

		pendingOperations.add(buildEvent(
				ContentProviderOperation.newUpdate(ContentUris.withAppendedId(entriesURI(), localEvent.getLocalID())), remoteEvent)
				.withYieldAllowed(true).build());
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

	@Override
	public void clearDirty(Resource resource) {
		pendingOperations.add(ContentProviderOperation
				.newUpdate(ContentUris.withAppendedId(entriesURI(), resource.getLocalID()))
				.withValue(Events.DIRTY, 0).build());
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
	protected Uri entriesURI() {
		return Events.CONTENT_URI.buildUpon().appendQueryParameter(Events.ACCOUNT_NAME, account.name)
				.appendQueryParameter(Events.ACCOUNT_TYPE, account.type)
				.appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true").build();
	}

	@SuppressWarnings("deprecation")
	@SuppressLint("InlinedApi")
	protected Builder buildEvent(Builder builder, Event event) {
		DateStart dateStart = event.getDtStart();
		if (dateStart == null)
			return null;
		DateEnd dateEnd = event.getDtEnd();
		if (dateEnd == null)
			dateEnd = new DateEnd(dateStart.getValue());
		
		long dtStart, dtEnd;
		boolean allDay = !dateStart.hasTime();
		if (allDay) {
			// start date without time, but in UTC
			DateTimeComponents components = dateStart.getRawComponents();
			dtStart = Date.UTC(components.getYear() - 1900, components.getMonth() - 1, components.getDate(), 0, 0, 0);
			Log.i(TAG, "All-day event starting: " + dtStart);
			
			// end date without time, but in UTC
			components = event.getDtEnd().getRawComponents();
			dtEnd = Date.UTC(components.getYear() - 1900, components.getMonth() - 1, components.getDate(), 0, 0, 0);
		} else {
			dtStart = dateStart.getValue().getTime();
			dtEnd = event.getDtEnd().getValue().getTime();
		}
		
		builder = builder.withValue(Events.CALENDAR_ID, id)
				.withValue(EVENTS_COLUMN_REMOTE_NAME, event.getName())
				.withValue(EVENTS_COLUMN_ETAG, event.getETag())
				.withValue(Events.ALL_DAY, allDay ? 1 : 0)
				.withValue(Events.DTSTART, dtStart)
				.withValue(Events.DTEND, dtEnd)
				.withValue(Events.EVENT_TIMEZONE, Time.TIMEZONE_UTC);
		
		if (event.getSummary() != null)
			builder = builder.withValue(Events.TITLE, event.getSummary());
		if (event.getLocation() != null)
			builder = builder.withValue(Events.EVENT_LOCATION, event.getLocation());
		if (event.getDescription() != null)
			builder = builder.withValue(Events.DESCRIPTION, event.getDescription());

		return builder;
	}
}
