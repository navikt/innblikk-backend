package no.nav.reops

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import java.util.UUID

@ControllerAdvice
class ControlAdvice {

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        val errorId = newErrorId()
        LOG.info("Bad request ({}) {} {}: {}", errorId, request.method, request.requestURI, ex.message)

        val body = ErrorResponse(
            error = "BAD_REQUEST",
            message = ex.message ?: "Invalid request",
            status = HttpStatus.BAD_REQUEST.value(),
            errorId = errorId,
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        val errorId = newErrorId()

        val details = ex.bindingResult.fieldErrors
            .mapNotNull { fe -> fe.field.takeIf { it.isNotBlank() }?.let { "${fe.field}: ${fe.defaultMessage}" } }
            .ifEmpty { ex.bindingResult.globalErrors.mapNotNull { it.defaultMessage } }

        LOG.info(
            "Validation failed ({}) {} {}: {}",
            errorId,
            request.method,
            request.requestURI,
            details.joinToString(", ")
        )

        val body = ErrorResponse(
            error = "VALIDATION_ERROR",
            message = "Validation failed",
            status = HttpStatus.BAD_REQUEST.value(),
            errorId = errorId,
            details = details.takeIf { it.isNotEmpty() },
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(
        ex: HttpMessageNotReadableException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        val errorId = newErrorId()
        // Don't leak parsing details (can include internal types / paths)
        LOG.info("Malformed JSON ({}) {} {}", errorId, request.method, request.requestURI, ex)

        val body = ErrorResponse(
            error = "MALFORMED_REQUEST",
            message = "Malformed request body",
            status = HttpStatus.BAD_REQUEST.value(),
            errorId = errorId,
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(
        ex: HttpRequestMethodNotSupportedException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        val errorId = newErrorId()
        LOG.info(
            "Method not allowed ({}) {} {} (supported: {})",
            errorId,
            request.method,
            request.requestURI,
            ex.supportedHttpMethods?.joinToString(",") ?: "unknown"
        )

        val body = ErrorResponse(
            error = "METHOD_NOT_ALLOWED",
            message = "Method not allowed",
            status = HttpStatus.METHOD_NOT_ALLOWED.value(),
            errorId = errorId,
        )
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body)
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneralException(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        val errorId = newErrorId()
        LOG.error("Unexpected error ({}) {} {}", errorId, request.method, request.requestURI, ex)

        val body = ErrorResponse(
            error = "INTERNAL_ERROR",
            message = "Unexpected error",
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            errorId = errorId,
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body)
    }

    private fun newErrorId(): String = UUID.randomUUID().toString()

    private companion object {
        private val LOG = LoggerFactory.getLogger(ControlAdvice::class.java)
    }
}

data class ErrorResponse(
    val error: String,
    val message: String,
    val status: Int,
    val errorId: String,
    val details: List<String>? = null,
)