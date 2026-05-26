/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import android.accounts.Account
import android.content.ContentProviderClient

interface JtxCollectionFactory<out T: JtxCollection<JtxICalObject>> {

    fun newInstance(account: Account, client: ContentProviderClient, id: Long): T

}