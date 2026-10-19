package pl.edu.webrtc.infrastructure.web

import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DomainExceptionMapperTest {

    private lateinit var mapper: DomainExceptionMapper

    @BeforeEach
    fun setUp() {
        mapper = DomainExceptionMapper()
    }

    @Test
    fun `should map NoSuchElementException to 404 NOT_FOUND with exception message`() {
        val exception = NoSuchElementException("Room with code 123456 was not found")

        val response = mapper.toResponse(exception)

        assertEquals(Response.Status.NOT_FOUND.statusCode, response.status)
        val body = response.entity as ErrorResponse
        assertEquals("NOT_FOUND", body.error)
        assertEquals("Room with code 123456 was not found", body.message)
    }

    @Test
    fun `should map IllegalArgumentException to 400 BAD_REQUEST with exception message`() {
        val exception = IllegalArgumentException("Room code must be exactly 6 digits")

        val response = mapper.toResponse(exception)

        assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status)
        val body = response.entity as ErrorResponse
        assertEquals("BAD_REQUEST", body.error)
        assertEquals("Room code must be exactly 6 digits", body.message)
    }

    @Test
    fun `should map IllegalStateException to 409 CONFLICT with exception message`() {
        val exception = IllegalStateException("Cannot join room in state ACTIVE")

        val response = mapper.toResponse(exception)

        assertEquals(Response.Status.CONFLICT.statusCode, response.status)
        val body = response.entity as ErrorResponse
        assertEquals("CONFLICT", body.error)
        assertEquals("Cannot join room in state ACTIVE", body.message)
    }

    @Test
    fun `should fallback to error code name when known exception has null message`() {
        val noSuchElementResponse = mapper.toResponse(NoSuchElementException())
        assertEquals("NOT_FOUND", (noSuchElementResponse.entity as ErrorResponse).message)

        val illegalArgResponse = mapper.toResponse(IllegalArgumentException())
        assertEquals("BAD_REQUEST", (illegalArgResponse.entity as ErrorResponse).message)

        val illegalStateResponse = mapper.toResponse(IllegalStateException())
        assertEquals("CONFLICT", (illegalStateResponse.entity as ErrorResponse).message)
    }

    @Test
    fun `should map unexpected RuntimeException to 500 INTERNAL_SERVER_ERROR without leaking internal details`() {
        val exception = RuntimeException("Sensitive internal database or memory failure")

        val response = mapper.toResponse(exception)

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.statusCode, response.status)
        val body = response.entity as ErrorResponse
        assertNotNull(body)
        assertEquals("INTERNAL_SERVER_ERROR", body.error)
        assertEquals("An unexpected error occurred", body.message)
    }
}