package at.bitfire.davdroid.ui.setup

import androidx.compose.runtime.Composable

interface LoginType {

    val title: String

    /** Whether this login type is shown in the generic section and expanded with [SelectorContent] as soon as it is selected. */
    val isGeneric: Boolean

    /** Order within the login type selection (high numbers first, `null` last). Login types with same order number are
     * sorted alphabetically (locale-dependent). */
    val order: Int?

    /**
     * Appears when the login type is [isGeneric] and selected.
     */
    @Composable
    fun SelectorContent()

    /**
     * If [isGeneric]: whether the
     */
    fun canContinue(): Boolean

}