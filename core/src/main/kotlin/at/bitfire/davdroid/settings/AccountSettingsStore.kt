/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings

import at.bitfire.synctools.util.SensitiveString

interface AccountSettingsStore {
    /**
     * Gets the value stored at [key].
     */
    fun getValue(key: String): String?

    /**
     * Stores [value] at [key].
     *
     * May throw [IllegalStateException] if the same key was already used with [putSensitiveValue]. Values stored using
     * [putValue] must only be retrieved using [getValue].
     */
    fun putValue(key: String, value: String?)

    /**
     * Gets the value stored at [key] as a [SensitiveString].
     */
    fun getSensitiveValue(key: String): SensitiveString?

    /**
     * Stores [value] at [key].
     *
     * May throw [IllegalStateException] if the same key was already used with [putValue]. Values stored using
     * [putSensitiveValue] must only be retrieved using [getSensitiveValue].
     */
    fun putSensitiveValue(key: String, value: SensitiveString?)
}
