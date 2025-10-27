/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.util

/**
 * Wrapper for passwords and other sensitive strings so that they're not directly [String]s,
 * so that they're less likely to be used in clear-text unintentionally, like being printed in logs
 * by [Any.toString].
 *
 * This class does not address the issue that clear-text passwords are stored in memory. This problem
 * could only be reduced if we would consequently store and process only encrypted passwords, with the
 * exception of some "providePassword" method that provides the clear-text password for a lambda function as
 * [CharArray] and wipes out the array values after usage.
 *
 * See also:
 *
 * - https://stackoverflow.com/a/8889285
 * - https://javaee.github.io/security-api/apidocs/javax/security/enterprise/credential/Password.html and
 *   https://javaee.github.io/security-api/apidocs/javax/security/enterprise/credential/UsernamePasswordCredential.html
 */
class SensitiveString private constructor(
    private val data: String
) {

    /**
     * Returns the sensitive string as a [CharArray].
     *
     * _Be careful when using it (for instance, don't print its content unintentionally)._
     */
    fun asCharArray() = data.toCharArray()

    /**
     * Returns the sensitive string as an immutable [String].
     *
     * _Be careful when using it (for instance, don't print it unintentionally)._
     */
    fun asString() = data


    // make comparable by data

    override fun equals(other: Any?) =
        if (other is SensitiveString)
            data == other.data
        else
            false

    override fun hashCode() = data.hashCode()


    /**
     * Overrides [toString] so that it doesn't expose the clear-text string (password).
     */
    override fun toString() = "*****"


    companion object {

        fun CharArray.toSensitiveString() =
            SensitiveString(this.concatToString())

        fun CharSequence.toSensitiveString() =
            SensitiveString(this.toString())

    }

}