/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.davtasks

/**
 * Fixed authority [DavTasksProvider][at.bitfire.davdroid.tasks.provider.DavTasksProvider] is
 * registered under for tests (see `synctools/src/androidTest/AndroidManifest.xml`) — in
 * production the authority is runtime-discovered (D1), but tests don't need discovery, just a
 * stable, real, reachable provider instance.
 */
const val DAV_TASKS_TEST_AUTHORITY = "at.bitfire.synctools.test.davtasks"
