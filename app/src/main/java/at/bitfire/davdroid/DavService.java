/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class DavService extends Service {

    public static final String
            ACTION_REFRESH_COLLECTIONS = "refreshCollections",
            EXTRA_DAV_SERVICE_ID = "davServiceID";

    private final IBinder binder = new InfoBinder();

    private final Set<Long> runningRefresh = new HashSet<>();
    private final List<RefreshingStatusListener> refreshingStatusListeners = new LinkedList<>();


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        long id = intent.getLongExtra(EXTRA_DAV_SERVICE_ID, -1);

        switch (action) {
            case ACTION_REFRESH_COLLECTIONS:
                if (runningRefresh.add(id)) {
                    new Thread(new RefreshCollections(id)).start();
                    for (RefreshingStatusListener listener : refreshingStatusListeners)
                        listener.onDavRefreshStatusChanged(id, true);
                }
                break;
        }

        return START_NOT_STICKY;
    }


    /* BOUND SERVICE PART
       for communicating with the activities
    */

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public interface RefreshingStatusListener {
        void onDavRefreshStatusChanged(long id, boolean refreshing);
    }

    public class InfoBinder extends Binder {
        public boolean isRefreshing(long id) {
            return runningRefresh.contains(id);
        }

        public void addRefreshingStatusListener(RefreshingStatusListener listener, boolean callImmediate) {
            refreshingStatusListeners.add(listener);
            if (callImmediate)
                for (long id : runningRefresh)
                    listener.onDavRefreshStatusChanged(id, true);
        }

        public void removeRefreshingStatusListener(RefreshingStatusListener listener) {
            refreshingStatusListeners.remove(listener);
        }
    }


    /* ACTION RUNNABLES
       which actually do the work
     */

    private class RefreshCollections implements Runnable {
        final long serviceId;

        RefreshCollections(long davServiceId) {
            this.serviceId = davServiceId;
        }

        @Override
        public void run() {
            Constants.log.debug("RefreshCollections.Runner STARTING {}", serviceId);
            try {
                Thread.currentThread().sleep(10000);
            } catch (InterruptedException e) {
            } finally {
                Constants.log.debug("RefreshCollections.Runner FINISHED {}", serviceId);
                runningRefresh.remove(serviceId);
                for (RefreshingStatusListener listener : refreshingStatusListeners)
                    listener.onDavRefreshStatusChanged(serviceId, false);
            }
        }
    }

}
