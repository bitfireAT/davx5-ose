/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.contacts.handler

import android.content.ContentValues
import androidx.annotation.CallSuper
import at.bitfire.synctools.mapping.contacts.Contact
import java.util.logging.Logger

/**
 * Handler for a raw contact's data row.
 */
abstract class DataRowHandler {

    protected val logger = Logger.getLogger(javaClass.name)

    abstract fun forMimeType(): String

    /**
     * Processes the given data.
     *
     * @param values   values to process
     * @param contact  contact that is modified according to the values
     */
    @CallSuper
    open fun handle(values: ContentValues, contact: Contact) {
        // remove empty strings
        val it = values.keySet().iterator()
        while (it.hasNext()) {
            val obj = values[it.next()]
            if (obj is String && obj.isEmpty())
                it.remove()
        }
    }

}