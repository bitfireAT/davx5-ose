/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid;

import junit.framework.TestCase;

public class TestDavUtils extends TestCase {

    private static final String exampleURL = "http://example.com/";

    public void testLastSegmentOfUrl() {
        assertEquals("/", DavUtils.lastSegmentOfUrl(exampleURL));
        assertEquals("dir", DavUtils.lastSegmentOfUrl(exampleURL + "dir"));
        assertEquals("dir", DavUtils.lastSegmentOfUrl(exampleURL + "dir/"));
        assertEquals("file.html", DavUtils.lastSegmentOfUrl(exampleURL + "dir/file.html"));
    }

}
