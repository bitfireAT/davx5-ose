/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.util

import com.google.common.base.Joiner
import com.google.common.base.Strings

fun String?.trimToNull() = Strings.emptyToNull(this?.trim())

fun String.withTrailingSlash() =
    if (this.endsWith('/'))
        this
    else
        "$this/"

const val MARKER = '\uFEFF'   // Start of hidden payload
const val ZERO = '\u200B'     // Zero Width Space for 0
const val ONE = '\u200C'      // Zero Width Non-Joiner for 1

/**
 * Encodes a number using our own steganographic encoding scheme to hide a number in UTF-8 invisible characters.
 */
fun encodeNumber(num: Long): String {
    require(num >= 0) { "Only non-negative integers are supported" }
    if (num == 0L) return ZERO.toString() // Represent zero as a single zero-width space

    val binary = num.toString(2) // e.g., 8 -> "1000"
    val builder = StringBuilder()
    for (bit in binary) {
        builder.append(
            if (bit == '0') ZERO  // Zero Width Space
            else ONE              // Zero Width Non-Joiner
        )
    }
    return builder.toString()
}

fun decodeWithMarker(text: String): Int? {
    val markerIndex = text.indexOf(MARKER)
    if (markerIndex == -1) return null // No hidden payload
    val hiddenPart = text.substring(markerIndex + 1)
    val binary = hiddenPart.map {
        when (it) {
            ZERO -> '0'
            ONE -> '1'
            else -> throw IllegalArgumentException("Invalid hidden character")
        }
    }.joinToString("")
    return binary.toInt(2)
}
