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

    override suspend fun getValue(key: String): String? {
        return storage[key]
    }

    override suspend fun putValue(key: String, value: String?) {
        check(key !in sensitiveStorage) { """Key "$key" is already used for a sensitive value""" }

        if (value == null)
            storage.remove(key)
        else
            storage[key] = value
    }

    override suspend fun getSensitiveValue(key: String): SensitiveString? {
        return sensitiveStorage[key]
    }

    override suspend fun putSensitiveValue(key: String, value: SensitiveString?) {
        check(key !in storage) { """Key "$key" is already used for a non-sensitive value""" }

        if (value == null)
            sensitiveStorage.remove(key)
        else
            sensitiveStorage[key] = value
    }
}
