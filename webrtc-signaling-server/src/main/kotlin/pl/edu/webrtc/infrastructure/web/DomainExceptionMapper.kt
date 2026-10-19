package pl.edu.webrtc.infrastructure.web

import org.jboss.logging.Logger
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider

@Provider
class DomainExceptionMapper: ExceptionMapper<Throwable> {
    private val logger = Logger.getLogger(DomainExceptionMapper::class.java)

    override fun toResponse(exception: Throwable): Response {
        return when (exception) {
            is NoSuchElementException -> buildResponse(Response.Status.NOT_FOUND, "NOT_FOUND", exception.message)
            is IllegalArgumentException -> buildResponse(Response.Status.BAD_REQUEST, "BAD_REQUEST", exception.message)
            is IllegalStateException -> buildResponse(Response.Status.CONFLICT, "CONFLICT", exception.message)
            else -> {
                logger.error("Internal server error", exception)
                buildResponse(Response.Status.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "An unexpected error occurred")
            }
        }
    }

    private fun buildResponse(status: Response.Status, error: String, message: String?): Response {
        return Response.status(status)
            .entity(ErrorResponse(error = error, message = message ?: error))
            .build()
    }
}