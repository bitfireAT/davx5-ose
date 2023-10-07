/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.settings

import android.content.Context

interface SettingsProviderFactory {

    fun getProviders(context: Context, settingsManager: SettingsManager): Iterable<SettingsProvider>

}