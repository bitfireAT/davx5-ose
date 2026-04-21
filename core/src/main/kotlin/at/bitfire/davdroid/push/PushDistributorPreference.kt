/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import at.bitfire.davdroid.R

enum class PushDistributorPreference(
    @StringRes val nameRes: Int,
    @DrawableRes val iconRes: Int?,
) {
    /**
     * Disables Push completely.
     */
    Disabled(R.string.app_settings_unifiedpush_disable, null),

    /**
     * Relays the distributor preference to UnifiedPush.
     */
    UnifiedPush(R.string.app_settings_unifiedpush_up, null),

    /**
     * Forces the distributor to be the embedded FCM distributor.
     */
    FCM(R.string.app_settings_unifiedpush_distributor_fcm, R.drawable.product_logomark_cloud_messaging_full_color)
}
