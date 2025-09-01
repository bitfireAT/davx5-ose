/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.util

/**
 * Wrapper for passwords and other sensitive strings so that they're not directly [String]s,
 * so that they're less likely to be used in plain text unintentionally, like being printed in logs
 * by [Any.toString].
 */
class SensitiveString private constructor(
    private val data: String
) {

    fun asCharArray() = data.toCharArray()
    fun asString() = data


    // make comparable by data

    override fun equals(other: Any?) =
        data == other

    override fun hashCode() = data.hashCode()

    override fun toString() = "*****"


    companion object {

        fun CharArray.toSensitiveString() =
            SensitiveString(this.toString())

        fun String.toSensitiveString() =
            SensitiveString(this)

    }

}