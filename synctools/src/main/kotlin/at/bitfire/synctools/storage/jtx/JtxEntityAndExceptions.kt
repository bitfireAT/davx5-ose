/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.jtx

/**
 * Represents a set of a local main jtx object (task, journal) and associated exceptions that are stored together.
 *
 * It consists of
 * - a (potentially recurring) main jtx object,
 * - optional exceptions to this main jtx object (exception instances, only useful if main item is recurring).
 *
 * Note: This class is only used for storing data to be written to the jtx content provider. [JtxEntity] might contain
 * binary data associated with specific sub-rows. When reading jtx objects from the content provider this binary data
 * is not included and [JtxObjectAndExceptions] is used instead. Code that wants to access this binary data, needs to
 * fetch it directly from the content provider.
 */
data class JtxEntityAndExceptions(
    val main: JtxEntity,
    val exceptions: List<JtxEntity>
)
