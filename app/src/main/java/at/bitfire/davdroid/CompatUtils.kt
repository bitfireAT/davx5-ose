package at.bitfire.davdroid

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.AccountManagerCallback
import android.accounts.AccountManagerFuture
import android.app.Activity
import android.content.ContentProviderClient
import android.os.Build
import android.os.Bundle
import android.os.Handler

fun ContentProviderClient.closeCompat() {
    if (Build.VERSION.SDK_INT >= 24)
        close()
    else
        release()
}
