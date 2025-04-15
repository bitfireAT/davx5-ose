/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.content.ContentProviderClient
import at.bitfire.davdroid.db.SyncState
import at.bitfire.ical4android.JtxCollection
import at.bitfire.ical4android.JtxCollectionFactory
import at.bitfire.ical4android.JtxICalObject

/**
 * Application-specific implementation for jtx collections.
 *
 * [at.techbee.jtx.JtxContract.JtxCollection.SYNC_ID] corresponds to the database collection ID ([at.bitfire.davdroid.db.Collection.id]).
 */
class LocalJtxCollection(account: Account, client: ContentProviderClient, id: Long):
    JtxCollection<JtxICalObject>(account, client, LocalJtxICalObject.Factory, id),
    LocalCollection<LocalJtxICalObject>{

    override val readOnly: Boolean
        get() = throw NotImplementedError()

    override val tag: String
        get() =  "jtx-${account.name}-$id"

    override val dbCollectionId: Long?
        get() = syncId

    override val title: String
        get() = displayname ?: id.toString()

    override var lastSyncState: SyncState?
        get() = SyncState.fromString(syncstate)
        set(value) { syncstate = value.toString() }


    override fun findDeleted(): List<LocalJtxICalObject> {
        val values = queryDeletedICalObjects()
        val localJtxICalObjects = mutableListOf<LocalJtxICalObject>()
        values.forEach {
            localJtxICalObjects.add(LocalJtxICalObject.Factory.fromProvider(this, it))
        }
        return localJtxICalObjects
    }

    override fun findDirty(): List<LocalJtxICalObject> {
        val values = queryDirtyICalObjects()
        val localJtxICalObjects = mutableListOf<LocalJtxICalObject>()
        values.forEach {
            localJtxICalObjects.add(LocalJtxICalObject.Factory.fromProvider(this, it))
        }
        return localJtxICalObjects
    }

    override fun findByName(name: String): LocalJtxICalObject? {
        val values = queryByFilename(name) ?: return null
        return LocalJtxICalObject.Factory.fromProvider(this, values)
    }

    /**
     * Finds and returns a recurrence instance of a [LocalJtxICalObject]
     * @param uid       UID of the main VTODO
     * @param recurid   RECURRENCE-ID of the recurrence instance
     * @return LocalJtxICalObject or null if none or multiple entries found
     */
    fun findRecurInstance(uid: String, recurid: String): LocalJtxICalObject? {
        val values = queryRecur(uid, recurid) ?: return null
        return LocalJtxICalObject.Factory.fromProvider(this, values)
    }

    override fun markNotDirty(flags: Int)= updateSetFlags(flags)

    override fun removeNotDirtyMarked(flags: Int) = deleteByFlags(flags)

    override fun forgetETags() = updateSetETag(null)


    object Factory: JtxCollectionFactory<LocalJtxCollection> {
        override fun newInstance(account: Account, client: ContentProviderClient, id: Long) = LocalJtxCollection(account, client, id)
    }

}