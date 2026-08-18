/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings

import at.bitfire.synctools.util.SensitiveString

interface AccountSettingsStore {
    /**
     * Gets all key-value pairs stored in this store.
     */
    fun getAllValues(): Map<String, String>

    /**
     * Gets the value stored at [key].
     */
    fun getValue(key: String): String?

    /**
     * Stores [value] at [key].
     */
    fun putValue(key: String, value: String?)

    /**
     * Gets the value stored at [key] as a [SensitiveString].
     */
    fun getSensitiveValue(key: String): SensitiveString?

    /**
     * Stores [value] at [key].
     *
     * May be stored in a different location than [putValue], so keys stored using [putSensitiveValue] must only be
     * retrieved using [getSensitiveValue].
     */
    fun putSensitiveValue(key: String, value: SensitiveString?)
}
