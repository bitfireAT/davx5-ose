/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.setup

import androidx.lifecycle.ViewModel
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.servicedetection.DavResourceFinder
import at.bitfire.vcard4android.GroupMethod
import java.net.URI

class LoginModel: ViewModel() {

    var baseURI: URI? = null
    var credentials: Credentials? = null

    var configuration: DavResourceFinder.Configuration? = null

    /** account name that should be used as default account name when no email addresses have been found */
    var suggestedAccountName: String? = null

    /** group method that should be pre-selectedbr */
    var suggestedGroupMethod: GroupMethod = GroupMethod.GROUP_VCARDS

}