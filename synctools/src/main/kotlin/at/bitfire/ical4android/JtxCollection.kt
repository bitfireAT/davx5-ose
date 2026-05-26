/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.storage.toContentValues
import at.techbee.jtx.JtxContract
import at.techbee.jtx.JtxContract.asSyncAdapter
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.component.VJournal
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.immutable.ImmutableVersion
import java.util.LinkedList
import java.util.logging.Level
import java.util.logging.Logger

open class JtxCollection<out T: JtxICalObject>(val account: Account,
                                               val client: ContentProviderClient,
                                               private val iCalObjectFactory: JtxICalObjectFactory<JtxICalObject>,
                                               val id: Long) {

    companion object {

        private val logger
            get() = Logger.getLogger(JtxCollection::class.java.name)

        fun create(account: Account, client: ContentProviderClient, values: ContentValues): Uri {
            logger.log(Level.FINE, "Creating jtx Board collection", values)
            return client.insert(JtxContract.JtxCollection.CONTENT_URI.asSyncAdapter(account), values)
                ?: throw LocalStorageException("Couldn't create JTX Collection")
        }

        fun<T: JtxCollection<JtxICalObject>> find(account: Account, client: ContentProviderClient, context: Context, factory: JtxCollectionFactory<T>, where: String?, whereArgs: Array<String>?): List<T> {
            val collections = LinkedList<T>()
            client.query(JtxContract.JtxCollection.CONTENT_URI.asSyncAdapter(account), null, where, whereArgs, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val values = cursor.toContentValues()
                    val collection = factory.newInstance(account, client, values.getAsLong(JtxContract.JtxCollection.ID))
                    collection.populate(values, context)
                    collections += collection
                }
            }
            return collections
        }
    }


    var url: String? = null
    var displayname: String? = null
    var syncstate: String? = null

    var supportsVEVENT = true
    var supportsVTODO = true
    var supportsVJOURNAL = true

    var syncId: Long? = null

    var context: Context? = null


    fun delete(): Boolean {
        logger.log(Level.FINE, "Deleting jtx Board collection (#$id)")
        return client.delete(ContentUris.withAppendedId(JtxContract.JtxCollection.CONTENT_URI.asSyncAdapter(account), id), null, null) > 0
    }

    fun update(values: ContentValues) {
        logger.log(Level.FINE, "Updating jtx Board collection (#$id)", values)
        client.update(ContentUris.withAppendedId(JtxContract.JtxCollection.CONTENT_URI.asSyncAdapter(account), id), values, null, null)
    }

    protected fun populate(values: ContentValues, context: Context) {
        url = values.getAsString(JtxContract.JtxCollection.URL)
        displayname = values.getAsString(JtxContract.JtxCollection.DISPLAYNAME)
        syncstate = values.getAsString(JtxContract.JtxCollection.SYNC_VERSION)

        supportsVEVENT = values.getAsString(JtxContract.JtxCollection.SUPPORTSVEVENT) == "1"
                || values.getAsString(JtxContract.JtxCollection.SUPPORTSVEVENT) == "true"
        supportsVTODO = values.getAsString(JtxContract.JtxCollection.SUPPORTSVTODO) == "1"
                || values.getAsString(JtxContract.JtxCollection.SUPPORTSVTODO) == "true"
        supportsVJOURNAL = values.getAsString(JtxContract.JtxCollection.SUPPORTSVJOURNAL) == "1"
                || values.getAsString(JtxContract.JtxCollection.SUPPORTSVJOURNAL) == "true"

        syncId = values.getAsLong(JtxContract.JtxCollection.SYNC_ID)

        this.context = context
    }


    /**
     * Builds the JtxICalObject content uri with appended parameters for account and syncadapter
     * @return the Uri for the JtxICalObject Sync in the content provider of jtx Board
     */
    fun jtxSyncURI(): Uri =
        JtxContract.JtxICalObject.CONTENT_URI.buildUpon()
            .appendQueryParameter(JtxContract.ACCOUNT_NAME, account.name)
            .appendQueryParameter(JtxContract.ACCOUNT_TYPE, account.type)
            .appendQueryParameter(JtxContract.CALLER_IS_SYNCADAPTER, "true")
            .build()


    /**
     * @return a list of content values of the deleted jtxICalObjects
     */
    fun queryDeletedICalObjects(): List<ContentValues> {
        val values = mutableListOf<ContentValues>()
        client.query(
            JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(account),
            null,
            "${JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID} = ? AND ${JtxContract.JtxICalObject.DELETED} = ? AND ${JtxContract.JtxICalObject.RECURID} IS NULL", arrayOf(id.toString(), "1"),
            null
        ).use { cursor ->
            logger.fine("findDeleted: found ${cursor?.count} deleted records in ${account.name}")
            while (cursor?.moveToNext() == true) {
                values.add(cursor.toContentValues())
            }
        }
        return values
    }


    /**
     * @return a list of content values of the dirty jtxICalObjects
     */
    fun queryDirtyICalObjects(): List<ContentValues> {
        val values = mutableListOf<ContentValues>()
        client.query(
            JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(account),
            null,
            "${JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID} = ? AND ${JtxContract.JtxICalObject.DIRTY} = ? AND ${JtxContract.JtxICalObject.RECURID} IS NULL", arrayOf(id.toString(), "1"),
            null
        ).use { cursor ->
            logger.fine("findDirty: found ${cursor?.count} dirty records in ${account.name}")
            while (cursor?.moveToNext() == true) {
                values.add(cursor.toContentValues())
            }
        }
        return values
    }

    /**
     * @param [filename] of the entry that should be retrieved as content values
     * @return Content Values of the found item with the given filename or null if the result was empty or more than 1
     */
    fun queryByFilename(filename: String): ContentValues? {
        client.query(
            JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(account),
            null,
            "${JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID} = ? AND ${JtxContract.JtxICalObject.FILENAME} = ? AND ${JtxContract.JtxICalObject.RECURID} IS NULL", arrayOf(id.toString(), filename),
            null
        ).use { cursor ->
            logger.fine("queryByFilename: found ${cursor?.count} records in ${account.name}")
            if (cursor?.count != 1)
                return null
            cursor.moveToFirst()
            return cursor.toContentValues()
        }
    }

    /**
     * @param [uid] of the entry that should be retrieved as content values
     * @return Content Values of the found item with the given UID or null if the result was empty or more than 1
     * The query checks for the [uid] within all collections of this account, not only the current collection.
     */
    fun queryByUID(uid: String): ContentValues? {
        client.query(JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(account), null, "${JtxContract.JtxICalObject.UID} = ?", arrayOf(uid), null).use { cursor ->
            logger.fine("queryByUID: found ${cursor?.count} records in ${account.name}")
            if (cursor?.count != 1)
                return null
            cursor.moveToFirst()
            return cursor.toContentValues()
        }
    }


    /**
     * @param [uid] of the entry that should be retrieved as content values
     * @param [recurid] of the entry that should be retrieved as content values
     * @return Content Values of the found item with the given UID or null if the result was empty or more than 1
     * The query checks for the [uid] within all collections of this account, not only the current collection.
     */
    fun queryRecur(uid: String, recurid: String): ContentValues? {
        client.query(
            JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(account),
            null,
            "${JtxContract.JtxICalObject.UID} = ? AND ${JtxContract.JtxICalObject.RECURID} = ?",
            arrayOf(uid, recurid),
            null
        ).use { cursor ->
            logger.fine("queryRecur: found ${cursor?.count} records in ${account.name}")
            if (cursor?.count != 1)
                return null
            cursor.moveToFirst()
            return cursor.toContentValues()
        }
    }

    /**
     * updates the flags of all entries in the collection with the given flag
     * @param [flags] to be set
     * @return the number of records that were updated
     */
    fun updateSetFlags(flags: Int): Int {
        val values = ContentValues(1)
        values.put(JtxContract.JtxICalObject.FLAGS, flags)
        return client.update(
            JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(account),
            values,
            "${JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID} = ? AND ${JtxContract.JtxICalObject.DIRTY} = ?",
            arrayOf(id.toString(), "0")
        )
    }

    /**
     * deletes all entries with the given flags
     * @param [flags] of the entries that should be deleted
     * @return the number of deleted records
     */
    fun deleteByFlags(flags: Int) =
        client.delete(JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(account), "${JtxContract.JtxICalObject.DIRTY} = ? AND ${JtxContract.JtxICalObject.FLAGS} = ? ", arrayOf("0", flags.toString()))

    /**
     * Updates the eTag value of all entries within a collection to the given eTag
     * @param [eTag] to be set (or null)
     */
    fun updateSetETag(eTag: String?) {
        val values = ContentValues(1)
        if(eTag == null)
            values.putNull(JtxContract.JtxICalObject.ETAG)
        else
            values.put(JtxContract.JtxICalObject.ETAG, eTag)
        client.update(JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(account), values, "${JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID} = ?", arrayOf(id.toString()))
    }


    /**
     * @param prodId    `PRODID` that identifies the app
     *
     * @return a string with all JtxICalObjects within the collection as iCalendar
     */
    fun getICSForCollection(prodId: ProdId): String {
        client.query(
            JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(account),
            null,
            "${JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID} = ? AND ${JtxContract.JtxICalObject.DELETED} = ? AND ${JtxContract.JtxICalObject.RECURID} IS NULL",
            arrayOf(id.toString(), "0"),
            null
        ).use { cursor ->
            logger.fine("getICSForCollection: found ${cursor?.count} records in ${account.name}")

            val ical = Calendar()
            ical += ImmutableVersion.VERSION_2_0
            ical += prodId

            while (cursor?.moveToNext() == true) {
                val jtxIcalObject = JtxICalObject(this)
                jtxIcalObject.populateFromContentValues(cursor.toContentValues())
                val singleICS = jtxIcalObject.getICalendarFormat(prodId)
                singleICS?.componentList?.all?.forEach { component ->
                    if(component is VToDo || component is VJournal)
                        ical += component
                }
            }
            return ical.toString()
        }
    }

    /**
     * Updates the last sync datetime for all collections of an account
     */
    fun updateLastSync() {
        val values = ContentValues(1)
        values.put(JtxContract.JtxCollection.LAST_SYNC, System.currentTimeMillis())
        client.update(JtxContract.JtxCollection.CONTENT_URI.asSyncAdapter(account), values, "${JtxContract.JtxCollection.ID} = ?", arrayOf(id.toString()))
    }
}