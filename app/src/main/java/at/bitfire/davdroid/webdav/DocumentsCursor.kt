/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.webdav

import android.database.MatrixCursor
import android.os.Bundle
import android.provider.DocumentsContract

class DocumentsCursor(columns: Array<out String>): MatrixCursor(columns) {

    private val documentsExtras = Bundle(1)

    override fun getExtras() = documentsExtras


    var error: String?
        get() = documentsExtras.getString(DocumentsContract.EXTRA_ERROR)
        set(value) = documentsExtras.putString(DocumentsContract.EXTRA_ERROR, value)

    var info: String?
        get() = documentsExtras.getString(DocumentsContract.EXTRA_INFO)
        set(value) = documentsExtras.putString(DocumentsContract.EXTRA_INFO, value)

    var loading: Boolean
        get() = documentsExtras.getBoolean(DocumentsContract.EXTRA_LOADING, false)
        set(value) = documentsExtras.putBoolean(DocumentsContract.EXTRA_LOADING, value)


    fun addRow(bundle: Bundle) {
        newRow().also { row ->
            for (entry in bundle.keySet()) {
                val value = bundle.get(entry)
                row.add(entry, value)
            }
        }
    }

}