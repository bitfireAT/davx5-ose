/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.dokka) apply false
}

group = "at.bitfire"
version = System.getenv("GIT_COMMIT")