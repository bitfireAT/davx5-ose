/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DavUtils {

    public static String ARGBtoCalDAVColor(int colorWithAlpha) {
        byte alpha = (byte)(colorWithAlpha >> 24);
        int color = colorWithAlpha & 0xFFFFFF;
        return String.format("#%06X%02X", color, alpha);
    }

}
