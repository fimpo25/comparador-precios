package com.majestad.precios.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** Una fuente de precios: un súper. */
interface Fuente {
    val superm: Super
    fun buscar(texto: String, limite: Int): List<Producto>
}

/**
 * Coto Digital usa Oracle Endeca: la misma URL del buscador devuelve JSON con `format=json`.
 * Ojo: en Coto `sku.activePrice` es el precio del paquete; `dtoPrice.precio` y
 * `referencePrice` son por kilo/litro. El precio es el de Coto Digital (uno para todo el país).
 */
class CotoFuente : Fuente {
    override val superm = Super.COTO
    private val base = "https://www.cotodigital.com.ar/sitios/cdigi"

    override fun buscar(texto: String, limite: Int): List<Producto> {
        val url = "$base/categoria?Dy=1&Ntt=${Http.enc(texto)}&Nrpp=${limite.coerceIn(1, 60)}&format=json"
        val r = Http.get(url, accept = "application/json")
        if (!r.ok) throw IOException("HTTP ${r.codigo}")
        val raiz = JSONObject(r.texto)
        val registros = ArrayList<JSONObject>()
        juntarRegistros(raiz, registros)
        return registros.mapNotNull { normalizar(it) }
    }

    /** Recorre el árbol de Endeca buscando listas `records` con `attributes`. */
    private fun juntarRegistros(nodo: Any?, salida: MutableList<JSONObject>) {
        when (nodo) {
            is JSONArray -> for (i in 0 until nodo.length()) juntarRegistros(nodo.opt(i), salida)
            is JSONObject -> {
                val recs = nodo.optJSONArray("records")
                if (recs != null && recs.length() > 0 && recs.optJSONObject(0)?.has("attributes") == true) {
                    for (i in 0 until recs.length()) {
                        val r = recs.optJSONObject(i) ?: continue
                        // Cada producto trae sus SKU anidados; el primero tiene los atributos completos.
                        salida += r.optJSONArray("records")?.optJSONObject(0) ?: r
                    }
                    return
                }
                val claves = nodo.keys()
                while (claves.hasNext()) juntarRegistros(nodo.opt(claves.next()), salida)
            }
        }
    }

    private fun normalizar(r: JSONObject): Producto? {
        val a = r.optJSONObject("attributes") ?: return null
        fun uno(k: String): String = a.optJSONArray(k)?.optString(0).orEmpty()
        fun todos(k: String): List<String> {
            val arr = a.optJSONArray(k) ?: return emptyList()
            return (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
        }

        var precio = uno("sku.activePrice").toDoubleOrNull() ?: return null
        if (precio <= 0) return null
        var lista = precio
        val promos = ArrayList<String>()

        runCatching {
            val descuentos = JSONArray(uno("product.dtoDescuentos"))
            for (i in 0 until descuentos.length()) {
                val d = descuentos.optJSONObject(i) ?: continue
                val txt = d.optString("textoDescuento").trim()
                val p = d.optString("precioDesc").replace(",", ".").toDoubleOrNull() ?: d.optDouble("precioDesc", 0.0)
                // Descuento directo ("20%Dto") se aplica; 2x1, "2do al 70 %" o con tarjeta van como promo.
                if (Regex("^\\s*\\d+\\s*%\\s*dto", RegexOption.IGNORE_CASE).containsMatchIn(txt) && p > 0 && p < precio) {
                    precio = p
                } else if (txt.isNotEmpty()) {
                    promos += txt.replace(Regex("\\s+"), " ").take(60)
                }
            }
        }
        runCatching {
            val d = JSONObject(uno("sku.dtoPrice"))
            val pl = d.optString("precioLista").replace(",", ".").toDoubleOrNull() ?: d.optDouble("precioLista", 0.0)
            if (pl > 0) lista = maxOf(lista, pl)
        }
        var contenido = ""
        runCatching {
            val c = JSONArray(uno("product.dtoCaracteristicas"))
            for (i in 0 until c.length()) {
                val o = c.optJSONObject(i) ?: continue
                if (o.optString("nombre").equals("CONTENIDO", ignoreCase = true)) contenido = o.optString("descripcion")
            }
        }

        val nombreBase = uno("sku.displayName").ifBlank { uno("product.displayName") }.trim()
        if (nombreBase.isEmpty()) return null
        // Si el nombre no trae el contenido, se lo agregamos para poder calcular precio por kilo/litro.
        val nombre = if (contenido.isNotBlank() && Cantidad.desdeNombre(nombreBase) == null) "$nombreBase $contenido" else nombreBase

        val ruta = todos("allAncestors.displayName").joinToString(" > ")
            .ifBlank { listOf(uno("parentCategory.displayName"), uno("product.category")).filter { it.isNotBlank() }.joinToString(" > ") }

        val estado = r.optJSONObject("detailsAction")?.optString("recordState").orEmpty().replace("?format=json", "")
        val imagen = uno("product.mediumImage.url").ifBlank { uno("product.largeImage.url") }
        return Producto(
            superm = superm,
            nombre = nombre,
            marca = uno("product.brand").ifBlank { uno("product.MARCA") },
            ean = uno("product.eanPrincipal"),
            precio = precio,
            precioLista = maxOf(lista, precio),
            promos = promos.distinct().take(3),
            categoriaOriginal = ruta,
            imagen = when {
                imagen.startsWith("//") -> "https:$imagen"
                imagen.startsWith("/") -> "https://www.cotodigital.com.ar$imagen"
                else -> imagen
            },
            link = if (estado.isNotBlank()) "$base/productos$estado" else "",
        )
    }
}
