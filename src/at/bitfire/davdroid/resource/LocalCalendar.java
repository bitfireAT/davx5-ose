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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

import org.apache.commons.lang.StringUtils;

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
import android.provider.ContactsContract;
import android.util.Log;
import at.bitfire.davdroid.syncadapter.ServerInfo;

public class LocalCalendar extends LocalCollection<Event> {
	private static final String TAG = "davdroid.LocalCalendar";

	@Getter protected long id;
	@Getter protected String path, cTag;
	
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
		return (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) ?
			Events.UID_2445 : Events.SYNC_DATA2;
	}

	
	/* class methods, constructor */

	@SuppressLint("InlinedApi")
	public static void create(Account account, ContentResolver resolver, ServerInfo.ResourceInfo info) throws RemoteException {
		ContentProviderClient client = resolver.acquireContentProviderClient(CalendarContract.AUTHORITY);
		
		int color = 0xFFC3EA6E;		// fallback: "DAVdroid green"
		if (info.getColor() != null) {
			Pattern p = Pattern.compile("#(\\p{XDigit}{6})(\\p{XDigit}{2})?");
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
		values.put(Calendars.NAME, info.getPath());
		values.put(Calendars.CALENDAR_DISPLAY_NAME, info.getTitle());
		values.put(Calendars.CALENDAR_COLOR, color);
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
				new String[] { Calendars._ID, Calendars.NAME, COLLECTION_COLUMN_CTAG },
				Calendars.DELETED + "=0 AND " + Calendars.SYNC_EVENTS + "=1", null, null);
		
		LinkedList<LocalCalendar> calendars = new LinkedList<LocalCalendar>();
		while (cursor != null && cursor.moveToNext())
			calendars.add(new LocalCalendar(account, providerClient, cursor.getInt(0), cursor.getString(1), cursor.getString(2)));
		return calendars.toArray(new LocalCalendar[0]);
	}

	public LocalCalendar(Account account, ContentProviderClient providerClient, int id, String path, String cTag) throws RemoteException {
		super(account, providerClient);
		this.id = id;
		this.path = path;
		this.cTag = cTag;
	}

	
	/* collection operations */
	
	@Override
	public void setCTag(String cTag) {
		pendingOperations.add(ContentProviderOperation.newUpdate(ContentUris.withAppendedId(calendarsURI(), id))
			.withValue(COLLECTION_COLUMN_CTAG, cTag)
			.build());
	}

	
	/* content provider (= database) querying */
	
	@Override
	public Event findById(long localID, String remoteName, String eTag, boolean populate) throws RemoteException {
		Event e = new Event(localID, remoteName, eTag);
		if (populate)
			populate(e);
		return e;
	}
	
	@Override
	public Event findByRemoteName(String remoteName) throws RemoteException {
		Cursor cursor = providerClient.query(entriesURI(),
				new String[] { entryColumnID(), entryColumnRemoteName(), entryColumnETag() },
				Events.CALENDAR_ID + "=? AND " + entryColumnRemoteName() + "=?",
				new String[] { String.valueOf(id), remoteName }, null);
		if (cursor != null && cursor.moveToNext())
			return new Event(cursor.getLong(0), cursor.getString(1), cursor.getString(2));
		else
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
				/* 14 */ Events.HAS_ATTENDEE_DATA, Events.ORGANIZER, Events.SELF_ATTENDEE_STATUS,
				/* 17 */ entryColumnUID()
			}, null, null, null);
		if (cursor != null && cursor.moveToNext()) {
			e.setUid(cursor.getString(17));
			
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
					Log.e(TAG, "Error parsing organizer URI, ignoring");
				}
				
				Uri attendeesUri = Attendees.CONTENT_URI.buildUpon()
						.appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
						.build();
				Cursor c = providerClient.query(attendeesUri, new String[] {
						/* 0 */ Attendees.ATTENDEE_EMAIL, Attendees.ATTENDEE_NAME, Attendees.ATTENDEE_TYPE,
						/* 3 */ Attendees.ATTENDEE_RELATIONSHIP, Attendees.STATUS
					}, Attendees.EVENT_ID + "=?", new String[] { String.valueOf(e.getLocalID()) }, null);
				while (c != null && c.moveToNext()) {
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

	
	public void deleteAllExceptRemoteNames(Resource[] remoteResources) {
		String where;
		
		if (remoteResources.length != 0) {
			List<String> sqlFileNames = new LinkedList<String>();
			for (Resource res : remoteResources)
				sqlFileNames.add(DatabaseUtils.sqlEscapeString(res.getName()));
			where = entryColumnRemoteName() + " NOT IN (" + StringUtils.join(sqlFileNames, ",") + ")";
		} else
			where = entryColumnRemoteName() + " IS NOT NULL";
		
		Builder builder = ContentProviderOperation.newDelete(entriesURI())
				.withSelection(entryColumnParentID() + "=? AND (" + where + ")", new String[] { String.valueOf(id) });
		pendingOperations.add(builder
				.withYieldAllowed(true)
				.build());
	}

	
	/* private helper methods */
	
	@Override
	protected String fileExtension() {
		return ".ics";
	}
	
	protected static Uri calendarsURI(Account account) {
		return Calendars.CONTENT_URI.buildUpon().appendQueryParameter(Calendars.ACCOUNT_NAME, account.name)
				.appendQueryParameter(Calendars.ACCOUNT_TYPE, account.type)
				.appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true").build();
	}

	protected Uri calendarsURI() {
		return calendarsURI(account);
	}
	
	
	
	/* content builder methods */

	@Override
	protected Builder buildEntry(Builder builder, Event event) {
		builder = builder
				.withValue(Events.CALENDAR_ID, id)
				.withValue(entryColumnRemoteName(), event.getName())
				.withValue(entryColumnETag(), event.getETag())
				.withValue(entryColumnUID(), event.getUid())
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

	
	@Override
	protected void addDataRows(Event event, long localID, int backrefIdx) {
		for (Attendee attendee : event.getAttendees())
			pendingOperations.add(buildAttendee(newDataInsertBuilder(Attendees.CONTENT_URI, Attendees.EVENT_ID, localID, backrefIdx), attendee).build());
	}
	
	@Override
	protected void removeDataRows(Event event) {
		pendingOperations.add(ContentProviderOperation.newDelete(syncAdapterURI(Attendees.CONTENT_URI))
				.withSelection(Attendees.EVENT_ID + "=?",
				new String[] { String.valueOf(event.getLocalID()) }).build());
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
