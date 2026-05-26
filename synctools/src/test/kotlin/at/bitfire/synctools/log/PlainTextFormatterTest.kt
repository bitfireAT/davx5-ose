/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.log

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.logging.Level
import java.util.logging.LogRecord

class PlainTextFormatterTest {

    private val minimum = PlainTextFormatter(
        withTime = false,
        withSource = false,
        padSource = 0,
        withException = false,
        lineSeparator = null
    )

    @Test
    fun test_format_param_null() {
        val result = minimum.format(LogRecord(Level.INFO, "Message").apply {
            parameters = arrayOf(null)
        })
        assertEquals("Message\n\tPARAMETER #1 = (null)", result)
    }

    @Test
    fun test_format_param_object() {
        val result = minimum.format(LogRecord(Level.INFO, "Message").apply {
            parameters = arrayOf(object {
                override fun toString() = "SomeObject[]"
            })
        })
        assertEquals("Message\n\tPARAMETER #1 = SomeObject[]", result)
    }

    @Test
    fun test_format_truncatesMessage() {
        val result = minimum.format(LogRecord(Level.INFO, "a".repeat(50000)))
        // PlainTextFormatter.MAX_LENGTH is 10,000
        assertEquals(10000, result.length)
    }


    @Test
    fun test_shortClassName_Empty() {
        assertEquals("", PlainTextFormatter.DEFAULT.shortClassName(""))
    }

    @Test
    fun test_shortClassName_NoDot_Anonymous() {
        assertEquals("NoDot", PlainTextFormatter.DEFAULT.shortClassName("NoDot\$Anonymous"))
    }

    @Test
    fun test_shortClassName_MultipleParts() {
        assertEquals("a.b.s.l.PlainTextFormatterTest", PlainTextFormatter.DEFAULT.shortClassName(javaClass.name))
    }

}