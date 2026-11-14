package org.ascensionassociation.fltr.util

import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTest {

    @Test
    fun durations() {
        assertEquals("0m", formatDuration(0))
        assertEquals("0m", formatDuration(-5))
        assertEquals("<1m", formatDuration(30))
        assertEquals("45m", formatDuration(45 * 60))
        assertEquals("2h", formatDuration(7200))
        assertEquals("1h 47m", formatDuration(6420))
        assertEquals("24h", formatDuration(86400))
    }

    @Test
    fun relativeTimes() {
        val now = Instant.parse("2026-11-14T15:00:00Z")
        val utc = ZoneOffset.UTC
        assertEquals("just now", formatRelative(now.minusSeconds(20), now, utc))
        assertEquals("5 min ago", formatRelative(now.minusSeconds(300), now, utc))
        assertEquals("3 h ago", formatRelative(now.minusSeconds(3 * 3600), now, utc))
        assertEquals("yesterday", formatRelative(Instant.parse("2026-11-13T01:00:00Z"), now, utc))
    }

    @Test
    fun userNames() {
        assertTrue(isValidUserName("alice"))
        assertTrue(isValidUserName("kid_2.tablet-1"))
        assertFalse(isValidUserName("Alice Smith"))
        assertFalse(isValidUserName(""))
        assertFalse(isValidUserName("é"))
        assertEquals("Alice", displayName("alice"))
    }

    @Test
    fun deviceValues() {
        assertEquals("192.168.1.23", displayDeviceValue("ip", "192.168.1.23/32"))
        assertEquals("192.168.1.0/28", displayDeviceValue("ip", "192.168.1.0/28"))
        assertTrue(isIpRange("ip", "192.168.1.0/28"))
        assertFalse(isIpRange("ip", "192.168.1.23/32"))
        assertFalse(isIpRange("mac", "aa:bb:cc:dd:ee:ff"))
    }
}
