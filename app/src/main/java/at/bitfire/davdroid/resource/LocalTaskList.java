/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.resource;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;

import org.dmfs.provider.tasks.TaskContract.TaskLists;
import org.dmfs.provider.tasks.TaskContract.Tasks;

import java.io.FileNotFoundException;

import at.bitfire.ical4android.AndroidTaskList;
import at.bitfire.ical4android.AndroidTaskListFactory;
import at.bitfire.ical4android.CalendarStorageException;
import at.bitfire.ical4android.TaskProvider;
import lombok.Cleanup;
import lombok.NonNull;

public class LocalTaskList extends AndroidTaskList implements LocalCollection {

    public static final int defaultColor = 0xFFC3EA6E;     // "DAVdroid green"

    public static final String COLUMN_CTAG = TaskLists.SYNC_VERSION;

    static String[] BASE_INFO_COLUMNS = new String[] {
            Tasks._ID,
            Tasks._SYNC_ID,
            LocalTask.COLUMN_ETAG
    };

    // can be cached, because after installing OpenTasks, you have to re-install DAVdroid anyway
    private static Boolean tasksProviderAvailable;


    @Override
    protected String[] taskBaseInfoColumns() {
        return BASE_INFO_COLUMNS;
    }


    protected LocalTaskList(Account account, TaskProvider provider, long id) {
        super(account, provider, LocalTask.Factory.INSTANCE, id);
    }

    public static Uri create(Account account, ContentResolver resolver, ServerInfo.ResourceInfo info) throws CalendarStorageException {
        TaskProvider provider = TaskProvider.acquire(resolver, TaskProvider.ProviderName.OpenTasks);
        if (provider == null)
            throw new CalendarStorageException("Couldn't access OpenTasks provider");

        ContentValues values = new ContentValues();
        values.put(TaskLists._SYNC_ID, info.getUrl());
        values.put(TaskLists.LIST_NAME, info.getTitle());
        values.put(TaskLists.LIST_COLOR, info.color != null ? info.color : defaultColor);
        values.put(TaskLists.OWNER, account.name);
        values.put(TaskLists.SYNC_ENABLED, 1);
        values.put(TaskLists.VISIBLE, 1);

        return create(account, provider, values);
    }


    @Override
    public LocalTask[] getAll() throws CalendarStorageException {
        return (LocalTask[])queryTasks(null, null);
    }

    @Override
    public LocalTask[] getDeleted() throws CalendarStorageException {
        return (LocalTask[])queryTasks(Tasks._DELETED + "!=0", null);
    }

    @Override
    public LocalTask[] getWithoutFileName() throws CalendarStorageException {
        return (LocalTask[])queryTasks(Tasks._SYNC_ID + " IS NULL", null);
    }

    @Override
    public LocalResource[] getDirty() throws CalendarStorageException, FileNotFoundException {
        LocalTask[] tasks = (LocalTask[])queryTasks(Tasks._DIRTY + "!=0", null);
        if (tasks != null)
        for (LocalTask task : tasks) {
            if (task.getTask().sequence == null)    // sequence has not been assigned yet (i.e. this task was just locally created)
                task.getTask().sequence = 0;
            else
                task.getTask().sequence++;
        }
        return tasks;
    }


    @Override
    @SuppressWarnings("Recycle")
    public String getCTag() throws CalendarStorageException {
        try {
            @Cleanup Cursor cursor = provider.client.query(taskListSyncUri(), new String[] { COLUMN_CTAG }, null, null, null);
            if (cursor != null && cursor.moveToNext())
                return cursor.getString(0);
        } catch (RemoteException e) {
            throw new CalendarStorageException("Couldn't read local (last known) CTag", e);
        }
        return null;
    }

    @Override
    public void setCTag(String cTag) throws CalendarStorageException {
        try {
            ContentValues values = new ContentValues(1);
            values.put(COLUMN_CTAG, cTag);
            provider.client.update(taskListSyncUri(), values, null, null);
        } catch (RemoteException e) {
            throw new CalendarStorageException("Couldn't write local (last known) CTag", e);
        }
    }


    // helpers

    public static boolean tasksProviderAvailable(@NonNull ContentResolver resolver) {
        if (tasksProviderAvailable != null)
            return tasksProviderAvailable;
        else {
            TaskProvider provider = TaskProvider.acquire(resolver, TaskProvider.ProviderName.OpenTasks);
            return tasksProviderAvailable = (provider != null);
        }
    }


    public static class Factory implements AndroidTaskListFactory {
        public static final Factory INSTANCE = new Factory();

        @Override
        public AndroidTaskList newInstance(Account account, TaskProvider provider, long id) {
            return new LocalTaskList(account, provider, id);
        }

        @Override
        public AndroidTaskList[] newArray(int size) {
            return new LocalTaskList[size];
        }
    }
}
