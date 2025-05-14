/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.RoomRawQuery
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface WebDavDocumentDao {

    @Query("SELECT * FROM webdav_document WHERE id=:id")
    fun get(id: Long): WebDavDocument?

    @Query("SELECT * FROM webdav_document WHERE mountId=:mountId AND (parentId=:parentId OR (parentId IS NULL AND :parentId IS NULL)) AND name=:name")
    fun getByParentAndName(mountId: Long, parentId: Long?, name: String): WebDavDocument?

    @RawQuery
    fun getChildren(query: RoomRawQuery): List<WebDavDocument>

    fun getChildren(parentId: Long, orderBy: String = "name ASC"): List<WebDavDocument> {
        val query = RoomRawQuery("SELECT * FROM webdav_document WHERE parentId = ? ORDER BY $orderBy") {
            it.bindLong(1, parentId)
        }
        return getChildren(query)
    }

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
        return newDoc.copy(id = id)
    }

}