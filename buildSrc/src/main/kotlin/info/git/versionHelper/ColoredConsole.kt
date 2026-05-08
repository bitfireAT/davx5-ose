package info.git.versionHelper

import info.git.versionHelper.ColoredConsole.Companion.BLACK
import info.git.versionHelper.ColoredConsole.Companion.BRIGHT_BLACK
import info.git.versionHelper.ColoredConsole.Companion.BRIGHT_WHITE
import info.git.versionHelper.ColoredConsole.Companion.RESET
import info.git.versionHelper.ColoredConsole.Companion.WHITE

// https://github.com/marcelmatula/colored-console
interface ColoredConsole {

    sealed class Style {

        @Suppress("unused")
        val bg: Style
            get() = when (this) {
                is Simple -> if (code.isColor) copy(code = code + BACKGROUND_SHIFT) else this
                is Composite -> if (parent is Simple && parent.code.isColor)
                    copy(parent = parent.copy(code = parent.code + BACKGROUND_SHIFT))
                else this

                is NotApplied -> this
            }

        abstract fun wrap(text: String): String

        object NotApplied : Style() {
            override fun wrap(text: String) = text
        }

        data class Simple(val code: Int) : Style() {
            override fun wrap(text: String) = text.applyCodes(code)
        }

        data class Composite(val parent: Style, private val child: Style) : Style() {
            override fun wrap(text: String) = parent.wrap(child.wrap(text))
        }

        operator fun plus(style: Style) = when (this) {
            is NotApplied -> this
            is Simple -> Composite(style, this)
            is Composite -> Composite(style, this)
        }
    }

    fun <N> N.style(style: Style, predicate: (N) -> Boolean = { true }) =
        takeIf { predicate(this) }?.let { style.wrap(toString()) } ?: toString()

    operator fun <N> N.invoke(style: Style, predicate: (N) -> Boolean = { true }) = style(style, predicate)

    fun <N> N.wrap(vararg ansiCodes: Int) = toString().let { text ->
        if (this@ColoredConsole is ColorConsoleDisabled)
            text
        else {
            val codes = ansiCodes.filter { it != RESET }
            text.applyCodes(*codes.toIntArray())
        }
    }

    // region styles
    val bold: Style get() = Style.Simple(HIGH_INTENSITY)
    val <N : Style> N.bold: Style get() = this + this@ColoredConsole.bold
    val <N> N.bold get() = wrap(HIGH_INTENSITY)

    val italic: Style get() = Style.Simple(ITALIC)
    val <N : Style> N.italic: Style get() = this + this@ColoredConsole.italic
    val <N> N.italic get() = wrap(ITALIC)

    val underline: Style get() = Style.Simple(UNDERLINE)
    val <N : Style> N.underline: Style get() = this + this@ColoredConsole.underline

    val blink: Style get() = Style.Simple(BLINK)
    val <N : Style> N.blink: Style get() = this + this@ColoredConsole.blink

    val reverse: Style get() = Style.Simple(REVERSE)
    val <N : Style> N.reverse: Style get() = this + this@ColoredConsole.reverse

    val hidden: Style get() = Style.Simple(HIDDEN)
    val <N : Style> N.hidden: Style get() = this + this@ColoredConsole.hidden
    val <N> N.hidden get() = wrap(HIDDEN)

    // endregion

    // region colors
    val black: Style get() = Style.Simple(BLACK)
    val <N : Style> N.black: Style get() = this + this@ColoredConsole.black

    val red: Style get() = Style.Simple(RED)
    val <N : Style> N.red: Style get() = this + this@ColoredConsole.red
    val <N> N.red get() = wrap(RED)

    val green: Style get() = Style.Simple(GREEN)
    val <N : Style> N.green: Style get() = this + this@ColoredConsole.green

    val yellow: Style get() = Style.Simple(YELLOW)
    val <N : Style> N.yellow: Style get() = this + this@ColoredConsole.yellow

    val blue: Style get() = Style.Simple(BLUE)
    val <N : Style> N.blue: Style get() = this + this@ColoredConsole.blue

    val purple: Style get() = Style.Simple(PURPLE)
    val <N : Style> N.purple: Style get() = this + this@ColoredConsole.purple

    val cyan: Style get() = Style.Simple(CYAN)
    val <N : Style> N.cyan: Style get() = this + this@ColoredConsole.cyan

    val white: Style get() = Style.Simple(WHITE)
    val <N : Style> N.white: Style get() = this + this@ColoredConsole.white
    // endregion

    companion object {
        const val RESET = 0

        const val HIGH_INTENSITY = 1

        const val BACKGROUND_SHIFT = 10
        const val BRIGHT_SHIFT = 60

        const val ITALIC = 3
        const val UNDERLINE = 4
        const val BLINK = 5
        const val REVERSE = 7
        const val HIDDEN = 8

        const val BLACK = 30
        const val RED = 31
        const val GREEN = 32
        const val YELLOW = 33
        const val BLUE = 34
        const val PURPLE = 35
        const val CYAN = 36
        const val WHITE = 37

        const val BRIGHT_BLACK = BLACK + BRIGHT_SHIFT

        @Suppress("unused")
        const val BRIGHT_RED = RED + BRIGHT_SHIFT

        @Suppress("unused")
        const val BRIGHT_GREEN = GREEN + BRIGHT_SHIFT

        @Suppress("unused")
        const val BRIGHT_YELLOW = YELLOW + BRIGHT_SHIFT

        @Suppress("unused")
        const val BRIGHT_BLUE = BLUE + BRIGHT_SHIFT

        @Suppress("unused")
        const val BRIGHT_PURPLE = PURPLE + BRIGHT_SHIFT

        @Suppress("unused")
        const val BRIGHT_CYAN = CYAN + BRIGHT_SHIFT

        const val BRIGHT_WHITE = WHITE + BRIGHT_SHIFT

    }
}

private interface ColorConsoleDisabled : ColoredConsole {

    override val bold get() = ColoredConsole.Style.NotApplied
    override val <N : ColoredConsole.Style> N.bold: ColoredConsole.Style get() = ColoredConsole.Style.NotApplied
    override val italic get() = ColoredConsole.Style.NotApplied
    override val <N : ColoredConsole.Style> N.italic: ColoredConsole.Style get() = ColoredConsole.Style.NotApplied
    override val underline get() = ColoredConsole.Style.NotApplied
    override val <N : ColoredConsole.Style> N.underline: ColoredConsole.Style get() = ColoredConsole.Style.NotApplied
    override val blink get() = ColoredConsole.Style.NotApplied
    override val <N : ColoredConsole.Style> N.blink: ColoredConsole.Style get() = ColoredConsole.Style.NotApplied
    override val reverse get() = ColoredConsole.Style.NotApplied
    override val <N : ColoredConsole.Style> N.reverse: ColoredConsole.Style get() = ColoredConsole.Style.NotApplied
    override val hidden get() = ColoredConsole.Style.NotApplied
    override val <N : ColoredConsole.Style> N.hidden: ColoredConsole.Style get() = ColoredConsole.Style.NotApplied

    override val red get() = ColoredConsole.Style.NotApplied
    override val <N : ColoredConsole.Style> N.red: ColoredConsole.Style get() = ColoredConsole.Style.NotApplied
    override val black get() = ColoredConsole.Style.NotApplied
    override val <N : ColoredConsole.Style> N.black: ColoredConsole.Style get() = ColoredConsole.Style.NotApplied
    override val green get() = ColoredConsole.Style.NotApplied
    override val <N : ColoredConsole.Style> N.green: ColoredConsole.Style get() = ColoredConsole.Style.NotApplied
    override val yellow get() = ColoredConsole.Style.NotApplied
    override val <N : ColoredConsole.Style> N.yellow: ColoredConsole.Style get() = ColoredConsole.Style.NotApplied
    override val blue get() = ColoredConsole.Style.NotApplied
    override val <N : ColoredConsole.Style> N.blue: ColoredConsole.Style get() = ColoredConsole.Style.NotApplied
    override val purple get() = ColoredConsole.Style.NotApplied
    override val <N : ColoredConsole.Style> N.purple: ColoredConsole.Style get() = ColoredConsole.Style.NotApplied
    override val cyan get() = ColoredConsole.Style.NotApplied
    override val <N : ColoredConsole.Style> N.cyan: ColoredConsole.Style get() = ColoredConsole.Style.NotApplied
    override val white get() = ColoredConsole.Style.NotApplied
    override val <N : ColoredConsole.Style> N.white: ColoredConsole.Style get() = ColoredConsole.Style.NotApplied
}

private val Int.isNormalColor get() = this in BLACK..WHITE
private val Int.isBrightColor get() = this in BRIGHT_BLACK..BRIGHT_WHITE
private val Int.isColor get() = isNormalColor || isBrightColor

private fun String.applyCodes(vararg codes: Int) = "\u001B[${RESET}m".let { reset ->
    val tags = codes.joinToString { "\u001B[${it}m" }
    split(reset).filter { it.isNotEmpty() }.joinToString(separator = "") { tags + it + reset }
}

fun <R> colored(enabled: Boolean = true, block: ColoredConsole.() -> R): R {
    check(true)
    return if (enabled) object : ColoredConsole {}.block() else object : ColorConsoleDisabled {}.block()
}

@Suppress("unused")
fun print(colored: Boolean = true, block: ColoredConsole.() -> String) = colored(colored) { print(block()) }

fun println(colored: Boolean = true, block: ColoredConsole.() -> String) = colored(colored) { println(block()) }