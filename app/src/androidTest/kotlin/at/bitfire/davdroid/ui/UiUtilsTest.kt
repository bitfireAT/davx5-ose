/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import at.bitfire.davdroid.ui.UiUtils.annotateHtml
import org.junit.Assert.assertEquals
import org.junit.Test

class UiUtilsTest {
    /**
     * Used for making sure that [UiUtils.annotateHtml] annotates the strings correctly, without
     * crashing and without missing any characters.
     *
     * There are multiple tests with some combinations, some of them use no quotes in the href
     * parameter to check that it also works correctly. This is checked because CDATA in string
     * resources removes the quotes.
     */
    @Test
    @OptIn(ExperimentalTextApi::class)
    fun testAnnotatingHtmlString() {
        // Test string with something before and after the link
        "Text <a href=\"https://example.com\">with link</a> and some text".annotateHtml(SpanStyle()).let { src ->
            assertEquals("Text with link and some text", src.toString())

            val annotations = src.getUrlAnnotations(5, 5)
            assertEquals(1, annotations.size)
            assertEquals("https://example.com", annotations[0].item.url)
        }

        // Test string with something before the link
        "Text <a href=https://example.com>with link</a>".annotateHtml(SpanStyle()).let { src ->
            assertEquals("Text with link", src.toString())

            val annotations = src.getUrlAnnotations(5, 5)
            assertEquals(1, annotations.size)
            assertEquals("https://example.com", annotations[0].item.url)
        }

        // Test string with something after the link
        "<a href=\"https://example.com\">Link</a> with some text".annotateHtml(SpanStyle()).let { src ->
            assertEquals("Link with some text", src.toString())

            val annotations = src.getUrlAnnotations(0, 0)
            assertEquals(1, annotations.size)
            assertEquals("https://example.com", annotations[0].item.url)
        }

        // Test string with single character at the start and end
        ".<a href=\"https://example.com\">link</a>.".annotateHtml(SpanStyle()).let { src ->
            assertEquals(".link.", src.toString())

            val annotations = src.getUrlAnnotations(1, 1)
            assertEquals(1, annotations.size)
            assertEquals("https://example.com", annotations[0].item.url)
        }

        // Test string with single character at the start
        ".<a href=https://example.com>link</a>".annotateHtml(SpanStyle()).let { src ->
            assertEquals(".link", src.toString())

            val annotations = src.getUrlAnnotations(1, 1)
            assertEquals(1, annotations.size)
            assertEquals("https://example.com", annotations[0].item.url)
        }

        // Test string with single character at the end
        "Text <a href=\"https://example.com\">with link</a>.".annotateHtml(SpanStyle()).let { src ->
            assertEquals("Text with link.", src.toString())

            val annotations = src.getUrlAnnotations(5, 5)
            assertEquals(1, annotations.size)
            assertEquals("https://example.com", annotations[0].item.url)
        }

        // Test with multiple links
        "Text with <a href=\"https://example1.com\">multiple</a> <a href=\"https://example2.com\">links</a>.".annotateHtml(SpanStyle()).let { src ->
            assertEquals("Text with multiple links.", src.toString())

            src.getUrlAnnotations(10, 10).let { annotations ->
                assertEquals(1, annotations.size)
                assertEquals("https://example1.com", annotations[0].item.url)
            }
            src.getUrlAnnotations(19, 19).let { annotations ->
                assertEquals(1, annotations.size)
                assertEquals("https://example2.com", annotations[0].item.url)
            }
        }
    }
}
