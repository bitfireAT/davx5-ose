/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.log

object Logger {

    @Deprecated("Use @Inject j.u.l.Logger or j.u.l.Logger.getGlobal() instead", ReplaceWith("Logger.getGlobal()", "java.util.logging.Logger"))
    val log: java.util.logging.Logger = java.util.logging.Logger.getGlobal()

}