package at.bitfire.davdroid

import at.bitfire.davdroid.util.lastSegment
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert
import org.junit.Test

class UrlUtilsTest {
    private val exampleURL = "http://example.com/"

    @Test
    fun testLastSegmentOfUrl() {
        Assert.assertEquals("/", exampleURL.toHttpUrl().lastSegment)
        Assert.assertEquals("dir", (exampleURL + "dir").toHttpUrl().lastSegment)
        Assert.assertEquals("dir", (exampleURL + "dir/").toHttpUrl().lastSegment)
        Assert.assertEquals("file.html", (exampleURL + "dir/file.html").toHttpUrl().lastSegment)
    }
}
