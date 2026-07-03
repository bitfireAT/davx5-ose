/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.content.Context
import android.net.Uri
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.jtx.JtxCollection
import at.bitfire.synctools.storage.jtx.JtxEntityAndExceptions
import at.bitfire.synctools.storage.jtx.JtxObjectAndExceptions
import at.bitfire.synctools.storage.jtx.JtxRecurringCollection
import at.techbee.jtx.JtxContract
import com.google.common.base.MoreObjects
import org.apache.commons.lang3.StringUtils
import java.util.Optional

class LocalJtxObject(
    val recurringCollection: JtxRecurringCollection,
    val jtxObjectAndExceptions: JtxObjectAndExceptions
) : LocalResource {

    val collection: JtxCollection
        get() = recurringCollection.collection

    private val mainValues = jtxObjectAndExceptions.main.entityValues

    override val id: Long
        get() = mainValues.getAsLong(JtxContract.JtxICalObject.ID)

    override val fileName: String?
        get() = mainValues.getAsString(JtxContract.JtxICalObject.FILENAME)

    override val eTag: String?
        get() = mainValues.getAsString(JtxContract.JtxICalObject.ETAG)

    override val scheduleTag: String?
        get() = mainValues.getAsString(JtxContract.JtxICalObject.SCHEDULETAG)

    override val flags: Int
        get() = mainValues.getAsInteger(JtxContract.JtxICalObject.FLAGS) ?: 0

    suspend fun update(data: JtxEntityAndExceptions) {
        recurringCollection.updateJtxObjectAndExceptions(id, data)
    }

    override fun clearDirty(fileName: Optional<String>, eTag: String?, scheduleTag: String?) {
        val values = contentValuesOf(
            JtxContract.JtxICalObject.DIRTY to 0,
            JtxContract.JtxICalObject.ETAG to eTag,
            JtxContract.JtxICalObject.SCHEDULETAG to scheduleTag
        )

        if (fileName.isPresent) {
            values.put(JtxContract.JtxICalObject.FILENAME, fileName.get())
        }

        collection.updateJtxObjectRow(id, values)
    }

    override fun updateFlags(flags: Int) {
        val values = contentValuesOf(
            JtxContract.JtxICalObject.FLAGS to flags,
        )

        collection.updateJtxObjectRow(id, values)
    }

    override fun updateUid(uid: String) {
        val values = contentValuesOf(
            JtxContract.JtxICalObject.UID to uid,
        )

        collection.updateJtxObjectRow(id, values)
    }

    override fun updateSequence(sequence: Int) {
        val values = contentValuesOf(
            JtxContract.JtxICalObject.SEQUENCE to sequence,
        )

        collection.updateJtxObjectRow(id, values)
    }

    override fun deleteLocal() {
        recurringCollection.deleteJtxObjectAndExceptions(id)
    }

    override fun resetDeleted() {
        val values = contentValuesOf(
            JtxContract.JtxICalObject.DELETED to 0,
        )

        collection.updateJtxObjectRow(id, values)
    }

    override fun getDebugSummary(): String {
        val jtxObjectString = try {
            // only include truncated main jtx object row (won't contain attachments, unknown properties etc.)
            StringUtils.abbreviate(mainValues.toString(), 1000)
        } catch (e: Exception) {
            e
        }

        val exceptionsString = try {
            jtxObjectAndExceptions.exceptions.take(10).joinToString { exception ->
                // truncated exception row
                StringUtils.abbreviate(exception.entityValues.toString(), 1000)
            }
        } catch (e: Exception) {
            e
        }

        return MoreObjects.toStringHelper(this)
            .add("id", id)
            .add("fileName", fileName)
            .add("eTag", eTag)
            .add("scheduleTag", scheduleTag)
            .add("flags", flags)
            .add("jtxObject", jtxObjectString)
            .add("exceptions [max 10]", exceptionsString)
            .toString()
    }

    override fun getViewUri(context: Context): Uri {
        return JtxContract.JtxICalObject.getViewIntentUriFor(id)
    }
}
