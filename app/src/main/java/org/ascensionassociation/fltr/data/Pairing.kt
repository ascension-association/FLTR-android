package org.ascensionassociation.fltr.data

import java.net.URI
import java.net.URLDecoder
import java.util.Base64
import kotlinx.serialization.Serializable

/**
 * What the FLTR pairing QR code carries (see FLTR-appliance docs/API.md):
 *
 *     fltr://pair?url=https%3A%2F%2F192.168.1.1%3A8443&token=<64 hex>&pin=sha256%2F<base64>
 *
 * [url] is the API base URL without a trailing slash, [token] the bearer
 * token, and [pin] the SHA-256 of the certificate's SubjectPublicKeyInfo.
 * Boxes running older FLTR releases print `gkfltr://`, which is accepted too.
 */
@Serializable
data class Pairing(val url: String, val token: String, val pin: String) {

    /** The host (and port, if not 443) to show people, e.g. `192.168.1.1:8443`. */
    val displayHost: String
        get() = runCatching {
            val u = URI(url)
            if (u.port == -1 || u.port == 443) u.host else "${u.host}:${u.port}"
        }.getOrDefault(url)

    companion object {
        private val TOKEN = Regex("[0-9a-fA-F]{64}")
        private const val PIN_PREFIX = "sha256/"
        val SCHEMES = listOf("fltr", "gkfltr")

        /**
         * Parses a pairing link. Throws [InvalidPairingException] with a
         * message suitable for showing to the person pairing.
         */
        fun parse(raw: String): Pairing {
            val text = raw.trim()
            val q = text.indexOf('?')
            val head = if (q < 0) text else text.substring(0, q)
            if (SCHEMES.none { head.equals("$it://pair", ignoreCase = true) } || q < 0) {
                throw InvalidPairingException("That isn't an FLTR pairing code.")
            }
            val params = text.substring(q + 1)
                .split('&')
                .filter { it.isNotEmpty() }
                .associate { part ->
                    val eq = part.indexOf('=')
                    val key = if (eq < 0) part else part.substring(0, eq)
                    val value = if (eq < 0) "" else part.substring(eq + 1)
                    // Go's url.Values.Encode escapes '+' as %2B, so a literal
                    // '+' only appears if someone retyped the link: keep it.
                    decode(key) to decode(value.replace("+", "%2B"))
                }

            val url = params["url"].orEmpty().trimEnd('/')
            val token = params["token"].orEmpty()
            val pin = params["pin"].orEmpty()

            val uri = runCatching { URI(url) }.getOrNull()
            if (uri == null || !uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrEmpty()) {
                throw InvalidPairingException("The pairing code has no valid https address.")
            }
            if (!TOKEN.matches(token)) {
                throw InvalidPairingException("The pairing code's token is damaged. Scan it again.")
            }
            if (!pin.startsWith(PIN_PREFIX) || decodedLength(pin.removePrefix(PIN_PREFIX)) != 32) {
                throw InvalidPairingException("The pairing code's certificate pin is damaged. Scan it again.")
            }
            return Pairing(url = url, token = token.lowercase(), pin = pin)
        }

        private fun decode(s: String): String = URLDecoder.decode(s, Charsets.UTF_8.name())

        private fun decodedLength(b64: String): Int =
            runCatching { Base64.getDecoder().decode(b64).size }.getOrDefault(-1)
    }
}

class InvalidPairingException(message: String) : IllegalArgumentException(message)
