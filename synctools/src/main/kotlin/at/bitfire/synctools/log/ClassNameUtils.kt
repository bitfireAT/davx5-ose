/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.log

object ClassNameUtils {

    /**
     * Shortens a fully qualified class name by
     *
     * - removing the `at.bitfire` package prefix,
     * - removing the `$...` suffix for anonymous classes,
     * - optionally placing the class name first (useful when the shortened class name can still be truncated).
     *
     * @param fullClassName The fully qualified class name to shorten.
     * @param classNameFirst If true, the class name is placed before the package name, separated by a slash.
     * @return The shortened class name, like
     *
     * - `.subpkg.SomeClass` when [classNameFirst] is false
     * - `SomeClass/.subpkg` when [classNameFirst] is true
     */
    fun shortenClassName(fullClassName: String, classNameFirst: Boolean): String {
        // remove $... that is appended for anonymous classes
        val withoutSuffix = fullClassName.replace(Regex("\\$.*$"), "")

        val idxDot = withoutSuffix.lastIndexOf('.')
        if (idxDot == -1)
            return withoutSuffix

        val packageName = withoutSuffix.substring(0, idxDot)
        val className = withoutSuffix.substring(idxDot + 1)
        val shortenedPackageName = packageName.removePrefix("at.bitfire")
        return if (classNameFirst)
            "$className/$shortenedPackageName"
        else
            "$shortenedPackageName.$className"
    }

}