package innsending.dto

import innsending.routes.SUPPORTED_TYPES
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

class ApiException(code: ErrorCode): RuntimeException(code.msg)

data class ApiError(
    val error: ErrorCode,
    val msg: String = error.msg,
)

suspend fun ApplicationCall.error(error: ErrorCode) {
    respond(
        status = error.code,
        message = ApiError(
            error = error,
            msg = error.msg
        ),
    )
}

enum class ErrorCode(
    val code: HttpStatusCode,
    val msg: String,
) {
    PDFGEN_INTERNAL_ERR(
        code = HttpStatusCode.InternalServerError,
        "PdfGen failed internally, check PdfGen's logs."
    ),

    PDFGEN_USAGE_ERR(
        code = HttpStatusCode.InternalServerError,
        "Innsending is using PdfGen incorrectly. Check Innsending's logs."
    ),

    PDFGEN_DESERIALIZE_ERR(
        code = HttpStatusCode.InternalServerError,
        "Innsending failed to deserialize (read) the response from PdfGen. Check Innsending's logs."
    ),

    OPPSLAG_INTERNAL_ERR(
        code = HttpStatusCode.InternalServerError,
        "Oppslag failed internally, check Oppslag's logs."
    ),

    OPPSLAG_USAGE_ERR(
        code = HttpStatusCode.InternalServerError,
        "Innsending is using Oppslag incorrectly. Check Innsending's logs."
    ),

    OPPSLAG_DESERIALIZE_ERR(
        code = HttpStatusCode.InternalServerError,
        "Innsending failed to deserialize (read) the response from Oppslag. Check Innsending's logs."
    ),

    NOT_FOUND_FILE(
        code = HttpStatusCode.NotFound,
        "File not found."
    ),

    NOT_FOUND_SOKNAD(
        code = HttpStatusCode.NotFound,
        "Søknad not found."
    ),

    NOT_FOUND_INNSENDING(
        code = HttpStatusCode.NotFound,
        "Innsending not found for user."
    ),

    UNPROC_ENCRYPTED_PDF(
        code = HttpStatusCode.UnprocessableEntity,
        "PDF is most likely encrypted, it could also be invalid."
    ),

    UNPROC_VIRUS(
        code = HttpStatusCode.UnprocessableEntity,
        "Virus found in file."
    ),

    UNPROC_EMPTY_FILE(
        code = HttpStatusCode.UnprocessableEntity,
        "File is empty."
    ),

    REQ_MISSING_MULTIPART_FILE(
        code = HttpStatusCode.BadRequest,
        "Request was either form-data or missing its multipart-file."
    ),

    REQ_MISSING_CONTENT_TYPE(
        code = HttpStatusCode.BadRequest,
        "Missing content-type in multipart file."
    ),

    REQ_WRONG_CONTENT_TYPE(
        code = HttpStatusCode.BadRequest,
        "File type is not supported. Supported types are: $SUPPORTED_TYPES"
    ),

    REQ_MISSING_FILID(
        code = HttpStatusCode.BadRequest,
        "Param filId is missing from the query"
    ),

    REQ_MISSING_INNSENDING_REF(
        code = HttpStatusCode.BadRequest,
        "Missing parameter for innsending reference"
    ),

    REQ_MISSING_JWT(
        code = HttpStatusCode.Unauthorized,
        "Principal not found. Missing or invalid Authorization bearer JWT token."
    ),

    REQ_MISSING_PID(
        code = HttpStatusCode.Unauthorized,
        "JWT was present, but missing claims 'pid' that holds 'personident'."
    ),

    DUPLICATE_INNSENDING(
        code = HttpStatusCode.Conflict,
        "Innsending already exists"
    ),

    UNAUTH_TOKENX(
        code = HttpStatusCode.Unauthorized,
        "TokenX validation failed"
    ),

    UNKNOWN_ERROR(
        code = HttpStatusCode.InternalServerError,
        "Unknown error occurred. Check Innsending's logs."
    ),
}

