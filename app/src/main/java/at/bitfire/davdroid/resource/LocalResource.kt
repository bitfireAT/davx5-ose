/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.resource

import android.net.Uri

interface LocalResource<in TData: Any> {

    companion object {
        /**
         * Resource is present on remote server. This flag is used to identify resources
         * which are not present on the remote server anymore and can be deleted at the end
         * of the synchronization.
         */
        const val FLAG_REMOTELY_PRESENT = 1
    }


    /**
     * Unique ID which identifies the resource in the local storage. May be null if the
     * resource has not been saved yet.
     */
    val id: Long?

    /**
     * Remote file name for the resource, for instance `mycontact.vcf`.
     */
    val fileName: String?
    var eTag: String?
    var scheduleTag: String?
    val flags: Int

    /**
     * Generates a new UID and file name and assigns them to this resource. Typically used
     * before uploading a resource which has just been created locally.
     */
    fun assignNameAndUID()

    /**
     * Unsets the /dirty/ field of the resource. Typically used after successfully uploading a
     * locally modified resource.
     *
     * @param eTag ETag of the uploaded resource as returned by the server (null if the server didn't return one)
     * @param scheduleTag CalDAV Schedule-Tag of the uploaded resource as returned by the server (null if not applicable or if the server didn't return one)
     */
    fun clearDirty(eTag: String?, scheduleTag: String? = null)

    /**
     * Sets (local) flags of the resource. At the moment, the only allowed values are
     * 0 and [FLAG_REMOTELY_PRESENT].
     */
    fun updateFlags(flags: Int)


    /**
     * Adds the data object to the content provider and ensures that the dirty flag is clear.
     * @return content URI of the created row (e.g. event URI)
     */
    fun add(): Uri

    /**
     * Updates the data object in the content provider and ensures that the dirty flag is clear.
     * @return content URI of the updated row (e.g. event URI)
     */
    fun update(data: TData): Uri

    /**
     * Deletes the data object from the content provider.
     * @return number of affected rows
     */
    fun delete(): Int

}