/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid

import java.util.Collections

class TextTable(
    val headers: List<String>
) {

    companion object {

        fun indent(str: String, pos: Int): String =
                " ".repeat(pos) +
                str.split('\n').joinToString("\n" + " ".repeat(pos))

    }

    constructor(vararg headers: String): this(headers.toList())


    private val lines = mutableListOf<Array<String>>()

    fun addLine(values: List<Any?>) {
        if (values.size != headers.size)
            throw IllegalArgumentException("Table line must have ${headers.size} column(s)")
        lines += values.map {
            it?.toString() ?: "—"
        }.toTypedArray()
    }

    fun addLine(vararg values: Any?) = addLine(values.toList())

    override fun toString(): String {
        val sb = StringBuilder()

        val headerWidths = headers.map { it.length }
        val colWidths = Array<Int>(headers.size) { colIdx ->
            Collections.max(listOf(headerWidths[colIdx]) + lines.map { it[colIdx] }.map { it.length })
        }

        // first line
        sb.append("\n┌")
        for (colIdx in headers.indices)
            sb  .append("─".repeat(colWidths[colIdx] + 2))
                .append(if (colIdx == headers.size - 1) '┐' else '┬')
        sb.append('\n')

        // header
        sb.append('│')
        for (colIdx in headers.indices)
            sb  .append(' ')
                .append(headers[colIdx].padEnd(colWidths[colIdx] + 1))
                .append('│')
        sb.append('\n')

        // separator between header and body
        sb.append('├')
        for (colIdx in headers.indices) {
            sb  .append("─".repeat(colWidths[colIdx] + 2))
                .append(if (colIdx == headers.size - 1) '┤' else '┼')
        }
        sb.append('\n')

        // body
        for (line in lines) {
            for (colIdx in headers.indices)
                sb  .append("│ ")
                    .append(line[colIdx].padEnd(colWidths[colIdx] + 1))
            sb.append("│\n")
        }

        // last line
        sb.append("└")
        for (colIdx in headers.indices) {
            sb  .append("─".repeat(colWidths[colIdx] + 2))
                .append(if (colIdx == headers.size - 1) '┘' else '┴')
        }
        sb.append("\n\n")

        return sb.toString()
    }

}