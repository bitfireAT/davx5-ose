/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.db

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface WebDavDocumentDao {

    @Query("SELECT * FROM webdav_document WHERE id=:id")
    fun get(id: Long): WebDavDocument?

    @Query("SELECT * FROM webdav_document WHERE mountId=:mountId AND (parentId=:parentId OR (parentId IS NULL AND :parentId IS NULL)) AND name=:name")
    fun getByParentAndName(mountId: Long, parentId: Long?, name: String): WebDavDocument?

    @Query("SELECT * FROM webdav_document WHERE parentId=:parentId")
    fun getChildren(parentId: Long): List<WebDavDocument>

    @Query("SELECT * FROM webdav_document WHERE parentId IS NULL")
    fun getRootsLive(): LiveData<List<WebDavDocument>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrReplace(document: WebDavDocument): Long

    @Query("DELETE FROM webdav_document WHERE parentId=:parentId")
    fun removeChildren(parentId: Long)

    @Insert
    fun insert(document: WebDavDocument): Long

    @Update
    fun update(document: WebDavDocument)
    
    @Delete
    fun delete(document: WebDavDocument)


    // complex operations

    /**
     * Tries to insert new row, but updates existing row if already present.
     * This method preserves the primary key, as opposed to using "@Insert(onConflict = OnConflictStrategy.REPLACE)"
     * which will create a new row with incremented ID and thus breaks entity relationships!
     *
     * @return ID of the row, that has been inserted or updated. -1 If the insert fails due to other reasons.
     */
    @Transaction
    fun insertOrUpdate(document: WebDavDocument): Long {
        val parentId = document.parentId
            ?: return insert(document)
        val existingDocument = getByParentAndName(document.mountId, parentId, document.name)
            ?: return insert(document)
        update(document.copy(id = existingDocument.id))
        return existingDocument.id
    }

    @Transaction
    fun getOrCreateRoot(mount: WebDavMount): WebDavDocument {
        getByParentAndName(mount.id, null, "")?.let { existing ->
            return existing
        }

        val newDoc = WebDavDocument(
            mountId = mount.id,
            parentId = null,
            name = "",
            isDirectory = true,
            displayName = mount.name
        )
        val id = insertOrReplace(newDoc)
        newDoc.id = id
        return newDoc
    }

}