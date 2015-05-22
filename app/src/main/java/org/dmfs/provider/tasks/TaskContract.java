/*
 * Copyright (C) 2012 Marten Gajda <marten@dmfs.org>
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
 *
 */

package org.dmfs.provider.tasks;

import android.content.ContentResolver;
import android.net.Uri;

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
public final class TaskContract {

    /**
     * Task provider authority.
     */
    // TODO how to do this better?
    public static final String AUTHORITY = "de.azapps.mirakel.provider";
    public static final String AUTHORITY_DMFS = "org.dmfs.tasks";

    /**
     * Base content URI.
     */
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

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
     * Account type for local, unsynced task lists.
     */
    public static final String LOCAL_ACCOUNT = "LOCAL";


    /**
     * Private constructor to prevent instantiation.
     */
    private TaskContract() {
    }

    /**
     * A set of columns for synchronization purposes. These columns exist in {@link Tasks} and in {@link TaskLists} but have different meanings. Only sync
     * adapters are allowed to change these values.
     *
     * @author Marten Gajda <marten@dmfs.org>
     */
    public interface CommonSyncColumns {

        /**
         * A unique Sync ID as set by the sync adapter.
         * <p>
         * Value: String
         * </p>
         */
        public static final String _SYNC_ID = "_sync_id";

        /**
         * Sync version as set by the sync adapter.
         * <p>
         * Value: String
         * </p>
         */
        public static final String SYNC_VERSION = "sync_version";

        /**
         * Indicates that a task or a task list has been changed.
         * <p>
         * Value: Integer
         * </p>
         */
        public static final String _DIRTY = "_dirty";

        /**
         * A general purpose column for the sync adapter.
         * <p>
         * Value: String
         * </p>
         */
        public static final String SYNC1 = "sync1";

        /**
         * A general purpose column for the sync adapter.
         * <p>
         * Value: String
         * </p>
         */
        public static final String SYNC2 = "sync2";

        /**
         * A general purpose column for the sync adapter.
         * <p>
         * Value: String
         * </p>
         */
        public static final String SYNC3 = "sync3";

        /**
         * A general purpose column for the sync adapter.
         * <p>
         * Value: String
         * </p>
         */
        public static final String SYNC4 = "sync4";

        /**
         * A general purpose column for the sync adapter.
         * <p>
         * Value: String
         * </p>
         */
        public static final String SYNC5 = "sync5";

        /**
         * A general purpose column for the sync adapter.
         * <p>
         * Value: String
         * </p>
         */
        public static final String SYNC6 = "sync6";

        /**
         * A general purpose column for the sync adapter.
         * <p>
         * Value: String
         * </p>
         */
        public static final String SYNC7 = "sync7";

        /**
         * A general purpose column for the sync adapter.
         * <p>
         * Value: String
         * </p>
         */
        public static final String SYNC8 = "sync8";

    }

    /**
     * Additional sync columns for task lists.
     *
     * @author Marten Gajda <marten@dmfs.org>
     */
    public interface TaskListSyncColumns {

        /**
         * The name of the account this list belongs to. This field is write-once.
         * <p>
         * Value: String
         * </p>
         */
        public static final String ACCOUNT_NAME = "account_name";

        /**
         * The type of the account this list belongs to. This field is write-once.
         * <p>
         * Value: String
         * </p>
         */
        public static final String ACCOUNT_TYPE = "account_type";
    }

    /**
     * Additional sync columns for tasks.
     *
     * @author Marten Gajda <marten@dmfs.org>
     */
    public interface TaskSyncColumns {
        /**
         * The UID of a task. This is field can be changed by a sync adapter only.
         * <p>
         * Value: String
         * </p>
         */
        public static final String _UID = "_uid";

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
        public static final String _DELETED = "_deleted";
    }

    /**
     * Data columns of task lists.
     *
     * @author Marten Gajda <marten@dmfs.org>
     */
    public interface TaskListColumns {

        /**
         * List ID.
         * <p>
         * Value: Long
         * </p>
         * <p>
         * read-only
         * </p>
         */
        public static final String _ID = "_id";

        /**
         * The name of the task list.
         * <p>
         * Value: String
         * </p>
         */
        public static final String LIST_NAME = "list_name";

        /**
         * The color of this list as integer (0xaarrggbb). Only the sync adapter can change this.
         * <p>
         * Value: Integer
         * </p>
         */
        public static final String LIST_COLOR = "list_color";

        /**
         * The access level a user has on this list. <strong>This value is not used yet, sync adapters should set it to <code>0</code></strong>.
         * <p>
         * Value: Integer
         * </p>
         */
        public static final String ACCESS_LEVEL = "list_access_level";

        /**
         * Indicates that a task list is set to be visible.
         * <p>
         * Value: Integer (0 or 1)
         * </p>
         */
        public static final String VISIBLE = "visible";

        /**
         * Indicates that a task list is set to be synced.
         * <p>
         * Value: Integer (0 or 1)
         * </p>
         */
        public static final String SYNC_ENABLED = "sync_enabled";

        /**
         * The email address of the list owner.
         * <p>
         * Value: String
         * </p>
         */
        public static final String OWNER = "list_owner";

    }

    /**
     * The task list table holds one entry for each task list.
     *
     * @author Marten Gajda <marten@dmfs.org>
     */
    public static final class TaskLists implements TaskListColumns, TaskListSyncColumns,
        CommonSyncColumns {
        public static final String CONTENT_URI_PATH = "tasklists";

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + CONTENT_URI_PATH);

        public static final String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" +
                AUTHORITY + "." + CONTENT_URI_PATH;

        public static final String CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + AUTHORITY +
                "." + CONTENT_URI_PATH;

        /**
         * The default sort order.
         */
        public static final String DEFAULT_SORT_ORDER = ACCOUNT_NAME + ", " + LIST_NAME;

        /**
         * An array of columns only a sync adapter is allowed to change.
         */
        public static final String[] SYNC_ADAPTER_COLUMNS = new String[] { ACCESS_LEVEL, _DIRTY, OWNER, SYNC1, SYNC2, SYNC3, SYNC4, SYNC5, SYNC6, SYNC7, SYNC8,
                _SYNC_ID, SYNC_VERSION,
                                                                         };

    }

    /**
     * Task data columns. Defines all the values a task can have at most once.
     *
     * @author Marten Gajda <marten@dmfs.org>
     */
    public interface TaskColumns {

        /**
         * The row id of a task. This value is <strong>read-only</strong>
         * <p>
         * Value: Integer
         * </p>
         */
        public static final String _ID = "_id";

        /**
         * The id of the list this task belongs to. This value is <strong>write-once</strong> and must not be <code>null</code>.
         * <p>
         * Value: Integer
         * </p>
         */
        public static final String LIST_ID = "list_id";

        /**
         * The title of the task.
         * <p>
         * Value: String
         * </p>
         */
        public static final String TITLE = "title";

        /**
         * The location of the task.
         * <p>
         * Value: String
         * </p>
         */
        public static final String LOCATION = "location";

        /**
         * A geographic location related to the task. The should be a string in the format "longitude,latitude".
         * <p>
         * Value: String
         * </p>
         */
        public static final String GEO = "geo";

        /**
         * The description of a task.
         * <p>
         * Value: String
         * </p>
         */
        public static final String DESCRIPTION = "description";

        /**
         * An URL for this task. Must be a valid URL if not <code>null</code>-
         * <p>
         * Value: String
         * </p>
         */
        public static final String URL = "url";

        /**
         * The email address of the organizer if any, {@code null} otherwise.
         * <p>
         * Value: String
         * </p>
         */
        public static final String ORGANIZER = "organizer";

        /**
         * The priority of a task. This is an Integer between zero and 9. Zero means there is no priority set. 1 is the highest priority and 9 the lowest.
         * <p>
         * Value: Integer
         * </p>
         */
        public static final String PRIORITY = "priority";

        /**
         * The default value of {@link #PRIORITY}.
         */
        public static final int PRIORITY_DEFAULT = 0;

        /**
         * The classification of a task. This value must be either <code>null</code> or one of {@link #CLASSIFICATION_PUBLIC}, {@link #CLASSIFICATION_PRIVATE},
         * {@link #CLASSIFICATION_CONFIDENTIAL}.
         * <p>
         * Value: Integer
         * </p>
         */
        public static final String CLASSIFICATION = "class";

        /**
         * Classification value for public tasks.
         */
        public static final int CLASSIFICATION_PUBLIC = 0;

        /**
         * Classification value for private tasks.
         */
        public static final int CLASSIFICATION_PRIVATE = 1;

        /**
         * Classification value for confidential tasks.
         */
        public static final int CLASSIFICATION_CONFIDENTIAL = 2;

        /**
         * Default value of {@link #CLASSIFICATION}.
         */
        public static final Integer CLASSIFICATION_DEFAULT = null;

        /**
         * Date of completion of this task in milliseconds since the epoch or {@code null} if this task has not been completed yet.
         * <p>
         * Value: Long
         * </p>
         */
        public static final String COMPLETED = "completed";

        /**
         * Indicates that the date of completion is an all-day date.
         * <p>
         * Value: Integer
         * </p>
         */
        public static final String COMPLETED_IS_ALLDAY = "completed_is_allday";

        /**
         * A number between 0 and 100 that indicates the progress of the task or <code>null</code>.
         * <p>
         * Value: Integer (0-100)
         * </p>
         */
        public static final String PERCENT_COMPLETE = "percent_complete";

        /**
         * The status of this task. One of {@link #STATUS_NEEDS_ACTION},{@link #STATUS_IN_PROCESS}, {@link #STATUS_COMPLETED}, {@link #STATUS_CANCELLED}.
         * <p>
         * Value: Integer
         * </p>
         */
        public static final String STATUS = "status";

        /**
         * A specific status indicating that nothing has been done yet.
         */
        public static final int STATUS_NEEDS_ACTION = 0;

        /**
         * A specific status indicating that some work has been done.
         */
        public static final int STATUS_IN_PROCESS = 1;

        /**
         * A specific status indicating that the task is completed.
         */
        public static final int STATUS_COMPLETED = 2;

        /**
         * A specific status indicating that the task has been cancelled.
         */
        public static final int STATUS_CANCELLED = 3;

        /**
         * The default status is "needs action".
         */
        public static final int STATUS_DEFAULT = STATUS_NEEDS_ACTION;

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
        public static final String IS_NEW = "is_new";

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
        public static final String IS_CLOSED = "is_closed";

        /**
         * An individual color for this task in the format 0xaarrggbb or {@code null} to use {@link TaskListColumns#LIST_COLOR} instead.
         * <p>
         * Value: Integer
         * </p>
         */
        public static final String TASK_COLOR = "task_color";

        /**
         * When this task starts in milliseconds since the epoch.
         * <p>
         * Value: Long
         * </p>
         */
        public static final String DTSTART = "dtstart";

        /**
         * Boolean: flag that indicates that this is an all-day task.
         */
        public static final String IS_ALLDAY = "is_allday";

        /**
         * When this task has been created in milliseconds since the epoch.
         * <p>
         * Value: Long
         * </p>
         */
        public static final String CREATED = "created";

        /**
         * When this task had been modified the last time in milliseconds since the epoch.
         * <p>
         * Value: Long
         * </p>
         */
        public static final String LAST_MODIFIED = "last_modified";

        /**
         * String: An Olson Id of the time zone of this task. If this value is <code>null</code>, it's automatically replaced by the local time zone.
         */
        public static final String TZ = "tz";

        /**
         * When this task is due in milliseconds since the epoch. Only one of {@link #DUE} or {@link #DURATION} must be supplied (or none of both if the task
         * has no due date).
         * <p>
         * Value: Long
         * </p>
         */
        public static final String DUE = "due";

        /**
         * The duration of this task. Only one of {@link #DUE} or {@link #DURATION} must be supplied (or none of both if the task has no due date). Setting a
         * {@link #DURATION} is not allowed when {@link #DTSTART} is <code>null</code>. The Value must be a duration string as in <a
         * href="http://tools.ietf.org/html/rfc5545#section-3.3.6">RFC 5545 Section 3.3.6</a>.
         * <p>
         * Value: String
         * </p>
         */
        public static final String DURATION = "duration";

        /**
         * A comma separated list of time Strings in RFC 5545 format (see <a href="http://tools.ietf.org/html/rfc5545#section-3.3.4">RFC 5545 Section 3.3.4</a>
         * and <a href="http://tools.ietf.org/html/rfc5545#section-3.3.5">RFC 5545 Section 3.3.5</a>) that contains dates of instances of e recurring task.
         * All-day tasks must use the DATE format specified in section 3.3.4 of RFC 5545.
         *
         * This value must be {@code null} for exception instances.
         * <p>
         * Value: String
         * </p>
         */
        public static final String RDATE = "rdate";

        /**
         * A comma separated list of time Strings in RFC 5545 format (see <a href="http://tools.ietf.org/html/rfc5545#section-3.3.4">RFC 5545 Section 3.3.4</a>
         * and <a href="http://tools.ietf.org/html/rfc5545#section-3.3.5">RFC 5545 Section 3.3.5</a>) that contains dates of exceptions of a recurring task.
         * All-day tasks must use the DATE format specified in section 3.3.4 of RFC 5545.
         *
         * This value must be {@code null} for exception instances.
         * <p>
         * Value: String
         * </p>
         */
        public static final String EXDATE = "exdate";

        /**
         * A recurrence rule as specified in <a href="http://tools.ietf.org/html/rfc5545#section-3.3.10">RFC 5545 Section 3.3.10</a>.
         *
         * This value must be {@code null} for exception instances.
         * <p>
         * Value: String
         * </p>
         */
        public static final String RRULE = "rrule";

        /**
         * The _sync_id of the original event if this is an exception, <code>null</code> otherwise. Only one of {@link #ORIGINAL_INSTANCE_SYNC_ID} or
         * {@link #ORIGINAL_INSTANCE_ID} must be set if this task is an exception. The other one will be updated by the content provider.
         * <p>
         * Value: String
         * </p>
         */
        public static final String ORIGINAL_INSTANCE_SYNC_ID = "original_instance_sync_id";

        /**
         * The row id of the original event if this is an exception, <code>null</code> otherwise. Only one of {@link #ORIGINAL_INSTANCE_SYNC_ID} or
         * {@link #ORIGINAL_INSTANCE_ID} must be set if this task is an exception. The other one will be updated by the content provider.
         * <p>
         * Value: Long
         * </p>
         */
        public static final String ORIGINAL_INSTANCE_ID = "original_instance_id";

        /**
         * The time in milliseconds since the Epoch of the original instance that is overridden by this instance or <code>null</code> if this task is not an
         * exception.
         * <p>
         * Value: Long
         * </p>
         */
        public static final String ORIGINAL_INSTANCE_TIME = "original_instance_time";

        /**
         * A flag indicating that the original instance was an all-day task.
         * <p>
         * Value: Integer
         * </p>
         */
        public static final String ORIGINAL_INSTANCE_ALLDAY = "original_instance_allday";

        /**
         * The row id of the parent task. <code>null</code> if the task has no parent task.
         * <p>
         * Value: Long
         * </p>
         */
        public static final String PARENT_ID = "parent_id";

        /**
         * The sorting of this task under it's parent task.
         * <p>
         * Value: String
         * </p>
         */
        public static final String SORTING = "sorting";

        /**
         * Indicates how many alarms a task has. <code>0</code> means the task has no alarms.
         * <p>
         * Value: Integer
         * </p>
         *
         */
        public static final String HAS_ALARMS = "has_alarms";
    }

    /**
     * The task table stores the data of all tasks.
     *
     * @author Marten Gajda <marten@dmfs.org>
     */
    public static final class Tasks implements TaskColumns, CommonSyncColumns, TaskSyncColumns {
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

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + CONTENT_URI_PATH);

        public static final String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" +
                AUTHORITY + "." + CONTENT_URI_PATH;

        public static final String CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + AUTHORITY +
                "." + CONTENT_URI_PATH;

        public static final String DEFAULT_SORT_ORDER = DUE;

        public static final String[] SYNC_ADAPTER_COLUMNS = new String[] { _DIRTY, SYNC1, SYNC2, SYNC3, SYNC4, SYNC5, SYNC6, SYNC7, SYNC8, _SYNC_ID,
                SYNC_VERSION,
                                                                         };
    }

    /**
     * Columns of a task instance.
     *
     * @author Yannic Ahrens <yannic@dmfs.org>
     * @author Marten Gajda <marten@dmfs.org>
     */
    public interface InstanceColumns {
        /**
         * _ID of task this instance belongs to.
         * <p>
         * Value: Long
         * </p>
         */
        public static final String TASK_ID = "task_id";

        /**
         * The start date of an instance in milliseconds since the epoch or <code>null</code> if the instance has no start date. At present this is read only.
         * <p>
         * Value: Long
         * </p>
         */
        public static final String INSTANCE_START = "instance_start";

        /**
         * The due date of an instance in milliseconds since the epoch or <code>null</code> if the instance has no due date. At present this is read only.
         * <p>
         * Value: Long
         * </p>
         */
        public static final String INSTANCE_DUE = "instance_due";

        /**
         * This column should be used in an order clause to sort instances by due date. It contains a slightly modified start date that takes allday tasks into
         * account.
         * <p>
         * Value: Long
         * </p>
         * <p>
         * read-only
         * </p>
         */
        public static final String INSTANCE_START_SORTING = "instance_start_sorting";

        /**
         * This column should be used in an order clause to sort instances by due date. It contains a slightly modified due date that takes allday tasks into
         * account.
         * <p>
         * Value: Long
         * </p>
         * <p>
         * read-only
         * </p>
         */
        public static final String INSTANCE_DUE_SORTING = "instance_due_sorting";

        /**
         * The duration of an instance in milliseconds or <code>null</code> if the instance has only one of start or due date or none of both. At present this
         * is read only.
         * <p>
         * Value: Long
         * </p>
         */
        public static final String INSTANCE_DURATION = "instance_duration";

    }

    /**
     * Instances of a task. At present this table is read only. Currently it contains exactly one entry per task (and task exception), so it's merely a copy of
     * {@link Tasks}.
     * <p>
     * TODO: Insert all instances of recurring the tasks.
     * </p>
     * <p>
     * TODO: In later releases it's planned to provide a convenient interface to add, change or delete task instances via this URI.
     * </p>
     *
     * @author Yannic Ahrens <yannic@dmfs.org>
     */
    public static final class Instances implements TaskColumns, InstanceColumns {

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

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + CONTENT_URI_PATH);

        public static final String CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + AUTHORITY +
                "." + CONTENT_URI_PATH;

        public static final String DEFAULT_SORT_ORDER = INSTANCE_DUE_SORTING;

    }

    /*
     * ==================================================================================
     *
     * Everything below this line is not used yet and subject to change. Don't use it.
     *
     * ==================================================================================
     */

    /**
     * Available values in Categories.
     *
     * Categories are per account. It's up to the front-end to ensure consistency of category colors across accounts.
     *
     * @author Marten Gajda <marten@dmfs.org>
     */
    public interface CategoriesColumns {

        public static final String _ID = "_id";

        public static final String ACCOUNT_NAME = "account_name";

        public static final String ACCOUNT_TYPE = "account_type";

        public static final String NAME = "name";

        public static final String COLOR = "color";
    }

    public static final class Categories implements CategoriesColumns {

        public static final String CONTENT_URI_PATH = "categories";

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + CONTENT_URI_PATH);

        public static final String DEFAULT_SORT_ORDER = NAME;

    }

    public interface AlarmsColumns {
        public static final String ALARM_ID = "alarm_id";

        public static final String LAST_TRIGGER = "last_trigger";

        public static final String NEXT_TRIGGER = "next_trigger";
    }

    public static final class Alarms implements AlarmsColumns {

        public static final String CONTENT_URI_PATH = "alarms";

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + CONTENT_URI_PATH);

    }

    public interface PropertySyncColumns {
        public static final String SYNC1 = "prop_sync1";

        public static final String SYNC2 = "prop_sync2";

        public static final String SYNC3 = "prop_sync3";

        public static final String SYNC4 = "prop_sync4";

        public static final String SYNC5 = "prop_sync5";

        public static final String SYNC6 = "prop_sync6";

        public static final String SYNC7 = "prop_sync7";

        public static final String SYNC8 = "prop_sync8";
    }

    public interface PropertyColumns {

        public static final String PROPERTY_ID = "property_id";

        public static final String TASK_ID = "task_id";

        public static final String MIMETYPE = "mimetype";

        public static final String VERSION = "prop_version";

        public static final String DATA0 = "data0";

        public static final String DATA1 = "data1";

        public static final String DATA2 = "data2";

        public static final String DATA3 = "data3";

        public static final String DATA4 = "data4";

        public static final String DATA5 = "data5";

        public static final String DATA6 = "data6";

        public static final String DATA7 = "data7";

        public static final String DATA8 = "data8";

        public static final String DATA9 = "data9";

        public static final String DATA10 = "data10";

        public static final String DATA11 = "data11";

        public static final String DATA12 = "data12";

        public static final String DATA13 = "data13";

        public static final String DATA14 = "data14";

        public static final String DATA15 = "data15";
    }

    public static final class Properties implements PropertySyncColumns, PropertyColumns {

        public static final String CONTENT_URI_PATH = "properties";

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + CONTENT_URI_PATH);

        public static final String DEFAULT_SORT_ORDER = DATA0;

    }

    public interface Property {
        /**
         * Attached documents.
         * <p>
         * <strong>Note:<strong> Attachments are write-once. To change an attachment you'll have to remove and re-add it.
         * </p>
         *
         * @author Marten Gajda <marten@dmfs.org>
         */
        public static interface Attachment extends PropertyColumns {
            /**
             * The mime-type of this property.
             */
            public final static String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE +
                    "/attachment";

            /**
             * ID of the attachment. Use this id to store and retrieve the attachment in the attachments table.
             * <p>
             * Value: Long
             * </p>
             */
            public final static String ATTACHMENT_ID = DATA1;

            /**
             * Content-type of the attachment.
             * <p>
             * Value: String
             * </p>
             */
            public final static String FORMAT = DATA2;
        }

        public static interface Attendee extends PropertyColumns {
            /**
             * The mime-type of this property.
             */
            public final static String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/attendee";

            /**
             * Name of the contact, if known.
             * <p>
             * Value: String
             * </p>
             */
            public final static String NAME = DATA0;

            /**
             * Email address of the contact.
             * <p>
             * Value: String
             * </p>
             */
            public final static String EMAIL = DATA1;

            public final static String ROLE = DATA2;

            public final static String STATUS = DATA3;

            public final static String RSVP = DATA4;
        }

        /**
         * Categories are immutable. For creation is either the category id or name necessary
         *
         */
        public static interface Category extends PropertyColumns {
            /**
             * The mime-type of this property.
             */
            public final static String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/category";

            /**
             * Row id of the category.
             * <p>
             * Value: Long
             * </p>
             */
            public final static String CATEGORY_ID = DATA0;

            /**
             * The name of the category
             * <p>
             * Value: String
             * </p>
             */
            public final static String CATEGORY_NAME = DATA1;

            /**
             * The decimal coded color of the category
             * <p>
             * Value: Integer
             * </p>
             * <p>
             * read-only
             * </p>
             */
            public final static String CATEGORY_COLOR = DATA2;
        }

        public static interface Comment extends PropertyColumns {
            /**
             * The mime-type of this property.
             */
            public final static String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/comment";

            /**
             * Comment text.
             * <p>
             * Value: String
             * </p>
             */
            public final static String COMMENT = DATA0;

            /**
             * Language code of the comment as defined in <a href="https://tools.ietf.org/html/rfc5646">RFC5646</a> or <code>null</code>.
             * <p>
             * Value: String
             * </p>
             */
            public final static String LANGUAGE = DATA1;
        }

        public static interface Contact extends PropertyColumns {
            /**
             * The mime-type of this property.
             */
            public final static String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/contact";

            public final static String NAME = DATA0;

            public final static String LANGUAGE = DATA1;
        }

        public static interface Relation extends PropertyColumns {
            /**
             * The mime-type of this property.
             */
            public final static String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/relation";

            public final static String RELATED_ID = DATA1;

            public final static String RELATED_TYPE = DATA2;
        }

        public static interface Alarm extends PropertyColumns {

            public static final int ALARM_TYPE_NOTHING = 0;

            public static final int ALARM_TYPE_MESSAGE = 1;

            public static final int ALARM_TYPE_EMAIL = 2;

            public static final int ALARM_TYPE_SMS = 3;

            public static final int ALARM_TYPE_SOUND = 4;

            public static final int ALARM_REFERENCE_DUE_DATE = 1;

            public static final int ALARM_REFERENCE_START_DATE = 2;

            /**
             * The mime-type of this property.
             */
            public final static String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/alarm";

            /**
             * Number of minutes from the reference date when the alarm goes off. If the value is < 0 the alarm will go off after the reference date.
             * <p>
             * Value: Integer
             * </p>
             */
            public final static String MINUTES_BEFORE = DATA0;

            /**
             * The reference date for the alarm. Either {@link ALARM_REFERENCE_DUE_DATE} or {@link ALARM_REFERENCE_START_DATE}.
             * <p>
             * Value: Integer
             * </p>
             */
            public final static String REFERENCE = DATA1;

            /**
             * A message that appears with the alarm.
             * <p>
             * Value: String
             * </p>
             */
            public final static String MESSAGE = DATA2;

            /**
             * The type of the alarm. Use the provided alarm types {@link ALARM_TYPE_MESSAGE}, {@link ALARM_TYPE_SOUND}, {@link ALARM_TYPE_NOTHING},
             * {@link ALARM_TYPE_EMAIL} and {@link ALARM_TYPE_SMS}.
             * <p>
             * Value: Integer
             * </p>
             */
            public final static String ALARM_TYPE = DATA3;
        }

    }

}
