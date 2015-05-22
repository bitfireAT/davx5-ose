package at.bitfire.davdroid.resource;

import android.accounts.Account;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.property.Clazz;
import net.fortuna.ical4j.model.property.Completed;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Status;

import org.dmfs.provider.tasks.TaskContract;

import java.util.LinkedList;

import lombok.Cleanup;
import lombok.Getter;

public class LocalTaskList extends LocalCollection<Task> {
	private static final String TAG = "davdroid.LocalTaskList";

	@Getter	protected String url;
	@Getter protected long id;

	protected static String COLLECTION_COLUMN_CTAG = TaskContract.TaskLists.SYNC1;

	@Override protected Uri entriesURI()                { return syncAdapterURI(TaskContract.Tasks.CONTENT_URI); }
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
		final ContentProviderClient client = resolver.acquireContentProviderClient(TaskContract.AUTHORITY);
		if (client == null)
			throw new LocalStorageException("No tasks provider found");

		ContentValues values = new ContentValues();
		values.put(TaskContract.TaskLists.ACCOUNT_NAME, account.name);
		values.put(TaskContract.TaskLists.ACCOUNT_TYPE, /*account.type*/"davdroid.new");
		values.put(TaskContract.TaskLists._SYNC_ID, info.getURL());
		values.put(TaskContract.TaskLists.LIST_NAME, info.getTitle());
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
				new String[] { TaskContract.TaskLists._ID, TaskContract.TaskLists._SYNC_ID },
				null, null, null);

		LinkedList<LocalTaskList> taskList = new LinkedList<>();
		while (cursor != null && cursor.moveToNext())
			taskList.add(new LocalTaskList(account, providerClient, cursor.getInt(0), cursor.getString(1)));
		return taskList.toArray(new LocalTaskList[0]);
	}

	public LocalTaskList(Account account, ContentProviderClient providerClient, long id, String url) throws RemoteException {
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
	public Task newResource(long localID, String resourceName, String eTag) {
		return new Task(localID, resourceName, eTag);
	}


	@Override
	public void populate(Resource record) throws LocalStorageException {
		try {
			@Cleanup final Cursor cursor = providerClient.query(entriesURI(),
					new String[] {
							/* 0 */ entryColumnUID(), TaskContract.Tasks.TITLE, TaskContract.Tasks.LOCATION, TaskContract.Tasks.DESCRIPTION, TaskContract.Tasks.URL,
							/* 5 */ TaskContract.Tasks.CLASSIFICATION, TaskContract.Tasks.STATUS, TaskContract.Tasks.PERCENT_COMPLETE,
							/* 8 */ TaskContract.Tasks.DTSTART, TaskContract.Tasks.IS_ALLDAY, /*TaskContract.Tasks.COMPLETED, TaskContract.Tasks.COMPLETED_IS_ALLDAY*/
					}, entryColumnID() + "=?", new String[]{ String.valueOf(record.getLocalID()) }, null);

			Task task = (Task)record;
			if (cursor != null && cursor.moveToFirst()) {
				task.setUid(cursor.getString(0));

				task.setSummary(cursor.getString(1));
				task.setLocation(cursor.getString(2));
				task.setDescription(cursor.getString(3));
				task.setUrl(cursor.getString(4));

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

				if (!cursor.isNull(8) && !cursor.isNull(9)) {
					long ts = cursor.getLong(8);
					boolean allDay = cursor.getInt(9) != 0;
					task.setDtStart(new DtStart(allDay ? new Date(ts) : new DateTime(ts)));
				}

				/*if (!cursor.isNull(10) && !cursor.isNull(11)) {
					long ts = cursor.getLong(10);
					// boolean allDay = cursor.getInt(11) != 0;
					task.setCompletedAt(new Completed(allDay ? new Date(ts) : new DateTime(ts)));
				}*/
			}

		} catch (RemoteException e) {
			throw new LocalStorageException("Couldn't process locally stored task", e);
		}
	}

	@Override
	protected ContentProviderOperation.Builder buildEntry(ContentProviderOperation.Builder builder, Resource resource, boolean update) {
		final Task task = (Task)resource;

		if (!update)
			builder = builder
					.withValue(entryColumnParentID(), id)
					.withValue(entryColumnRemoteName(), task.getName());

		builder = builder
				.withValue(entryColumnUID(), task.getUid())
				.withValue(entryColumnETag(), task.getETag())
				.withValue(TaskContract.Tasks.TITLE, task.getSummary())
				.withValue(TaskContract.Tasks.LOCATION, task.getLocation())
				.withValue(TaskContract.Tasks.DESCRIPTION, task.getDescription())
				.withValue(TaskContract.Tasks.URL, task.getUrl());

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
		builder = builder
				.withValue(TaskContract.Tasks.STATUS, statusCode)
				.withValue(TaskContract.Tasks.PERCENT_COMPLETE, task.getPercentComplete());

		/*if (task.getCreatedAt() != null)
			builder = builder.withValue(TaskContract.Tasks.CREATED, task.getCreatedAt().getDate().getTime());/*

		if (task.getDtStart() != null) {
			Date start = task.getDtStart().getDate();
			boolean allDay;
			if (start instanceof DateTime)
				allDay = false;
			else {
				task.getDtStart().setUtc(true);
				allDay = true;
			}
			long ts = start.getTime();
			builder = builder.withValue(TaskContract.Tasks.DTSTART, ts);
			builder = builder.withValue(TaskContract.Tasks.IS_ALLDAY, allDay ? 1 : 0);
		}

		/*if (task.getCompletedAt() != null) {
			Date completed = task.getCompletedAt().getDate();
			boolean allDay;
			if (completed instanceof DateTime)
				allDay = false;
			else {
				task.getCompletedAt().setUtc(true);
				allDay = true;
			}
			long ts = completed.getTime();
			builder = builder.withValue(TaskContract.Tasks.COMPLETED, ts);
			builder = builder.withValue(TaskContract.Tasks.COMPLETED_IS_ALLDAY, allDay ? 1 : 0);
		}*/

		return builder;
	}

	@Override
	protected void addDataRows(Resource resource, long localID, int backrefIdx) {
	}

	@Override
	protected void removeDataRows(Resource resource) {
	}


	// helpers

	@Override
	protected Uri syncAdapterURI(Uri baseURI) {
		return baseURI.buildUpon()
				.appendQueryParameter(entryColumnAccountType(), /*account.type*/"davdroid.new")
				.appendQueryParameter(entryColumnAccountName(), account.name)
				.appendQueryParameter(TaskContract.CALLER_IS_SYNCADAPTER, "true")
				.build();
	}

	protected static Uri taskListsURI(Account account) {
		return TaskContract.TaskLists.CONTENT_URI.buildUpon()
				.appendQueryParameter(TaskContract.TaskLists.ACCOUNT_TYPE, /*account.type*/"davdroid.new")
				.appendQueryParameter(TaskContract.TaskLists.ACCOUNT_NAME, account.name)
				.appendQueryParameter(TaskContract.CALLER_IS_SYNCADAPTER, "true")
				.build();
	}
}
