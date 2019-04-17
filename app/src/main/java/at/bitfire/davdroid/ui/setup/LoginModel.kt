/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui.setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import at.bitfire.davdroid.model.Credentials
import java.net.URI

class LoginModel(
        application: Application
): AndroidViewModel(application) {

    var baseURI: URI? = null
    var credentials: Credentials? = null

    var configuration: DavResourceFinder.Configuration? = null

}
