/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

/**
 * Represents the kind of data to be synced.
 *
 * The specific content provider that syncs this kind of data is specified by the authority.
 */
enum class SyncDomain {
    CONTACTS,
    EVENTS,
    TASKS
}