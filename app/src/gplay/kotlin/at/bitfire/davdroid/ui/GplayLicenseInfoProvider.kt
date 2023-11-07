/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import at.bitfire.davdroid.R
import javax.inject.Inject

class GplayLicenseInfoProvider @Inject constructor() : AboutActivity.AppLicenseInfoProvider {

    @Composable
    override fun LicenseInfo() {
        Text(stringResource(R.string.about_flavor_info))
    }

    @Composable
    @Preview
    fun LicenseInfo_Preview() {
        LicenseInfo()
    }

}