/*******************************************************************************
 * Copyright (c) 2014 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.resource;

import java.util.ArrayList;

import lombok.Cleanup;
import android.accounts.Account;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentProviderOperation.Builder;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.CalendarContract;
import android.util.Log;

/**
 * Represents a locally-stored synchronizable collection (for instance, the
 * address book or a calendar). Manages a CTag that stores the last known
 * remote CTag (the remote CTag changes whenever something in the remote collection changes).
 * 
 * @param <T> Subtype of Resource that can be stored in the collection 
 */
public abstract class LocalCollection<T extends Resource> {
	private static final String TAG = "davdroid.LocalCollection";
	
	protected Account account;
	protected ContentProviderClient providerClient;
	protected ArrayList<ContentProviderOperation> pendingOperations = new ArrayList<ContentProviderOperation>();

	
	// database fields
	
	/** base Uri of the collection's entries (for instance, Events.CONTENT_URI);
	 *  apply syncAdapterURI() before returning a value */
	abstract protected Uri entriesURI();

	/** column name of the type of the account the entry belongs to */
	abstract protected String entryColumnAccountType();
	/** column name of the name of the account the entry belongs to */
	abstract protected String entryColumnAccountName();
	
	/** column name of the collection ID the entry belongs to */
	abstract protected String entryColumnParentID();
	/** column name of an entry's ID */
	abstract protected String entryColumnID();
	/** column name of an entry's file name on the WebDAV server */
	abstract protected String entryColumnRemoteName();
	/** column name of an entry's last ETag on the WebDAV server; null if entry hasn't been uploaded yet */
	abstract protected String entryColumnETag();
	
	/** column name of an entry's "dirty" flag (managed by content provider) */
	abstract protected String entryColumnDirty();
	/** column name of an entry's "deleted" flag (managed by content provider) */
	abstract protected String entryColumnDeleted();
	
	/** column name of an entry's UID */
	abstract protected String entryColumnUID();
	

	LocalCollection(Account account, ContentProviderClient providerClient) {
		this.account = account;
		this.providerClient = providerClient;
	}
	

	// collection operations
	
	/** gets the ID if the collection (for instance, ID of the Android calendar) */
	abstract public long getId();
	/** gets the CTag of the collection */
	abstract public String getCTag() throws LocalStorageException;
	/** sets the CTag of the collection */
	abstract public void setCTag(String cTag) throws LocalStorageException;

	
	// content provider (= database) querying

	/**
	 * Finds new resources (resources which haven't been uploaded yet).
	 * New resources are 1) dirty, and 2) don't have an ETag yet.
	 * 
	 * @return IDs of new resources
	 * @throws LocalStorageException when the content provider couldn't be queried
	 */
	public long[] findNew() throws LocalStorageException {
		String where = entryColumnDirty() + "=1 AND " + entryColumnETag() + " IS NULL";
		if (entryColumnParentID() != null)
			where += " AND " + entryColumnParentID() + "=" + String.valueOf(getId());
		try {
			@Cleanup Cursor cursor = providerClient.query(entriesURI(),
					new String[] { entryColumnID() },
					where, null, null);
			if (cursor == null)
				throw new LocalStorageException("Couldn't query new records");
			
			long[] fresh = new long[cursor.getCount()];
			for (int idx = 0; cursor.moveToNext(); idx++) {
				long id = cursor.getLong(0);
				
				// new record: generate UID + remote file name so that we can upload
				T resource = findById(id, false);
				resource.initialize();
				// write generated UID + remote file name into database
				ContentValues values = new ContentValues(2);
				values.put(entryColumnUID(), resource.getUid());
				values.put(entryColumnRemoteName(), resource.getName());
				providerClient.update(ContentUris.withAppendedId(entriesURI(), id), values, null, null);
				
				fresh[idx] = id;
			}
			return fresh;
		} catch(RemoteException ex) {
			throw new LocalStorageException(ex);
		}
	}
	
	/**
	 * Finds updated resources (resources which have already been uploaded, but have changed locally).
	 * Updated resources are 1) dirty, and 2) already have an ETag.
	 * 
	 * @return IDs of updated resources
	 * @throws LocalStorageException when the content provider couldn't be queried
	 */
	public long[] findUpdated() throws LocalStorageException {
		String where = entryColumnDirty() + "=1 AND " + entryColumnETag() + " IS NOT NULL";
		if (entryColumnParentID() != null)
			where += " AND " + entryColumnParentID() + "=" + String.valueOf(getId());
		try {
			@Cleanup Cursor cursor = providerClient.query(entriesURI(),
					new String[] { entryColumnID(), entryColumnRemoteName(), entryColumnETag() },
					where, null, null);
			if (cursor == null)
				throw new LocalStorageException("Couldn't query updated records");
			
			long[] updated = new long[cursor.getCount()];
			for (int idx = 0; cursor.moveToNext(); idx++)
				updated[idx] = cursor.getLong(0);
			return updated;
		} catch(RemoteException ex) {
			throw new LocalStorageException(ex);
		}
	}

	/**
	 * Finds deleted resources (resources which have been marked for deletion).
	 * Deleted resources have the "deleted" flag set.
	 * 
	 * @return IDs of deleted resources
	 * @throws LocalStorageException when the content provider couldn't be queried
	 */
	public long[] findDeleted() throws LocalStorageException {
		String where = entryColumnDeleted() + "=1";
		if (entryColumnParentID() != null)
			where += " AND " + entryColumnParentID() + "=" + String.valueOf(getId());
		try {
			@Cleanup Cursor cursor = providerClient.query(entriesURI(),
					new String[] { entryColumnID(), entryColumnRemoteName(), entryColumnETag() },
					where, null, null);
			if (cursor == null)
				throw new LocalStorageException("Couldn't query dirty records");
			
			long deleted[] = new long[cursor.getCount()];
			for (int idx = 0; cursor.moveToNext(); idx++)
				deleted[idx] = cursor.getLong(0);
			return deleted;
		} catch(RemoteException ex) {
			throw new LocalStorageException(ex);
		}
	}
	
	/**
	 * Finds a specific resource by ID.
	 * @param localID	ID of the resource
	 * @param populate	true: populates all data fields (for instance, contact or event details);
	 * 					false: only remote file name and ETag are populated
	 * @return resource with either ID/remote file/name/ETag or all fields populated
	 * @throws RecordNotFoundException when the resource couldn't be found
	 * @throws LocalStorageException when the content provider couldn't be queried
	 */
	public T findById(long localID, boolean populate) throws LocalStorageException {
		try {
			@Cleanup Cursor cursor = providerClient.query(ContentUris.withAppendedId(entriesURI(), localID),
					new String[] { entryColumnRemoteName(), entryColumnETag() }, null, null, null);
			if (cursor != null && cursor.moveToNext()) {
				T resource = newResource(localID, cursor.getString(0), cursor.getString(1));
				if (populate)
					populate(resource);
				return resource;
			} else
				throw new RecordNotFoundException();
		} catch(RemoteException ex) {
			throw new LocalStorageException(ex);
		}
	}
	
	/**
	 * Finds a specific resource by remote file name.
	 * @param localID	remote file name of the resource
	 * @param populate	true: populates all data fields (for instance, contact or event details);
	 * 					false: only remote file name and ETag are populated
	 * @return resource with either ID/remote file/name/ETag or all fields populated
	 * @throws RecordNotFoundException when the resource couldn't be found
	 * @throws LocalStorageException when the content provider couldn't be queried
	 */
	public T findByRemoteName(String remoteName, boolean populate) throws LocalStorageException {
		try {
			@Cleanup Cursor cursor = providerClient.query(entriesURI(),
					new String[] { entryColumnID(), entryColumnRemoteName(), entryColumnETag() },
					entryColumnRemoteName() + "=?", new String[] { remoteName }, null);
			if (cursor != null && cursor.moveToNext()) {
				T resource = newResource(cursor.getLong(0), cursor.getString(1), cursor.getString(2));
				if (populate)
					populate(resource);
				return resource;
			} else
				throw new RecordNotFoundException();
		} catch(RemoteException ex) {
			throw new LocalStorageException(ex);
		}
	}

	/** populates all data fields from the content provider */
	public abstract void populate(Resource record) throws LocalStorageException;

	
	// create/update/delete
	
	/**
	 * Creates a new resource object in memory. No content provider operations involved.
	 * @param localID the ID of the resource
	 * @param resourceName the (remote) file name of the resource
	 * @param ETag of the resource
	 * @return the new resource object */
	abstract public T newResource(long localID, String resourceName, String eTag);
	
	/** Enqueues adding the resource (including all data) to the local collection. Requires commit(). */
	public void add(Resource resource) {
		int idx = pendingOperations.size();
		pendingOperations.add(
				buildEntry(ContentProviderOperation.newInsert(entriesURI()), resource)
				.withYieldAllowed(true)
				.build());
		
		addDataRows(resource, -1, idx);
	}
	
	/** Enqueues updating an existing resource in the local collection. The resource will be found by 
	 * the remote file name and all data will be updated. Requires commit(). */
	public void updateByRemoteName(Resource remoteResource) throws LocalStorageException {
		T localResource = findByRemoteName(remoteResource.getName(), false);
		pendingOperations.add(
				buildEntry(ContentProviderOperation.newUpdate(ContentUris.withAppendedId(entriesURI(), localResource.getLocalID())), remoteResource)
				.withValue(entryColumnETag(), remoteResource.getETag())
				.withYieldAllowed(true)
				.build());
		
		removeDataRows(localResource);
		addDataRows(remoteResource, localResource.getLocalID(), -1);
	}

	/** Enqueues deleting a resource from the local collection. Requires commit(). */
	public void delete(Resource resource) {
		pendingOperations.add(ContentProviderOperation
				.newDelete(ContentUris.withAppendedId(entriesURI(), resource.getLocalID()))
				.withYieldAllowed(true)
				.build());
	}

	/**
	 * Enqueues deleting all resources except the give ones from the local collection. Requires commit().
	 * @param remoteResources resources with these remote file names will be kept
	 */
	public abstract void deleteAllExceptRemoteNames(Resource[] remoteResources);
	
	/** Updates the locally-known ETag of a resource. */
	public void updateETag(Resource res, String eTag) throws LocalStorageException {
		Log.d(TAG, "Setting ETag of local resource " + res + " to " + eTag);
		
		ContentValues values = new ContentValues(1);
		values.put(entryColumnETag(), eTag);
		try {
			providerClient.update(ContentUris.withAppendedId(entriesURI(), res.getLocalID()), values, null, new String[] {});
		} catch (RemoteException e) {
			throw new LocalStorageException(e);
		}
	}
	
	/** Enqueues removing the dirty flag from a locally-stored resource. Requires commit(). */
	public void clearDirty(Resource resource) {
		pendingOperations.add(ContentProviderOperation
				.newUpdate(ContentUris.withAppendedId(entriesURI(), resource.getLocalID()))
				.withValue(entryColumnDirty(), 0)
				.build());
	}

	/** Commits enqueued operations to the content provider (for batch operations). */
	public void commit() throws LocalStorageException {
		if (!pendingOperations.isEmpty())
			try {
				Log.d(TAG, "Committing " + pendingOperations.size() + " operations");
				providerClient.applyBatch(pendingOperations);
				pendingOperations.clear();
			} catch (RemoteException ex) {
				throw new LocalStorageException(ex);
			} catch(OperationApplicationException ex) {
				throw new LocalStorageException(ex);
			}
	}

	
	// helpers

	protected void queueOperation(Builder builder) {
		if (builder != null)
			pendingOperations.add(builder.build());
	}

	/** Appends account type, name and CALLER_IS_SYNCADAPTER to an Uri. */
	protected Uri syncAdapterURI(Uri baseURI) {
		return baseURI.buildUpon()
				.appendQueryParameter(entryColumnAccountType(), account.type)
				.appendQueryParameter(entryColumnAccountName(), account.name)
				.appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
				.build();
	}
	
	protected Builder newDataInsertBuilder(Uri dataUri, String refFieldName, long raw_ref_id, Integer backrefIdx) {
		Builder builder = ContentProviderOperation.newInsert(syncAdapterURI(dataUri));
		if (backrefIdx != -1)
			return builder.withValueBackReference(refFieldName, backrefIdx);
		else
			return builder.withValue(refFieldName, raw_ref_id);
	}
	
	
	// content builders

	/**
	 * Builds the main entry (for instance, a ContactsContract.RawContacts row) from a resource.
	 * The entry is built for insertion to the location identified by entriesURI().
	 * 
	 * @param builder Builder to be extended by all resource data that can be stored without extra data rows.
	 */
	protected abstract Builder buildEntry(Builder builder, Resource resource);
	
	/** Enqueues adding extra data rows of the resource to the local collection. */
	protected abstract void addDataRows(Resource resource, long localID, int backrefIdx);
	
	/** Enqueues removing all extra data rows of the resource from the local collection. */
	protected abstract void removeDataRows(Resource resource);
}
