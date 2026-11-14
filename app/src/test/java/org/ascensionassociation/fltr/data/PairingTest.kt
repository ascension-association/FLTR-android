package org.ascensionassociation.fltr.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PairingTest {

    private val token = "0123456789abcdef".repeat(4)
    private val pinB64 = "47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU="

    // Exactly what Go's url.Values.Encode produces: keys sorted, '/', ':', '+' and '=' escaped.
    private val fromFltr =
        "fltr://pair?pin=sha256%2F47DEQpj8HBSa%2B%2FTImW%2B5JCeuQeRkm5NMpJWZG3hSuFU%3D" +
            "&token=$token&url=https%3A%2F%2F192.168.1.1%3A8443"

    @Test
    fun parsesTheLinkFltrLogs() {
        val p = Pairing.parse(fromFltr)
        assertEquals("https://192.168.1.1:8443", p.url)
        assertEquals(token, p.token)
        assertEquals("sha256/$pinB64", p.pin)
        assertEquals("192.168.1.1:8443", p.displayHost)
    }

    @Test
    fun toleratesWhitespaceTrailingSlashUppercaseAndUnescapedPlus() {
        val raw = "  FLTR://pair?url=https://fltr.lan/&token=${token.uppercase()}&pin=sha256/$pinB64\n"
        val p = Pairing.parse(raw)
        assertEquals("https://fltr.lan", p.url)
        assertEquals(token, p.token)
        assertEquals("sha256/$pinB64", p.pin)
        assertEquals("fltr.lan", p.displayHost)
    }

    @Test
    fun rejectsOtherCodes() {
        assertThrows(InvalidPairingException::class.java) { Pairing.parse("https://example.com") }
        assertThrows(InvalidPairingException::class.java) { Pairing.parse("fltr://pair") }
        assertThrows(InvalidPairingException::class.java) { Pairing.parse("WIFI:S:home;T:WPA;P:secret;;") }
    }

    @Test
    fun rejectsPlainHttp() {
        assertThrows(InvalidPairingException::class.java) {
            Pairing.parse(fromFltr.replace("https%3A", "http%3A"))
        }
    }

    @Test
    fun rejectsDamagedTokenOrPin() {
        assertThrows(InvalidPairingException::class.java) { Pairing.parse(fromFltr.replace(token, token.drop(2))) }
        assertThrows(InvalidPairingException::class.java) { Pairing.parse(fromFltr.replace("sha256%2F", "md5%2F")) }
        assertThrows(InvalidPairingException::class.java) { Pairing.parse(fromFltr.replace("47DEQ", "")) }
    }
}
