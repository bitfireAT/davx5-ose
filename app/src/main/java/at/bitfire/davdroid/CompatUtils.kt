/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid

import android.content.ContentProviderClient
import android.os.Build

@Suppress("DEPRECATION")
fun ContentProviderClient.closeCompat() {
    if (Build.VERSION.SDK_INT >= 24)
        close()
    else
        release()
}
