/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.settings

import java.io.Closeable

interface Provider: Closeable {

    fun has(key: String): Pair<Boolean, Boolean>

    fun getBoolean(key: String): Pair<Boolean?, Boolean>
    fun getInt(key: String): Pair<Int?, Boolean>
    fun getLong(key: String): Pair<Long?, Boolean>
    fun getString(key: String): Pair<String?, Boolean>

    fun isWritable(key: String): Pair<Boolean, Boolean>

    fun putBoolean(key: String, value: Boolean?): Boolean
    fun putInt(key: String, value: Int?): Boolean
    fun putLong(key: String, value: Long?): Boolean
    fun putString(key: String, value: String?): Boolean

    fun remove(key: String): Boolean


    interface Observer {
        fun onReload()
    }

}