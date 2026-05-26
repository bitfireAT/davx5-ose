/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools

import android.os.Build
import io.mockk.MockKVerificationScope
import io.mockk.verify
import java.util.logging.Logger

/**
 * Wrapper for Mockk [verify] for different API levels.
 *
 * On Android P (API 28) and above, it performs the verification as specified.
 * On lower Android versions, it logs a warning and skips the verification to maintain compatibility.
 *
 * Not necessary for all [verify] calls, see https://mockk.io/ANDROID.html.
 */
fun verifyCompat(exactly: Int = -1, verifyBlock: MockKVerificationScope.() -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        verify(exactly = exactly, verifyBlock = verifyBlock)
    else
        Logger.getLogger("MockkCompat.verifyCompat").warning("Ignoring verify { } block on Android <P")
}