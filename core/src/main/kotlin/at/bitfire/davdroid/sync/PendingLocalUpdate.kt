/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

/**
 * Local resource properties that a [at.bitfire.davdroid.sync.mapping.ResourceMapper] has
 * computed while generating the upload body, and that must be persisted locally only _once
 * the upload has actually succeeded_.
 *
 * Persisting them earlier would be wrong if the upload fails.
 *
 * @param sequence  new SEQUENCE to persist (*null*: SEQUENCE not modified)
 */
data class PendingLocalUpdate(
    val sequence: Int? = null
)
