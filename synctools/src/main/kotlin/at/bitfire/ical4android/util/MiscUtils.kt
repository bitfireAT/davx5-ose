/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android.util

import android.accounts.Account
import android.content.ContentProviderClient
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract

object MiscUtils {

    // various extension methods



    fun Uri.asSyncAdapter(account: Account): Uri = buildUpon()
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account.name)
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, account.type)
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .build()

}