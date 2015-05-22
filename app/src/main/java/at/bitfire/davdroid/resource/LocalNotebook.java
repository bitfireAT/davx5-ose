package at.bitfire.davdroid.resource;

import android.accounts.Account;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.CalendarContract;
import android.util.Log;

import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.property.Created;
import net.fortuna.ical4j.model.property.DtStamp;

import org.apache.commons.lang.StringUtils;

import java.util.LinkedList;
import java.util.List;

import at.bitfire.davdroid.Constants;
import at.bitfire.notebooks.provider.NoteContract;
import lombok.Cleanup;
import lombok.Getter;

public class LocalNotebook extends LocalCollection<Note> {
	private final static String TAG = "davdroid.LocalNotebook";

	@Getter	protected final String url;
	@Getter protected final long id;

	protected static String COLLECTION_COLUMN_CTAG = NoteContract.Notebooks.SYNC1;

	@Override protected Uri entriesURI()                { return syncAdapterURI(NoteContract.Notes.CONTENT_URI); }
	@Override protected String entryColumnAccountType()	{ return NoteContract.Notes.ACCOUNT_TYPE; }
	@Override protected String entryColumnAccountName()	{ return NoteContract.Notes.ACCOUNT_NAME; }
	@Override protected String entryColumnParentID()	{ return NoteContract.Notes.NOTEBOOK_ID; }
	@Override protected String entryColumnID()			{ return NoteContract.Notes._ID; }
	@Override protected String entryColumnRemoteName()	{ return NoteContract.Notes._SYNC_ID; }
	@Override protected String entryColumnETag()		{ return NoteContract.Notes.SYNC1; }
	@Override protected String entryColumnDirty()		{ return NoteContract.Notes.DIRTY; }
	@Override protected String entryColumnDeleted()		{ return NoteContract.Notes.DELETED; }
	@Override protected String entryColumnUID()		    { return NoteContract.Notes.UID; }


	public static Uri create(Account account, ContentResolver resolver, ServerInfo.ResourceInfo info) throws LocalStorageException {
		final ContentProviderClient client = resolver.acquireContentProviderClient(NoteContract.AUTHORITY);
		if (client == null)
			throw new LocalStorageException("No notes provider found");

		ContentValues values = new ContentValues();
		values.put(NoteContract.Notebooks._SYNC_ID, info.getURL());
		values.put(NoteContract.Notebooks.NAME, info.getTitle());

		Log.i(TAG, "Inserting notebook: " + values.toString());
		try {
			return client.insert(notebooksURI(account), values);
		} catch (RemoteException e) {
			throw new LocalStorageException(e);
		}
	}

	public static LocalNotebook[] findAll(Account account, ContentProviderClient providerClient) throws RemoteException {
		@Cleanup Cursor cursor = providerClient.query(notebooksURI(account),
				new String[] { NoteContract.Notebooks._ID, NoteContract.Notebooks._SYNC_ID },
				NoteContract.Notebooks.DELETED + "=0", null, null);

		LinkedList<LocalNotebook> notebooks = new LinkedList<>();
		while (cursor != null && cursor.moveToNext())
			notebooks.add(new LocalNotebook(account, providerClient, cursor.getInt(0), cursor.getString(1)));
		return notebooks.toArray(new LocalNotebook[0]);
	}

	public LocalNotebook(Account account, ContentProviderClient providerClient, long id, String url) throws RemoteException {
		super(account, providerClient);
		this.id = id;
		this.url = url;

	}


	@Override
	public String getCTag() throws LocalStorageException {
		try {
			@Cleanup Cursor c = providerClient.query(ContentUris.withAppendedId(notebooksURI(account), id),
					new String[] { COLLECTION_COLUMN_CTAG }, null, null, null);
			if (c != null && c.moveToFirst())
				return c.getString(0);
			else
				throw new LocalStorageException("Couldn't query notebook CTag");
		} catch(RemoteException e) {
			throw new LocalStorageException(e);
		}
	}

	@Override
	public void setCTag(String cTag) throws LocalStorageException {
		ContentValues values = new ContentValues(1);
		values.put(COLLECTION_COLUMN_CTAG, cTag);
		try {
			providerClient.update(ContentUris.withAppendedId(notebooksURI(account), id), values, null, null);
		} catch(RemoteException e) {
			throw new LocalStorageException(e);
		}
	}

	@Override
	public Note newResource(long localID, String resourceName, String eTag) {
		return new Note(localID, resourceName, eTag);
	}


	@Override
	public void populate(Resource record) throws LocalStorageException {
		try {
			@Cleanup final Cursor cursor = providerClient.query(entriesURI(),
					new String[] {
							/* 0 */  entryColumnUID(), NoteContract.Notes.CREATED_AT, NoteContract.Notes.UPDATED_AT, NoteContract.Notes.DTSTART,
							/* 4 */  NoteContract.Notes.SUMMARY, NoteContract.Notes.DESCRIPTION, NoteContract.Notes.COMMENT,
							/* 7 */  NoteContract.Notes.ORGANIZER, NoteContract.Notes.STATUS, NoteContract.Notes.CLASSIFICATION,
							/* 10 */ NoteContract.Notes.CONTACT, NoteContract.Notes.URL
					}, entryColumnID() + "=?", new String[]{ String.valueOf(record.getLocalID()) }, null);

			Note note = (Note)record;
			if (cursor != null && cursor.moveToFirst()) {
				note.setUid(cursor.getString(0));

				if (!cursor.isNull(1))
					note.setCreated(new Created(new DateTime(cursor.getLong(1))));

				note.setSummary(cursor.getString(4));
				note.setDescription(cursor.getString(5));
			}

		} catch (RemoteException e) {
			throw new LocalStorageException("Couldn't process locally stored note", e);
		}
	}

	@Override
	protected ContentProviderOperation.Builder buildEntry(ContentProviderOperation.Builder builder, Resource resource, boolean update) {
		final Note note = (Note)resource;
		builder = builder
				.withValue(entryColumnParentID(), id)
				.withValue(entryColumnRemoteName(), note.getName())
				.withValue(entryColumnUID(), note.getUid())
				.withValue(entryColumnETag(), note.getETag())
				.withValue(NoteContract.Notes.SUMMARY, note.getSummary())
				.withValue(NoteContract.Notes.DESCRIPTION, note.getDescription());

		if (note.getCreated() != null)
			builder = builder.withValue(NoteContract.Notes.CREATED_AT, note.getCreated().getDateTime().getTime());

		return builder;
	}

	@Override
	protected void addDataRows(Resource resource, long localID, int backrefIdx) {
	}

	@Override
	protected void removeDataRows(Resource resource) {
	}


	// helpers

	protected static Uri notebooksURI(Account account) {
		return NoteContract.Notebooks.CONTENT_URI.buildUpon()
				.appendQueryParameter(NoteContract.Notebooks.ACCOUNT_TYPE, account.type)
				.appendQueryParameter(NoteContract.Notebooks.ACCOUNT_NAME, account.name)
				.appendQueryParameter(NoteContract.CALLER_IS_SYNCADAPTER, "true")
				.build();
	}
}
