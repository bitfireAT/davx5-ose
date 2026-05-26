/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.util

import android.accounts.Account
import android.net.Uri
import android.provider.ContactsContract
import ezvcard.property.StructuredName
import java.util.Locale

object Utils {

    fun String.capitalize(): String = split(' ').joinToString(" ") { word ->
        word.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
    }

    /**
     * Returns a string having leading and trailing whitespace removed.
     * If the resulting string is empty, returns null.
     */
    fun String?.trimToNull(): String? = this?.trim()?.takeIf { it.isNotBlank() }

    fun StructuredName.isEmpty() =
        prefixes.isEmpty() && given == null && additionalNames.isEmpty() && family == null && suffixes.isEmpty()

    fun Uri.asSyncAdapter(addressBookAccount: Account): Uri = buildUpon()
        .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, addressBookAccount.name)
        .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, addressBookAccount.type)
        .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
        .build()

}