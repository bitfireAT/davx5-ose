/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.log

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * The app-private directory where debug files (log files, debug info ZIP, etc.) are stored.
 * Must match the paths declared in `res/xml/debug.paths.xml`.
 */
class DebugDirectory @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** A file name (not a path) of a file inside this [DebugDirectory]. */
    @JvmInline
    value class FileName(val name: String) {
        init {
            require(!name.contains(File.separatorChar))
        }
    }

    /**
     * Gets the debug directory, creating it if it doesn't exist.
     *
     * The debug directory is always app-private and can't be read by other apps.
     *
     * @return The debug directory if it exists or could be created, otherwise null.
     */
    fun get(): File? {
        val dir = File(context.filesDir, DIRECTORY_NAME)
        if (dir.exists() && dir.isDirectory)
            return dir
        if (dir.mkdir())
            return dir
        return null
    }

    /**
     * Resolves the given file name to a [File] in the debug directory.
     *
     * @param fileName The file name to resolve.
     * @return The resolved file if the debug directory exists, otherwise null.
     */
    fun resolve(fileName: FileName): File? = get()?.let { File(it, fileName.name) }

    companion object {
        const val DIRECTORY_NAME = "debug"
    }

}
