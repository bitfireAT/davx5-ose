/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.ktor.http.Url

@Entity(tableName = "webdav_mount")
data class WebDavMount(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** display name of the WebDAV mount */
    val name: String,

    /** URL of the WebDAV service, including trailing slash */
    val url: Url

    // credentials are stored using CredentialsStore

)