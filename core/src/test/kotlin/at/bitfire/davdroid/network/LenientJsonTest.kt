/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Test

class LenientJsonTest {

    @Serializable
    data class TestDataClass(
        val name: String,
        val value: Int
    )

    @Test
    fun `lenientJson parses trailing comma`() {
        // Test that lenient JSON can parse JSON with trailing commas
        val jsonString = "{\"name\": \"test\", \"value\": 42,}"
        
        val result = lenientJson.decodeFromString<TestDataClass>(jsonString)
        assertEquals("test", result.name)
        assertEquals(42, result.value)
    }

    @Test
    fun `lenientJson parses unquoted keys`() {
        // Test that lenient JSON can parse JSON with unquoted keys
        val jsonString = "{name: \"test\", value: 42}"
        
        val result = lenientJson.decodeFromString<TestDataClass>(jsonString)
        assertEquals("test", result.name)
        assertEquals(42, result.value)
    }

    @Test
    fun `lenientJson ignores unknown keys`() {
        // Test that unknown keys are ignored during deserialization
        val jsonString = "{\"name\": \"test\", \"value\": 42, \"unknownKey\": \"shouldBeIgnored\", \"anotherUnknown\": 123}"
        
        val result = lenientJson.decodeFromString<TestDataClass>(jsonString)
        assertEquals("test", result.name)
        assertEquals(42, result.value)
    }

    @Test
    fun `lenientJson parses normal JSON`() {
        // Test that well-formed JSON works correctly
        val jsonString = "{\"name\": \"test\", \"value\": 42}"
        
        val result = lenientJson.decodeFromString<TestDataClass>(jsonString)
        assertEquals("test", result.name)
        assertEquals(42, result.value)
    }

}