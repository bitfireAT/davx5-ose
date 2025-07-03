/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import at.bitfire.davdroid.resource.LocalResource.Companion.FLAG_REMOTELY_PRESENT


interface LocalResource<DataObjectType> {

    /**
     * Unique ID which identifies the resource in the local storage. May be null if the
     * resource has not been saved yet.
     */
    val id: Long?

    /**
     * Remote file name for the resource, for instance `mycontact.vcf`. Also used to determine whether
     * a dirty record has just been created (in this case, [fileName] is *null*) or modified
     * (in this case, [fileName] is the remote file name).
     */
    val fileName: String?

    /** remote ETag for the resource */
    var eTag: String?

    /** remote Schedule-Tag for the resource */
    var scheduleTag: String?

    /** bitfield of flags; currently either [FLAG_REMOTELY_PRESENT] or 0 */
    val flags: Int


    /**
     * Unsets the /dirty/ field of the resource. Typically used after successfully uploading a
     * locally modified resource.
     *
     * @param fileName If this argument is not *null*, [LocalResource.fileName] will be set to its value.
     * @param eTag ETag of the uploaded resource as returned by the server (null if the server didn't return one)
     * @param scheduleTag CalDAV Schedule-Tag of the uploaded resource as returned by the server (null if not applicable or if the server didn't return one)
     */
    fun clearDirty(fileName: String?, eTag: String?, scheduleTag: String? = null)

    /**
     * Deletes the local resource from the content provider.
     *
     * @return number of affected rows
     */
    fun delete(): Int

    /**
     * Prepares the resource for uploading:
     *
     *   1. If the resource doesn't have an UID yet, this method generates one and writes it to the content provider.
     *   2. The new file name which can be used for the upload is derived from the UID and returned, but not
     *   saved to the content provider. The sync manager is responsible for saving the file name that
     *   was actually used.
     *
     * @return new file name of the resource (like "<uid>.vcf")
     */
    fun prepareForUpload(): String

    /**
     * Undoes deletion of the data object from the content provider.
     */
    fun resetDeleted()

    /**
     * Updates the data object in the content provider and ensures that the dirty flag is clear.
     */
    fun updateFromDataObject(data: DataObjectType, eTag: String?, scheduleTag: String?)

    /**
     * Sets (local) flags of the resource. At the moment, the only allowed values are
     * 0 and [FLAG_REMOTELY_PRESENT].
     */
    fun updateFlags(flags: Int)


    companion object {
        /**
         * Resource is present on remote server. This flag is used to identify resources
         * which are not present on the remote server anymore and can be deleted at the end
         * of the synchronization.
         */
        const val FLAG_REMOTELY_PRESENT = 1
    }

}