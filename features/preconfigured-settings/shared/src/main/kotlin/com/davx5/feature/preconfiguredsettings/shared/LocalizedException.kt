/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.feature.preconfiguredsettings.shared

import android.content.Context
import androidx.annotation.StringRes

class LocalizedException(@StringRes val messageRes: Int): Exception() {
    fun localizedMessage(context: Context): String = context.getString(messageRes)
}
