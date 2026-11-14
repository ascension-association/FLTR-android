package org.ascensionassociation.fltr.data

import java.io.IOException
import java.time.Instant
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/** Why a call to FLTR failed, in terms the UI can act on. */
sealed class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** No connection at all: off the home network, or the box is down. */
    class Unreachable(cause: Throwable?) : ApiException("Can't reach your FLTR box.", cause)

    /** 403, or a connection FLTR closed before TLS: not on the home network. */
    class NotHome(cause: Throwable? = null) : ApiException("FLTR only answers on your home network.", cause)

    /** The server's key doesn't match the pin: some other device has this address. */
    class WrongBox(cause: Throwable?) : ApiException("The device at this address isn't your FLTR box.", cause)

    /** 401: the token was replaced with -unpair. */
    class Unauthorized : ApiException("This phone is no longer paired with FLTR.")

    /** 429: too many failed logins from this address. */
    class RateLimited(val retryAfterSeconds: Long?) : ApiException(
        "FLTR is pausing logins from this phone" +
            (retryAfterSeconds?.let { " for $it seconds." } ?: ".")
    )

    /** 409 from POST /pair: another app paired first, so only the QR code works now. */
    class AlreadyPaired : ApiException("Your FLTR box is already paired with another phone.")

    class NotFound(message: String) : ApiException(message)
    class BadRequest(message: String) : ApiException(message)
    class Server(val code: Int, message: String) : ApiException(message)
}

/**
 * Client for FLTR's app API v1 (docs/API.md). Every call needs the bearer
 * token from pairing and goes through the pinned [client].
 */
class FltrApi(
    private val pairing: Pairing,
    private val client: OkHttpClient = PinnedTls.client(pairing.pin),
) {
    private val base: HttpUrl = pairing.url.toHttpUrl()

    suspend fun status(): FltrStatus = get(url("status"), FltrStatus.serializer())

    suspend fun users(): List<FltrUser> = get(url("users"), ListSerializer(FltrUser.serializer()))

    suspend fun user(name: String): FltrUser = get(url("users", name), FltrUser.serializer())

    suspend fun putUser(name: String, body: PutUserBody): FltrUser =
        call("PUT", url("users", name), json(PutUserBody.serializer(), body), FltrUser.serializer())

    suspend fun deleteUser(name: String) {
        execute("DELETE", url("users", name), null)
    }

    /** Grants (or with a negative value, takes back) extra time until the next reset. */
    suspend fun addExtra(name: String, seconds: Long): FltrUser =
        call("POST", url("users", name, "extra"), json(ExtraBody.serializer(), ExtraBody(seconds)), FltrUser.serializer())

    suspend fun setPaused(name: String, paused: Boolean): FltrUser =
        call(if (paused) "PUT" else "DELETE", url("users", name, "pause"), null, FltrUser.serializer())

    suspend fun devices(): List<SeenDevice> = get(url("devices"), ListSerializer(SeenDevice.serializer()))

    /** Names a device, or with an empty [name] removes its name. */
    suspend fun setName(type: String, value: String, name: String): NameBody =
        call("PUT", url("names"), json(NameBody.serializer(), NameBody(type, value, name)), NameBody.serializer())

    /** Recent blocks, oldest first, as the API returns them. */
    suspend fun blocks(user: String? = null, since: Instant? = null, limit: Int = 200): List<BlockEntry> {
        val u = url("blocks").newBuilder().apply {
            user?.let { addQueryParameter("user", it) }
            since?.let { addQueryParameter("since", it.toString()) }
            addQueryParameter("limit", limit.coerceIn(1, 1000).toString())
        }.build()
        return get(u, ListSerializer(BlockEntry.serializer()))
    }

    private fun url(vararg segments: String): HttpUrl =
        base.newBuilder().addPathSegments("api/v1").apply { segments.forEach { addPathSegment(it) } }.build()

    private fun <T> json(serializer: KSerializer<T>, value: T): RequestBody =
        JSON.encodeToString(serializer, value).toRequestBody(JSON_TYPE)

    private suspend fun <T> get(url: HttpUrl, serializer: KSerializer<T>): T = call("GET", url, null, serializer)

    /** Sends a request and decodes the JSON response. */
    private suspend fun <T> call(method: String, url: HttpUrl, body: RequestBody?, serializer: KSerializer<T>): T =
        JSON.decodeFromString(serializer, execute(method, url, body))

    /** Sends a request and returns the response body; throws [ApiException] on any failure. */
    private suspend fun execute(method: String, url: HttpUrl, body: RequestBody?): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${pairing.token}")
                .header("Accept", "application/json")
                .method(method, body ?: if (method == "PUT" || method == "POST") EMPTY_BODY else null)
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    val text = response.body.string()
                    if (!response.isSuccessful) {
                        throw httpError(response.code, text, response.header("Retry-After"))
                    }
                    text
                }
            } catch (e: ApiException) {
                throw e
            } catch (e: IOException) {
                throw networkError(e)
            }
        }

    companion object {
        val JSON = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = false
            coerceInputValues = true
        }
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        private val EMPTY_BODY = ByteArray(0).toRequestBody(null)

        internal fun httpError(code: Int, body: String, retryAfter: String?): ApiException {
            val message = runCatching { JSON.decodeFromString(ErrorBody.serializer(), body).error }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.replaceFirstChar { it.uppercase() }
                ?: "FLTR answered with HTTP $code."
            return when (code) {
                400 -> ApiException.BadRequest(message)
                401 -> ApiException.Unauthorized()
                403 -> ApiException.NotHome()
                404 -> ApiException.NotFound(message)
                409 -> ApiException.AlreadyPaired()
                429 -> ApiException.RateLimited(retryAfter?.trim()?.toLongOrNull())
                else -> ApiException.Server(code, message)
            }
        }

        internal fun networkError(e: IOException): ApiException {
            var cause: Throwable? = e
            while (cause != null) {
                if (cause is PinMismatchException) return ApiException.WrongBox(e)
                cause = cause.cause
            }
            // FLTR drops connections from outside the home network before
            // TLS, which shows up as a handshake failure or a reset.
            return if (e is SSLException) ApiException.NotHome(e) else ApiException.Unreachable(e)
        }
    }
}
