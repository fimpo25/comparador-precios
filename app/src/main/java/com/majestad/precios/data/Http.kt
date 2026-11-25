package com.majestad.precios.data

import java.io.IOException
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Cliente HTTP mínimo, con cookies (La Reina guarda la sesión en una cookie ASP). */
object Http {
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Mobile Safari/537.36"

    init {
        if (CookieHandler.getDefault() == null) {
            CookieHandler.setDefault(CookieManager(null, CookiePolicy.ACCEPT_ALL))
        }
    }

    class Respuesta(val codigo: Int, val bytes: ByteArray, val url: String, val contentType: String?) {
        val texto: String get() = String(bytes, Charsets.UTF_8)
        val ok: Boolean get() = codigo in 200..299
    }

    fun get(url: String, accept: String = "*/*", referer: String? = null): Respuesta =
        pedir("GET", url, null, accept, referer)

    fun postForm(url: String, campos: Map<String, String>, referer: String? = null, charset: String = "UTF-8"): Respuesta {
        val cuerpo = campos.entries.joinToString("&") { (k, v) ->
            URLEncoder.encode(k, charset) + "=" + URLEncoder.encode(v, charset)
        }
        return pedir("POST", url, cuerpo.toByteArray(Charsets.US_ASCII), "text/html,*/*", referer)
    }

    private fun pedir(metodo: String, url: String, cuerpo: ByteArray?, accept: String, referer: String?): Respuesta {
        var actual = url
        var metodoActual = metodo
        var cuerpoActual = cuerpo
        repeat(6) {
            val conn = (URL(actual).openConnection() as HttpURLConnection).apply {
                requestMethod = metodoActual
                instanceFollowRedirects = false
                connectTimeout = 12_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", accept)
                setRequestProperty("Accept-Language", "es-AR,es;q=0.9")
                if (referer != null) setRequestProperty("Referer", referer)
                if (cuerpoActual != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                }
            }
            try {
                cuerpoActual?.let { b -> conn.outputStream.use { it.write(b) } }
                val codigo = conn.responseCode
                if (codigo in 300..399) {
                    val destino = conn.getHeaderField("Location") ?: throw IOException("redirección sin destino")
                    actual = URL(URL(actual), destino).toString()
                    if (codigo != 307 && codigo != 308) {
                        metodoActual = "GET"
                        cuerpoActual = null
                    }
                    return@repeat
                }
                val stream = if (codigo >= 400) conn.errorStream else conn.inputStream
                val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
                return Respuesta(codigo, bytes, actual, conn.contentType)
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("demasiadas redirecciones")
    }

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
