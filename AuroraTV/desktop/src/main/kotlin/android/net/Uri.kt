package android.net

import java.net.URI
import java.net.URLEncoder
import java.net.URLDecoder

open class Uri(val rawUrl: String) {
    private val javaUri: URI? = runCatching { URI(rawUrl) }.getOrNull()

    open val host: String? get() = javaUri?.host
    open val scheme: String? get() = javaUri?.scheme
    open val path: String? get() = javaUri?.path
    open val query: String? get() = javaUri?.query

    val queryParameterNames: Set<String>
        get() {
            val q = query ?: return emptySet()
            return q.split("&").mapNotNull { it.split("=").firstOrNull() }.toSet()
        }

    fun getQueryParameter(name: String): String? {
        val q = query ?: return null
        return q.split("&")
            .map { it.split("=") }
            .firstOrNull { it.size == 2 && it[0] == name }
            ?.get(1)
            ?.let { URLDecoder.decode(it, "UTF-8") }
    }

    fun buildUpon(): Builder = Builder(rawUrl)

    override fun toString(): String = rawUrl

    class Builder(private var base: String = "") {
        fun scheme(s: String): Builder {
            if (!base.contains("://")) base = "$s://$base"
            return this
        }
        fun authority(a: String): Builder {
            base = if (base.contains("://")) base.substringBefore("://") + "://" + a else a
            return this
        }
        fun appendPath(p: String): Builder {
            base = if (base.endsWith("/")) base + p else "$base/$p"
            return this
        }
        fun appendQueryParameter(key: String, value: String): Builder {
            val delim = if (base.contains("?")) "&" else "?"
            base += "$delim$key=${encode(value)}"
            return this
        }
        fun fragment(fragment: String?): Builder {
            if (fragment == null) {
                base = base.substringBefore('#')
            } else {
                base = base.substringBefore('#') + "#$fragment"
            }
            return this
        }
        fun build(): Uri = Uri(base)
    }

    companion object {
        @JvmStatic
        fun parse(uriString: String): Uri = Uri(uriString)

        @JvmStatic
        fun encode(s: String, allow: String? = null): String = runCatching { URLEncoder.encode(s, "UTF-8") }.getOrDefault(s)

        @JvmStatic
        fun decode(s: String): String = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
    }
}
