// SPDX-FileCopyrightText: 2023 DAVx⁵ contributors <https://github.com/bitfireAT/davx5-ose/graphs/contributors>
//
// SPDX-License-Identifier: GPL-3.0-only

/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.settings

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class SettingsManagerTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var settingsManager: SettingsManager

    @Before
    fun inject() {
        hiltRule.inject()
    }


    @Test
    fun testContainsKey_NotExisting() {
        assertFalse(settingsManager.containsKey("notExisting"))
    }

    @Test
    fun testContainsKey_Existing() {
        // provided by DefaultsProvider
        assertEquals(Settings.PROXY_TYPE_SYSTEM, settingsManager.getInt(Settings.PROXY_TYPE))
    }

}