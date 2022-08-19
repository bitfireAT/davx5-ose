/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.intro

import android.content.Context
import androidx.fragment.app.Fragment

interface IntroFragmentFactory {

    companion object {
        const val DONT_SHOW = 0
    }

    /**
     * Used to determine whether an intro fragment of this type (for instance,
     * the [BatteryOptimizationsFragment]) should be shown.
     *
     * @param context   used to determine whether the fragment shall be shown
     *
     * @return Order with which an instance of this fragment type shall be created and shown. Possible values:
     *
     *   * <0: only show the fragment when there is at least one other fragment with positive order (lower numbers are shown first)
     *   * 0: don't show the fragment
     *   * ≥0: show the fragment (lower numbers are shown first)
     */
    fun getOrder(context: Context): Int

    /**
     * Creates an instance of this intro fragment type. Will only be called when
     * [getOrder] is true.
     *
     * @return the fragment (for instance, a [BatteryOptimizationsFragment]])
     */
    fun create(): Fragment

}