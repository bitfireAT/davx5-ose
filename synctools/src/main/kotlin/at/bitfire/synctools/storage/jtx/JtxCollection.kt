/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.jtx

import android.content.ContentUris
import android.content.ContentValues
import android.content.Entity
import android.net.Uri
import android.os.RemoteException
import at.bitfire.synctools.storage.BatchOperation.CpoBuilder
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.storage.toContentValues
import at.techbee.jtx.JtxContract
import at.techbee.jtx.JtxContract.asSyncAdapter
import org.jetbrains.annotations.TestOnly

/**
 * Represents a locally stored jtx collection (journals, notes, tasks). Communicates with
 * the jtx Board content provider via [provider].
 *
 * Methods that use [ContentValues] operate directly on rows of the [JtxContract.JtxICalObject] table.
 * Methods that use [Entity] operate on [JtxContract.JtxICalObject] rows together with
 * associated sub-rows (attendees, categories, alarms, etc.).
 *
 * @param provider  jtx collection provider
 * @param values    content values as read from the jtx Board provider; [JtxContract.JtxCollection.ID] must be set
 *
 * @throws IllegalArgumentException when [JtxContract.JtxCollection.ID] is not set
 */
class JtxCollection(
    val provider: JtxCollectionProvider,
    val values: ContentValues
) {

    /** see [JtxContract.JtxCollection.ID] */
    val id: Long = values.getAsLong(JtxContract.JtxCollection.ID)
        ?: throw IllegalArgumentException("${JtxContract.JtxCollection.ID} must be available")


    // data fields

    /** see [JtxContract.JtxCollection.URL] */
    val url: String?
        get() = values.getAsString(JtxContract.JtxCollection.URL)

    /** see [JtxContract.JtxCollection.DISPLAYNAME] */
    val displayName: String?
        get() = values.getAsString(JtxContract.JtxCollection.DISPLAYNAME)

    /** see [JtxContract.JtxCollection.DESCRIPTION] */
    val description: String?
        get() = values.getAsString(JtxContract.JtxCollection.DESCRIPTION)

    /** see [JtxContract.JtxCollection.COLOR] */
    val color: Int?
        get() = values.getAsInteger(JtxContract.JtxCollection.COLOR)

    /** see [JtxContract.JtxCollection.SUPPORTSVEVENT] */
    val supportsVEvent: Boolean
        get() = values.getAsString(JtxContract.JtxCollection.SUPPORTSVEVENT) == "1"
                || values.getAsString(JtxContract.JtxCollection.SUPPORTSVEVENT) == "true"

    /** see [JtxContract.JtxCollection.SUPPORTSVTODO] */
    val supportsVTodo: Boolean
        get() = values.getAsString(JtxContract.JtxCollection.SUPPORTSVTODO) == "1"
                || values.getAsString(JtxContract.JtxCollection.SUPPORTSVTODO) == "true"

    /** see [JtxContract.JtxCollection.SUPPORTSVJOURNAL] */
    val supportsVJournal: Boolean
        get() = values.getAsString(JtxContract.JtxCollection.SUPPORTSVJOURNAL) == "1"
                || values.getAsString(JtxContract.JtxCollection.SUPPORTSVJOURNAL) == "true"

    /** see [JtxContract.JtxCollection.SYNC_ID] */
    val syncId: Long?
        get() = values.getAsLong(JtxContract.JtxCollection.SYNC_ID)

    /** see [JtxContract.JtxCollection.READONLY] */
    val readonly: Boolean
        get() = values.getAsString(JtxContract.JtxCollection.READONLY) == "1"
                || values.getAsString(JtxContract.JtxCollection.READONLY) == "true"


    // CRUD jtx collection objects (representing vtodo, vjournal)

    /**
     * Inserts a new jtx object into this collection.
     *
     * @param entity    object to insert
     *
     * @return ID of the newly inserted object
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun addJtxObject(entity: Entity): Long {
        try {
            val batch = JtxBatchOperation(client)
            addJtxObject(entity, batch)
            batch.commit()

            val uri = batch.getResult(0)?.uri ?: throw LocalStorageException("Content provider returned null on insert")
            return ContentUris.parseId(uri)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't insert jtx object", e)
        }
    }

    fun addJtxObject(entity: Entity, batch: JtxBatchOperation) {
        // insert jtx object row
        val objectRowIdx = batch.nextBackrefIdx()
        batch += CpoBuilder.newInsert(jtxObjectsUri).withValues(entity.entityValues)

        // insert data rows (with reference to jtx object ID)
        for (subValue in entity.subValues)
            batch += CpoBuilder.newInsert(subValue.uri.asSyncAdapter(account))
                .withValues(subValue.values)
                .withValueBackReference(ICALOBJECT_ID, objectRowIdx)
    }

    /**
     * Counts jtx objects in this collection that match the given selection criteria.
     *
     * @param where     selection
     * @param whereArgs arguments for selection
     *
     * @return number of matching objects
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun countJtxObjects(where: String?, whereArgs: Array<String>?): Int {
        try {
            val (protectedWhere, protectedWhereArgs) = whereWithCollectionId(where, whereArgs)
            client.query(jtxObjectsUri, arrayOf(JtxContract.JtxICalObject.ID),
                protectedWhere, protectedWhereArgs, null)?.use { cursor ->
                return cursor.count
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't count jtx objects", e)
        }
        return 0
    }

    /**
     * Gets the first object from this jtx collection that matches the given query.
     *
     * Adds a WHERE clause that restricts the query to [JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID] = [id].
     *
     * @param where     selection
     * @param whereArgs arguments for selection
     * @param sortOrder sort order
     *
     * @return first jtx object entity from this collection that matches the selection, or `null` if none found
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun findJtxObject(where: String?, whereArgs: Array<String>?, sortOrder: String? = null): Entity? {
        try {
            val (protectedWhere, protectedWhereArgs) = whereWithCollectionId(where, whereArgs)
            client.query(jtxObjectsUri, null, protectedWhere, protectedWhereArgs, sortOrder)?.use { cursor ->
                if (cursor.moveToNext())
                    return readEntity(cursor.toContentValues())
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query jtx objects", e)
        }
        return null
    }

    /**
     * Gets the first jtx object row (without sub-rows) that matches the given query.
     *
     * Adds a WHERE clause that restricts the query to [JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID] = [id].
     *
     * @param projection    requested fields
     * @param where         selection
     * @param whereArgs     arguments for selection
     *
     * @return first matching jtx object row as [ContentValues], or `null` if none found
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun findJtxObjectRow(projection: Array<String>?, where: String?, whereArgs: Array<String>?): ContentValues? {
        try {
            val (protectedWhere, protectedWhereArgs) = whereWithCollectionId(where, whereArgs)
            client.query(jtxObjectsUri, projection, protectedWhere, protectedWhereArgs, null)?.use { cursor ->
                if (cursor.moveToNext())
                    return cursor.toContentValues()
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query jtx object rows", e)
        }
        return null
    }

    /**
     * Gets a specific jtx object, identified by its ID, from this collection.
     *
     * @param id    jtx object ID
     *
     * @return object entity (or `null` if not found)
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun getJtxObject(id: Long): Entity? {
        try {
            client.query(jtxObjectUri(id), null, null, null, null)?.use { cursor ->
                if (cursor.moveToNext())
                    return readEntity(cursor.toContentValues())
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query jtx object entity", e)
        }
        return null
    }

    /**
     * Gets the main row of a specific jtx object, identified by its ID, from this collection.
     *
     * @param id            jtx object ID
     * @param projection    requested fields
     * @param where         optional additional selection
     * @param whereArgs     arguments for additional selection
     *
     * @return jtx object main row (or `null` if not found)
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun getJtxObjectRow(id: Long, projection: Array<String>? = null, where: String? = null, whereArgs: Array<String>? = null): ContentValues? {
        try {
            client.query(jtxObjectUri(id), projection, where, whereArgs, null)?.use { cursor ->
                if (cursor.moveToNext())
                    return cursor.toContentValues()
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query jtx object row", e)
        }
        return null
    }

    /**
     * Iterates jtx object rows (without sub-rows) from this collection.
     *
     * Adds a WHERE clause that restricts the query to [JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID] = [id].
     *
     * @param projection    requested fields
     * @param where         selection
     * @param whereArgs     arguments for selection
     * @param body          callback that is called for each main row
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun iterateJtxObjectRows(projection: Array<String>?, where: String?, whereArgs: Array<String>?, body: (ContentValues) -> Unit) {
        try {
            val (protectedWhere, protectedWhereArgs) = whereWithCollectionId(where, whereArgs)
            client.query(jtxObjectsUri, projection, protectedWhere, protectedWhereArgs, null)?.use { cursor ->
                while (cursor.moveToNext())
                    body(cursor.toContentValues())
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't iterate jtx object rows", e)
        }
    }

    /**
     * Iterates jtx objects (with sub-rows) from this collection.
     *
     * Adds a WHERE clause that restricts the query to [JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID] = [id].
     *
     * @param where         selection
     * @param whereArgs     arguments for selection
     * @param body          callback that is called for each jtx object entity
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun iterateJtxObjects(where: String?, whereArgs: Array<String>?, body: (Entity) -> Unit) {
        try {
            val (protectedWhere, protectedWhereArgs) = whereWithCollectionId(where, whereArgs)
            client.query(jtxObjectsUri, null, protectedWhere, protectedWhereArgs, null)?.use { cursor ->
                while (cursor.moveToNext())
                    body(readEntity(cursor.toContentValues()))
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't iterate jtx objects", e)
        }
    }

    /**
     * Updates a specific jtx object's main row with the given values. Doesn't influence sub-rows.
     *
     * This method always uses the update method of the content provider and does not
     * re-create rows, as it is required for some operations (see [updateJtxObject] for more
     * information).
     *
     * @param id        jtx object ID
     * @param values    new values
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun updateJtxObjectRow(id: Long, values: ContentValues) {
        try {
            client.update(jtxObjectUri(id), values, null, null)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't update jtx object row $id", e)
        }
    }

    /**
     * Updates a specific jtx object's main row with the given values. Doesn't influence sub-rows.
     *
     * This method always uses the update method of the content provider and does not
     * re-create rows, as it is required for some operations (see [updateJtxObject] for more
     * information).
     *
     * @param id        jtx object ID
     * @param values    new values
     * @param batch     batch operation in which the update is enqueued
     */
    fun updateJtxObjectRow(id: Long, values: ContentValues, batch: JtxBatchOperation) {
        batch += CpoBuilder.newUpdate(jtxObjectUri(id))
            .withValues(values)
    }

    /**
     * Updates a jtx object's main row and refreshes all sub-rows.
     *
     * Sub-rows are always deleted and re-created from [entity].
     *
     * @param id        ID of the jtx object to update
     * @param entity    new values of the jtx object
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun updateJtxObject(id: Long, entity: Entity) {
        try {
            val batch = JtxBatchOperation(client)
            updateJtxObject(id, entity, batch)
            batch.commit()
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't update jtx object $id", e)
        }
    }

    /**
     * Enqueues an update of a jtx object's main row and refreshes all sub-rows into [batch].
     *
     * Sub-rows are always deleted and re-created from [entity].
     *
     * @param id        ID of the jtx object to update
     * @param entity    new values of the jtx object
     * @param batch     batch operation in which the update is enqueued
     */
    fun updateJtxObject(id: Long, entity: Entity, batch: JtxBatchOperation) {
        // delete existing sub-rows
        deleteDataRows(id, batch)

        // update main row
        val newValues = ContentValues(entity.entityValues).apply {
            remove(JtxContract.JtxICalObject.ID)
        }
        batch += CpoBuilder.newUpdate(jtxObjectUri(id))
            .withValues(newValues)

        // insert new sub-rows
        for (subValue in entity.subValues)
            batch += CpoBuilder.newInsert(subValue.uri.asSyncAdapter(account))
                .withValues(ContentValues(subValue.values).apply {
                    put(ICALOBJECT_ID, id) // always keep reference to main row ID
                })
    }

    /**
     * Enqueues deletions of all known sub-rows for the given jtx object into [batch].
     *
     * @param objectId    ID of the jtx object whose sub-rows should be deleted
     * @param batch     batch operation in which the deletions are enqueued
     */
    private fun deleteDataRows(objectId: Long, batch: JtxBatchOperation) {
        val idStr = objectId.toString()
        for (subUri in SUB_VALUE_URIS)
            batch += CpoBuilder
                .newDelete(subUri.asSyncAdapter(account))
                .withSelection("$ICALOBJECT_ID=?", arrayOf(idStr))
    }

    /**
     * Updates jtx object rows in this collection.
     *
     * Adds a WHERE clause that restricts the query to [JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID] = [id].
     *
     * @param values    values to update
     * @param where     selection
     * @param whereArgs arguments for selection
     *
     * @return number of updated rows
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun updateJtxObjectRows(values: ContentValues, where: String?, whereArgs: Array<String>?): Int =
        try {
            val (protectedWhere, protectedWhereArgs) = whereWithCollectionId(where, whereArgs)
            client.update(jtxObjectsUri, values, protectedWhere, protectedWhereArgs)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't update jtx objects", e)
        }

    /**
     * Deletes all jtx objects of this collection from the local storage.
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    @TestOnly
    fun deleteAllJtxObjects() {
        try {
            val (protectedWhere, protectedWhereArgs) = whereWithCollectionId(null, null)
            client.delete(jtxObjectsUri, protectedWhere, protectedWhereArgs)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't delete all jtx objects from collection", e)
        }
    }

    /**
     * Deletes a jtx object row. The jtx Board provider automatically deletes associated sub-rows.
     *
     * @param id    ID of the jtx object
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun deleteJtxObject(id: Long) {
        try {
            client.delete(jtxObjectUri(id), null, null)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't delete jtx object $id", e)
        }
    }

    internal fun deleteJtxObject(id: Long, batch: JtxBatchOperation) {
        batch += CpoBuilder.newDelete(jtxObjectUri(id))
    }


    // shortcuts to upper level

    /**
     * Deletes this collection from the jtx Board content provider.
     *
     * @return number of deleted rows
     * @throws LocalStorageException when the content provider returns an error
     */
    fun delete(): Int =
        provider.deleteCollection(id)

    /**
     * Updates this collection in the jtx Board content provider.
     *
     * @param values    values to update
     * @return number of updated rows
     * @throws LocalStorageException when the content provider returns an error
     */
    fun update(values: ContentValues): Int =
        provider.updateCollection(id, values)


    // helpers

    val account
        get() = provider.account

    val client
        get() = provider.client

    val jtxObjectsUri
        get() = JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(account)

    fun jtxObjectUri(id: Long): Uri =
        ContentUris.withAppendedId(jtxObjectsUri, id)

    /**
     * Restricts a given selection/where clause to this collection ID.
     *
     * @param where     selection
     * @param whereArgs arguments for selection
     *
     * @return restricted selection and arguments
     */
    private fun whereWithCollectionId(where: String?, whereArgs: Array<String>?): Pair<String, Array<String>> {
        val protectedWhere = "(${where ?: "1"}) AND ${JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID}=?"
        val protectedWhereArgs = (whereArgs ?: arrayOf()) + id.toString()
        return Pair(protectedWhere, protectedWhereArgs)
    }

    /**
     * Builds an [Entity] from a main jtx object row by also querying all known sub-tables.
     *
     * @param mainValues    content values of the main [JtxContract.JtxICalObject] row
     *
     * @return entity combining the main row and all associated sub-rows
     */
    private fun readEntity(mainValues: ContentValues): Entity {
        val entity = Entity(mainValues)
        val objectId = mainValues.getAsLong(JtxContract.JtxICalObject.ID) ?: return entity

        for (subUri in SUB_VALUE_URIS) {
            try {
                client.query(
                    subUri.asSyncAdapter(account),
                    null, "$ICALOBJECT_ID=?", arrayOf(objectId.toString()), null
                )?.use { cursor ->
                    while (cursor.moveToNext())
                        entity.addSubValue(subUri, cursor.toContentValues())
                }
            } catch (e: RemoteException) {
                throw LocalStorageException("Couldn't query jtx sub-rows from $subUri", e)
            }
        }

        return entity
    }

    companion object {

        /**
         * Column name used in all jtx Board sub-tables to reference the parent [JtxContract.JtxICalObject] row.
         *
         * All sub-table contract objects define `ICALOBJECT_ID` with this same value,
         * so we can use the value of [JtxContract.JtxAttendee].
         */
        private const val ICALOBJECT_ID = JtxContract.JtxAttendee.ICALOBJECT_ID

        /**
         * Content URIs of all supported jtx Board sub-tables.
         *
         * Used to read sub-rows into [Entity] objects and to delete sub-rows on jtx object update.
         */
        private val SUB_VALUE_URIS: List<Uri>
            get() = listOf(
                JtxContract.JtxAttendee.CONTENT_URI,
                JtxContract.JtxCategory.CONTENT_URI,
                JtxContract.JtxComment.CONTENT_URI,
                JtxContract.JtxOrganizer.CONTENT_URI,
                JtxContract.JtxRelatedto.CONTENT_URI,
                JtxContract.JtxResource.CONTENT_URI,
                JtxContract.JtxAttachment.CONTENT_URI,
                JtxContract.JtxAlarm.CONTENT_URI,
                JtxContract.JtxUnknown.CONTENT_URI
            )

    }

}
