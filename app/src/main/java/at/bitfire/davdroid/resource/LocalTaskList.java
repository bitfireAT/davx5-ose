/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.resource;

import android.accounts.Account;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.property.Clazz;
import net.fortuna.ical4j.model.property.Completed;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Due;
import net.fortuna.ical4j.model.property.Duration;
import net.fortuna.ical4j.model.property.Status;
import net.fortuna.ical4j.util.TimeZones;

import org.apache.commons.lang3.StringUtils;
import org.dmfs.provider.tasks.TaskContract;

import java.util.LinkedList;

import at.bitfire.davdroid.DAVUtils;
import at.bitfire.davdroid.DateUtils;
import at.bitfire.davdroid.webdav.WebDavResource;
import lombok.Cleanup;
import lombok.Getter;

public class LocalTaskList extends LocalCollection<Task> {
	private static final String TAG = "davdroid.LocalTaskList";

	@Getter	protected String url;
	@Getter protected long id;

	public static final String TASKS_AUTHORITY = "org.dmfs.tasks";

	protected static final String COLLECTION_COLUMN_CTAG = TaskContract.TaskLists.SYNC1;

	@Override protected Uri entriesURI()                { return syncAdapterURI(TaskContract.Tasks.getContentUri(TASKS_AUTHORITY)); }
	@Override protected String entryColumnAccountType()	{ return TaskContract.Tasks.ACCOUNT_TYPE; }
	@Override protected String entryColumnAccountName()	{ return TaskContract.Tasks.ACCOUNT_NAME; }
	@Override protected String entryColumnParentID()	{ return TaskContract.Tasks.LIST_ID; }
	@Override protected String entryColumnID()			{ return TaskContract.Tasks._ID; }
	@Override protected String entryColumnRemoteName()	{ return TaskContract.Tasks._SYNC_ID; }
	@Override protected String entryColumnETag()		{ return TaskContract.Tasks.SYNC1; }
	@Override protected String entryColumnDirty()		{ return TaskContract.Tasks._DIRTY; }
	@Override protected String entryColumnDeleted()		{ return TaskContract.Tasks._DELETED; }
	@Override protected String entryColumnUID()		    { return TaskContract.Tasks.SYNC2; }


	public static Uri create(Account account, ContentResolver resolver, ServerInfo.ResourceInfo info) throws LocalStorageException {
		@Cleanup("release") final ContentProviderClient client = resolver.acquireContentProviderClient(TASKS_AUTHORITY);
		if (client == null)
			throw new LocalStorageException("No tasks provider found");

		ContentValues values = new ContentValues();
		values.put(TaskContract.TaskLists.ACCOUNT_NAME, account.name);
		values.put(TaskContract.TaskLists.ACCOUNT_TYPE, account.type);
		values.put(TaskContract.TaskLists._SYNC_ID, info.getURL());
		values.put(TaskContract.TaskLists.LIST_NAME, info.getTitle());
		values.put(TaskContract.TaskLists.LIST_COLOR, info.getColor() != null ? info.getColor() : DAVUtils.calendarGreen);
		values.put(TaskContract.TaskLists.OWNER, account.name);
		values.put(TaskContract.TaskLists.ACCESS_LEVEL, 0);
		values.put(TaskContract.TaskLists.SYNC_ENABLED, 1);
		values.put(TaskContract.TaskLists.VISIBLE, 1);

		Log.i(TAG, "Inserting task list: " + values.toString());
		try {
			return client.insert(taskListsURI(account), values);
		} catch (RemoteException e) {
			throw new LocalStorageException(e);
		}
	}

	public static LocalTaskList[] findAll(Account account, ContentProviderClient providerClient) throws RemoteException {
		@Cleanup Cursor cursor = providerClient.query(taskListsURI(account),
				new String[]{TaskContract.TaskLists._ID, TaskContract.TaskLists._SYNC_ID},
				null, null, null);

		LinkedList<LocalTaskList> taskList = new LinkedList<>();
		while (cursor != null && cursor.moveToNext())
			taskList.add(new LocalTaskList(account, providerClient, cursor.getInt(0), cursor.getString(1)));
		return taskList.toArray(new LocalTaskList[taskList.size()]);
	}

	public LocalTaskList(Account account, ContentProviderClient providerClient, long id, String url) {
		super(account, providerClient);
		this.id = id;
		this.url = url;
	}


	@Override
	public String getCTag() throws LocalStorageException {
		try {
			@Cleanup Cursor c = providerClient.query(ContentUris.withAppendedId(taskListsURI(account), id),
					new String[] { COLLECTION_COLUMN_CTAG }, null, null, null);
			if (c != null && c.moveToFirst())
				return c.getString(0);
			else
				throw new LocalStorageException("Couldn't query task list CTag");
		} catch(RemoteException e) {
			throw new LocalStorageException(e);
		}
	}

	@Override
	public void setCTag(String cTag) throws LocalStorageException {
		ContentValues values = new ContentValues(1);
		values.put(COLLECTION_COLUMN_CTAG, cTag);
		try {
			providerClient.update(ContentUris.withAppendedId(taskListsURI(account), id), values, null, null);
		} catch(RemoteException e) {
			throw new LocalStorageException(e);
		}
	}

	@Override
	public void updateMetaData(WebDavResource.Properties properties) throws LocalStorageException {
		ContentValues values = new ContentValues();

		final String displayName = properties.getDisplayName();
		if (displayName != null)
			values.put(TaskContract.TaskLists.LIST_NAME, displayName);

		final Integer color = properties.getColor();
		if (color != null)
			values.put(TaskContract.TaskLists.LIST_COLOR, color);

		try {
			if (values.size() > 0)
				providerClient.update(ContentUris.withAppendedId(taskListsURI(account), id), values, null, null);
		} catch(RemoteException e) {
			throw new LocalStorageException(e);
		}
	}

	@Override
	public Task newResource(long localID, String resourceName, String eTag) {
		return new Task(localID, resourceName, eTag);
	}


	@Override
	public void populate(Resource record) throws LocalStorageException {
		try {
			@Cleanup final Cursor cursor = providerClient.query(entriesURI(),
					new String[] {
							/*  0 */ entryColumnUID(), TaskContract.Tasks.TITLE, TaskContract.Tasks.LOCATION, TaskContract.Tasks.DESCRIPTION, TaskContract.Tasks.URL,
							/*  5 */ TaskContract.Tasks.CLASSIFICATION, TaskContract.Tasks.STATUS, TaskContract.Tasks.PERCENT_COMPLETE,
							/*  8 */ TaskContract.Tasks.TZ, TaskContract.Tasks.DTSTART, TaskContract.Tasks.IS_ALLDAY,
							/* 11 */ TaskContract.Tasks.DUE, TaskContract.Tasks.DURATION, TaskContract.Tasks.COMPLETED,
							/* 14 */ TaskContract.Tasks.CREATED, TaskContract.Tasks.LAST_MODIFIED, TaskContract.Tasks.PRIORITY
					}, entryColumnID() + "=?", new String[]{ String.valueOf(record.getLocalID()) }, null);

			Task task = (Task)record;
			if (cursor != null && cursor.moveToFirst()) {
				task.setUid(cursor.getString(0));

				if (!cursor.isNull(14))
					task.setCreatedAt(new DateTime(cursor.getLong(14)));
				if (!cursor.isNull(15))
					task.setLastModified(new DateTime(cursor.getLong(15)));

				if (!StringUtils.isEmpty(cursor.getString(1)))
					task.setSummary(cursor.getString(1));

				if (!StringUtils.isEmpty(cursor.getString(2)))
					task.setLocation(cursor.getString(2));

				if (!StringUtils.isEmpty(cursor.getString(3)))
					task.setDescription(cursor.getString(3));

				if (!StringUtils.isEmpty(cursor.getString(4)))
					task.setUrl(cursor.getString(4));

				if (!cursor.isNull(16))
					task.setPriority(cursor.getInt(16));

				if (!cursor.isNull(5))
					switch (cursor.getInt(5)) {
						case TaskContract.Tasks.CLASSIFICATION_PUBLIC:
							task.setClassification(Clazz.PUBLIC);
							break;
						case TaskContract.Tasks.CLASSIFICATION_CONFIDENTIAL:
							task.setClassification(Clazz.CONFIDENTIAL);
							break;
						default:
							task.setClassification(Clazz.PRIVATE);
					}

				if (!cursor.isNull(6))
					switch (cursor.getInt(6)) {
						case TaskContract.Tasks.STATUS_IN_PROCESS:
							task.setStatus(Status.VTODO_IN_PROCESS);
							break;
						case TaskContract.Tasks.STATUS_COMPLETED:
							task.setStatus(Status.VTODO_COMPLETED);
							break;
						case TaskContract.Tasks.STATUS_CANCELLED:
							task.setStatus(Status.VTODO_CANCELLED);
							break;
						default:
							task.setStatus(Status.VTODO_NEEDS_ACTION);
					}
				if (!cursor.isNull(7))
					task.setPercentComplete(cursor.getInt(7));

				TimeZone tz = null;
				if (!cursor.isNull(8))
					tz = DateUtils.tzRegistry.getTimeZone(cursor.getString(8));

				if (!cursor.isNull(9) && !cursor.isNull(10)) {
					long ts = cursor.getLong(9);
					boolean allDay = cursor.getInt(10) != 0;

					Date dt;
					if (allDay)
						dt = new Date(ts);
					else {
						dt = new DateTime(ts);
						if (tz != null)
							((DateTime)dt).setTimeZone(tz);
					}
					task.setDtStart(new DtStart(dt));
				}

				if (!cursor.isNull(11)) {
					DateTime dt = new DateTime(cursor.getLong(11));
					if (tz != null)
						dt.setTimeZone(tz);
					task.setDue(new Due(dt));
				}

				if (!cursor.isNull(12))
					task.setDuration(new Duration(new Dur(cursor.getString(12))));

				if (!cursor.isNull(13))
					task.setCompletedAt(new Completed(new DateTime(cursor.getLong(13))));
			}

		} catch (RemoteException e) {
			throw new LocalStorageException("Couldn't process locally stored task", e);
		}
	}

	@Override
	protected ContentProviderOperation.Builder buildEntry(ContentProviderOperation.Builder builder, Resource resource, boolean update) {
		final Task task = (Task)resource;

		if (!update)
			builder	.withValue(entryColumnParentID(), id)
					.withValue(entryColumnRemoteName(), task.getName())
                    .withValue(entryColumnDirty(), 0);      // _DIRTY is INTEGER DEFAULT 1 in org.dmfs.provider.tasks

		 builder.withValue(entryColumnUID(), task.getUid())
				.withValue(entryColumnETag(), task.getETag())
				.withValue(TaskContract.Tasks.TITLE, task.getSummary())
				.withValue(TaskContract.Tasks.LOCATION, task.getLocation())
				.withValue(TaskContract.Tasks.DESCRIPTION, task.getDescription())
				.withValue(TaskContract.Tasks.URL, task.getUrl())
		        .withValue(TaskContract.Tasks.PRIORITY, task.getPriority());

		if (task.getCreatedAt() != null)
			builder.withValue(TaskContract.Tasks.CREATED, task.getCreatedAt().getTime());
		if (task.getLastModified() != null)
			builder.withValue(TaskContract.Tasks.LAST_MODIFIED, task.getLastModified().getTime());

		if (task.getClassification() != null) {
			int classCode = TaskContract.Tasks.CLASSIFICATION_PRIVATE;
			if (task.getClassification() == Clazz.PUBLIC)
				classCode = TaskContract.Tasks.CLASSIFICATION_PUBLIC;
			else if (task.getClassification() == Clazz.CONFIDENTIAL)
				classCode = TaskContract.Tasks.CLASSIFICATION_CONFIDENTIAL;
			builder = builder.withValue(TaskContract.Tasks.CLASSIFICATION, classCode);
		}

		int statusCode = TaskContract.Tasks.STATUS_DEFAULT;
		if (task.getStatus() != null) {
			if (task.getStatus() == Status.VTODO_NEEDS_ACTION)
				statusCode = TaskContract.Tasks.STATUS_NEEDS_ACTION;
			else if (task.getStatus() == Status.VTODO_IN_PROCESS)
				statusCode = TaskContract.Tasks.STATUS_IN_PROCESS;
			else if (task.getStatus() == Status.VTODO_COMPLETED)
				statusCode = TaskContract.Tasks.STATUS_COMPLETED;
			else if (task.getStatus() == Status.VTODO_CANCELLED)
				statusCode = TaskContract.Tasks.STATUS_CANCELLED;
		}
		builder	.withValue(TaskContract.Tasks.STATUS, statusCode)
				.withValue(TaskContract.Tasks.PERCENT_COMPLETE, task.getPercentComplete());

		TimeZone tz = null;

		if (task.getDtStart() != null) {
			Date start = task.getDtStart().getDate();
			boolean allDay;
			if (start instanceof DateTime) {
				allDay = false;
				tz = ((DateTime)start).getTimeZone();
			} else
				allDay = true;
			long ts = start.getTime();
			builder	.withValue(TaskContract.Tasks.DTSTART, ts)
					.withValue(TaskContract.Tasks.IS_ALLDAY, allDay ? 1 : 0);
		}

		if (task.getDue() != null) {
			Due due = task.getDue();
			builder.withValue(TaskContract.Tasks.DUE, due.getDate().getTime());
			if (tz == null)
				tz = due.getTimeZone();

		} else if (task.getDuration() != null)
			builder.withValue(TaskContract.Tasks.DURATION, task.getDuration().getValue());

		if (task.getCompletedAt() != null) {
			Date completed = task.getCompletedAt().getDate();
			boolean allDay;
			if (completed instanceof DateTime) {
				allDay = false;
				if (tz == null)
					tz = ((DateTime)completed).getTimeZone();
			} else {
				task.getCompletedAt().setUtc(true);
				allDay = true;
			}
			long ts = completed.getTime();
			builder	.withValue(TaskContract.Tasks.COMPLETED, ts)
					.withValue(TaskContract.Tasks.COMPLETED_IS_ALLDAY, allDay ? 1 : 0);
		}

		// TZ *must* be provided when DTSTART or DUE is set
		if ((task.getDtStart() != null || task.getDue() != null) && tz == null)
			tz = DateUtils.tzRegistry.getTimeZone(TimeZones.GMT_ID);
		if (tz != null)
			builder.withValue(TaskContract.Tasks.TZ, DateUtils.findAndroidTimezoneID(tz.getID()));

		return builder;
	}

	@Override
	protected void addDataRows(Resource resource, long localID, int backrefIdx) {
	}

	@Override
	protected void removeDataRows(Resource resource) {
	}


	// helpers

	public static boolean isAvailable(Context context) {
		try {
			@Cleanup("release") ContentProviderClient client = context.getContentResolver().acquireContentProviderClient(TASKS_AUTHORITY);
			return client != null;
		} catch (SecurityException e) {
			Log.e(TAG, "DAVdroid is not allowed to access tasks", e);
			return false;
		}
	}

	@Override
	protected Uri syncAdapterURI(Uri baseURI) {
		return baseURI.buildUpon()
				.appendQueryParameter(entryColumnAccountType(), account.type)
				.appendQueryParameter(entryColumnAccountName(), account.name)
				.appendQueryParameter(TaskContract.CALLER_IS_SYNCADAPTER, "true")
				.build();
	}

	protected static Uri taskListsURI(Account account) {
		return TaskContract.TaskLists.getContentUri(TASKS_AUTHORITY).buildUpon()
				.appendQueryParameter(TaskContract.TaskLists.ACCOUNT_TYPE, account.type)
				.appendQueryParameter(TaskContract.TaskLists.ACCOUNT_NAME, account.name)
				.appendQueryParameter(TaskContract.CALLER_IS_SYNCADAPTER, "true")
				.build();
	}
}
