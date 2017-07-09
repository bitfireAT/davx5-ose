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
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.dmfs.provider.tasks.TaskContract.TaskLists;
import org.dmfs.provider.tasks.TaskContract.Tasks;

import java.io.FileNotFoundException;
import java.util.List;

import at.bitfire.davdroid.DavUtils;
import at.bitfire.davdroid.model.CollectionInfo;
import at.bitfire.ical4android.AndroidTaskList;
import at.bitfire.ical4android.AndroidTaskListFactory;
import at.bitfire.ical4android.CalendarStorageException;
import at.bitfire.ical4android.TaskProvider;
import lombok.Cleanup;

public class LocalTaskList extends AndroidTaskList<LocalTask> implements LocalCollection<LocalTask> {

    public static final int defaultColor = 0xFFC3EA6E;     // "DAVdroid green"

    public static final String COLUMN_CTAG = TaskLists.SYNC_VERSION;

    static String[] BASE_INFO_COLUMNS = new String[] {
            Tasks._ID,
            Tasks._SYNC_ID,
            LocalTask.COLUMN_ETAG
    };


    @NonNull
    @Override
    protected String[] taskBaseInfoColumns() {
        return BASE_INFO_COLUMNS;
    }


    protected LocalTaskList(Account account, TaskProvider provider, long id) {
        super(account, provider, LocalTask.Factory.INSTANCE, id);
    }

    public static Uri create(Account account, TaskProvider provider, CollectionInfo info) throws CalendarStorageException {
        ContentValues values = valuesFromCollectionInfo(info, true);
        values.put(TaskLists.OWNER, account.name);
        values.put(TaskLists.SYNC_ENABLED, 1);
        values.put(TaskLists.VISIBLE, 1);
        return create(account, provider, values);
    }

    public void update(CollectionInfo info, boolean updateColor) throws CalendarStorageException {
        update(valuesFromCollectionInfo(info, updateColor));
    }

    private static ContentValues valuesFromCollectionInfo(CollectionInfo info, boolean withColor) {
        ContentValues values = new ContentValues();
        values.put(TaskLists._SYNC_ID, info.getUrl());
        values.put(TaskLists.LIST_NAME, !TextUtils.isEmpty(info.getDisplayName()) ? info.getDisplayName() : DavUtils.lastSegmentOfUrl(info.getUrl()));

        if (withColor)
            values.put(TaskLists.LIST_COLOR, info.getColor() != null ? info.getColor() : defaultColor);

        return values;
    }


    @Override
    public List<LocalTask> getAll() throws CalendarStorageException {
        return queryTasks(null, null);
    }

    @Override
    public List<LocalTask> getDeleted() throws CalendarStorageException {
        return queryTasks(Tasks._DELETED + "!=0", null);
    }

    @Override
    public List<LocalTask> getWithoutFileName() throws CalendarStorageException {
        return queryTasks(Tasks._SYNC_ID + " IS NULL", null);
    }

    @Override
    public List<LocalTask> getDirty() throws CalendarStorageException, FileNotFoundException {
        List<LocalTask> tasks = queryTasks(Tasks._DIRTY + "!=0", null);
        for (LocalTask task : tasks) {
            if (task.getTask().getSequence() == null)    // sequence has not been assigned yet (i.e. this task was just locally created)
                task.getTask().setSequence(0);
            else
                task.getTask().setSequence(task.getTask().getSequence() + 1);
        }
        return tasks;
    }


    @Override
    @SuppressWarnings("Recycle")
    public String getCTag() throws CalendarStorageException {
        try {
            @Cleanup Cursor cursor = getProvider().getClient().query(taskListSyncUri(), new String[] { COLUMN_CTAG }, null, null, null);
            if (cursor != null && cursor.moveToNext())
                return cursor.getString(0);
        } catch (Exception e) {
            throw new CalendarStorageException("Couldn't read local (last known) CTag", e);
        }
        return null;
    }

    @Override
    public void setCTag(String cTag) throws CalendarStorageException {
        try {
            ContentValues values = new ContentValues(1);
            values.put(COLUMN_CTAG, cTag);
            getProvider().getClient().update(taskListSyncUri(), values, null, null);
        } catch (Exception e) {
            throw new CalendarStorageException("Couldn't write local (last known) CTag", e);
        }
    }


    // helpers

    public static boolean tasksProviderAvailable(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            return context.getPackageManager().resolveContentProvider(TaskProvider.ProviderName.OpenTasks.getAuthority(), 0) != null;
        else {
            @Cleanup TaskProvider provider = TaskProvider.acquire(context.getContentResolver(), TaskProvider.ProviderName.OpenTasks);
            return provider != null;
        }
    }


    public static class Factory implements AndroidTaskListFactory<LocalTaskList> {
        public static final Factory INSTANCE = new Factory();

        @Override
        public LocalTaskList newInstance(Account account, TaskProvider provider, long id) {
            return new LocalTaskList(account, provider, id);
        }

    }


    // HELPERS

    public static void onRenameAccount(@NonNull ContentResolver resolver, @NonNull String oldName, @NonNull String newName) throws Exception {
        @Cleanup("release") ContentProviderClient client = resolver.acquireContentProviderClient(TaskProvider.ProviderName.OpenTasks.getAuthority());
        if (client != null) {
            ContentValues values = new ContentValues(1);
            values.put(Tasks.ACCOUNT_NAME, newName);
            client.update(Tasks.getContentUri(TaskProvider.ProviderName.OpenTasks.getAuthority()), values, Tasks.ACCOUNT_NAME + "=?", new String[]{oldName});
        }
    }

}
