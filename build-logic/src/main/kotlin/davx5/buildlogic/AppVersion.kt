/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package davx5.buildlogic

/**
 * Centralized version information for DAVx5 app variants.
 *
 * Version names use Semantic Versioning. Pre-release identifiers are "alpha" (closed alpha in
 * internal track), "beta" (public beta track) and "rc" (public beta track).
 *
 * Version codes are derived from the version name like this:
 *
 * MmmppIIII   (example `405120000`)   where
 *
 * - M is the major version (`4` in the example)
 * - mm the minor version (two decimal digits, `05` in the example),
 * - pp the patch level (two decimal digits, `12` in the example), and
 * - IIII an increasing number (four decimal digits) that starts with `0000` and is increased for
 *   every release with the same major/minor/patch version (alpha-1, alpha-2, beta-1, ..., final).
 *   So usually the first pre-release has `0000` and the final version has the greatest number.
 */
object AppVersion {

    const val CODE: Int = 405170100
    const val NAME: String = "4.5.17.1"

}
