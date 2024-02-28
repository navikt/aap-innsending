package innsending.dto

import innsending.routes.SUPPORTED_TYPES

data class MellomlagringRespons(
    val filId: String,
)

data class ApiError(
    val code: ErrorCode,
    val msg: String = code.msg,
)

enum class ErrorCode(val msg: String) {
    CLIENT_SETUP_ERR("Innsending has incorrectly setup its http client toward PdfGen. Check Innsending's logs."),
    CLIENT_INTERNAL_ERR("PdfGen failed internally, check PdfGen's logs."),
    CLIENT_USAGE_ERR("Innsending is using PdfGen incorrectly. Check Innsending's logs."),
    DESERIALIZE_ERR("Innsending failed to deserialize (read) the response from PdfGen. Check Innsending's logs."),
    NOT_FOUND_FILE("File not found."),
    NOT_FOUND_SOKNAD("Application not found."),
    UNPROC_ENCRYPTED_PDF("PDF is most likely encrypted, it could also be invalid."),
    UNPROC_VIRUS("Virus found in file."),
    UNPROC_EMPTY_FILE("File is empty."),
    REQ_MISSING_MULTIPART_FILE("Request was either form-data or missing its multipart-file."),
    REQ_MISSING_CONTENT_TYPE("Missing content-type in multipart file."),
    REQ_WRONG_CONTENT_TYPE("File type is not supported. Supported types are: $SUPPORTED_TYPES"),
    REQ_MISSING_FILID("Param filId is missing from the query"),
}
