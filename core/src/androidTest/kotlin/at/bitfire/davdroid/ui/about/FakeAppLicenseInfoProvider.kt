/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.about

import androidx.compose.runtime.Composable
import javax.inject.Inject

class FakeAppLicenseInfoProvider @Inject constructor(): AboutActivity.AppLicenseInfoProvider {

    @Composable
    override fun LicenseInfo() {
        throw NotImplementedError()
    }

}