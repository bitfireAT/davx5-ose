/*******************************************************************************
 * Copyright (c) 2013 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.resource;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang.StringUtils;

import android.accounts.Account;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentProviderOperation.Builder;
import android.content.ContentUris;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.CalendarContract;

public abstract class LocalCollection<ResourceType extends Resource> {
	protected Account account;
	protected ContentProviderClient providerClient;
	protected ArrayList<ContentProviderOperation> pendingOperations = new ArrayList<ContentProviderOperation>();

	
	// database fields
	
	abstract protected Uri entriesURI();

	abstract protected String entryColumnAccountType();
	abstract protected String entryColumnAccountName();

	abstract protected String entryColumnID();
	abstract protected String entryColumnRemoteName();
	abstract protected String entryColumnETag();
	
	abstract protected String entryColumnDirty();
	abstract protected String entryColumnDeleted();
	

	LocalCollection(Account account, ContentProviderClient providerClient) {
		this.account = account;
		this.providerClient = providerClient;
	}
	

	// collection operations
	
	abstract public String getCTag();
	abstract public void setCTag(String cTag);

	
	// content provider (= database) querying
	
	public Resource[] findDirty() throws RemoteException {
		Cursor cursor = providerClient.query(entriesURI(),
				new String[] { entryColumnID(), entryColumnRemoteName(), entryColumnETag() },
				entryColumnDirty() + "=1", null, null);
		LinkedList<Resource> dirty = new LinkedList<Resource>();
		while (cursor.moveToNext())
			dirty.add(findById(cursor.getLong(0), cursor.getString(1), cursor.getString(2), true));
		return dirty.toArray(new Resource[0]);
	}

	public Resource[] findDeleted() throws RemoteException {
		Cursor cursor = providerClient.query(entriesURI(),
				new String[] { entryColumnID(), entryColumnRemoteName(), entryColumnETag() },
				entryColumnDeleted() + "=1", null, null);
		LinkedList<Resource> deleted = new LinkedList<Resource>();
		while (cursor.moveToNext())
			deleted.add(findById(cursor.getLong(0), cursor.getString(1), cursor.getString(2), false));
		return deleted.toArray(new Resource[0]);
	}

	public Resource[] findNew() throws RemoteException {
		Cursor cursor = providerClient.query(entriesURI(),
				new String[] { entryColumnID() },
				entryColumnDirty() + "=1 AND " + entryColumnRemoteName() + " IS NULL", null, null);
		LinkedList<Resource> fresh = new LinkedList<Resource>();
		while (cursor.moveToNext()) {
			String uid = UUID.randomUUID().toString(),
				   resourceName = uid + fileExtension();
			Resource resource = findById(cursor.getLong(0), resourceName, null, true); //new Event(cursor.getLong(0), resourceName, null);
			resource.setUid(uid);

			// new record: set generated resource name in database
			pendingOperations.add(ContentProviderOperation
					.newUpdate(ContentUris.withAppendedId(entriesURI(), resource.getLocalID()))
					.withValue(entryColumnRemoteName(), resourceName)
					.build());
			
			fresh.add(resource);
		}
		return fresh.toArray(new Resource[0]);
	}
	
	abstract public Resource findById(long localID, String resourceName, String eTag, boolean populate) throws RemoteException;
	abstract public ResourceType findByRemoteName(String name) throws RemoteException;

	public abstract void populate(Resource record) throws RemoteException;

	
	// create/update/delete
	
	public void add(ResourceType resource) {
		int idx = pendingOperations.size();
		pendingOperations.add(
				buildEntry(ContentProviderOperation.newInsert(entriesURI()), resource)
				.withYieldAllowed(true)
				.build());
		
		addDataRows(resource, -1, idx);
	}
	
	public void updateByRemoteName(ResourceType remoteResource) throws RemoteException {
		ResourceType localResource = findByRemoteName(remoteResource.getName());

		pendingOperations.add(
				buildEntry(ContentProviderOperation.newUpdate(ContentUris.withAppendedId(entriesURI(), localResource.getLocalID())), remoteResource)
				.withValue(entryColumnETag(), remoteResource.getETag())
				.withYieldAllowed(true)
				.build());
		
		removeDataRows(localResource);
		addDataRows(remoteResource, localResource.getLocalID(), -1);
	}

	public void delete(Resource resource) {
		pendingOperations.add(ContentProviderOperation
				.newDelete(ContentUris.withAppendedId(entriesURI(), resource.getLocalID()))
				.withYieldAllowed(true)
				.build());
	}

	public void deleteAllExceptRemoteNames(Resource[] remoteResources) {
		Builder builder = ContentProviderOperation.newDelete(entriesURI());
		
		if (remoteResources.length != 0) {
			List<String> terms = new LinkedList<String>();
			for (Resource res : remoteResources)
				terms.add(entryColumnRemoteName() + "<>" + DatabaseUtils.sqlEscapeString(res.getName()));
			String where = StringUtils.join(terms, " AND ");
			builder = builder.withSelection(where, new String[] {});
		} else
			builder = builder.withSelection(entryColumnRemoteName() + " IS NOT NULL", null);
		
		pendingOperations.add(builder
				.withYieldAllowed(true)
				.build());
	}
	
	public void clearDirty(Resource resource) {
		pendingOperations.add(ContentProviderOperation
				.newUpdate(ContentUris.withAppendedId(entriesURI(), resource.getLocalID()))
				.withValue(entryColumnDirty(), 0).build());
	}

	public void commit() throws RemoteException, OperationApplicationException {
		if (!pendingOperations.isEmpty())
			providerClient.applyBatch(pendingOperations);
		
		pendingOperations.clear();
	}

	
	// helpers
	
	protected abstract String fileExtension();
	
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

	protected abstract Builder buildEntry(Builder builder, ResourceType resource);
	
	protected abstract void addDataRows(ResourceType resource, long localID, int backrefIdx);
	protected abstract void removeDataRows(ResourceType resource);
}
