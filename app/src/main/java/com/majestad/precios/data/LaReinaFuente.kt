package com.majestad.precios.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.Charset

/**
 * La Reina Online es un sitio ASP clásico (HTML armado en el servidor), sin API.
 * En vez de fijar la URL del buscador, la app lee el formulario de búsqueda de la propia
 * página y lo completa; así sobrevive a cambios chicos del sitio. Los productos se leen del
 * `gtag` de la página (EAN, nombre, precio) y de las tarjetas con `PCompra('<EAN>')`.
 */
class LaReinaFuente : Fuente {
    override val superm = Super.LA_REINA
    private val base = "https://www.lareinaonline.com.ar"
    private val inicio = "$base/productosnl.asp"

    /** Cómo se busca en el sitio; se descubre una vez por sesión de la app. */
    private data class Buscador(
        val url: String,
        val metodo: String,
        val campo: String,
        val ocultos: Map<String, String>,
        val charset: String,
    )

    @Volatile private var buscador: Buscador? = null

    override fun buscar(texto: String, limite: Int): List<Producto> {
        val b = buscador ?: descubrirBuscador().also { buscador = it }
        val r = if (b.metodo == "post") {
            Http.postForm(b.url, b.ocultos + (b.campo to texto), referer = inicio, charset = b.charset)
        } else {
            val params = (b.ocultos + (b.campo to texto)).entries.joinToString("&") { (k, v) ->
                URLEncoder.encode(k, b.charset) + "=" + URLEncoder.encode(v, b.charset)
            }
            val sep = if (b.url.contains('?')) "&" else "?"
            Http.get(b.url + sep + params, accept = "text/html,*/*", referer = inicio)
        }
        if (!r.ok) {
            buscador = null
            throw IOException("HTTP ${r.codigo}")
        }
        val doc = Jsoup.parse(r.bytes.inputStream(), null, r.url)
        return leerProductos(doc, r.url).take(limite)
    }

    private fun descubrirBuscador(): Buscador {
        val r = Http.get(inicio, accept = "text/html,*/*")
        if (!r.ok) throw IOException("HTTP ${r.codigo} al abrir La Reina")
        val doc = Jsoup.parse(r.bytes.inputStream(), null, r.url)
        val charset = runCatching { doc.charset().name() }.getOrDefault("ISO-8859-1")

        // 1) Un <form> con un campo de texto que no sea el de login.
        val excluir = Regex("alias|clave|pass|mail|usuario|user|login|nombre|apellido|tel|dni|direcc", RegexOption.IGNORE_CASE)
        val preferir = Regex("bus|search|txt|query|^q$|palabra|texto|prod", RegexOption.IGNORE_CASE)
        data class Candidato(val form: Element, val input: Element, val puntaje: Int)
        val candidatos = ArrayList<Candidato>()
        for (form in doc.select("form")) {
            for (input in form.select("input")) {
                val tipo = input.attr("type").lowercase()
                if (tipo.isNotEmpty() && tipo != "text" && tipo != "search") continue
                val nombre = input.attr("name")
                if (nombre.isEmpty()) continue
                val pista = listOf(nombre, input.id(), input.attr("placeholder"), form.attr("action"), form.id(), form.attr("name")).joinToString(" ")
                if (excluir.containsMatchIn(nombre) || excluir.containsMatchIn(input.id())) continue
                var puntaje = 0
                if (preferir.containsMatchIn(pista)) puntaje += 2
                if (tipo == "search") puntaje += 1
                if (input.attr("placeholder").contains("busc", ignoreCase = true)) puntaje += 3
                candidatos += Candidato(form, input, puntaje)
            }
        }
        candidatos.maxByOrNull { it.puntaje }?.let { c ->
            val accion = c.form.absUrl("action").ifBlank { r.url }
            val ocultos = c.form.select("input[type=hidden]")
                .filter { it.attr("name").isNotEmpty() }
                .associate { it.attr("name") to it.attr("value") }
            val cs = c.form.attr("accept-charset").takeIf { it.isNotBlank() && Charset.isSupported(it) } ?: charset
            return Buscador(accion, c.form.attr("method").lowercase().ifBlank { "get" }, c.input.attr("name"), ocultos, cs)
        }

        // 2) Buscador armado por JavaScript: location.href = 'algo.asp?param=' + texto
        val js = doc.select("script").joinToString("\n") { it.data() } + "\n" +
            doc.select("[onclick],[onkeypress],[onkeyup],[onsubmit]").joinToString("\n") {
                it.attr("onclick") + it.attr("onkeypress") + it.attr("onkeyup") + it.attr("onsubmit")
            }
        val reJs = Regex("""['"]([\w/]*\w+\.asp\?[^'"]*?=)['"]\s*\+""", RegexOption.IGNORE_CASE)
        val m = reJs.findAll(js).firstOrNull { Regex("bus|txt|search|text|palabra", RegexOption.IGNORE_CASE).containsMatchIn(it.groupValues[1]) }
            ?: reJs.find(js)
        if (m != null) {
            val completo = java.net.URL(java.net.URL(r.url), m.groupValues[1]).toString()
            val campo = completo.substringAfterLast('&').substringAfterLast('?').removeSuffix("=")
            val sinCampo = completo.removeSuffix("$campo=").trimEnd('&', '?')
            return Buscador(sinCampo, "get", campo, emptyMap(), charset)
        }
        throw IOException("no encontré el buscador del sitio")
    }

    // ---------- lectura de productos ----------

    private val rePrecio = Regex("""\$\s*(\d{1,3}(?:\.\d{3})*|\d+)(?:\s*,\s*(\d{1,2}))?""")
    private val rePCompra = Regex("""PCompra\(\s*['"]?(\d{6,14})""")

    private fun aNumero(entero: String, dec: String?): Double? =
        (entero.replace(".", "") + "." + (dec ?: "0").padEnd(2, '0')).toDoubleOrNull()

    private fun preciosEn(texto: String): List<Double> =
        rePrecio.findAll(texto).mapNotNull { aNumero(it.groupValues[1], it.groupValues[2].ifEmpty { null }) }
            .filter { it > 0 }.toList()

    private data class Item(var nombre: String = "", var precio: Double = 0.0, var lista: Double = 0.0,
                            var categoria: String = "", var marca: String = "", var imagen: String = "",
                            var oferta: Boolean = false)

    private fun leerProductos(doc: Document, urlPagina: String): List<Producto> {
        val items = LinkedHashMap<String, Item>()

        // a) gtag / dataLayer: { item_id: "779...", item_name: "...", price: 1234.5, item_category: "..." }
        val scripts = doc.select("script").joinToString("\n") { it.data() }
        val reObj = Regex("""\{[^{}]*item_id[^{}]*\}""")
        fun campo(obj: String, k: String): String? =
            Regex("""['"]?$k['"]?\s*:\s*(?:'([^']*)'|"([^"]*)"|([\d.]+))""").find(obj)?.let { m ->
                m.groupValues[1].ifEmpty { m.groupValues[2].ifEmpty { m.groupValues[3] } }
            }
        for (m in reObj.findAll(scripts)) {
            val o = m.value
            val ean = campo(o, "item_id")?.filter { it.isDigit() }.orEmpty()
            if (ean.length < 6) continue
            val item = items.getOrPut(ean) { Item() }
            campo(o, "item_name")?.let { n -> if (n.isNotBlank()) item.nombre = n.trim() }
            campo(o, "price")?.toDoubleOrNull()?.let { p -> if (p > 0) item.precio = p }
            campo(o, "item_brand")?.let { b -> item.marca = b.trim() }
            val cats = (listOf("item_category") + (2..5).map { n -> "item_category$n" }).mapNotNull { k -> campo(o, k)?.trim()?.takeIf { s -> s.isNotEmpty() } }
            if (cats.isNotEmpty()) item.categoria = cats.joinToString(" > ")
        }

        // b) Tarjetas del HTML con el botón "Agregar" -> PCompra('<EAN>')
        val botones = doc.select("[onclick*=PCompra], a[href*=PCompra]")
        for (btn in botones) {
            val ean = rePCompra.find(btn.attr("onclick") + " " + btn.attr("href"))?.groupValues?.get(1) ?: continue
            val tarjeta = subirHastaTarjeta(btn) ?: continue
            val item = items.getOrPut(ean) { Item() }
            val texto = tarjeta.text()
            val precios = preciosEn(texto)
            if (precios.isNotEmpty()) {
                item.precio = precios.min()
                item.lista = precios.max()
            }
            if (texto.contains("oferta", ignoreCase = true)) item.oferta = true
            if (item.nombre.isBlank()) item.nombre = nombreDeTarjeta(tarjeta)
            if (item.imagen.isBlank()) {
                tarjeta.selectFirst("img")?.let { img ->
                    item.imagen = listOf("data-src", "data-original", "src").map { a -> img.absUrl(a) }.firstOrNull { u -> u.isNotBlank() }.orEmpty()
                }
            }
        }

        return items.mapNotNull { (ean, item) ->
            if (item.nombre.isBlank() || item.precio <= 0) return@mapNotNull null
            Producto(
                superm = superm,
                nombre = item.nombre.split(' ').joinToString(" ") { w -> w.lowercase().replaceFirstChar { c -> c.titlecase() } },
                marca = item.marca,
                ean = ean,
                precio = item.precio,
                precioLista = maxOf(item.lista, item.precio),
                promos = if (item.oferta && item.lista <= item.precio) listOf("Oferta") else emptyList(),
                categoriaOriginal = item.categoria,
                imagen = item.imagen,
                link = urlPagina,
            )
        }
    }

    /** Sube desde el botón hasta el contenedor de un solo producto que tenga el precio. */
    private fun subirHastaTarjeta(desde: Element): Element? {
        var e: Element? = desde
        var nivel = 0
        while (e != null && nivel < 8) {
            val html = e.outerHtml()
            val compras = rePCompra.findAll(html).map { it.groupValues[1] }.distinct().count()
            if (compras > 1) return null
            if (rePrecio.containsMatchIn(e.text())) return e
            e = e.parent()
            nivel++
        }
        return null
    }

    private fun nombreDeTarjeta(tarjeta: Element): String {
        val ignorar = Regex("^(agregar|oferta|comprar|\\$.*|[\\d.,\\s$]+|unidad|un\\.?|kg|x)$", RegexOption.IGNORE_CASE)
        val textos = ArrayList<String>()
        fun recorrer(el: Element) {
            for (n in el.childNodes()) {
                when (n) {
                    is TextNode -> n.text().trim().takeIf { it.length > 3 && !ignorar.matches(it) }?.let { textos += it }
                    is Element -> if (n.tagName() !in setOf("script", "style", "button")) recorrer(n)
                }
            }
        }
        recorrer(tarjeta)
        // El sitio muestra el nombre en mayúsculas y repite una versión en minúsculas.
        return textos.firstOrNull { t -> t.any { it.isLetter() } && t == t.uppercase() } ?: textos.firstOrNull().orEmpty()
    }
}
