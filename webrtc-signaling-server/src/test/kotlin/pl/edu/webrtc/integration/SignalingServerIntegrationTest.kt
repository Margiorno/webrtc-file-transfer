package pl.edu.webrtc.integration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import jakarta.inject.Inject
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import pl.edu.webrtc.domain.model.JoinDecision
import pl.edu.webrtc.domain.model.RoomState
import pl.edu.webrtc.domain.model.SecurityMode
import pl.edu.webrtc.infrastructure.web.*
import pl.edu.webrtc.infrastructure.websocket.IncomingWsMessage
import pl.edu.webrtc.infrastructure.websocket.WsMessageType
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@QuarkusTest
class SignalingServerIntegrationTest {

    @TestHTTPResource("/")
    lateinit var baseUri: URI

    @Inject
    lateinit var objectMapper: ObjectMapper

    private val httpClient: HttpClient = HttpClient.newHttpClient()

    private inner class TestWsClient(roomCode: String) {
        val messages = LinkedBlockingQueue<JsonNode>()
        private val buffer = StringBuilder()
        val socket: WebSocket

        init {
            val wsUri = URI.create(
                "ws://${baseUri.host}:${baseUri.port}/ws/signaling/$roomCode"
            )
            socket = httpClient.newWebSocketBuilder()
                .buildAsync(wsUri, object : WebSocket.Listener {
                    override fun onText(
                        webSocket: WebSocket,
                        data: CharSequence,
                        last: Boolean
                    ): CompletionStage<*> {
                        buffer.append(data)
                        if (last) {
                            val fullText = buffer.toString()
                            buffer.setLength(0)
                            messages.offer(objectMapper.readTree(fullText))
                        }
                        webSocket.request(1)
                        return CompletableFuture.completedFuture(null)
                    }
                }).join()
        }

        fun send(message: IncomingWsMessage) {
            val json = objectMapper.writeValueAsString(message)
            socket.sendText(json, true).join()
        }

        fun awaitMessage(timeoutSeconds: Long = 5): JsonNode {
            val msg = messages.poll(timeoutSeconds, TimeUnit.SECONDS)
            assertNotNull(msg, "Expected WebSocket message within $timeoutSeconds seconds, but none arrived")
            return msg!!
        }

        fun close() {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "Test finished").join()
        }
    }

    @Test
    fun `E2E STANDARD flow - create room, join, accept, and exchange WebRTC SDP and ICE over WebSocket`() {
        val created = given()
            .contentType(ContentType.JSON)
            .body(CreateRoomRequest(SecurityMode.STANDARD))
            .`when`()
            .post("/api/rooms")
            .then()
            .statusCode(201)
            .extract()
            .`as`(RoomCreatedResponse::class.java)

        assertEquals(6, created.roomCode.length)
        assertEquals(RoomState.WAITING_FOR_GUEST, created.state)
        assertEquals(SecurityMode.STANDARD, created.securityMode)

        val hostWs = TestWsClient(created.roomCode)
        var guestWs: TestWsClient? = null

        try {
            hostWs.send(
                IncomingWsMessage(
                    type = WsMessageType.AUTH,
                    clientId = created.hostId,
                    clientSecret = created.hostSecret
                )
            )
            val hostAuthMsg = hostWs.awaitMessage()
            assertEquals("AUTH_SUCCESS", hostAuthMsg.get("type").asText())
            assertEquals("WAITING_FOR_GUEST", hostAuthMsg.get("state").asText())

            val joined = given()
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0"
                )
                .`when`()
                .post("/api/rooms/${created.roomCode}/join")
                .then()
                .statusCode(200)
                .extract()
                .`as`(JoinRoomResponse::class.java)

            assertEquals(RoomState.PENDING_HOST_APPROVAL, joined.state)

            val joinNotification = hostWs.awaitMessage()
            assertEquals("GUEST_JOIN_REQUESTED", joinNotification.get("type").asText())
            assertEquals("PENDING_HOST_APPROVAL", joinNotification.get("state").asText())
            val metadataNode = joinNotification.get("payload").get("deviceMetadata")
            assertEquals("DESKTOP_PC", metadataNode.get("deviceType").asText())
            assertEquals("WINDOWS", metadataNode.get("operatingSystem").asText())
            assertEquals("FIREFOX", metadataNode.get("browser").asText())

            guestWs = TestWsClient(created.roomCode)
            guestWs.send(
                IncomingWsMessage(
                    type = WsMessageType.AUTH,
                    clientId = joined.guestId,
                    clientSecret = joined.guestSecret
                )
            )
            val guestAuthMsg = guestWs.awaitMessage()
            assertEquals("AUTH_SUCCESS", guestAuthMsg.get("type").asText())
            assertEquals("PENDING_HOST_APPROVAL", guestAuthMsg.get("state").asText())

            val resolved = given()
                .contentType(ContentType.JSON)
                .header("X-Client-Secret", created.hostSecret)
                .body(ResolveJoinRequest(JoinDecision.ACCEPT))
                .`when`()
                .post("/api/rooms/${created.roomCode}/resolve")
                .then()
                .statusCode(200)
                .extract()
                .`as`(ResolveJoinHostResponse::class.java)

            assertEquals(RoomState.ACTIVE, resolved.state)
            assertNull(resolved.twoFactorCorrectCode)

            val guestActiveMsg = guestWs.awaitMessage()
            assertEquals("ROOM_ACTIVE", guestActiveMsg.get("type").asText())
            assertEquals("ACTIVE", guestActiveMsg.get("state").asText())

            val sdpOfferPayload = mapOf("type" to "offer", "sdp" to "v=0\r\no=- 12345 2 IN IP4 127.0.0.1...")
            hostWs.send(
                IncomingWsMessage(
                    type = WsMessageType.SDP_OFFER,
                    clientId = created.hostId,
                    clientSecret = created.hostSecret,
                    payload = sdpOfferPayload
                )
            )

            val receivedOffer = guestWs.awaitMessage()
            assertEquals("SDP_OFFER", receivedOffer.get("type").asText())
            assertEquals("offer", receivedOffer.get("payload").get("type").asText())

            val sdpAnswerPayload = mapOf("type" to "answer", "sdp" to "v=0\r\no=- 67890 2 IN IP4 127.0.0.1...")
            guestWs.send(
                IncomingWsMessage(
                    type = WsMessageType.SDP_ANSWER,
                    clientId = joined.guestId,
                    clientSecret = joined.guestSecret,
                    payload = sdpAnswerPayload
                )
            )

            val receivedAnswer = hostWs.awaitMessage()
            assertEquals("SDP_ANSWER", receivedAnswer.get("type").asText())
            assertEquals("answer", receivedAnswer.get("payload").get("type").asText())

            val icePayload = mapOf("candidate" to "candidate:1 1 UDP 2122252543 192.168.1.10 54321 typ host")
            hostWs.send(
                IncomingWsMessage(
                    type = WsMessageType.ICE_CANDIDATE,
                    clientId = created.hostId,
                    clientSecret = created.hostSecret,
                    payload = icePayload
                )
            )

            val receivedIce = guestWs.awaitMessage()
            assertEquals("ICE_CANDIDATE", receivedIce.get("type").asText())
            assertTrue(receivedIce.get("payload").get("candidate").asText().contains("192.168.1.10"))
        } finally {
            hostWs.close()
            guestWs?.close()
        }
    }

    @Test
    fun `E2E TWO_FACTOR flow - full 2FA challenge verification and transition to ACTIVE`() {
        val created = given()
            .contentType(ContentType.JSON)
            .body(CreateRoomRequest(SecurityMode.TWO_FACTOR))
            .`when`()
            .post("/api/rooms")
            .then()
            .statusCode(201)
            .extract()
            .`as`(RoomCreatedResponse::class.java)

        val hostWs = TestWsClient(created.roomCode)
        var guestWs: TestWsClient? = null

        try {
            hostWs.send(IncomingWsMessage(WsMessageType.AUTH, created.hostId, created.hostSecret))
            assertEquals("AUTH_SUCCESS", hostWs.awaitMessage().get("type").asText())

            val joined = given()
                .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_6 like Mac OS X) Version/17.6 Safari/604.1")
                .`when`()
                .post("/api/rooms/${created.roomCode}/join")
                .then()
                .statusCode(200)
                .extract()
                .`as`(JoinRoomResponse::class.java)

            val hostJoinNotif = hostWs.awaitMessage()
            assertEquals("GUEST_JOIN_REQUESTED", hostJoinNotif.get("type").asText())
            assertEquals("IPHONE", hostJoinNotif.get("payload").get("deviceMetadata").get("deviceType").asText())

            guestWs = TestWsClient(created.roomCode)
            guestWs.send(IncomingWsMessage(WsMessageType.AUTH, joined.guestId, joined.guestSecret))
            assertEquals("AUTH_SUCCESS", guestWs.awaitMessage().get("type").asText())

            val resolved = given()
                .contentType(ContentType.JSON)
                .header("X-Client-Secret", created.hostSecret)
                .body(ResolveJoinRequest(JoinDecision.ACCEPT))
                .`when`()
                .post("/api/rooms/${created.roomCode}/resolve")
                .then()
                .statusCode(200)
                .extract()
                .`as`(ResolveJoinHostResponse::class.java)

            assertEquals(RoomState.PENDING_2FA, resolved.state)
            assertNotNull(resolved.twoFactorCorrectCode)
            val correctCode = resolved.twoFactorCorrectCode!!

            val challengeMsg = guestWs.awaitMessage()
            assertEquals("TWO_FACTOR_CHALLENGE", challengeMsg.get("type").asText())
            val options = challengeMsg.get("payload").get("options").map { it.asInt() }
            assertEquals(3, options.size)
            assertTrue(options.contains(correctCode))

            given()
                .contentType(ContentType.JSON)
                .header("X-Client-Id", joined.guestId)
                .header("X-Client-Secret", joined.guestSecret)
                .body(Submit2FARequest(correctCode))
                .`when`()
                .post("/api/rooms/${created.roomCode}/2fa")
                .then()
                .statusCode(200)
                .body("state", equalTo("ACTIVE"))

            val hostActiveMsg = hostWs.awaitMessage()
            assertEquals("ROOM_ACTIVE", hostActiveMsg.get("type").asText())
            assertEquals("ACTIVE", hostActiveMsg.get("state").asText())
        } finally {
            hostWs.close()
            guestWs?.close()
        }
    }

    @Test
    fun `E2E Burn-on-Fail - submitting wrong 2FA code permanently closes and deletes the room`() {
        val created = given()
            .contentType(ContentType.JSON)
            .body(CreateRoomRequest(SecurityMode.TWO_FACTOR))
            .`when`()
            .post("/api/rooms")
            .then()
            .statusCode(201)
            .extract()
            .`as`(RoomCreatedResponse::class.java)

        val joined = given()
            .`when`()
            .post("/api/rooms/${created.roomCode}/join")
            .then()
            .statusCode(200)
            .extract()
            .`as`(JoinRoomResponse::class.java)

        val resolved = given()
            .contentType(ContentType.JSON)
            .header("X-Client-Secret", created.hostSecret)
            .body(ResolveJoinRequest(JoinDecision.ACCEPT))
            .`when`()
            .post("/api/rooms/${created.roomCode}/resolve")
            .then()
            .statusCode(200)
            .extract()
            .`as`(ResolveJoinHostResponse::class.java)

        val correctCode = resolved.twoFactorCorrectCode!!
        val wrongCode = if (correctCode == 100) 101 else 100

        given()
            .contentType(ContentType.JSON)
            .header("X-Client-Id", joined.guestId)
            .header("X-Client-Secret", joined.guestSecret)
            .body(Submit2FARequest(wrongCode))
            .`when`()
            .post("/api/rooms/${created.roomCode}/2fa")
            .then()
            .statusCode(200)
            .body("state", equalTo("CLOSED"))

        given()
            .contentType(ContentType.JSON)
            .header("X-Client-Id", joined.guestId)
            .header("X-Client-Secret", joined.guestSecret)
            .body(Submit2FARequest(correctCode))
            .`when`()
            .post("/api/rooms/${created.roomCode}/2fa")
            .then()
            .statusCode(404)
            .body("error", equalTo("NOT_FOUND"))
    }

    @Test
    fun `HTTP Exception Mapping - should return proper 400, 404 and 409 error responses`() {
        given()
            .`when`()
            .post("/api/rooms/abc/join")
            .then()
            .statusCode(400)
            .body("error", equalTo("BAD_REQUEST"))

        given()
            .`when`()
            .post("/api/rooms/999999/join")
            .then()
            .statusCode(404)
            .body("error", equalTo("NOT_FOUND"))

        val created = given()
            .contentType(ContentType.JSON)
            .`when`()
            .post("/api/rooms")
            .then()
            .statusCode(201)
            .extract()
            .`as`(RoomCreatedResponse::class.java)

        given()
            .`when`()
            .post("/api/rooms/${created.roomCode}/join")
            .then()
            .statusCode(200)

        given()
            .`when`()
            .post("/api/rooms/${created.roomCode}/join")
            .then()
            .statusCode(409)
            .body("error", equalTo("CONFLICT"))
    }

    @Test
    fun `E2E Disconnect - closing WebSocket notifies peer with PEER_DISCONNECTED and deletes room`() {
        val created = given()
            .contentType(ContentType.JSON)
            .body(CreateRoomRequest(SecurityMode.STANDARD))
            .`when`()
            .post("/api/rooms")
            .then()
            .statusCode(201)
            .extract()
            .`as`(RoomCreatedResponse::class.java)

        val hostWs = TestWsClient(created.roomCode)
        var guestWs: TestWsClient? = null

        try {
            hostWs.send(IncomingWsMessage(WsMessageType.AUTH, created.hostId, created.hostSecret))
            assertEquals("AUTH_SUCCESS", hostWs.awaitMessage().get("type").asText())

            val joined = given()
                .`when`()
                .post("/api/rooms/${created.roomCode}/join")
                .then()
                .statusCode(200)
                .extract()
                .`as`(JoinRoomResponse::class.java)

            assertEquals("GUEST_JOIN_REQUESTED", hostWs.awaitMessage().get("type").asText())

            guestWs = TestWsClient(created.roomCode)
            guestWs.send(IncomingWsMessage(WsMessageType.AUTH, joined.guestId, joined.guestSecret))
            assertEquals("AUTH_SUCCESS", guestWs.awaitMessage().get("type").asText())

            guestWs.close()
            guestWs = null

            val disconnectMsg = hostWs.awaitMessage()
            assertEquals("PEER_DISCONNECTED", disconnectMsg.get("type").asText())
            assertEquals("CLOSED", disconnectMsg.get("state").asText())

            given()
                .contentType(ContentType.JSON)
                .header("X-Client-Secret", created.hostSecret)
                .body(ResolveJoinRequest(JoinDecision.ACCEPT))
                .`when`()
                .post("/api/rooms/${created.roomCode}/resolve")
                .then()
                .statusCode(404)
        } finally {
            hostWs.close()
            guestWs?.close()
        }
    }
}