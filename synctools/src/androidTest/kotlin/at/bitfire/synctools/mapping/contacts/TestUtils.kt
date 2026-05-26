/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.contacts

object TestUtils {

    /**
     * Reads a resource from the classpath and returns it as a byte array.
     * @param resource The resource to read. Note that it must start with a `/`.
     * @throws NullPointerException If the resource doesn't exist.
     */
    fun resourceToByteArray(resource: String): ByteArray =
        this::class.java.getResourceAsStream(resource)!!.use { it.readBytes() }

}