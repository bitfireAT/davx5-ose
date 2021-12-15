/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.intro

import android.content.Context
import androidx.fragment.app.Fragment
import at.bitfire.davdroid.settings.SettingsManager

interface IIntroFragmentFactory {

    enum class ShowMode {
        /** show the fragment */
        SHOW,
        /** show the fragment only when there is at least one other fragment with mode [SHOW] */
        SHOW_NOT_ALONE,
        /** don't show the fragment */
        DONT_SHOW
    }

    /**
     * Used to determine whether an intro fragment of this type (for instance,
     * the [BatteryOptimizationsFragment]) should be shown.
     *
     * @param context   used to determine whether the fragment shall be shown
     * @param settingsManager  used to determine whether the fragment shall be shown
     *
     * @return whether an instance of this fragment type shall be created and shown
     */
    fun shouldBeShown(context: Context, settingsManager: SettingsManager): ShowMode

    /**
     * Creates an instance of this intro fragment type. Will only be called when
     * [shouldBeShown] is true.
     *
     * @return the fragment (for instance, a [BatteryOptimizationsFragment]])
     */
    fun create(): Fragment

}