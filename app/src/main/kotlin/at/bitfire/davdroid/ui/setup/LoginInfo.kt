package at.bitfire.davdroid.ui.setup

import at.bitfire.davdroid.db.Credentials
import at.bitfire.vcard4android.GroupMethod
import java.net.URI

data class LoginInfo(
    val baseUri: URI,
    val credentials: Credentials? = null,

    val suggestedAccountName: String? = null,

    /** group method that should be pre-selected */
    val suggestedGroupMethod: GroupMethod = GroupMethod.GROUP_VCARDS
)