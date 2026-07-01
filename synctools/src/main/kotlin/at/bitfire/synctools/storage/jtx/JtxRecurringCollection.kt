/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.jtx

import android.content.ContentValues
import android.content.Entity
import android.os.RemoteException
import androidx.annotation.VisibleForTesting
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.storage.containsNotNull
import at.techbee.jtx.JtxContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Adds support for [JtxObjectAndExceptions] data objects to [JtxCollection].
 *
 * In the jtx Board content provider, recurring exceptions are linked to their main object
 * exclusively by sharing the same [JtxContract.JtxICalObject.UID]. Exceptions are identified by
 * a non-null [JtxContract.JtxICalObject.RECURID].
 *
 * [JtxContract.JtxICalObject.RECUR_ORIGINALICALOBJECTID] is intentionally never used here.
 */
class JtxRecurringCollection(
    val collection: JtxCollection
) {

    val logger: Logger
        get() = Logger.getLogger(javaClass.name)

    /**
     * Inserts a jtx object and all its exceptions. Input data is first cleaned up using [cleanUp].
     *
     * @param jtxEntityAndExceptions object and exceptions to insert
     *
     * @return ID of the resulting main jtx object
     */
    fun addJtxEntityAndExceptions(jtxEntityAndExceptions: JtxEntityAndExceptions): Long {
        try {
            // validate / clean up input
            val cleaned = cleanUp(jtxEntityAndExceptions)

            return collection.addJtxObjects(listOf(cleaned.main) + cleaned.exceptions)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't insert jtx object/exceptions", e)
        }
    }

    /**
     * Find first main jtx object (not an exception, i.e. [JtxContract.JtxICalObject.RECURID] IS NULL)
     * that matches the given query, together with all its exceptions.
     *
     * @param where         selection (applied to main objects only; [JtxContract.JtxICalObject.RECURID] IS NULL is added automatically)
     * @param whereArgs     arguments for selection
     */
    suspend fun findJtxObjectAndExceptions(where: String?, whereArgs: Array<String>?): JtxObjectAndExceptions? {
        val mainWhere = mainJtxObjectOnlyWhere(where)

        // attach exceptions
        val main = collection.findJtxObject(mainWhere, whereArgs) ?: return null
        val uid = main.entityValues.getAsString(JtxContract.JtxICalObject.UID)
        return JtxObjectAndExceptions(
            main = main,
            exceptions = if (uid != null) findExceptionsByUid(uid) else emptyList()
        )
    }

    /**
     * Retrieves a jtx object and its exceptions from the content provider.
     * Exceptions are found by matching [JtxContract.JtxICalObject.UID] of the main object.
     *
     * Returns `null` if [mainId] refers to an exception row (i.e. [JtxContract.JtxICalObject.RECURID]
     * IS NOT NULL), because jtx exceptions are linked by UID and treating an exception as a main
     * would produce an invalid [JtxObjectAndExceptions] (the exception would also appear in its own
     * exceptions list).
     *
     * @param mainId    [JtxContract.JtxICalObject.ID] of the main jtx object
     *
     * @return jtx object and exceptions, or `null` if not found or [mainId] is an exception
     */
    suspend fun getById(mainId: Long): JtxObjectAndExceptions? {
        val main = collection.getJtxObject(mainId) ?: return null
        if (main.entityValues.getAsString(JtxContract.JtxICalObject.RECURID) != null) {
            logger.warning("getById called with exception ID $mainId (RECURID IS NOT NULL) – returning null")
            return null
        }
        val uid = main.entityValues.getAsString(JtxContract.JtxICalObject.UID)
        val exceptions = if (uid != null) findExceptionsByUid(uid) else emptyList()
        return JtxObjectAndExceptions(
            main = main,
            exceptions = exceptions
        )
    }

    /**
     * Cold [Flow] of main jtx objects together with their exceptions; the per-main exceptions
     * lookup stays a small bounded query (exceptions of a single object are not streamed).
     *
     * @param where         selection (applied to main objects only; [JtxContract.JtxICalObject.RECURID] IS NULL is added automatically)
     * @param whereArgs     arguments for selection
     */
    fun queryJtxObjectsAndExceptions(where: String?, whereArgs: Array<String>?): Flow<JtxObjectAndExceptions> {
        val mainWhere = mainJtxObjectOnlyWhere(where)
        return collection
            .queryJtxObjects(mainWhere, whereArgs)
            .map { main ->
                val uid = main.entityValues.getAsString(JtxContract.JtxICalObject.UID)
                JtxObjectAndExceptions(
                    main = main,
                    exceptions = if (uid != null) findExceptionsByUid(uid) else emptyList()
                )
            }
            .flowOn(Dispatchers.IO)
    }

    /**
     * Updates a jtx object and all its exceptions. Input data is first cleaned up using [cleanUp].
     * Old exceptions are deleted and replaced with the ones provided.
     *
     * @param id                        ID of the main jtx object row (must have [JtxContract.JtxICalObject.RECURID] IS NULL)
     * @param jtxEntityAndExceptions    new jtx object (including exceptions)
     *
     * @return main jtx object ID of the updated row (always equal to [id])
     *
     * @throws LocalStorageException when [id] refers to an exception row instead of a main object
     */
    suspend fun updateJtxObjectAndExceptions(id: Long, jtxEntityAndExceptions: JtxEntityAndExceptions): Long {
        try {
            // validate / clean up input
            val cleaned = cleanUp(jtxEntityAndExceptions)

            // get UID of the existing main object to find and delete its old exceptions (because
            // they may be invalid for the updated event); also enforce that id is a main object
            // (RECURID IS NULL) — jtx exceptions are linked by UID, so updating an exception as
            // if it were a main would corrupt the data
            val existingRow = collection.getJtxObjectRow(
                id,
                arrayOf(JtxContract.JtxICalObject.UID, JtxContract.JtxICalObject.RECURID),
                where = "${JtxContract.JtxICalObject.RECURID} IS NULL"
            )
            if (existingRow == null) {
                throw LocalStorageException("updateJtxObjectAndExceptions called with ID $id which does not exist or is an exception (RECURID IS NOT NULL)")
            }
            val existingUid = existingRow.getAsString(JtxContract.JtxICalObject.UID)

            // Delete old exceptions individually by item URI. The jtx Board provider blocks
            // bulk directory-level deletes of RECURID-bearing rows (adds "AND RECURID IS NULL"),
            // so a bulk delete would silently be a no-op here.
            // See: https://github.com/TechbeeAT/jtxBoard/blob/45a5f75693b2a50e55f2812bb5681016e50500d8/app/src/main/java/at/techbee/jtx/SyncContentProvider.kt#L197
            if (existingUid != null) {
                val batch = JtxBatchOperation(collection.client)

                for (oldException in findExceptionsByUid(existingUid)) {
                    collection.deleteJtxObject(
                        oldException.entityValues.getAsLong(JtxContract.JtxICalObject.ID),
                        batch
                    )
                }

                batch.commit()
            }

            // update main object
            collection.updateJtxObject(id, cleaned.main)

            // add updated exceptions
            collection.addJtxObjects(cleaned.exceptions)

            return id
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't update jtx object/exceptions", e)
        }
    }

    /**
     * Enqueues deletion of a jtx main object into [batch]. Possible exceptions are automatically
     * removed by the jtx Board content provider.
     *
     * @param id    ID of the main jtx object
     * @param batch batch to enqueue the deletion into
     */
    fun deleteJtxObjectAndExceptions(id: Long, batch: JtxBatchOperation) {
        try {
            // delete main object; the jtx Board provider's removeOrphans() will clean up
            // associated exceptions and auto-generated instances when the main is removed
            collection.deleteJtxObject(id, batch)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't delete jtx object $id", e)
        }
    }

    /**
     * Deletes a jtx main object and all its exceptions.
     *
     * @param id    ID of the main jtx object
     */
    fun deleteJtxObjectAndExceptions(id: Long) {
        val batch = JtxBatchOperation(collection.client)
        deleteJtxObjectAndExceptions(id, batch)
        try {
            batch.commit()
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't delete jtx object $id", e)
        }
    }


    // validation / cleanup logic

    /**
     * Prepares a jtx object and exceptions so that it can be inserted into the jtx Board provider:
     *
     * - If the main object is not recurring (no RRULE or RDATE) or has no [JtxContract.JtxICalObject.UID],
     *   exceptions are dropped.
     * - Cleans up the main object with [cleanMainObject].
     * - Cleans up exceptions with [cleanException].
     *
     * @param original  original object and exceptions
     *
     * @return object and exceptions that can actually be inserted
     */
    @VisibleForTesting
    internal fun cleanUp(original: JtxEntityAndExceptions): JtxEntityAndExceptions {
        val main = cleanMainObject(original.main)

        val mainValues = main.entity.entityValues
        val uid = mainValues.getAsString(JtxContract.JtxICalObject.UID)
        val recurring = mainValues.containsNotNull(JtxContract.JtxICalObject.RRULE)
                || mainValues.containsNotNull(JtxContract.JtxICalObject.RDATE)

        if (uid == null || !recurring) {
            // without a UID, exceptions cannot be associated to the main object in the jtx provider
            // and without recurrence fields, exceptions are meaningless
            if (original.exceptions.isNotEmpty())
                logger.log(Level.WARNING, "Dropping exceptions of jtx object because it is not recurring or UID is not set", main)

            return JtxEntityAndExceptions(main = main, exceptions = emptyList())
        }

        return JtxEntityAndExceptions(
            main = main,
            exceptions = original.exceptions.map { originalException ->
                cleanException(originalException, uid)
            }
        )
    }

    /**
     * Prepares a main jtx object for insertion by removing fields that a main must not have
     * ([JtxContract.JtxICalObject.RECURID] and [JtxContract.JtxICalObject.RECURID_TIMEZONE]).
     *
     * @param original  original jtx object entity
     *
     * @return cleaned entity that can actually be inserted as a main
     */
    @VisibleForTesting
    internal fun cleanMainObject(original: JtxEntity): JtxEntity {
        // make a copy (don't modify original entity / values)
        val values = ContentValues(original.entity.entityValues)

        // remove values that a main jtx object shouldn't have
        values.remove(JtxContract.JtxICalObject.RECURID)
        values.remove(JtxContract.JtxICalObject.RECURID_TIMEZONE)

        val result = Entity(values)
        for (subValue in original.entity.subValues)
            result.addSubValue(subValue.uri, subValue.values)

        return JtxEntity(
            entity = result,
            binaryDataRows = original.binaryDataRows
        )
    }

    /**
     * Prepares an exception for insertion into the jtx Board provider:
     *
     * - Removes recurrence rule fields that an exception must not have (`RRULE`, `RDATE`, `EXDATE`).
     *   EXRULE does not apply/unsupported.
     * - Ensures [JtxContract.JtxICalObject.UID] matches the main's UID so that the jtx Board
     *   provider can associate the exception with its main.
     *
     * @param original  original exception entity
     * @param uid       [JtxContract.JtxICalObject.UID] of the main object
     *
     * @return cleaned exception that can actually be inserted
     */
    @VisibleForTesting
    internal fun cleanException(original: JtxEntity, uid: String): JtxEntity {
        // make a copy (don't modify original entity / values)
        val values = ContentValues(original.entity.entityValues)

        // remove fields that an exception must not have
        values.remove(JtxContract.JtxICalObject.RRULE)
        values.remove(JtxContract.JtxICalObject.RDATE)
        values.remove(JtxContract.JtxICalObject.EXDATE)

        // ensure UID matches the main so the jtx provider can link them
        values.put(JtxContract.JtxICalObject.UID, uid)

        val result = Entity(values)
        for (subValue in original.entity.subValues)
            result.addSubValue(subValue.uri, subValue.values)

        return JtxEntity(
            entity = result,
            binaryDataRows = original.binaryDataRows
        )
    }


    // helpers for dirty/deleted exceptions

    /**
     * Iterates through all exceptions in [collection] that are marked as deleted.
     * For every found exception:
     *
     * - the [JtxContract.JtxICalObject.SEQUENCE] field of the main onject is increased by one,
     * - the main object is marked as dirty (so that it will be synced),
     * - and the exception is permanently removed (so that it won't appear during sync).
     *
     * The main is found by matching [JtxContract.JtxICalObject.UID] with
     * [JtxContract.JtxICalObject.RECURID] IS NULL.
     */
    fun processDeletedExceptions() {
        val batch = JtxBatchOperation(collection.client)

        // iterate through deleted exceptions
        collection.iterateJtxObjectRows(
            arrayOf(JtxContract.JtxICalObject.ID, JtxContract.JtxICalObject.UID),
            "${JtxContract.JtxICalObject.DELETED}=1 AND ${JtxContract.JtxICalObject.RECURID} IS NOT NULL", null
        ) { values ->
            val exceptionId = values.getAsLong(JtxContract.JtxICalObject.ID)!!
            val uid = values.getAsString(JtxContract.JtxICalObject.UID) ?: return@iterateJtxObjectRows
            logger.fine("Found deleted exception #$exceptionId, removing it and marking main jtx object (uid=$uid) as dirty")

            // find main object and its current SEQUENCE
            val mainValues = collection.findJtxObjectRow(
                arrayOf(JtxContract.JtxICalObject.ID, JtxContract.JtxICalObject.SEQUENCE),
                "${JtxContract.JtxICalObject.UID}=? AND ${JtxContract.JtxICalObject.RECURID} IS NULL",
                arrayOf(uid)
            ) ?: return@iterateJtxObjectRows

            val mainId = mainValues.getAsLong(JtxContract.JtxICalObject.ID)!!
            val mainSeq = mainValues.getAsInteger(JtxContract.JtxICalObject.SEQUENCE) ?: 0

            // increase SEQUENCE and mark main as dirty
            collection.updateJtxObjectRow(mainId, contentValuesOf(
                JtxContract.JtxICalObject.SEQUENCE to mainSeq + 1,
                JtxContract.JtxICalObject.DIRTY to 1
            ), batch)

            // permanently remove the deleted exception
            collection.deleteJtxObject(exceptionId, batch)
        }

        batch.commit()
    }

    /**
     * Iterates through all exceptions in [collection] that are marked as dirty (but not deleted).
     * For every found exception:
     *
     * - the [JtxContract.JtxICalObject.SEQUENCE] field of the exception is increased by one,
     * - the exception is marked as not dirty anymore,
     * - but the main is marked as dirty (so that it will be synced).
     *
     * The main is found by matching [JtxContract.JtxICalObject.UID] with
     * [JtxContract.JtxICalObject.RECURID] IS NULL.
     */
    fun processDirtyExceptions() {
        val batch = JtxBatchOperation(collection.client)

        collection.iterateJtxObjectRows(
            arrayOf(JtxContract.JtxICalObject.ID, JtxContract.JtxICalObject.UID, JtxContract.JtxICalObject.SEQUENCE),
            "${JtxContract.JtxICalObject.DIRTY}=1 AND ${JtxContract.JtxICalObject.DELETED}=0 AND ${JtxContract.JtxICalObject.RECURID} IS NOT NULL", null
        ) { values ->
            val exceptionId = values.getAsLong(JtxContract.JtxICalObject.ID)!!
            val uid = values.getAsString(JtxContract.JtxICalObject.UID) ?: return@iterateJtxObjectRows
            val exceptionSeq = values.getAsInteger(JtxContract.JtxICalObject.SEQUENCE) ?: 0
            logger.fine("Found dirty exception #$exceptionId, increasing SEQUENCE and marking main jtx object (uid=$uid) as dirty")

            // find main
            val mainValues = collection.findJtxObjectRow(
                arrayOf(JtxContract.JtxICalObject.ID),
                "${JtxContract.JtxICalObject.UID}=? AND ${JtxContract.JtxICalObject.RECURID} IS NULL",
                arrayOf(uid)
            ) ?: return@iterateJtxObjectRows

            val mainId = mainValues.getAsLong(JtxContract.JtxICalObject.ID)!!

            // mark main as dirty
            collection.updateJtxObjectRow(mainId, contentValuesOf(
                JtxContract.JtxICalObject.DIRTY to 1
            ), batch)

            // increase exception SEQUENCE and clear DIRTY
            collection.updateJtxObjectRow(exceptionId, contentValuesOf(
                JtxContract.JtxICalObject.SEQUENCE to exceptionSeq + 1,
                JtxContract.JtxICalObject.DIRTY to 0
            ), batch)
        }

        batch.commit()
    }


    /**
     * Finds a specific recurrence exception by UID and RECURRENCE-ID.
     *
     * @param uid       UID of the main object
     * @param recurid   RECURRENCE-ID of the exception
     *
     * @return exception row values, or null if not found
     */
    fun findExceptionRow(uid: String, recurid: String): ContentValues? =
        collection.findJtxObjectRow(
            null,
            "${JtxContract.JtxICalObject.UID}=? AND ${JtxContract.JtxICalObject.RECURID}=?",
            arrayOf(uid, recurid)
        )


    // private helpers

    /**
     * Finds all exceptions for a given main object UID from this collection.
     * Exceptions are identified by [JtxContract.JtxICalObject.RECURID] IS NOT NULL and
     * [JtxContract.JtxICalObject.SEQUENCE] > 0. The jtx Board content provider auto-generates
     * recurrence instances (SEQUENCE = 0) from the RRULE of recurring items; those are not iCal
     * exceptions and must not be treated as such. The provider sets SEQUENCE to at least 1 for any
     * sync-adapter-inserted exception.
     */
    private suspend fun findExceptionsByUid(uid: String): List<Entity> =
        collection.queryJtxObjects(
            "${JtxContract.JtxICalObject.UID}=? AND ${JtxContract.JtxICalObject.RECURID} IS NOT NULL AND ${JtxContract.JtxICalObject.SEQUENCE} > 0",
            arrayOf(uid)
        ).toList()

    /**
     * Adds [JtxContract.JtxICalObject.RECURID] IS NULL to [where] to restrict queries to main objects only.
     */
    private fun mainJtxObjectOnlyWhere(where: String?): String =
        if (where != null)
            "($where) AND ${JtxContract.JtxICalObject.RECURID} IS NULL"
        else
            "${JtxContract.JtxICalObject.RECURID} IS NULL"

}
