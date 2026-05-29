/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.ical4android

import android.accounts.Account
import android.content.ContentProviderClient

interface JtxCollectionFactory<out T: JtxCollection<JtxICalObject>> {

    fun newInstance(account: Account, client: ContentProviderClient, id: Long): T

}