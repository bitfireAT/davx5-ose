/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid

import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import at.bitfire.synctools.icalendar.ical4jVersion
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import ezvcard.Ezvcard
import okhttp3.OkHttp
import javax.inject.Inject

@Reusable
class ProductIds @Inject constructor(
    @ApplicationContext context: Context
) {

    // HTTP User-Agent

    private val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    private val versionName = packageInfo.versionName ?: PackageInfoCompat.getLongVersionCode(packageInfo).toString()
    val httpUserAgent = "DAVx5/$versionName (dav4jvm; okhttp/${OkHttp.VERSION}) Android/${Build.VERSION.RELEASE}"


    // product IDs for iCalendar/vCard

    val iCalProdId = "DAVx5/$versionName ical4j/${ical4jVersion}"
    val vCardProdId = "+//IDN bitfire.at//DAVx5/$versionName ez-vcard/${Ezvcard.VERSION}"

}