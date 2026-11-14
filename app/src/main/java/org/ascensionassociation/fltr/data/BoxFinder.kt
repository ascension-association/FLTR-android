package org.ascensionassociation.fltr.data

import android.annotation.SuppressLint
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URI
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** An FLTR box that answered on the home network, and the pin of the key it presented. */
data class FoundBox(val url: String, val pin: String, val name: String, val open: Boolean) {
    val displayHost: String
        get() = runCatching {
            val u = URI(url)
            if (u.port == -1 || u.port == 443) u.host else "${u.host}:${u.port}"
        }.getOrDefault(url)
}

/**
 * Finds FLTR boxes on the phone's subnet and pairs with one without the QR
 * code, which FLTR allows until the first app pairs (docs/API.md, "Pairing
 * without the QR code").
 *
 * There is no pin to check yet, so this trusts on first use: [probe] accepts
 * any certificate and records its pin, and [claim] then talks only to that
 * key, so the token can't go to some other device.
 */
class BoxFinder(
    private val port: Int = 8443,
    private val probeBase: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build(),
) {

    /** Boxes on [subnet], trying the gateway (where FLTR often is) before the rest. */
    suspend fun find(subnet: Subnet): List<FoundBox> {
        val (first, rest) = candidates(subnet)
        val atGateway = probeAll(first)
        if (atGateway.any { it.open }) return atGateway
        return atGateway + probeAll(rest)
    }

    private suspend fun probeAll(hosts: List<String>): List<FoundBox> = coroutineScope {
        hosts.map { host -> async(PROBES) { probe("https://${hostPort(host)}") } }.awaitAll().filterNotNull()
    }

    private fun hostPort(host: String) = if (':' in host) "[$host]:$port" else "$host:$port"

    /** Asks the server at [baseUrl] whether it's FLTR; null if it isn't or doesn't answer. */
    internal suspend fun probe(baseUrl: String): FoundBox? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/api/v1/pair").header("Accept", "application/json").build()
        val recorder = RecordingTrustManager()
        try {
            trustOnFirstUse(probeBase, recorder).newCall(request).execute().use { response ->
                // Not response.handshake: OkHttp can't build a trusted chain
                // for a self-signed certificate, so it reports none there.
                val leaf = recorder.leaf
                if (!response.isSuccessful || leaf == null) return@withContext null
                val info = FltrApi.JSON.decodeFromString(PairInfo.serializer(), response.body.string())
                if (!info.fltr) return@withContext null
                FoundBox(baseUrl, PinnedTls.spkiPin(leaf.publicKey), info.name, info.open)
            }
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) { // not JSON, or not FLTR's
            null
        }
    }

    /**
     * Gets the token from [box], over a connection pinned to the key it
     * presented when found. Throws [ApiException.AlreadyPaired] once some
     * app has paired.
     */
    suspend fun claim(box: FoundBox, client: OkHttpClient = PinnedTls.client(box.pin)): Pairing =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${box.url}/api/v1/pair")
                .header("Accept", "application/json")
                .post(ByteArray(0).toRequestBody(null))
                .build()
            val text = try {
                client.newCall(request).execute().use { response ->
                    val body = response.body.string()
                    if (!response.isSuccessful) {
                        throw FltrApi.httpError(response.code, body, response.header("Retry-After"))
                    }
                    body
                }
            } catch (e: ApiException) {
                throw e
            } catch (e: IOException) {
                throw FltrApi.networkError(e)
            }
            val token = runCatching { FltrApi.JSON.decodeFromString(TokenBody.serializer(), text).token }.getOrNull()
            if (token == null || !TOKEN.matches(token)) {
                throw ApiException.Server(200, "FLTR sent a damaged pairing token. Try again.")
            }
            Pairing(url = box.url, token = token.lowercase(), pin = box.pin)
        }

    @Serializable
    private data class PairInfo(val fltr: Boolean = false, val name: String = "", val open: Boolean = false)

    @Serializable
    private data class TokenBody(val token: String)

    companion object {
        private val TOKEN = Regex("[0-9a-fA-F]{64}")

        /** How many hosts are probed at once. */
        private val PROBES = Dispatchers.IO.limitedParallelism(64)

        /** Scanning more than a /24 would take minutes; larger networks are scanned around the phone. */
        private const val WIDEST_PREFIX = 24

        /**
         * The hosts to try: the gateways first, then every other address of
         * the phone's subnet (at most the /24 around it), but not the phone.
         */
        internal fun candidates(subnet: Subnet): Pair<List<String>, List<String>> {
            val own = toInt(subnet.address)
            val prefix = if (subnet.prefixLength in WIDEST_PREFIX..30) subnet.prefixLength else WIDEST_PREFIX
            val size = 1 shl (32 - prefix)
            val network = own and (-1 shl (32 - prefix))
            val gateways = subnet.gateways.map { it.hostAddress!! }.distinct()
            val rest = (1 until size - 1)
                .map { network + it }
                .filter { it != own }
                .map { fromInt(it).hostAddress!! }
                .filter { it !in gateways }
            return gateways to rest
        }

        private fun toInt(a: Inet4Address): Int =
            a.address.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xff) }

        private fun fromInt(v: Int): InetAddress =
            InetAddress.getByAddress(ByteArray(4) { i -> (v ushr (24 - 8 * i)).toByte() })

        /**
         * A client for one probe that accepts any certificate, so a box can
         * be found before its pin is known, and records it in [recorder].
         * Only [probe] uses it, to learn the pin; nothing secret is sent
         * over it.
         */
        private fun trustOnFirstUse(base: OkHttpClient, recorder: RecordingTrustManager): OkHttpClient {
            val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(recorder), SecureRandom()) }
            return base.newBuilder()
                .sslSocketFactory(context.socketFactory, recorder)
                .hostnameVerifier { _, _ -> true }
                .build()
        }
    }
}

/** Accepts any server certificate and keeps the leaf, for [BoxFinder.probe] alone. */
@SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
private class RecordingTrustManager : X509TrustManager {
    @Volatile
    var leaf: X509Certificate? = null
        private set

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        leaf = chain?.firstOrNull() ?: throw CertificateException("The server sent no certificate.")
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("Client certificates are not used.")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
