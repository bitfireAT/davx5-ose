/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.vcard

enum class GroupMethod {

    /**
     * Groups are separate VCards.
     * If VCard4 is available, group VCards have "KIND:group".
     * Otherwise (if only VCard3 is available), group VCards have "X-ADDRESSBOOKSERVER-KIND:group".
     */
    GROUP_VCARDS,

    /**
     * Groups are stored in a contact's CATEGORIES.
     */
    CATEGORIES

}
