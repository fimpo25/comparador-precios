package com.majestad.precios.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Carrefour y Jumbo usan VTEX, que expone un catálogo público en JSON:
 * /api/catalog_system/pub/products/search?ft=<texto>&_from=0&_to=49
 */
class VtexFuente(override val superm: Super) : Fuente {

    override fun buscar(texto: String, limite: Int): List<Producto> {
        val hasta = (limite.coerceIn(1, 50) - 1)
        val url = "${superm.web}/api/catalog_system/pub/products/search?ft=${Http.enc(texto)}&_from=0&_to=$hasta"
        val r = Http.get(url, accept = "application/json")
        if (!r.ok) throw IOException("HTTP ${r.codigo}")
        val datos = JSONArray(r.texto)
        val salida = ArrayList<Producto>()
        for (i in 0 until datos.length()) {
            datos.optJSONObject(i)?.let { normalizar(it) }?.let { salida += it }
        }
        return salida
    }

    private fun normalizar(p: JSONObject): Producto? {
        val item = p.optJSONArray("items")?.optJSONObject(0) ?: return null
        val sellers = item.optJSONArray("sellers") ?: return null
        // Primer vendedor con stock; si ninguno tiene, el primero.
        var oferta: JSONObject? = null
        for (i in 0 until sellers.length()) {
            val o = sellers.optJSONObject(i)?.optJSONObject("commertialOffer") ?: continue
            if (oferta == null) oferta = o
            if (o.optDouble("AvailableQuantity", 0.0) > 0 && o.optDouble("Price", 0.0) > 0) {
                oferta = o
                break
            }
        }
        if (oferta == null) return null
        val precio = oferta.optDouble("Price", 0.0)
        if (precio <= 0 || oferta.optDouble("AvailableQuantity", 0.0) <= 0) return null
        var lista = oferta.optDouble("ListPrice", 0.0).takeIf { it > 0 }
            ?: oferta.optDouble("PriceWithoutDiscount", precio)
        if (lista > precio * 20) lista /= 100 // Jumbo a veces informa el precio de lista en centavos
        lista = maxOf(lista, precio)

        val promos = LinkedHashSet<String>()
        for (clave in listOf("Teasers", "PromotionTeasers")) {
            val arr = oferta.optJSONArray(clave) ?: continue
            for (i in 0 until arr.length()) {
                val t = arr.optJSONObject(i) ?: continue
                val nombre = (t.optString("Name").ifBlank { t.optString("<Name>k__BackingField") }).trim()
                if (nombre.isNotEmpty()) promos += limpiarPromo(nombre)
            }
        }

        // "categories": ["/Almacén/Desayuno/Café/", "/Almacén/Desayuno/", "/Almacén/"]
        val cats = p.optJSONArray("categories")
        var ruta = ""
        if (cats != null) {
            for (i in 0 until cats.length()) {
                val c = cats.optString(i)
                if (c.length > ruta.length) ruta = c
            }
        }
        val rutaLegible = ruta.trim('/').split('/').filter { it.isNotBlank() }.joinToString(" > ")

        val link = p.optString("link").ifBlank {
            p.optString("linkText").takeIf { it.isNotBlank() }?.let { "${superm.web}/$it/p" } ?: ""
        }
        return Producto(
            superm = superm,
            nombre = p.optString("productName").trim(),
            marca = p.optString("brand").trim(),
            ean = item.optString("ean"),
            precio = precio,
            precioLista = lista,
            promos = promos.toList().take(3),
            categoriaOriginal = rutaLegible,
            imagen = item.optJSONArray("images")?.optJSONObject(0)?.optString("imageUrl").orEmpty(),
            link = link,
        )
    }

    private fun limpiarPromo(s: String): String =
        s.replace(Regex("\\s+"), " ")
            .replace(Regex("^(PROMO|Promo)\\s*[-:]\\s*"), "")
            .take(60)
}
