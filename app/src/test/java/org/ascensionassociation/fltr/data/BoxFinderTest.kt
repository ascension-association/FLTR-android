package org.ascensionassociation.fltr.data

import java.net.Inet4Address
import java.net.InetAddress
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class BoxFinderTest {

    private val token = "cd".repeat(32)
    private val cert = HeldCertificate.Builder().commonName("fltr").build()
    private val pin = PinnedTls.spkiPin(cert.keyPair.public)
    private val server = MockWebServer()
    private val finder = BoxFinder()
    private val url get() = "https://127.0.0.1:${server.port}"

    @Before
    fun setUp() {
        server.useHttps(HandshakeCertificates.Builder().heldCertificate(cert).build().sslSocketFactory())
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun json(body: String, code: Int = 200) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    private fun v4(s: String) = InetAddress.getByName(s) as Inet4Address

    @Test
    fun candidatesPutTheGatewayFirstAndSkipThePhone() {
        val (first, rest) = BoxFinder.candidates(Subnet(v4("192.168.1.37"), 24, listOf(v4("192.168.1.1"))))
        assertEquals(listOf("192.168.1.1"), first)
        assertEquals(252, rest.size) // .1 to .254, less the gateway and the phone
        assertEquals("192.168.1.2", rest.first())
        assertEquals("192.168.1.254", rest.last())
        assertTrue("192.168.1.37" !in rest)
    }

    @Test
    fun candidatesScanOnlyTheSlash24AroundThePhoneOnWideNetworks() {
        val (_, rest) = BoxFinder.candidates(Subnet(v4("10.0.5.9"), 16, emptyList()))
        assertEquals("10.0.5.1", rest.first())
        assertEquals("10.0.5.254", rest.last())
    }

    @Test
    fun probeRecordsThePinOfAnOpenBox() = runTest {
        server.enqueue(json("""{"fltr":true,"name":"fltr-1","open":true}"""))
        val box = finder.probe(url)!!
        assertEquals(FoundBox(url, pin, "fltr-1", open = true), box)
        assertEquals("/api/v1/pair", server.takeRequest().url.encodedPath)
    }

    @Test
    fun probeIgnoresOtherServers() = runTest {
        server.enqueue(json("""{"hello":"world"}"""))
        assertNull(finder.probe(url))
        server.enqueue(MockResponse.Builder().body("<html>router login</html>").build())
        assertNull(finder.probe(url))
        server.enqueue(json("{}", code = 401))
        assertNull(finder.probe(url))
    }

    @Test
    fun claimGetsTheTokenOverThePinnedKey() = runTest {
        server.enqueue(json("""{"token":"$token"}"""))
        val pairing = finder.claim(FoundBox(url, pin, "fltr-1", true))
        assertEquals(Pairing(url, token, pin), pairing)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertNull(request.headers["Authorization"])
    }

    @Test
    fun claimRefusesAKeyOtherThanTheOneFound() = runTest {
        server.enqueue(json("""{"token":"$token"}"""))
        val other = PinnedTls.spkiPin(HeldCertificate.Builder().build().keyPair.public)
        try {
            finder.claim(FoundBox(url, other, "fltr-1", true))
            fail("expected WrongBox")
        } catch (e: ApiException.WrongBox) {
            // expected
        }
    }

    @Test
    fun claimReportsAnotherAppPairedFirst() = runTest {
        server.enqueue(json("""{"error":"an app has already paired"}""", code = 409))
        try {
            finder.claim(FoundBox(url, pin, "fltr-1", true))
            fail("expected AlreadyPaired")
        } catch (e: ApiException.AlreadyPaired) {
            // expected
        }
    }
}
