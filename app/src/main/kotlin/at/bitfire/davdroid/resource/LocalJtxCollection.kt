/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentValues
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Principal
import at.bitfire.davdroid.db.SyncState
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.util.DavUtils
import at.bitfire.ical4android.JtxCollection
import at.bitfire.ical4android.JtxCollectionFactory
import at.bitfire.ical4android.JtxICalObject
import at.techbee.jtx.JtxContract
import java.util.logging.Level

class LocalJtxCollection(account: Account, client: ContentProviderClient, id: Long):
    JtxCollection<JtxICalObject>(account, client, LocalJtxICalObject.Factory, id),
    LocalCollection<LocalJtxICalObject>{

    companion object {

        fun create(account: Account, client: ContentProviderClient, info: Collection, owner: Principal?) {
            val values = valuesFromCollection(info, account, owner, true)
            create(account, client, values)
        }

        fun valuesFromCollection(info: Collection, account: Account, owner: Principal?, withColor: Boolean) =
            ContentValues().apply {
                put(JtxContract.JtxCollection.URL, info.url.toString())
                put(JtxContract.JtxCollection.DISPLAYNAME, info.displayName ?: DavUtils.lastSegmentOfUrl(info.url))
                put(JtxContract.JtxCollection.DESCRIPTION, info.description)
                if (owner != null)
                    put(JtxContract.JtxCollection.OWNER, owner.url.toString())
                else Logger.log.log(Level.SEVERE, "No collection owner given. Will create jtx collection without owner")
                put(JtxContract.JtxCollection.OWNER_DISPLAYNAME, owner?.displayName)
                if (withColor)
                    put(JtxContract.JtxCollection.COLOR, info.color ?: Constants.DAVDROID_GREEN_RGBA)
                put(JtxContract.JtxCollection.SUPPORTSVEVENT, info.supportsVEVENT)
                put(JtxContract.JtxCollection.SUPPORTSVJOURNAL, info.supportsVJOURNAL)
                put(JtxContract.JtxCollection.SUPPORTSVTODO, info.supportsVTODO)
                put(JtxContract.JtxCollection.ACCOUNT_NAME, account.name)
                put(JtxContract.JtxCollection.ACCOUNT_TYPE, account.type)
                put(JtxContract.JtxCollection.READONLY, info.forceReadOnly || !info.privWriteContent)
            }
    }

    override val readOnly: Boolean
        get() = throw NotImplementedError()

    override val tag: String
        get() =  "jtx-${account.name}-$id"
    override val title: String
        get() = displayname ?: id.toString()
    override var lastSyncState: SyncState?
        get() = SyncState.fromString(syncstate)
        set(value) { syncstate = value.toString() }

    fun updateCollection(info: Collection, owner: Principal?, withColor: Boolean) {
        val values = valuesFromCollection(info, account, owner, withColor)
        update(values)
    }

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
     * Finds and returns a recurring instance of a [LocalJtxICalObject]
     */
    fun findRecurring(uid: String, recurid: String, dtstart: Long): LocalJtxICalObject? {
        val values = queryRecur(uid, recurid, dtstart) ?: return null
        return LocalJtxICalObject.Factory.fromProvider(this, values)
    }

    override fun markNotDirty(flags: Int)= updateSetFlags(flags)

    override fun removeNotDirtyMarked(flags: Int) = deleteByFlags(flags)

    override fun forgetETags() = updateSetETag(null)


    object Factory: JtxCollectionFactory<LocalJtxCollection> {
        override fun newInstance(account: Account, client: ContentProviderClient, id: Long) = LocalJtxCollection(account, client, id)
    }

}