/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings

import at.bitfire.synctools.util.SensitiveString

class InMemorySettingsStore(
    storage: Map<String, String> = mapOf(),
    sensitiveStorage: Map<String, SensitiveString> = mapOf()
) : AccountSettingsStore {
    private val storage: MutableMap<String, String> = storage.toMutableMap()
    private val sensitiveStorage: MutableMap<String, SensitiveString> = sensitiveStorage.toMutableMap()

    override fun getValue(key: String): String? {
        return storage[key]
    }

    override fun putValue(key: String, value: String?) {
        if (value == null)
            storage.remove(key)
        else
            storage[key] = value
    }

    override fun getSensitiveValue(key: String): SensitiveString? {
        return sensitiveStorage[key]
    }

    override fun putSensitiveValue(key: String, value: SensitiveString?) {
        if (value == null)
            sensitiveStorage.remove(key)
        else
            sensitiveStorage[key] = value
    }
}
