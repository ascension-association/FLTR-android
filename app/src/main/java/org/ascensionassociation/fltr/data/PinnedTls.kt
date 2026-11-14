package org.ascensionassociation.fltr.data

import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

/**
 * FLTR's API certificate is self-signed and not tied to a host name, so it is
 * trusted by public-key pin alone (docs/API.md, "TLS: trust by pin"):
 *
 * 1. accept the server only if SHA-256(SubjectPublicKeyInfo) equals the pin;
 * 2. skip host name verification, which the pin makes redundant and which
 *    would break whenever the box's address changes.
 */
object PinnedTls {

    /** `sha256/<base64>` of the key's DER-encoded SubjectPublicKeyInfo. */
    fun spkiPin(key: PublicKey): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.encoded)
        return "sha256/" + java.util.Base64.getEncoder().encodeToString(digest)
    }

    fun client(pin: String, base: OkHttpClient = OkHttpClient()): OkHttpClient {
        val trustManager = PinnedTrustManager(pin)
        val context = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), SecureRandom())
        }
        return base.newBuilder()
            .sslSocketFactory(context.socketFactory, trustManager)
            // Safe only because PinnedTrustManager rejects every other key.
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}

/** Thrown when the server presents a key other than the paired one. */
class PinMismatchException : CertificateException("The server's certificate doesn't match the paired FLTR box.")

class PinnedTrustManager(private val pin: String) : X509TrustManager {

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("The server sent no certificate.")
        val actual = PinnedTls.spkiPin(leaf.publicKey)
        if (!MessageDigest.isEqual(actual.toByteArray(), pin.toByteArray())) {
            throw PinMismatchException()
        }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("Client certificates are not used.")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
