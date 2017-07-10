/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid;

import okhttp3.HttpUrl
import java.util.*

object DavUtils {

    @JvmStatic
    fun ARGBtoCalDAVColor(colorWithAlpha: Int): String {
        val alpha = (colorWithAlpha shr 24) and 0xFF
        val color = colorWithAlpha and 0xFFFFFF
        return String.format("#%06X%02X", color, alpha)
    }

    @JvmStatic
    fun lastSegmentOfUrl(url: String): String {
        val httpUrl = HttpUrl.parse(url) ?: throw IllegalArgumentException("url not parsable")

        // the list returned by HttpUrl.pathSegments() is unmodifiable, so we have to create a copy
        val segments = LinkedList<String>(httpUrl.pathSegments())
        Collections.reverse(segments)

        for (segment in segments)
            if (segment.isNotEmpty())
                return segment

        return "/"
    }

}
