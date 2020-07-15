package at.bitfire.davdroid.syncadapter

import android.net.Uri
import at.bitfire.davdroid.resource.LocalResource

class LocalTestResource: LocalResource<Any> {

    override val id: Long? = null
    override var fileName: String? = null
    override var eTag: String? = null
    override var scheduleTag: String? = null
    override var flags: Int = 0

    var deleted = false
    var dirty = false

    override fun prepareForUpload() = "generated-file.txt"

    override fun clearDirty(fileName: String?, eTag: String?, scheduleTag: String?) {
        dirty = false
        if (fileName != null)
            this.fileName = fileName
        this.eTag = eTag
        this.scheduleTag = scheduleTag
    }

    override fun updateFlags(flags: Int) {
        this.flags = flags
    }

    override fun add(): Uri {
        TODO("Not yet implemented")
    }

    override fun update(data: Any): Uri {
        TODO("Not yet implemented")
    }

    override fun delete(): Int {
        TODO("Not yet implemented")
    }

}