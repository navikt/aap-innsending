package innsending.http

@JvmInline
value class Path private constructor(private val path: String) {

    companion object {
        fun from(path: String): Path {
            require(path.startsWith("/")) { "Path must start with /" }
            return Path(path)
        }
    }

    override fun toString(): String = path
}
