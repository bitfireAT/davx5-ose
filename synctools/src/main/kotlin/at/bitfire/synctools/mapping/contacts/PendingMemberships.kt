/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.contacts

/**
 * Serializes/deserializes a set of member UIDs stored in [AddressContract.GroupColumns.PENDING_MEMBERS].
 * The list will be used to establish group memberships when all groups and contacts have been synchronized.
 */
class PendingMemberships(
    val uids: Set<String>
) {

    companion object {
        const val SEPARATOR = '\n'

        fun fromString(value: String) =
            PendingMemberships(value.split(SEPARATOR).toSet())
    }

    override fun toString() = uids.joinToString(SEPARATOR.toString())

}
