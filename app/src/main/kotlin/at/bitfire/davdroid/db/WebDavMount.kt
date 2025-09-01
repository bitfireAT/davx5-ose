/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import at.bitfire.davdroid.util.SensitiveString
import okhttp3.HttpUrl

@Entity(tableName = "webdav_mount")
data class WebDavMount(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** display name of the WebDAV mount */
    val name: String,

    /** URL of the WebDAV service, including trailing slash */
    val url: HttpUrl,

    // credentials
    val username: String?,
    val password: SensitiveString?,
    var certificateAlias: String?
)