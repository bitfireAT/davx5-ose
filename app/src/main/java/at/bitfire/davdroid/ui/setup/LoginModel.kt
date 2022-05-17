/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.setup

import androidx.lifecycle.ViewModel
import at.bitfire.davdroid.db.Credentials
import java.net.URI

class LoginModel: ViewModel() {

    var baseURI: URI? = null
    var credentials: Credentials? = null

    var configuration: DavResourceFinder.Configuration? = null

}
