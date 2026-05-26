/*
 * Copyright 2017 dmfs GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dmfs.tasks.contract;

import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.SyncStateContract;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


/**
 * Task contract. This class defines the interface to the task provider.
 * <p>
 * TODO: Add missing javadoc.
 * </p>
 * <p>
 * TODO: Specify extended properties
 * </p>
 * <p>
 * TODO: Add CONTENT_URI for the attachment store.
 * </p>
 * <p>
 * TODO: Also, we could use some refactoring...
 * </p>
 *
 * @author Marten Gajda <marten@dmfs.org>
 * @author Tobias Reinsch <tobias@dmfs.org>
 */
@SuppressWarnings("ALL")
public final class TaskContract
{

    private static Map<String, UriFactory> sUriFactories = new HashMap<String, UriFactory>(4);

    /**
     * URI parameter to signal that the caller is a sync adapter.
     */
    public static final String CALLER_IS_SYNCADAPTER = "caller_is_syncadapter";

    /**
     * URI parameter to signal the request of the extended properties of a task.
     */
    public static final String LOAD_PROPERTIES = "load_properties";

    /**
     * URI parameter to submit the account name of the account we operate on.
     */
    public static final String ACCOUNT_NAME = "account_name";

    /**
     * URI parameter to submit the account type of the account we operate on.
     */
    public static final String ACCOUNT_TYPE = "account_type";

    /**
     * Account name for local, unsynced task lists.
     */
    public static final String LOCAL_ACCOUNT_NAME = "Local";

    /**
     * Account type for local, unsynced task lists.
     */
    public static final String LOCAL_ACCOUNT_TYPE = "org.dmfs.account.LOCAL";

    /**
     * Broadcast action that's sent when the task database has been initialized, either because the app was launched for the first time or because the app was
     * launched after the user cleared the app data.
     * <p/>
     * The intent data represents the authority of the provider, the MIME type will be {@link #MIMETYPE_AUTHORITY}.
     */
    public static final String ACTION_DATABASE_INITIALIZED = "org.dmfs.tasks.DATABASE_INITIALIZED";

    /**
     * A MIME type of an authority. Authorities itself don't seem to have a MIME type in Android, so we just use our own.
     */
    public static final String MIMETYPE_AUTHORITY = ContentResolver.CURSOR_DIR_BASE_TYPE + "/vnd.org.dmfs.authority.mimetype";

    /**
     * The action of the broadcast that's send when a task becomes due. The intent data will be a {@link Uri} of the task that became due.
     */
    public static final String ACTION_BROADCAST_TASK_DUE = "org.dmfs.android.tasks.TASK_DUE";

    /**
     * The action of the broadcast that's send when a task starts. The intent data will be a {@link Uri} of the task that has started.
     */
    public static final String ACTION_BROADCAST_TASK_STARTING = "org.dmfs.android.tasks.TASK_START";

    /**
     * A Long extra that contains a timestamp of the event that's triggered. So this is either the timestamp of the start or due date of the task.
     */
    public final static String EXTRA_TASK_TIMESTAMP = "org.dmfs.provider.tasks.extra.TIMESTAMP";

    /**
     * A Boolean extra to indicate that the event that was triggered is an all-day date.
     */
    public final static String EXTRA_TASK_ALLDAY = "org.dmfs.provider.tasks.extra.ALLDAY";

    /**
     * A String extra containing the timezone id of the task.
     */
    public final static String EXTRA_TASK_TIMEZONE = "org.dmfs.provider.tasks.extra.TIMEZONE";

    /**
     * A String extra containing the title of the task.
     */
    public final static String EXTRA_TASK_TITLE = "org.dmfs.provider.tasks.extra.TITLE";

    /**
     * The name of the {@link Intent#ACTION_PROVIDER_CHANGED} extra that contains the {@link ArrayList} of {@link Uri}s that have been modified. This always
     * goes along with an {@link #EXTRA_OPERATIONS} which contains a code for the operation executed on a Uri at the same index.
     */
    public final static String EXTRA_OPERATIONS_URIS = "org.dmfs.tasks.OPERATIONS_URIS";

    /**
     * The name of the {@link Intent#ACTION_PROVIDER_CHANGED} extra that contains the {@link ArrayList} of provider operation codes. The following codes are
     * used:
     * <ul>
     * <li>0 - for inserts</li>
     * <li>1 - for updates</li>
     * <li>2 - for deletes</li>
     * </ul>
     */
    public final static String EXTRA_OPERATIONS = "org.dmfs.tasks.OPERATIONS";


    /**
     * Private constructor to prevent instantiation.
     */
    private TaskContract()
    {
    }


    /**
     * A table provided for sync adapters to use for storing private sync state data.
     * <p/>
     * Only sync adapters are allowed to access this table and they may access their own rows only.
     * <p/>
     * Note that only one row per account will be stored. Updating or inserting a sync state for a specific account will override any previous sync state for
     * this account.
     */
    public static class SyncState implements SyncStateContract.Columns, BaseColumns
    {
        public final static String CONTENT_URI_PATH = "syncstate";


        /**
         * Get the sync state content {@link Uri} using the given authority.
         *
         * @param authority
         *         The authority.
         *
         * @return A {@link Uri}.
         */
        public static Uri getContentUri(String authority)
        {
            return getUriFactory(authority).getUri(CONTENT_URI_PATH);
        }

    }


    /**
     * Get the base content {@link Uri} using the given authority.
     *
     * @param authority
     *         The authority.
     *
     * @return A {@link Uri}.
     */
    public static Uri getContentUri(String authority)
    {
        return getUriFactory(authority).getUri();
    }


    /**
     * A set of columns for synchronization purposes. These columns exist in {@link Tasks} and in {@link TaskLists} but have different meanings. Only sync
     * adapters are allowed to change these values.
     *
     * @author Marten Gajda <marten@dmfs.org>
     */
    public interface CommonSyncColumns
    {

        /**
         * A unique Sync ID as set by the sync adapter.
         * <p>
         * Value: String
         * </p>
         */
        String _SYNC_ID = "_sync_id";

        /**
         * Sync version as set by the sync adapter.
         * <p>
         * Value: String
         * </p>
         */
        String SYNC_VERSION = "sync_version";

        /**
         * Indicates that a task or a task list has been changed.
         * <p>
         * Value: Integer
         * </p>
         */
        String _DIRTY = "_dirty";

        /**
         * A general purpose column for the sync adapter.
         * <p>
         * Value: String
         * </p>
         */
        String SYNC1 = "sync1";

        /**
         * A general purpose column for the sync adapter.
         * <p>
         * Value: String
         * </p>
         */
        String SYNC2 = "sync2";

        /**
         * A general purpose column for the sync adapter.
         * <p>
         * Value: String
         * </p>
         */
        String SYNC3 = "sync3";

        /**
         * A general purpose column for the sync adapter.
         * <p>
         * Value: String
         * </p>
         */
        String SYNC4 = "sync4";

        /**
         * A general purpose column for the sync adapter.
         * <p>
         * Value: String
         * </p>
         */
        String SYNC5 = "sync5";

        /**
         * A general purpose column for the sync adapter.
         * <p>
         * Value: String
         * </p>
         */
        String SYNC6 = "sync6";

        /**
         * A general purpose column for the sync adapter.
         * <p>
         * Value: String
         * </p>
         */
        String SYNC7 = "sync7";

        /**
         * A general purpose column for the sync adapter.
         * <p>
         * Value: String
         * </p>
         */
        String SYNC8 = "sync8";

    }


    /**
     * Additional sync columns for task lists.
     *
     * @author Marten Gajda <marten@dmfs.org>
     */
    public interface TaskListSyncColumns
    {

        /**
         * The name of the account this list belongs to. This field is write-once.
         * <p>
         * Value: String
         * </p>
         */
        String ACCOUNT_NAME = "account_name";

        /**
         * The type of the account this list belongs to. This field is write-once.
         * <p>
         * Value: String
         * </p>
         */
        String ACCOUNT_TYPE = "account_type";
    }


    /**
     * Additional sync columns for tasks.
     *
     * @author Marten Gajda <marten@dmfs.org>
     */
    public interface TaskSyncColumns
    {
        /**
         * The UID of a task. This is field can be changed by a sync adapter only.
         * <p>
         * Value: String
         * </p>
         */
        String _UID = "_uid";

        /**
         * Deleted flag of a task. This is set to <code>1</code> by the content provider when a task app deletes a task. The sync adapter has to remove the task
         * again to finish the removal. This value is <strong>read-only</strong>.
         * <p>
         * Value: Integer
         * </p>
         * <p>
         * read-only
         * </p>
         */
        String _DELETED = "_deleted";
    }


    /**
     * Data columns of task lists.
     *
     * @author Marten Gajda <marten@dmfs.org>
     */
    public interface TaskListColumns
    {

        /**
         * List ID.
         * <p>
         * Value: Long
         * </p>
         * <p>
         * read-only
         * </p>
         */
        String _ID = "_id";

        /**
         * The name of the task list.
         * <p>
         * Value: String
         * </p>
         */
        String LIST_NAME = "list_name";

        /**
         * The color of this list as integer (0xaarrggbb). Only the sync adapter can change this.
         * <p>
         * Value: Integer
         * </p>
         */
        String LIST_COLOR = "list_color";

        /**
         * The access level a user has on this list (taken from android.provider.CalendarContract).
         * <p>
         * Value: Integer (one of the values below)
         * </p>
         */
        String ACCESS_LEVEL = "list_access_level";

        /** Not specified by client, should be treated as read-write */
        Integer ACCESS_LEVEL_UNDEFINED = 0;

        /** Can read all tasks and details, no write access */
        Integer ACCESS_LEVEL_READ = 200;

        /** Full access to the tasks list */
        Integer ACCESS_LEVEL_OWNER = 700;

        /**
         * Indicates that a task list is set to be visible.
         * <p>
         * Value: Integer (0 or 1)
         * </p>
         */
        String VISIBLE = "visible";

        /**
         * Indicates that a task list is set to be synced.
         * <p>
         * Value: Integer (0 or 1)
         * </p>
         */
        String SYNC_ENABLED = "sync_enabled";

        /**
         * The email address of the list owner.
         * <p>
         * Value: String
         * </p>
         */
        String OWNER = "list_owner";

    }


    /**
     * The task list table holds one entry for each task list.
     *
     * @author Marten Gajda <marten@dmfs.org>
     */
    public static final class TaskLists implements TaskListColumns, TaskListSyncColumns, CommonSyncColumns
    {
        public static final String CONTENT_URI_PATH = "tasklists";

        /**
         * The default sort order.
         */
        public static final String DEFAULT_SORT_ORDER = ACCOUNT_NAME + ", " + LIST_NAME;

        /**
         * An array of columns only a sync adapter is allowed to change.
         */
        public static final String[] SYNC_ADAPTER_COLUMNS = new String[] {
                ACCESS_LEVEL, _DIRTY, OWNER, SYNC1, SYNC2, SYNC3, SYNC4, SYNC5, SYNC6, SYNC7, SYNC8,
                _SYNC_ID, SYNC_VERSION, };


        /**
         * Get the task list content {@link Uri} using the given authority.
         *
         * @param authority
         *         The authority.
         *
         * @return A {@link Uri}.
         */
        public static Uri getContentUri(String authority)
        {
            return getUriFactory(authority).getUri(CONTENT_URI_PATH);
        }

    }


    /**
     * Task data columns. Defines all the values a task can have at most once.
     *
     * @author Marten Gajda <marten@dmfs.org>
     */
    public interface TaskColumns extends BaseColumns
    {

        /**
         * The row id of a task. This value is <strong>read-only</strong>
         * <p>
         * Value: Integer
         * </p>
         */
        String _ID = "_id";

        /**
         * The local version number of this task. The only guarantee about the value is, it's incremented whenever the task changes (this includes any
         * changes applied by sync adapters).
         * <p>
         * Note, there is no guarantee about how much it's incremented other than by at least 1.
         * <p>
         * Value: Integer
         * <p>
         * read-only
         */
        String VERSION = "version";

        /**
         * The id of the list this task belongs to. This value is <strong>write-once</strong> and must not be <code>null</code>.
         * <p>
         * Value: Integer
         * </p>
         */
        String LIST_ID = "list_id";

        /**
         * The title of the task.
         * <p>
         * Value: String
         * </p>
         */
        String TITLE = "title";

        /**
         * The location of the task.
         * <p>
         * Value: String
         * </p>
         */
        String LOCATION = "location";

        /**
         * A geographic location related to the task. The should be a string in the format "longitude,latitude".
         * <p>
         * Value: String
         * </p>
         */
        String GEO = "geo";

        /**
         * The description of a task.
         * <p>
         * Value: String
         * </p>
         */
        String DESCRIPTION = "description";

        /**
         * The URL iCalendar field for this task. Must be a valid URI if not <code>null</code>-
         * <p>
         * Value: String
         * </p>
         */
        String URL = "url";

        /**
         * The email address of the organizer if any, {@code null} otherwise.
         * <p>
         * Value: String
         * </p>
         */
        String ORGANIZER = "organizer";

        /**
         * The priority of a task. This is an Integer between zero and 9. Zero means there is no priority set. 1 is the highest priority and 9 the lowest.
         * <p>
         * Value: Integer
         * </p>
         */
        String PRIORITY = "priority";

        /**
         * The default value of {@link #PRIORITY}.
         */
        int PRIORITY_DEFAULT = 0;

        /**
         * The classification of a task. This value must be either <code>null</code> or one of {@link #CLASSIFICATION_PUBLIC}, {@link #CLASSIFICATION_PRIVATE},
         * {@link #CLASSIFICATION_CONFIDENTIAL}.
         * <p>
         * Value: Integer
         * </p>
         */
        String CLASSIFICATION = "class";

        /**
         * Classification value for public tasks.
         */
        int CLASSIFICATION_PUBLIC = 0;

        /**
         * Classification value for private tasks.
         */
        int CLASSIFICATION_PRIVATE = 1;

        /**
         * Classification value for confidential tasks.
         */
        int CLASSIFICATION_CONFIDENTIAL = 2;

        /**
         * Default value of {@link #CLASSIFICATION}.
         */
        Integer CLASSIFICATION_DEFAULT = null;

        /**
         * Date of completion of this task in milliseconds since the epoch or {@code null} if this task has not been completed yet.
         * <p>
         * Value: Long
         * </p>
         */
        String COMPLETED = "completed";

        /**
         * Indicates that the date of completion is an all-day date.
         * <p>
         * Value: Integer
         * </p>
         */
        String COMPLETED_IS_ALLDAY = "completed_is_allday";

        /**
         * A number between 0 and 100 that indicates the progress of the task or <code>null</code>.
         * <p>
         * Value: Integer (0-100)
         * </p>
         */
        String PERCENT_COMPLETE = "percent_complete";

        /**
         * The status of this task. One of {@link #STATUS_NEEDS_ACTION},{@link #STATUS_IN_PROCESS}, {@link #STATUS_COMPLETED}, {@link #STATUS_CANCELLED}.
         * <p>
         * Value: Integer
         * </p>
         */
        String STATUS = "status";

        /**
         * A specific status indicating that nothing has been done yet.
         */
        int STATUS_NEEDS_ACTION = 0;

        /**
         * A specific status indicating that some work has been done.
         */
        int STATUS_IN_PROCESS = 1;

        /**
         * A specific status indicating that the task is completed.
         */
        int STATUS_COMPLETED = 2;

        /**
         * A specific status indicating that the task has been cancelled.
         */
        int STATUS_CANCELLED = 3;

        /**
         * The default status is "needs action".
         */
        int STATUS_DEFAULT = STATUS_NEEDS_ACTION;

        /**
         * A flag that indicates a task is new (i.e. not work has been done yet). This flag is <strong>read-only</strong>. Its value is <code>1</code> when
         * {@link #STATUS} equals {@link #STATUS_NEEDS_ACTION} and <code>0</code> otherwise.
         * <p>
         * Value: Integer
         * </p>
         * <p>
         * read-only
         * </p>
         */
        String IS_NEW = "is_new";

        /**
         * A flag that indicates a task is closed (no more work has to be done). This flag is <strong>read-only</strong>. Its value is <code>1</code> when
         * {@link #STATUS} equals {@link #STATUS_COMPLETED} or {@link #STATUS_CANCELLED} and <code>0</code> otherwise.
         * <p>
         * Value: Integer
         * </p>
         * <p>
         * read-only
         * </p>
         */
        String IS_CLOSED = "is_closed";

        /**
         * An individual color for this task in the format 0xaarrggbb or {@code null} to use {@link TaskListColumns#LIST_COLOR} instead.
         * <p>
         * Value: Integer
         * </p>
         */
        String TASK_COLOR = "task_color";

        /**
         * When this task starts in milliseconds since the epoch.
         * <p>
         * Value: Long
         * </p>
         */
        String DTSTART = "dtstart";

        /**
         * Boolean: flag that indicates that this is an all-day task.
         */
        String IS_ALLDAY = "is_allday";

        /**
         * When this task has been created in milliseconds since the epoch.
         * <p>
         * Value: Long
         * </p>
         */
        String CREATED = "created";

        /**
         * When this task had been modified the last time in milliseconds since the epoch.
         * <p>
         * Value: Long
         * </p>
         */
        String LAST_MODIFIED = "last_modified";

        /**
         * String: An Olson Id of the time zone of this task. If this value is <code>null</code>, it's automatically replaced by the local time zone.
         */
        String TZ = "tz";

        /**
         * When this task is due in milliseconds since the epoch. Only one of {@link #DUE} or {@link #DURATION} must be supplied (or none of both if the task
         * has no due date).
         * <p>
         * Value: Long
         * </p>
         */
        String DUE = "due";

        /**
         * The duration of this task. Only one of {@link #DUE} or {@link #DURATION} must be supplied (or none of both if the task has no due date). Setting a
         * {@link #DURATION} is not allowed when {@link #DTSTART} is <code>null</code>. The Value must be a duration string as in <a
         * href="http://tools.ietf.org/html/rfc5545#section-3.3.6">RFC 5545 Section 3.3.6</a>.
         * <p>
         * Value: String
         * </p>
         */
        String DURATION = "duration";

        /**
         * A comma separated list of time Strings in RFC 5545 format (see <a href="http://tools.ietf.org/html/rfc5545#section-3.3.4">RFC 5545 Section 3.3.4</a>
         * and <a href="http://tools.ietf.org/html/rfc5545#section-3.3.5">RFC 5545 Section 3.3.5</a>) that contains dates of instances of e recurring task.
         * All-day tasks must use the DATE format specified in section 3.3.4 of RFC 5545.
         * <p>
         * This value must be {@code null} for exception instances.
         * <p>
         * Value: String
         * </p>
         */
        String RDATE = "rdate";

        /**
         * A comma separated list of time Strings in RFC 5545 format (see <a href="http://tools.ietf.org/html/rfc5545#section-3.3.4">RFC 5545 Section 3.3.4</a>
         * and <a href="http://tools.ietf.org/html/rfc5545#section-3.3.5">RFC 5545 Section 3.3.5</a>) that contains dates of exceptions of a recurring task.
         * All-day tasks must use the DATE format specified in section 3.3.4 of RFC 5545.
         * <p>
         * This value must be {@code null} for exception instances.
         * <p>
         * Value: String
         * </p>
         */
        String EXDATE = "exdate";

        /**
         * A recurrence rule as specified in <a href="http://tools.ietf.org/html/rfc5545#section-3.3.10">RFC 5545 Section 3.3.10</a>.
         * <p>
         * This value must be {@code null} for exception instances.
         * <p>
         * Value: String
         * </p>
         */
        String RRULE = "rrule";

        /**
         * The _sync_id of the original event if this is an exception, <code>null</code> otherwise. Only one of {@link #ORIGINAL_INSTANCE_SYNC_ID} or
         * {@link #ORIGINAL_INSTANCE_ID} must be set if this task is an exception. The other one will be updated by the content provider.
         * <p>
         * Value: String
         * </p>
         */
        String ORIGINAL_INSTANCE_SYNC_ID = "original_instance_sync_id";

        /**
         * The row id of the original event if this is an exception, <code>null</code> otherwise. Only one of {@link #ORIGINAL_INSTANCE_SYNC_ID} or
         * {@link #ORIGINAL_INSTANCE_ID} must be set if this task is an exception. The other one will be updated by the content provider.
         * <p>
         * Value: Long
         * </p>
         */
        String ORIGINAL_INSTANCE_ID = "original_instance_id";

        /**
         * The time in milliseconds since the Epoch of the original instance that is overridden by this instance or <code>null</code> if this task is not a
         * recurring instance.
         * <p>
         * Value: Long
         * </p>
         */
        String ORIGINAL_INSTANCE_TIME = "original_instance_time";

        /**
         * A flag indicating that the original instance was an all-day task.
         * <p>
         * Value: Integer
         * </p>
         */
        String ORIGINAL_INSTANCE_ALLDAY = "original_instance_allday";

        /**
         * The row id of the parent task. <code>null</code> if the task has no parent task.
         * <p>
         * Note, when writing this value the task {@link Property.Relation} properties are updated accordingly. Any parent or child relations which
         * make this a child of another task are deleted and a new {@link Property.Relation#RELTYPE_PARENT} relation pointing to the new parent is created.
         * Be aware that Siblings will be split, i.e. they are not moved to the new parent. Currently this might cause siblings to become orphans if they
         * don't have a parent-child relationship. This behavior may change in future version.
         * </p>
         *
         * <p>
         * Value: Long
         * </p>
         */
        String PARENT_ID = "parent_id";

        /**
         * The sorting of this task under it's parent task.
         * <p>
         * Value: String
         * </p>
         */
        String SORTING = "sorting";

        /**
         * Indicates how many alarms a task has. <code>0</code> means the task has no alarms. This field is read only as it's set automatically.
         * <p>
         * Value: Integer
         * </p>
         * Read-only
         */
        String HAS_ALARMS = "has_alarms";

        /**
         * Indicates that this task has extended properties like attachments, alarms or relations. This field is read only as it's set automatically.
         * <p>
         * Value: Integer
         * </p>
         * <p>
         * read-only
         * </p>
         */
        String HAS_PROPERTIES = "has_properties";

        /**
         * Indicates that this task has been pinned to the notification area. This flag is moved to the exception when an exception for the first instance of a
         * recurring task is created. That means, if you edit a pinned recurring task, the pinned flag is moved to the exception and cleared from the master
         * task.
         * <p>
         * Value: Integer
         * </p>
         * <p>
         * read-only
         * </p>
         */
        String PINNED = "pinned";
    }


    /**
     * Columns that are valid in a search query.
     *
     * @author Marten Gajda <marten@dmfs.org>
     */
    public interface TaskSearchColumns
    {
        /**
         * The score of a task in a search result. It's an indicator for the relevance of the task. Value is in (0, 1.0] where 0 would be "no relevance" at all
         * (though the result doesn't contain such tasks).
         * <p>
         * Value: Float
         * </p>
         */
        String SCORE = "score";
    }


    /**
     * The task table stores the data of all tasks.
     *
     * @author Marten Gajda <marten@dmfs.org>
     */
    public static final class Tasks implements TaskColumns, CommonSyncColumns, TaskSyncColumns, TaskSearchColumns
    {
        /**
         * The name of the account the task belongs to. This is auto-derived from the list the task belongs to. Do not write this value here.
         * <p>
         * Value: String
         * </p>
         * <p>
         * read-only
         * </p>
         */
        public static final String ACCOUNT_NAME = TaskLists.ACCOUNT_NAME;

        /**
         * The type of the account the task belongs to. This is auto-derived from the list the task belongs to. Do not write this value here.
         * <p>
         * Value: String
         * </p>
         * <p>
         * read-only
         * </p>
         */
        public static final String ACCOUNT_TYPE = TaskLists.ACCOUNT_TYPE;

        /**
         * The name of the list this task belongs to as integer (0xaarrggbb). This is auto-derived from the list the task belongs to. Do not write this value
         * here.
         * <p>
         * Value: String
         * </p>
         * <p>
         * read-only
         * </p>
         */
        public static final String LIST_NAME = TaskLists.LIST_NAME;
        /**
         * The color of the list this task belongs to as integer (0xaarrggbb). This is auto-derived from the list the task belongs to. Do not write this value
         * here. To change the color of an individual task use {@code TASK_COLOR} instead.
         * <p>
         * Value: Integer
         * </p>
         * <p>
         * read-only
         * </p>
         */
        public static final String LIST_COLOR = TaskLists.LIST_COLOR;

        /**
         * The owner of the list this task belongs. This is auto-derived from the list the task belongs to. Do not write this value here.
         * <p>
         * Value: String
         * </p>
         * <p>
         * read-only
         * </p>
         */
        public static final String LIST_OWNER = TaskLists.OWNER;

        /**
         * The access level of the list this task belongs. This is auto-derived from the list the task belongs to. Do not write this value here.
         * <p>
         * Value: Integer
         * </p>
         * <p>
         * read-only
         * </p>
         */
        public static final String LIST_ACCESS_LEVEL = TaskLists.ACCESS_LEVEL;

        /**
         * The visibility of the list this task belongs. This is auto-derived from the list the task belongs to. Do not write this value here.
         * <p>
         * Value: Integer
         * </p>
         * <p>
         * read-only
         * </p>
         */
        public static final String VISIBLE = "visible";

        public static final String CONTENT_URI_PATH = "tasks";

        public static final String SEARCH_URI_PATH = "tasks_search";

        public static final String SEARCH_QUERY_PARAMETER = "q";

        public static final String DEFAULT_SORT_ORDER = DUE;

        public static final String[] SYNC_ADAPTER_COLUMNS = new String[] {
                _DIRTY, SYNC1, SYNC2, SYNC3, SYNC4, SYNC5, SYNC6, SYNC7, SYNC8, _SYNC_ID,
                SYNC_VERSION, };


        /**
         * Get the tasks content {@link Uri} using the given authority.
         *
         * @param authority
         *         The authority.
         *
         * @return A {@link Uri}.
         */
        public static Uri getContentUri(String authority)
        {
            return getUriFactory(authority).getUri(CONTENT_URI_PATH);
        }


        public static Uri getSearchUri(String authority, String query)
        {
            Uri.Builder builder = getUriFactory(authority).getUri(SEARCH_URI_PATH).buildUpon();
            builder.appendQueryParameter(SEARCH_QUERY_PARAMETER, Uri.encode(query));
            return builder.build();
        }
    }


    /**
     * Columns of a task instance.
     *
     * @author Yannic Ahrens <yannic@dmfs.org>
     * @author Marten Gajda <marten@dmfs.org>
     */
    public interface InstanceColumns
    {
        /**
         * _ID of task this instance belongs to.
         * <p>
         * Value: Long
         * </p>
         */
        String TASK_ID = "task_id";

        /**
         * The start date of an instance in milliseconds since the epoch or <code>null</code> if the instance has no start date. At present this is read only.
         * <p>
         * Value: Long
         * </p>
         */
        String INSTANCE_START = "instance_start";

        /**
         * The due date of an instance in milliseconds since the epoch or <code>null</code> if the instance has no due date. At present this is read only.
         * <p>
         * Value: Long
         * </p>
         */
        String INSTANCE_DUE = "instance_due";

        /**
         * This column should be used in an order clause to sort instances by start date. The only guarantee about the values in this column is the sort order.
         * Don't make any other assumptions about the value.
         * <p>
         * Value: Long
         * </p>
         * <p>
         * read-only
         * </p>
         */
        String INSTANCE_START_SORTING = "instance_start_sorting";

        /**
         * This column should be used in an order clause to sort instances by due date. The only guarantee about the values in this column is the sort order.
         * Don't make any other assumptions about the value.
         * <p>
         * Value: Long
         * </p>
         * <p>
         * read-only
         * </p>
         */
        String INSTANCE_DUE_SORTING = "instance_due_sorting";

        /**
         * The duration of an instance in milliseconds or <code>null</code> if the instance has only one of start or due date or none of both. At present this
         * is read only.
         * <p>
         * Value: Long
         * </p>
         */
        String INSTANCE_DURATION = "instance_duration";

        /**
         * The start of the original instance as specified in the master task. For non-recurring task instances this is {@code null}.
         * <p>
         * For recurring tasks, these are the timestamps which have been derived from the recurrence rule or dates, except those specified as exdates.
         */
        String INSTANCE_ORIGINAL_TIME = "instance_original_time";

        /**
         * The distance of the instance from the current one. For closed instances this is always {@code -1}, for the current instance this is {@code 0}. For
         * the instance after the current one this is {@code 1}, for the instance after that one it's {@code 2}, etc..
         * <p>
         * Value: Integer
         * <p>
         * read-only
         */
        String DISTANCE_FROM_CURRENT = "distance_from_current";
    }


    /**
     * A table containing one entry per task instance. This table is writable in order to allow modification of single instances of a task. Write operations to
     * this table will be converted into operations on overrides and forwarded to the task table.
     * <p>
     * Note: The {@link #DTSTART}, {@link #DUE} values of instances of recurring tasks represent the actual instance values, i.e. they are different for each
     * instance ({@link #DURATION} is always {@code null}).
     * <p>
     * Also, none of the instances are recurring themselves, so {@link #RRULE}, {@link #RDATE} and {@link #EXDATE} are always {@code null}.
     * <p>
     * TODO: Insert all instances of recurring tasks.
     * <p>
     * The following operations are supported:
     * <p>
     * <h2>Insert</h2>
     * <p>
     * Note, the data of an insert must not contain the fields {@link #RRULE}, {@link #RDATE} or {@link #EXDATE}. If the new instance belongs to an existing
     * task the data must contain the fields {@link #ORIGINAL_INSTANCE_ID} and {@link #ORIGINAL_INSTANCE_TIME}. Also note, this table supports writing {@link
     * #DURATION} (if the instance has a {@link #DTSTART}), but reading it back will always return a {@code null} {@link #DURATION} and a non-{@code null}
     * {@link #DUE} date. Reading the task in the tasks table will, however, return the original {@link #DURATION}.
     * <p>
     * If there already is an instance (with or without override) for the given {@link #ORIGINAL_INSTANCE_ID} and {@link #ORIGINAL_INSTANCE_TIME} an exception
     * is thrown.
     * <p>
     * <table> <tr><th>ORIGINAL_INSTANCE_ID value</th><th>Result</th></tr> <tr><td>absent or empty</td><td>A new non-recurring task is created with the given
     * values.</td></tr> <tr><td>a valid {@link Tasks} row {@code _ID}</td><td>An {@link #RDATE} for the given {@link #ORIGINAL_INSTANCE_TIME} time is added to
     * the given master task, any {@link #EXDATE} for this time is removed. The task is inserted as an override to the given master. No fields are inherited
     * though. {@link #ORIGINAL_INSTANCE_ALLDAY} will be set to {@link #IS_ALLDAY} of the master.
     * <p>
     * Note, if the given master is non-recurring, this operation will turn it into a recurring task. </td></tr> <tr><td>invalid {@link Tasks} row {@code
     * _ID}</td><td>An exception is thrown.</td></tr></table>
     * <p>
     * <h2>Update</h2>
     * <p>
     * Note, the data of an update must not contain any fields related to recurrence ({@link #RRULE}, {@link #RDATE}, {@link #EXDATE}, {@link
     * #ORIGINAL_INSTANCE_ID}, {@link #ORIGINAL_INSTANCE_TIME} and {@link #ORIGINAL_INSTANCE_ALLDAY}). Also note, this table supports writing {@link #DURATION}
     * (if the instance has a {@link #DTSTART}), but reading it back will always return a {@code null} {@link #DURATION} and a non-{@code null} {@link #DUE}
     * date. Reading the task in the tasks table will, however, return the original {@link #DURATION}.
     * <p>
     * <table> <tr><th>Target task type</th><th>Result</th></tr> <tr><td>Recurring master task</td><td>A new override is created with the given data.<p> Note,
     * any fields which are not provided are inherited from the master, except for {@link #DTSTART} and {@link #DUE} which will be inherited from the instance
     * and {@link #DURATION}, {@link #RRULE}, {@link #RDATE} and {@link #EXDATE} which are set to {@code null}. {@link #ORIGINAL_INSTANCE_ID}, {@link
     * #ORIGINAL_INSTANCE_TIME} and {@link #ORIGINAL_INSTANCE_ALLDAY} will be set accordingly.</td></tr> <tr><td>Single instance task</td><td>The task is
     * updated with the given values.</td></tr> <tr><td>Recurrence override with existing master</td><td>The task is updated with the given values.</td></tr>
     * <tr><td>Recurrence override without existing master</td><td>The task is updated with the given values.</td></tr> </table>
     * <p>
     * <h2>Delete</h2>
     * <p>
     * <table> <tr><th>Target task type</th><th>Result</th></tr> <tr><td>Recurring master task</td><td>An {@link #EXDATE} for this instance is added, any {@link
     * #RDATE} for this instance is removed. The instance row is removed.<p> TODO: mark the task deleted if the remaining recurrence set is empty </td></tr>
     * <tr><td>Single instance task</td><td>The {@link Tasks#_DELETED} flag of the task is set.</td></tr> <tr><td>Recurrence override with existing
     * master</td><td>The {@link Tasks#_DELETED} flag of the override is set, an {@link #EXDATE} for this instance is added to the master, any {@link #RDATE}
     * for this instance is removed from the master. TODO: mark the master deleted if the remaining recurrence set of the master is empty </td></tr>
     * <tr><td>Recurrence override without existing master</td><td>The {@link Tasks#_DELETED} flag of the task is set.</td></tr> </table>
     *
     * @author Yannic Ahrens
     * @author Marten Gajda
     */
    public static final class Instances implements TaskColumns, InstanceColumns
    {

        /**
         * The name of the account the task belongs to. This is auto-derived from the list the task belongs to. Do not write this value here.
         * <p>
         * Value: String
         * </p>
         * <p>
         * read-only
         * </p>
         */
        public static final String ACCOUNT_NAME = TaskLists.ACCOUNT_NAME;

        /**
         * The type of the account the task belongs to. This is auto-derived from the list the task belongs to. Do not write this value here.
         * <p>
         * Value: String
         * </p>
         * <p>
         * read-only
         * </p>
         */
        public static final String ACCOUNT_TYPE = TaskLists.ACCOUNT_TYPE;

        /**
         * The name of the list this task belongs to as integer (0xaarrggbb). This is auto-derived from the list the task belongs to. Do not write this value
         * here.
         * <p>
         * Value: String
         * </p>
         * <p>
         * read-only
         * </p>
         */
        public static final String LIST_NAME = TaskLists.LIST_NAME;
        /**
         * The color of the list this task belongs to as integer (0xaarrggbb). This is auto-derived from the list the task belongs to. Do not write this value
         * here. To change the color of an individual task use {@code TASK_COLOR} instead.
         * <p>
         * Value: Integer
         * </p>
         * <p>
         * read-only
         * </p>
         */
        public static final String LIST_COLOR = TaskLists.LIST_COLOR;

        /**
         * The owner of the list this task belongs. This is auto-derived from the list the task belongs to. Do not write this value here.
         * <p>
         * Value: String
         * </p>
         * <p>
         * read-only
         * </p>
         */
        public static final String LIST_OWNER = TaskLists.OWNER;

        /**
         * The access level of the list this task belongs. This is auto-derived from the list the task belongs to. Do not write this value here.
         * <p>
         * Value: Integer
         * </p>
         * <p>
         * read-only
         * </p>
         */
        public static final String LIST_ACCESS_LEVEL = TaskLists.ACCESS_LEVEL;

        /**
         * The visibility of the list this task belongs. This is auto-derived from the list the task belongs to. Do not write this value here.
         * <p>
         * Value: Integer
         * </p>
         * <p>
         * read-only
         * </p>
         */
        public static final String VISIBLE = "visible";

        public static final String CONTENT_URI_PATH = "instances";

        public static final String DEFAULT_SORT_ORDER = INSTANCE_DUE_SORTING;


        /**
         * Get the instances content {@link Uri} using the given authority.
         *
         * @param authority
         *         The authority.
         *
         * @return A {@link Uri}.
         */
        public static Uri getContentUri(String authority)
        {
            return getUriFactory(authority).getUri(CONTENT_URI_PATH);
        }

    }


    /**
     * Available values in Categories.
     * <p>
     * Categories are per account. It's up to the front-end to ensure consistency of category colors across accounts.
     *
     * @author Marten Gajda <marten@dmfs.org>
     */
    public interface CategoriesColumns
    {

        String _ID = "_id";

        String ACCOUNT_NAME = "account_name";

        String ACCOUNT_TYPE = "account_type";

        String NAME = "name";

        String COLOR = "color";
    }


    public static final class Categories implements CategoriesColumns
    {

        public static final String CONTENT_URI_PATH = "categories";

        public static final String DEFAULT_SORT_ORDER = NAME;


        /**
         * Get the categories content {@link Uri} using the given authority.
         *
         * @param authority
         *         The authority.
         *
         * @return A {@link Uri}.
         */
        public static Uri getContentUri(String authority)
        {
            return getUriFactory(authority).getUri(CONTENT_URI_PATH);
        }

    }


    public interface AlarmsColumns
    {
        String ALARM_ID = "alarm_id";

        String LAST_TRIGGER = "last_trigger";

        String NEXT_TRIGGER = "next_trigger";
    }


    public static final class Alarms implements AlarmsColumns
    {

        public static final String CONTENT_URI_PATH = "alarms";


        /**
         * Get the alarms content {@link Uri} using the given authority.
         *
         * @param authority
         *         The authority.
         *
         * @return A {@link Uri}.
         */
        public static Uri getContentUri(String authority)
        {
            return getUriFactory(authority).getUri(CONTENT_URI_PATH);
        }

    }


    public interface PropertySyncColumns
    {
        String SYNC1 = "prop_sync1";

        String SYNC2 = "prop_sync2";

        String SYNC3 = "prop_sync3";

        String SYNC4 = "prop_sync4";

        String SYNC5 = "prop_sync5";

        String SYNC6 = "prop_sync6";

        String SYNC7 = "prop_sync7";

        String SYNC8 = "prop_sync8";
    }


    public interface PropertyColumns
    {

        String PROPERTY_ID = "property_id";

        String TASK_ID = "task_id";

        String MIMETYPE = "mimetype";

        String VERSION = "prop_version";

        String DATA0 = "data0";

        String DATA1 = "data1";

        String DATA2 = "data2";

        String DATA3 = "data3";

        String DATA4 = "data4";

        String DATA5 = "data5";

        String DATA6 = "data6";

        String DATA7 = "data7";

        String DATA8 = "data8";

        String DATA9 = "data9";

        String DATA10 = "data10";

        String DATA11 = "data11";

        String DATA12 = "data12";

        String DATA13 = "data13";

        String DATA14 = "data14";

        String DATA15 = "data15";
    }


    public static final class Properties implements PropertySyncColumns, PropertyColumns
    {

        public static final String CONTENT_URI_PATH = "properties";

        public static final String DEFAULT_SORT_ORDER = DATA0;


        /**
         * Get the properties content {@link Uri} using the given authority.
         *
         * @param authority
         *         The authority.
         *
         * @return A {@link Uri}.
         */
        public static Uri getContentUri(String authority)
        {
            return getUriFactory(authority).getUri(CONTENT_URI_PATH);
        }

    }


    public interface Property
    {
        /**
         * Attached documents.
         * <p>
         * <strong>Note:<strong> Attachments are write-once. To change an attachment you'll have to remove and re-add it.
         * </p>
         *
         * @author Marten Gajda <marten@dmfs.org>
         */
        interface Attachment extends PropertyColumns
        {
            /**
             * The mime-type of this property.
             */
            String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/attachment";

            /**
             * URL of the attachment. This is the link that points to the attached resource.
             * <p>
             * Value: String
             * </p>
             */
            String URL = DATA1;

            /**
             * The display name of the attachment, if any.
             * <p>
             * Value: String
             * </p>
             */
            String DISPLAY_NAME = DATA2;

            /**
             * Content-type of the attachment.
             * <p>
             * Value: String
             * </p>
             */
            String FORMAT = DATA3;

            /**
             * File size of the attachment or <code>-1</code> if unknown.
             * <p>
             * Value: Long
             * </p>
             */
            String SIZE = DATA4;

            /**
             * A content {@link Uri} that can be used to retrieve the attachment. Sync adapters can set this field if they know how to download the attachment
             * without going through the browser.
             * <p>
             * Value: String
             * </p>
             */
            String CONTENT_URI = DATA5;

        }


        interface Attendee extends PropertyColumns
        {
            /**
             * The mime-type of this property.
             */
            String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/attendee";

            /**
             * Name of the contact, if known.
             * <p>
             * Value: String
             * </p>
             */
            String NAME = DATA0;

            /**
             * Email address of the contact.
             * <p>
             * Value: String
             * </p>
             */
            String EMAIL = DATA1;

            String ROLE = DATA2;

            String STATUS = DATA3;

            String RSVP = DATA4;
        }


        /**
         * Categories are immutable. For creation is either the category id or name necessary
         */
        interface Category extends PropertyColumns
        {
            /**
             * The mime-type of this property.
             */
            String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/category";

            /**
             * Row id of the category.
             * <p>
             * Value: Long
             * </p>
             */
            String CATEGORY_ID = DATA0;

            /**
             * The name of the category
             * <p>
             * Value: String
             * </p>
             */
            String CATEGORY_NAME = DATA1;

            /**
             * The decimal coded color of the category
             * <p>
             * Value: Integer
             * </p>
             * <p>
             * read-only
             * </p>
             */
            String CATEGORY_COLOR = DATA2;
        }


        interface Comment extends PropertyColumns
        {
            /**
             * The mime-type of this property.
             */
            String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/comment";

            /**
             * Comment text.
             * <p>
             * Value: String
             * </p>
             */
            String COMMENT = DATA0;

            /**
             * Language code of the comment as defined in <a href="https://tools.ietf.org/html/rfc5646">RFC5646</a> or <code>null</code>.
             * <p>
             * Value: String
             * </p>
             */
            String LANGUAGE = DATA1;
        }


        interface Contact extends PropertyColumns
        {
            /**
             * The mime-type of this property.
             */
            String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/contact";

            String NAME = DATA0;

            String LANGUAGE = DATA1;
        }


        /**
         * Relations of a task.
         * <p>
         * When writing a relation, exactly one of {@link #RELATED_ID} or {@link #RELATED_UID} must be present. The missing value and {@link
         * #RELATED_CONTENT_URI} will be populated automatically if possible.
         * <p>
         * {@link Tasks#PARENT_ID} is updated automatically if possible.
         */
        interface Relation extends PropertyColumns
        {
            /**
             * The mime-type of this property.
             */
            String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/relation";

            /**
             * The row id of the related task. May be <code>-1</code> if the property doesn't refer to a task in this database or if it doesn't refer to a task
             * at all.
             * <p>
             * Value: long
             * </p>
             */
            String RELATED_ID = DATA1;

            /**
             * The relation type. This must be one of the {@code RELTYPE_*} values.
             * <p>
             * Value: int
             * </p>
             */
            String RELATED_TYPE = DATA2;

            /**
             * The UID of the related object.
             * <p>
             * Value: String
             * </p>
             */
            String RELATED_UID = DATA3;

            /**
             * The content Uri of a related object in another Android content provider, if found.
             * <p>
             * Value: String (URI)
             * </p>
             * <p>
             * This field is read-only.
             * </p>
             */
            String RELATED_CONTENT_URI = DATA5;

            /**
             * The related object is the parent of the object owning this relation.
             */
            int RELTYPE_PARENT = 0;

            /**
             * The related object is the child of the object owning this relation.
             */
            int RELTYPE_CHILD = 1;

            /**
             * The related object is a sibling of the object owning this relation.
             */
            int RELTYPE_SIBLING = 2;

        }


        interface Alarm extends PropertyColumns
        {

            int ALARM_TYPE_NOTHING = 0;

            int ALARM_TYPE_MESSAGE = 1;

            int ALARM_TYPE_EMAIL = 2;

            int ALARM_TYPE_SMS = 3;

            int ALARM_TYPE_SOUND = 4;

            int ALARM_REFERENCE_DUE_DATE = 1;

            int ALARM_REFERENCE_START_DATE = 2;

            /**
             * The mime-type of this property.
             */
            String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/alarm";

            /**
             * Number of minutes from the reference date when the alarm goes off. If the value is < 0 the alarm will go off after the reference date.
             * <p>
             * Value: Integer
             * </p>
             */
            String MINUTES_BEFORE = DATA0;

            /**
             * The reference date for the alarm. Either {@link #ALARM_REFERENCE_DUE_DATE} or {@link #ALARM_REFERENCE_START_DATE}.
             * <p>
             * Value: Integer
             * </p>
             */
            String REFERENCE = DATA1;

            /**
             * A message that appears with the alarm.
             * <p>
             * Value: String
             * </p>
             */
            String MESSAGE = DATA2;

            /**
             * The type of the alarm. Use the provided alarm types {@link #ALARM_TYPE_MESSAGE}, {@link #ALARM_TYPE_SOUND}, {@link #ALARM_TYPE_NOTHING},
             * {@link #ALARM_TYPE_EMAIL} and {@link #ALARM_TYPE_SMS}.
             * <p>
             * Value: Integer
             * </p>
             */
            String ALARM_TYPE = DATA3;
        }

    }


    private static synchronized UriFactory getUriFactory(String authority)
    {
        UriFactory uriFactory = sUriFactories.get(authority);
        if (uriFactory == null)
        {
            uriFactory = new UriFactory(authority);
            uriFactory.addUri(SyncState.CONTENT_URI_PATH);
            uriFactory.addUri(TaskLists.CONTENT_URI_PATH);
            uriFactory.addUri(Tasks.CONTENT_URI_PATH);
            uriFactory.addUri(Tasks.SEARCH_URI_PATH);
            uriFactory.addUri(Instances.CONTENT_URI_PATH);
            uriFactory.addUri(Categories.CONTENT_URI_PATH);
            uriFactory.addUri(Alarms.CONTENT_URI_PATH);
            uriFactory.addUri(Properties.CONTENT_URI_PATH);
            sUriFactories.put(authority, uriFactory);

        }
        return uriFactory;
    }

}
