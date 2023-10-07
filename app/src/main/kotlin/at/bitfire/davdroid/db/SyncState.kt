/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.db

import at.bitfire.dav4jvm.property.SyncToken
import org.json.JSONException
import org.json.JSONObject

data class SyncState(
        val type: Type,
        val value: String,

        /**
         * Whether this sync state occurred during an initial sync as described
         * in RFC 6578, which means the initial sync is not complete yet.
         */
        var initialSync: Boolean? = null
) {

    companion object {

        private const val KEY_TYPE = "type"
        private const val KEY_VALUE = "value"
        private const val KEY_INITIAL_SYNC = "initialSync"

        fun fromString(s: String?): SyncState? {
            if (s == null)
                return null

            return try {
                val json = JSONObject(s)
                SyncState(
                        Type.valueOf(json.getString(KEY_TYPE)),
                        json.getString(KEY_VALUE),
                        try { json.getBoolean(KEY_INITIAL_SYNC) } catch(e: JSONException) { null }
                )
            } catch (e: JSONException) {
                null
            }
        }

        fun fromSyncToken(token: SyncToken, initialSync: Boolean? = null) =
                SyncState(Type.SYNC_TOKEN, requireNotNull(token.token), initialSync)

    }

    enum class Type { CTAG, SYNC_TOKEN }

    override fun toString(): String {
        val json = JSONObject()
        json.put(KEY_TYPE, type.name)
        json.put(KEY_VALUE, value)
        initialSync?.let { json.put(KEY_INITIAL_SYNC, it) }
        return json.toString()
    }

}