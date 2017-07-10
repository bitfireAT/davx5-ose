/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestDavUtils {

    private static final String exampleURL = "http://example.com/";

    @Test
    public void testARGBtoCalDAVColor() {
        assertEquals("#00000000", DavUtils.ARGBtoCalDAVColor(0));
        assertEquals("#123456FF", DavUtils.ARGBtoCalDAVColor(0xFF123456));
        assertEquals("#000000FF", DavUtils.ARGBtoCalDAVColor(0xFF000000));
    }

    @Test
    public void testLastSegmentOfUrl() {
        assertEquals("/", DavUtils.lastSegmentOfUrl(exampleURL));
        assertEquals("dir", DavUtils.lastSegmentOfUrl(exampleURL + "dir"));
        assertEquals("dir", DavUtils.lastSegmentOfUrl(exampleURL + "dir/"));
        assertEquals("file.html", DavUtils.lastSegmentOfUrl(exampleURL + "dir/file.html"));
    }

}
