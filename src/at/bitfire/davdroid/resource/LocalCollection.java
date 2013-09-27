/*******************************************************************************
 * Copyright (c) 2013 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.resource;

import java.util.ArrayList;

import android.accounts.Account;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.RemoteException;

public abstract class LocalCollection {
	protected Account account;
	protected ContentProviderClient providerClient;
	protected ArrayList<ContentProviderOperation> pendingOperations = new ArrayList<ContentProviderOperation>();;
	
	LocalCollection(Account account, ContentProviderClient providerClient) {
		this.account = account;
		this.providerClient = providerClient;
	}

	// query
	abstract public Resource[] findDeleted() throws RemoteException;
	abstract public Resource[] findDirty() throws RemoteException;
	abstract public Resource[] findNew() throws RemoteException;
	
	// cache management
	abstract public String getCTag();
	abstract public void setCTag(String cTag);
	
	// fetch
	public abstract Resource getByRemoteName(String name) throws RemoteException;
	public abstract void populate(Resource record) throws RemoteException;
	
	// modify
	public abstract void add(Resource resource);
	public abstract void updateByRemoteName(Resource remoteResource) throws RemoteException;
	public abstract void delete(Resource resource);
	public abstract void deleteAllExceptRemoteNames(Resource[] remoteRecords);
	
	// database operations
	protected abstract Uri entriesURI();
	public abstract void clearDirty(Resource resource);
	
	public void commit() throws RemoteException, OperationApplicationException {
		if (!pendingOperations.isEmpty())
			providerClient.applyBatch(pendingOperations);
		pendingOperations.clear();
	}
}
