/*******************************************************************************
 * Copyright (c) 2013 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.syncadapter;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.ValidationException;

import org.apache.http.HttpException;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.OperationApplicationException;
import android.content.SyncResult;
import android.os.RemoteException;
import android.util.Log;
import at.bitfire.davdroid.resource.IncapableResourceException;
import at.bitfire.davdroid.resource.LocalCollection;
import at.bitfire.davdroid.resource.RemoteCollection;
import at.bitfire.davdroid.resource.Resource;
import at.bitfire.davdroid.webdav.PreconditionFailedException;

public class SyncManager {
	private static String TAG = "davdroid.SyncManager";
	
	protected Account account;
	protected AccountManager accountManager;
	
	
	public SyncManager(Account account, AccountManager accountManager) {
		this.account = account;
		this.accountManager = accountManager;
	}

	public void synchronize(LocalCollection local, RemoteCollection dav, boolean manualSync, SyncResult syncResult) throws RemoteException, OperationApplicationException, IOException, IncapableResourceException, HttpException, ParserException {
		boolean fetchCollection = false;
		
		// PHASE 1: UPLOAD LOCALLY-CHANGED RESOURCES
		// remove deleted resources from remote
		Resource[] deletedResources = local.findDeleted();
		if (deletedResources != null) {
			Log.i(TAG, "Remotely removing " + deletedResources.length + " deleted resource(s) (if not changed)");
			for (Resource res : deletedResources) {
				try {
					dav.delete(res);
				} catch(PreconditionFailedException e) {
					Log.i(TAG, "Locally-deleted resource has been changed on the server in the meanwhile");
				}
				fetchCollection = true;
				local.delete(res);
			}
			local.commit();
		}
		
		// upload new resources
		Resource[] newResources = local.findNew();
		if (newResources != null) {
			Log.i(TAG, "Uploading " + newResources.length + " new resource(s) (if not existing)");
			for (Resource res : newResources) {
				try {
					dav.add(res);
				} catch(PreconditionFailedException e) {
					Log.i(TAG, "Didn't overwrite existing resource with other content");
				} catch (ValidationException e) {
					Log.e(TAG, "Couldn't create entity for adding: " + e.toString());
				}
				fetchCollection = true;
				local.clearDirty(res);
			}
			local.commit();
		}
		
		// upload modified resources
		Resource[] dirtyResources = local.findDirty();
		if (dirtyResources != null) {
			Log.i(TAG, "Uploading " + dirtyResources.length + " modified resource(s) (if not changed)");
			for (Resource res : dirtyResources) {
				try {
					dav.update(res);
				} catch(PreconditionFailedException e) {
					Log.i(TAG, "Locally changed resource has been changed on the server in the meanwhile");
				} catch (ValidationException e) {
					Log.e(TAG, "Couldn't create entity for updating: " + e.toString());
				}
				fetchCollection = true;
				local.clearDirty(res);
			}
			local.commit();
		}
		
		
		// PHASE 2A: FETCH REMOTE COLLECTION STATUS
		// has collection changed -> fetch resources?
		if (manualSync) {
			Log.i(TAG, "Synchronization forced");
			fetchCollection = true;
		}
		if (!fetchCollection) {
			String	currentCTag = dav.getCTag(),
					lastCTag = local.getCTag();
			if (currentCTag == null || !currentCTag.equals(lastCTag))
				fetchCollection = true;
		}
		
		if (!fetchCollection)
			return;

		// PHASE 2B: FETCH REMOTE COLLECTION SUMMARY
		// fetch remote resources -> add/overwrite local resources
		Log.i(TAG, "Fetching remote resource list");
		
		Set<Resource> resourcesToAdd = new HashSet<Resource>(),
				resourcesToUpdate = new HashSet<Resource>();
		
		Resource[] remoteResources = dav.getMemberETags();
		if (remoteResources == null)	// failure
			return;
		
		for (Resource remoteResource : remoteResources) {
			Resource localResource = local.findByRemoteName(remoteResource.getName());
			if (localResource == null)
				resourcesToAdd.add(remoteResource);
			else if (localResource.getETag() == null || !localResource.getETag().equals(remoteResource.getETag()))
				resourcesToUpdate.add(remoteResource);
		}
		
		// PHASE 3: DOWNLOAD NEW/REMOTELY-CHANGED RESOURCES
		Log.i(TAG, "Adding " + resourcesToAdd.size() + " remote resource(s)");
		if (!resourcesToAdd.isEmpty())
			for (Resource res : dav.multiGet(resourcesToAdd.toArray(new Resource[0]))) {
				Log.i(TAG, "Adding " + res.getName());
				try {
					local.add(res);
				} catch (ValidationException e) {
					Log.e(TAG, "Invalid resource: " + res.getName());
				}
				syncResult.stats.numInserts++;
			}
		local.commit();
		
		Log.i(TAG, "Updating " + resourcesToUpdate.size() + " remote resource(s)");
		if (!resourcesToUpdate.isEmpty())
			for (Resource res : dav.multiGet(resourcesToUpdate.toArray(new Resource[0]))) {
				try {
					local.updateByRemoteName(res);
				} catch (ValidationException e) {
					Log.e(TAG, "Invalid resource: " + res.getName());
				}
				Log.i(TAG, "Updating " + res.getName());
				syncResult.stats.numInserts++;
			}
		local.commit();

		// delete remotely removed resources
		Log.i(TAG, "Removing resources that are missing remotely");
		local.deleteAllExceptRemoteNames(remoteResources);
		local.commit();

		// update collection CTag
		local.setCTag(dav.getCTag());
		local.commit();
	}
}
