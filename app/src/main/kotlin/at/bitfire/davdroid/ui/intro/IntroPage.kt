/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.intro

import android.app.Application
import androidx.compose.runtime.Composable

interface IntroPage {

    enum class ShowPolicy {
        DONT_SHOW,
        SHOW_ALWAYS,
        SHOW_ONLY_WITH_OTHERS
    }

    /**
     * Used to determine whether an intro page of this type (for instance,
     * the [BatteryOptimizationsPage]) should be shown.
     *
     * @param application   used to determine whether the page shall be shown
     *
     * @return Order with which an instance of this page type shall be created and shown. Possible values:
     *
     *   * < 0: only show the page when there is at least one other page with positive order (lower numbers are shown first)
     *   * [DONT_SHOW] (0): don't show the page
     *   * ≥ 0: show the page (lower numbers are shown first)
     */
    fun getShowPolicy(application: Application): ShowPolicy

    /**
     * Composes this page. Will only be called when [getShowPolicy] is not [DONT_SHOW].
     */
    @Composable
    fun ComposePage()

}