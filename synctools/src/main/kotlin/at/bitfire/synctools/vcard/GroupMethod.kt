/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
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
