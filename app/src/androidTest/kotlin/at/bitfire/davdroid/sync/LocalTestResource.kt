/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import at.bitfire.davdroid.resource.LocalResource
import java.util.Optional

class LocalTestResource: LocalResource<Any> {

    override val id: Long? = null
    override var fileName: String? = null
    override var eTag: String? = null
    override var scheduleTag: String? = null
    override var flags: Int = 0

    var deleted = false
    var dirty = false

    override fun prepareForUpload() = "generated-file.txt"

    override fun clearDirty(fileName: Optional<String>, eTag: String?, scheduleTag: String?) {
        dirty = false
        if (fileName.isPresent)
            this.fileName = fileName.get()
        this.eTag = eTag
        this.scheduleTag = scheduleTag
    }

    override fun updateFlags(flags: Int) {
        this.flags = flags
    }

    override fun update(data: Any, fileName: String?, eTag: String?, scheduleTag: String?, flags: Int) = throw NotImplementedError()
    override fun deleteLocal() = throw NotImplementedError()
    override fun resetDeleted() = throw NotImplementedError()

}