package pl.edu.webrtc.integration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import jakarta.inject.Inject
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
import java.util.concurrent.*

@QuarkusTest
class UserJourneySimulationTest {

    @TestHTTPResource("/")
    lateinit var baseUri: URI

    @Inject
    lateinit var objectMapper: ObjectMapper

    private val httpClient: HttpClient = HttpClient.newHttpClient()

    private inner class SimulatedUser(val roomCode: String, val clientId: String, val clientSecret: String) {
        val incomingMessages = LinkedBlockingQueue<JsonNode>()
        private val buffer = StringBuilder()
        val socket: WebSocket

        init {
            val wsUri = URI.create("ws://${baseUri.host}:${baseUri.port}/ws/signaling/$roomCode")
            socket = httpClient.newWebSocketBuilder()
                .buildAsync(wsUri, object : WebSocket.Listener {
                    override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*> {
                        buffer.append(data)
                        if (last) {
                            incomingMessages.offer(objectMapper.readTree(buffer.toString()))
                            buffer.setLength(0)
                        }
                        webSocket.request(1)
                        return CompletableFuture.completedFuture(null)
                    }
                }).join()
        }

        fun authenticate() {
            val msg = IncomingWsMessage(WsMessageType.AUTH, clientId, clientSecret)
            socket.sendText(objectMapper.writeValueAsString(msg), true).join()
        }

        fun sendSignal(type: WsMessageType, payload: Any) {
            val msg = IncomingWsMessage(type, clientId, clientSecret, payload)
            socket.sendText(objectMapper.writeValueAsString(msg), true).join()
        }

        fun expectMessage(type: WsMessageType, timeoutSec: Long = 3): JsonNode {
            val node = incomingMessages.poll(timeoutSec, TimeUnit.SECONDS)
            assertNotNull(node, "Timeout waiting for message type $type")
            assertEquals(type.name, node.get("type").asText())
            return node
        }

        fun close() {
            try {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "Simulation end").join()
            } catch (_: Exception) {}
        }
    }

    @Test
    fun `Simulation 1 - Asynchronous WebRTC Signaling Exchange under Network Latency`() {
        val room = given()
            .contentType(ContentType.JSON)
            .body(CreateRoomRequest(SecurityMode.STANDARD))
            .`when`()
            .post("/api/rooms")
            .then()
            .statusCode(201)
            .extract()
            .`as`(RoomCreatedResponse::class.java)

        val host = SimulatedUser(room.roomCode, room.hostId, room.hostSecret)
        host.authenticate()
        host.expectMessage(WsMessageType.AUTH_SUCCESS)

        val join = given()
            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_7) Chrome/129.0.0.0 Safari/537.36")
            .`when`()
            .post("/api/rooms/${room.roomCode}/join")
            .then()
            .statusCode(200)
            .extract()
            .`as`(JoinRoomResponse::class.java)

        val joinNotif = host.expectMessage(WsMessageType.GUEST_JOIN_REQUESTED)
        assertEquals(join.guestId, joinNotif.get("payload").get("guestId").asText())

        val guest = SimulatedUser(room.roomCode, join.guestId, join.guestSecret)
        guest.authenticate()
        guest.expectMessage(WsMessageType.AUTH_SUCCESS)

        given()
            .contentType(ContentType.JSON)
            .header("X-Client-Secret", room.hostSecret)
            .body(ResolveJoinRequest(JoinDecision.ACCEPT))
            .`when`()
            .post("/api/rooms/${room.roomCode}/resolve")
            .then()
            .statusCode(200)

        guest.expectMessage(WsMessageType.ROOM_ACTIVE)

        val offerPayload = mapOf("type" to "offer", "sdp" to "v=0\r\no=host...")
        host.sendSignal(WsMessageType.SDP_OFFER, offerPayload)

        val receivedOffer = guest.expectMessage(WsMessageType.SDP_OFFER)
        assertEquals("offer", receivedOffer.get("payload").get("type").asText())

        Thread.sleep(100)

        val answerPayload = mapOf("type" to "answer", "sdp" to "v=0\r\no=guest...")
        guest.sendSignal(WsMessageType.SDP_ANSWER, answerPayload)

        val receivedAnswer = host.expectMessage(WsMessageType.SDP_ANSWER)
        assertEquals("answer", receivedAnswer.get("payload").get("type").asText())

        host.close()
        guest.close()
    }

    @Test
    fun `Simulation 2 - Concurrent Room Creation Stress Test`() {
        val concurrentCount = 20
        val executor = Executors.newFixedThreadPool(4)
        try {
            val tasks = (1..concurrentCount).map {
                Callable {
                    given()
                        .contentType(ContentType.JSON)
                        .body(CreateRoomRequest(SecurityMode.STANDARD))
                        .`when`()
                        .post("/api/rooms")
                        .then()
                        .statusCode(201)
                        .extract()
                        .`as`(RoomCreatedResponse::class.java)
                }
            }

            val results = executor.invokeAll(tasks)
            val codes = results.map { it.get().roomCode }.toSet()

            assertEquals(concurrentCount, codes.size)
        } finally {
            executor.shutdownNow()
        }
    }
}