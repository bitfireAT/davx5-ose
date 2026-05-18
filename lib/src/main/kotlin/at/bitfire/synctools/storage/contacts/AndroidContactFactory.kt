/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.contacts

import android.content.ContentValues

interface AndroidContactFactory<T: AndroidContact> {

    fun fromProvider(addressBook: AndroidAddressBook<T, out AndroidGroup>, values: ContentValues): T

}
