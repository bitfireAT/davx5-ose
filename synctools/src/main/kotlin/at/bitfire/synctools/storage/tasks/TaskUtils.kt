/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.tasks

import android.accounts.Account
import android.net.Uri
import org.dmfs.tasks.contract.TaskContract

fun Uri.asSyncAdapter(account: Account): Uri = buildUpon()
    .appendQueryParameter(TaskContract.ACCOUNT_NAME, account.name)
    .appendQueryParameter(TaskContract.ACCOUNT_TYPE, account.type)
    .appendQueryParameter(TaskContract.CALLER_IS_SYNCADAPTER, "true")
    .build()
