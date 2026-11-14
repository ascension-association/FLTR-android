package org.ascensionassociation.fltr.data

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Runs the API client against an HTTPS server with a self-signed certificate
 * that, like FLTR's, isn't valid for the address it's reached on.
 */
class FltrApiTest {

    private val token = "ab".repeat(32)
    private val cert = HeldCertificate.Builder().commonName("fltr").build()
    private val server = MockWebServer()

    @Before
    fun setUp() {
        server.useHttps(HandshakeCertificates.Builder().heldCertificate(cert).build().sslSocketFactory())
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun api(pin: String = PinnedTls.spkiPin(cert.keyPair.public)): FltrApi {
        // 127.0.0.1 isn't in the certificate: only the pin makes this work.
        val url = "https://127.0.0.1:${server.port}"
        return FltrApi(Pairing(url, token, pin))
    }

    private fun json(body: String, code: Int = 200) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    @Test
    fun trustsThePinnedKeyAndSendsTheToken() = runTest {
        server.enqueue(
            json(
                """{"mode":"queue","fail_closed":true,"protocols":["YouTube"],"started":"2026-10-12T08:00:00Z",
                   "time":"2026-10-12T15:00:00Z","uptime_seconds":25200,"next_reset":"2026-10-13T00:00:00-06:00",
                   "rules":{"ip":1,"mac":3,"ja4":0},"users":2,"some_future_field":1}"""
            )
        )
        val status = api().status()
        assertEquals("queue", status.mode)
        assertEquals(listOf("YouTube"), status.protocols)
        assertEquals("2026-10-13T06:00:00Z", parseTime(status.nextReset).toString())

        val request = server.takeRequest()
        assertEquals("/api/v1/status", request.url.encodedPath)
        assertEquals("Bearer $token", request.headers["Authorization"])
    }

    @Test
    fun rejectsAnyOtherKey() = runTest {
        server.enqueue(json("{}"))
        val other = HeldCertificate.Builder().build()
        try {
            api(pin = PinnedTls.spkiPin(other.keyPair.public)).status()
            fail("expected WrongBox")
        } catch (e: ApiException.WrongBox) {
            // expected
        }
    }

    @Test
    fun decodesUsers() = runTest {
        server.enqueue(
            json(
                """[{"name":"alice","allowance_seconds":7200,"used_seconds":2580,"extra_seconds":1800,
                    "remaining_seconds":6420,"paused":false,"managed":true,
                    "devices":[{"type":"mac","value":"aa:bb:cc:dd:ee:01","source":"settings"},
                               {"type":"ip","value":"192.168.1.23/32","source":"rules"}]}]"""
            )
        )
        val alice = api().users().single()
        assertEquals(9000, alice.totalSeconds)
        assertEquals(6420f / 9000f, alice.remainingFraction, 0.0001f)
        assertEquals(listOf("aa:bb:cc:dd:ee:01"), alice.settingsDevices.map { it.value })
        assertFalse(alice.devices[1].editable)
    }

    @Test
    fun putSendsOnlyDocumentedFields() = runTest {
        server.enqueue(json("""{"name":"bob","allowance_seconds":3600,"devices":[]}"""))
        api().putUser("bob", PutUserBody(allowanceSeconds = 3600, devices = listOf(DeviceBody("ip", "192.168.1.9"))))
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/users/bob", request.url.encodedPath)
        assertEquals(
            """{"allowance_seconds":3600,"devices":[{"type":"ip","value":"192.168.1.9"}]}""",
            request.body?.utf8(),
        )
    }

    @Test
    fun nullAllowanceIsLeftOut() = runTest {
        server.enqueue(json("""{"name":"bob"}"""))
        api().putUser("bob", PutUserBody(devices = listOf(DeviceBody("mac", "aa:bb:cc:dd:ee:02"))))
        assertEquals("""{"devices":[{"type":"mac","value":"aa:bb:cc:dd:ee:02"}]}""", server.takeRequest().body?.utf8())
    }

    @Test
    fun pauseAndExtraUseTheRightMethods() = runTest {
        repeat(3) { server.enqueue(json("""{"name":"alice","paused":true}""")) }
        val a = api()
        assertTrue(a.setPaused("alice", true).paused)
        a.setPaused("alice", false)
        a.addExtra("alice", -900)
        assertEquals("PUT", server.takeRequest().method)
        assertEquals("DELETE", server.takeRequest().method)
        val extra = server.takeRequest()
        assertEquals("/api/v1/users/alice/extra", extra.url.encodedPath)
        assertEquals("""{"seconds":-900}""", extra.body?.utf8())
    }

    @Test
    fun blocksQueryAndClassField() = runTest {
        server.enqueue(
            json(
                """[{"time":"2026-10-12T22:19:17.702995018Z","proto":"tcp","client":"192.168.1.10","client_port":35338,
                    "server":"142.250.1.1","server_port":443,"class":"TLS.YouTube","host":"www.youtube.com",
                    "rule":{"type":"ip","value":"192.168.1.0/28"},"reason":"allowance_exhausted","mode":"queue"}]"""
            )
        )
        val block = api().blocks(user = "alice", limit = 5000).single()
        assertEquals("YouTube", block.app)
        assertEquals(BlockEntry.REASON_ALLOWANCE, block.reason)
        val url = server.takeRequest().url
        assertEquals("alice", url.queryParameter("user"))
        assertEquals("1000", url.queryParameter("limit"))
    }

    @Test
    fun deleteAccepts204() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())
        api().deleteUser("bob")
        assertEquals("DELETE", server.takeRequest().method)
    }

    @Test
    fun mapsHttpErrors() {
        assertTrue(FltrApi.httpError(401, "{}", null) is ApiException.Unauthorized)
        assertTrue(FltrApi.httpError(403, """{"error":"forbidden"}""", null) is ApiException.NotHome)
        val limited = FltrApi.httpError(429, "", " 42 ")
        assertEquals(42L, (limited as ApiException.RateLimited).retryAfterSeconds)
        assertNull((FltrApi.httpError(429, "", "soon") as ApiException.RateLimited).retryAfterSeconds)
        val bad = FltrApi.httpError(400, """{"error":"allowance_seconds 90000 out of range (0 to 86400)"}""", null)
        assertEquals("Allowance_seconds 90000 out of range (0 to 86400)", bad.message)
        assertTrue(FltrApi.httpError(404, "", null) is ApiException.NotFound)
        assertTrue(FltrApi.httpError(500, "not json", null) is ApiException.Server)
    }

    @Test
    fun unreachableServer() = runTest {
        val port = server.port
        server.close()
        try {
            FltrApi(Pairing("https://127.0.0.1:$port", token, PinnedTls.spkiPin(cert.keyPair.public))).status()
            fail("expected Unreachable")
        } catch (e: ApiException.Unreachable) {
            // expected
        }
    }
}
