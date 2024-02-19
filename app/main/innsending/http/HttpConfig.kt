package innsending.http

import org.slf4j.Logger

open class HttpConfig(
    open val host: String,
    open val log: Logger,
    open val alias: String
)