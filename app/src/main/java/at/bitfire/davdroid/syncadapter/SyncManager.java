/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.syncadapter;

import android.content.SyncResult;
import android.util.Log;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;

import at.bitfire.davdroid.ArrayUtils;
import at.bitfire.davdroid.resource.LocalCollection;
import at.bitfire.davdroid.resource.LocalStorageException;
import at.bitfire.davdroid.resource.RecordNotFoundException;
import at.bitfire.davdroid.resource.RemoteCollection;
import at.bitfire.davdroid.resource.Resource;
import at.bitfire.davdroid.webdav.DavException;
import at.bitfire.davdroid.webdav.HttpException;
import at.bitfire.davdroid.webdav.NotFoundException;
import at.bitfire.davdroid.webdav.PreconditionFailedException;

public class SyncManager {
	private static final String TAG = "davdroid.SyncManager";
	
	private static final int MAX_MULTIGET_RESOURCES = 35;
	
	final protected LocalCollection<? extends Resource> local;
	final protected RemoteCollection<? extends Resource> remote;
	
	
	public SyncManager(LocalCollection<? extends Resource> local, RemoteCollection<? extends Resource> remote) {
		this.local = local;
		this.remote = remote;
	}

	
	public void synchronize(boolean manualSync, SyncResult syncResult) throws URISyntaxException, LocalStorageException, IOException, HttpException, DavException {
		// PHASE 1: push local changes to server
		int	deletedRemotely = pushDeleted(),
			addedRemotely = pushNew(),
			updatedRemotely = pushDirty();
		
		// PHASE 2A: check if there's a reason to do a sync with remote (= forced sync or remote CTag changed)
		boolean fetchCollection = (deletedRemotely + addedRemotely + updatedRemotely) > 0;
		if (manualSync) {
			Log.i(TAG, "Synchronization forced");
			fetchCollection = true;
		}
		if (!fetchCollection) {
			String	currentCTag = remote.getCTag(),
					lastCTag = local.getCTag();
			Log.d(TAG, "Last local CTag = " + lastCTag + "; current remote CTag = " + currentCTag);
			if (currentCTag == null || !currentCTag.equals(lastCTag))
				fetchCollection = true;
		}
		
		if (!fetchCollection) {
			Log.i(TAG, "No local changes and CTags match, no need to sync");
			return;
		}
		
		// PHASE 2B: detect details of remote changes
		Log.i(TAG, "Fetching remote resource list");
		Set<Resource>	remotelyAdded = new HashSet<>(),
						remotelyUpdated = new HashSet<>();
		
		Resource[] remoteResources = remote.getMemberETags();
		for (Resource remoteResource : remoteResources) {
			try {
				Resource localResource = local.findByRemoteName(remoteResource.getName(), false);
				if (localResource.getETag() == null || !localResource.getETag().equals(remoteResource.getETag()))
					remotelyUpdated.add(remoteResource);
			} catch(RecordNotFoundException e) {
				remotelyAdded.add(remoteResource);
			}
		}

		// PHASE 3: pull remote changes from server
		syncResult.stats.numInserts = pullNew(remotelyAdded.toArray(new Resource[remotelyAdded.size()]));
		syncResult.stats.numUpdates = pullChanged(remotelyUpdated.toArray(new Resource[remotelyUpdated.size()]));

		Log.i(TAG, "Removing non-dirty resources that are not present remotely anymore");
		syncResult.stats.numDeletes = local.deleteAllExceptRemoteNames(remoteResources);

        syncResult.stats.numEntries = syncResult.stats.numInserts + syncResult.stats.numUpdates + syncResult.stats.numDeletes;

		// update collection CTag
		Log.i(TAG, "Sync complete, fetching new CTag");
		local.setCTag(remote.getCTag());
	}
	
	
	private int pushDeleted() throws URISyntaxException, LocalStorageException, IOException, HttpException {
		int count = 0;
		long[] deletedIDs = local.findDeleted();
		
		try {
			Log.i(TAG, "Remotely removing " + deletedIDs.length + " deleted resource(s) (if not changed)");
			for (long id : deletedIDs)
				try {
					Resource res = local.findById(id, false);
					if (res.getName() != null)	// is this resource even present remotely?
						try {
							remote.delete(res);
						} catch(NotFoundException e) {
							Log.i(TAG, "Locally-deleted resource has already been removed from server");
						} catch(PreconditionFailedException e) {
							Log.i(TAG, "Locally-deleted resource has been changed on the server in the meanwhile");
						}
					
					// always delete locally so that the record with the DELETED flag doesn't cause another deletion attempt
					local.delete(res);
					
					count++;
				} catch (RecordNotFoundException e) {
					Log.wtf(TAG, "Couldn't read locally-deleted record", e);
				}
		} finally {
			local.commit();
		}
		return count;
	}
	
	private int pushNew() throws URISyntaxException, LocalStorageException, IOException, HttpException {
		int count = 0;
		long[] newIDs = local.findNew();
		Log.i(TAG, "Uploading " + newIDs.length + " new resource(s) (if not existing)");
		try {
			for (long id : newIDs)
				try {
					Resource res = local.findById(id, true);
					String eTag = remote.add(res);
					if (eTag != null)
						local.updateETag(res, eTag);
					local.clearDirty(res);
					count++;
				} catch (PreconditionFailedException e) {
                    Log.i(TAG, "Didn't overwrite existing resource with other content");
				} catch (RecordNotFoundException e) {
					Log.wtf(TAG, "Couldn't read new record", e);
				}
		} finally {
			local.commit();
		}
		return count;
	}
	
	private int pushDirty() throws URISyntaxException, LocalStorageException, IOException, HttpException {
		int count = 0;
		long[] dirtyIDs = local.findUpdated();
		Log.i(TAG, "Uploading " + dirtyIDs.length + " modified resource(s) (if not changed)");
		try {
			for (long id : dirtyIDs) {
				try {
					Resource res = local.findById(id, true);
					String eTag = remote.update(res);
					if (eTag != null)
						local.updateETag(res, eTag);
					local.clearDirty(res);
					count++;
				} catch (PreconditionFailedException e) {
                    Log.i(TAG, "Locally changed resource has been changed on the server in the meanwhile");
				} catch (RecordNotFoundException e) {
					Log.e(TAG, "Couldn't read dirty record", e);
				}
			}
		} finally {
			local.commit();
		}
		return count;
	}
	
	private int pullNew(Resource[] resourcesToAdd) throws URISyntaxException, LocalStorageException, IOException, HttpException, DavException {
		int count = 0;
		Log.i(TAG, "Fetching " + resourcesToAdd.length + " new remote resource(s)");
		
		for (Resource[] resources : ArrayUtils.partition(resourcesToAdd, MAX_MULTIGET_RESOURCES))
			for (Resource res : remote.multiGet(resources)) {
				Log.d(TAG, "Adding " + res.getName());
				local.add(res);
				count++;
			}
		return count;
	}
	
	private int pullChanged(Resource[] resourcesToUpdate) throws URISyntaxException, LocalStorageException, IOException, HttpException, DavException {
		int count = 0;
		Log.i(TAG, "Fetching " + resourcesToUpdate.length + " updated remote resource(s)");
		
		for (Resource[] resources : ArrayUtils.partition(resourcesToUpdate, MAX_MULTIGET_RESOURCES))
			for (Resource res : remote.multiGet(resources)) {
				Log.i(TAG, "Updating " + res.getName());
				local.updateByRemoteName(res);
				count++;
			}
		return count;
	}

}
