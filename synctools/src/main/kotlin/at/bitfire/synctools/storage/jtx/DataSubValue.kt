/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.jtx

import android.content.ContentValues
import android.net.Uri
import java.nio.ByteBuffer

/**
 * This is a data class modeled after [android.content.Entity.NamedContentValues]. In addition to a [android.net.Uri] and
 * [android.content.ContentValues] it can hold binary data in form of a [java.nio.ByteBuffer].
 */
data class DataSubValue(
    val uri: Uri,
    val values: ContentValues,
    val data: ByteBuffer?
)