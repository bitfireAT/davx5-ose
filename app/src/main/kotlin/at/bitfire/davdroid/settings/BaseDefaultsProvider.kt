/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.settings

import android.content.Context
import at.bitfire.davdroid.TextTable
import java.io.Writer

abstract class BaseDefaultsProvider(
    val context: Context,
    val settingsManager: SettingsManager
): SettingsProvider {

    abstract val booleanDefaults: Map<String, Boolean>
    abstract val intDefaults: Map<String, Int>
    abstract val longDefaults: Map<String, Long>
    abstract val stringDefaults: Map<String, String?>


    override fun canWrite() = false

    override fun close() {
        // override this, if needed
    }

    override fun forceReload() {
        // override this, if needed
    }


    override fun contains(key: String) =
            booleanDefaults.containsKey(key) ||
            intDefaults.containsKey(key) ||
            longDefaults.containsKey(key) ||
            stringDefaults.containsKey(key)

    override fun getBoolean(key: String) = booleanDefaults[key]
    override fun getInt(key: String) = intDefaults[key]
    override fun getLong(key: String) = longDefaults[key]
    override fun getString(key: String) = stringDefaults[key]

    override fun putBoolean(key: String, value: Boolean?) = throw NotImplementedError()
    override fun putInt(key: String, value: Int?) = throw NotImplementedError()
    override fun putLong(key: String, value: Long?) = throw NotImplementedError()
    override fun putString(key: String, value: String?) = throw NotImplementedError()

    override fun remove(key: String) = throw NotImplementedError()


    override fun dump(writer: Writer) {
        val strValues = mutableMapOf<String, String?>()
        strValues.putAll(booleanDefaults.mapValues { (_, value) -> value.toString() })
        strValues.putAll(intDefaults.mapValues { (_, value) -> value.toString() })
        strValues.putAll(longDefaults.mapValues { (_, value) -> value.toString() })
        strValues.putAll(stringDefaults)

        val table = TextTable("Setting", "Value")
        for ((key, value) in strValues.toSortedMap())
            table.addLine(key, value)
        writer.write(table.toString())
    }

}