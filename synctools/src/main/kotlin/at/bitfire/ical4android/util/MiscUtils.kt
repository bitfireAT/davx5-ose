/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.ical4android.util

import android.accounts.Account
import android.net.Uri
import android.provider.CalendarContract

object MiscUtils {

    // various extension methods



    fun Uri.asSyncAdapter(account: Account): Uri = buildUpon()
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account.name)
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, account.type)
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .build()

}