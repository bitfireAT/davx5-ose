/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.model

import at.bitfire.davdroid.log.Logger
import java.util.logging.Level

data class SyncState(
        val type: Type,
        val value: String
) {

    companion object {

        fun fromString(s: String): SyncState? {
            val pos = s.indexOf(':')
            if (pos == -1)
                return null

            return try {
                SyncState(
                        Type.valueOf(s.substring(0, pos)),
                        s.substring(pos + 1)
                )
            } catch (e: Exception) {
                Logger.log.log(Level.WARNING, "Couldn't restore SyncState", e)
                null
            }
        }

    }

    enum class Type { CTAG, SYNC_TOKEN }

    override fun toString() =
            "${type.name}:${value}"

}