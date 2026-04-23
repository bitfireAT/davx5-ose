/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import android.content.Context
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.junit4.MockKRule
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.unifiedpush.android.connector.UnifiedPush
import javax.inject.Inject

@HiltAndroidTest
class PushDistributorManagerTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mockKRule = MockKRule(this)

    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var settingsManager: SettingsManager

    @Inject
    lateinit var pushDistributorManager: PushDistributorManager

    @Before
    fun setUp() {
        hiltRule.inject()
        
        // Mock UnifiedPush static methods
        mockkStatic(UnifiedPush::class)
        
        // Clear any existing settings
        settingsManager.remove(Settings.PUSH_ENABLED)
    }
    
    @Test
    fun testGetCurrentDistributor_PushDisabled() {
        // Given push is disabled
        settingsManager.putBoolean(Settings.PUSH_ENABLED, false)

        // When getting current distributor
        val result = pushDistributorManager.getCurrentDistributor()

        // Then result is null
        assertNull(result)
    }
    
    @Test
    fun testGetCurrentDistributor_PushEnabled_DistributorSet() {
        // Given push is enabled and distributor is set
        settingsManager.putBoolean(Settings.PUSH_ENABLED, true)
        every { UnifiedPush.getSavedDistributor(any()) } returns "com.example.distributor"
        
        // When getting current distributor
        val result = pushDistributorManager.getCurrentDistributor()
        
        // Then result is the distributor
        assertEquals("com.example.distributor", result)
    }
    
    @Test
    fun testGetCurrentDistributor_PushEnabled_NoDistributor() {
        // Given push is enabled but no distributor is set
        settingsManager.putBoolean(Settings.PUSH_ENABLED, true)
        
        // When getting current distributor
        val result = pushDistributorManager.getCurrentDistributor()
        
        // Then result is null
        assertNull(result)
    }


    @Test
    fun testSetPushDistributor() {
        // Given mock behavior
        every { UnifiedPush.saveDistributor(any(), any()) } just runs
        
        // When setting distributor
        pushDistributorManager.setPushDistributor("com.example.newdistributor")
        
        // Then distributor is stored
        verify { UnifiedPush.saveDistributor(context, "com.example.newdistributor") }
    }
    
    @Test
    fun testIsPushEnabled_DefaultSetting() {
        // Given no setting exists
        // (already cleared in setUp)
        
        // When checking if push is enabled
        val result = pushDistributorManager.isPushEnabled()
        
        // Then result is true (default)
        assertTrue(result)
    }
    
    @Test
    fun testIsPushEnabled_SettingExists() {
        // Given setting exists
        settingsManager.putBoolean(Settings.PUSH_ENABLED, false)
        
        // When checking if push is enabled
        val result = pushDistributorManager.isPushEnabled()
        
        // Then result is the stored value
        assertFalse(result)
    }
    
    @Test
    fun testSetPushEnabled_WhenDisabling_RemovesDistributorAndUpdatesSetting() {
        // Given distributor is set
        every { UnifiedPush.getSavedDistributor(any()) } returns "com.example.distributor"
        every { UnifiedPush.removeDistributor(any()) } just runs
        
        // When disabling push
        pushDistributorManager.setPushEnabled(false)
        
        // Then setting is stored and distributor is removed
        val isEnabled = settingsManager.getBooleanOrNull(Settings.PUSH_ENABLED)
        assertFalse(isEnabled!!)
        
        verify { UnifiedPush.removeDistributor(context) }
        verify(exactly = 0) { UnifiedPush.saveDistributor(any(), any()) }
    }
    
    @Test
    fun testSetPushEnabled_WhenEnabling_UpdatesSetting() {
        // Given distributor is set but push was disabled
        settingsManager.putBoolean(Settings.PUSH_ENABLED, false)
        
        // When enabling push
        pushDistributorManager.setPushEnabled(true)
        
        // Then setting is stored
        val isEnabled = settingsManager.getBooleanOrNull(Settings.PUSH_ENABLED)
        assertTrue(isEnabled!!)
        
        // Should not call saveDistributor when enabling
        verify(exactly = 0) { UnifiedPush.saveDistributor(any(), any()) }
    }

}