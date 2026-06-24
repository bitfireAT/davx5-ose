/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import java.io.StringWriter

/**
 * An implementation of [ByteArrayContent] that allows specifying a [io.ktor.http.ContentType].
 */
class ByteArrayContentImpl(
    private val bytes: ByteArray,
    override val contentType: ContentType? = null
): OutgoingContent.ByteArrayContent() {
    override fun bytes(): ByteArray = bytes

    companion object {
        /**
         * Encodes this writer's output into a [ByteArray] and wraps it into a [ByteArrayContentImpl] with the given [contentType].
         */
        fun StringWriter.toOutgoingContent(
            contentType: ContentType? = null
        ): OutgoingContent = ByteArrayContentImpl(
            bytes = toString().encodeToByteArray(),
            contentType = contentType
        )
    }
}
