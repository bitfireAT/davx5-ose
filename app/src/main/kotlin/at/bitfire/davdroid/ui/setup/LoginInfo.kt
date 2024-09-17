/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.setup

import android.os.Parcelable
import at.bitfire.davdroid.db.Credentials
import at.bitfire.vcard4android.GroupMethod
import java.net.URI
import kotlinx.parcelize.Parcelize

@Parcelize
data class LoginInfo(
    val baseUri: URI? = null,
    val credentials: Credentials? = null,

    val suggestedAccountName: String? = null,

    /** group method that should be pre-selected */
    val suggestedGroupMethod: GroupMethod = GroupMethod.GROUP_VCARDS
): Parcelable