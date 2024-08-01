/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import at.bitfire.davdroid.db.SyncState
import at.bitfire.davdroid.resource.LocalCollection

class LocalTestCollection: LocalCollection<LocalTestResource> {

    override val tag = "LocalTestCollection"
    override val url: String
        get() = "https://example.com"
    override val title = "Local Test Collection"

    override var lastSyncState: SyncState? = null

    val entries = mutableListOf<LocalTestResource>()

    override val readOnly: Boolean
        get() = throw NotImplementedError()

    override fun delete(): Boolean = true

    override fun findDeleted() = entries.filter { it.deleted }
    override fun findDirty() = entries.filter { it.dirty }

    override fun findByName(name: String) = entries.firstOrNull { it.fileName == name }

    override fun markNotDirty(flags: Int): Int {
        var updated = 0
        for (dirty in findDirty()) {
            dirty.flags = flags
            updated++
        }
        return updated
    }

    override fun removeNotDirtyMarked(flags: Int): Int {
        val numBefore = entries.size
        entries.removeIf { !it.dirty && it.flags == flags }
        return numBefore - entries.size
    }

    override fun forgetETags() {
    }

}