/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.log

import java.util.logging.Logger

@Deprecated("Remove as soon as all usages are replaced")
object Logger {

    @Deprecated("See LogManager for preferred ways to get a Logger.", ReplaceWith("java.util.logging.Logger.getGlobal()", "java.util.logging.Logger"))
    val log: Logger = Logger.getGlobal()

}