/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.operation

import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.WebDavDocument
import java.io.FileNotFoundException
import java.util.logging.Logger
import javax.inject.Inject

class IsChildDocumentOperation @Inject constructor(
    db: AppDatabase,
    private val logger: Logger
) {

    private val documentDao = db.webDavDocumentDao()

    operator fun invoke(parentDocumentId: String, documentId: String): Boolean {
        logger.fine("WebDAV isChildDocument $parentDocumentId $documentId")
        val parent = documentDao.get(parentDocumentId.toLong()) ?: throw FileNotFoundException()

        var iter: WebDavDocument? = documentDao.get(documentId.toLong()) ?: throw FileNotFoundException()
        while (iter != null) {
            val currentParentId = iter.parentId
            if (currentParentId == parent.id)
                return true

            iter = if (currentParentId != null)
                documentDao.get(currentParentId)
            else
                null
        }
        return false
    }

}