/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid;

import org.junit.Assert;
import org.junit.Test;

public class TestDavUtils {

    private static final String exampleURL = "http://example.com/";

    @Test
    public void testLastSegmentOfUrl() {
        Assert.assertEquals("/", DavUtils.lastSegmentOfUrl(exampleURL));
        Assert.assertEquals("dir", DavUtils.lastSegmentOfUrl(exampleURL + "dir"));
        Assert.assertEquals("dir", DavUtils.lastSegmentOfUrl(exampleURL + "dir/"));
        Assert.assertEquals("file.html", DavUtils.lastSegmentOfUrl(exampleURL + "dir/file.html"));
    }

}
