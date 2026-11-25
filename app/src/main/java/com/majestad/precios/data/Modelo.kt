package com.majestad.precios.data

/** Los supermercados que compara la app. */
enum class Super(val nombre: String, val corto: String, val color: Long, val web: String) {
    CARREFOUR("Carrefour", "Carrefour", 0xFF1E4FA3, "https://www.carrefour.com.ar"),
    JUMBO("Jumbo", "Jumbo", 0xFF2E8B3E, "https://www.jumbo.com.ar"),
    COTO("Coto", "Coto", 0xFFD32F2F, "https://www.cotodigital.com.ar"),
    LA_REINA("La Reina", "La Reina", 0xFF7B3FA0, "https://www.lareinaonline.com.ar"),
}

/** Un producto tal como lo publica un súper. */
data class Producto(
    val superm: Super,
    val nombre: String,
    val marca: String,
    val ean: String,
    /** Precio que se paga por una unidad (con descuento directo aplicado). */
    val precio: Double,
    /** Precio sin descuento; igual a [precio] si no hay rebaja. */
    val precioLista: Double,
    /** Promos que dependen de llevar más de uno, o con tarjeta (2x1, 2do al 70 %, etc.). */
    val promos: List<String>,
    /** Ruta de categorías del propio súper, ej. "Almacén > Desayuno > Café". */
    val categoriaOriginal: String,
    val imagen: String,
    val link: String,
) {
    val categoria: Categoria = Categorias.clasificar(categoriaOriginal, nombre)
    val cantidad: Cantidad? = Cantidad.desdeNombre(nombre)

    /** Precio por kilo, por litro o por unidad (según [cantidad]). */
    val precioPorUnidad: Double? = cantidad?.let { if (it.valor > 0) precio / it.valor else null }

    val tieneRebaja: Boolean get() = precioLista > precio * 1.005
    val eanNormalizado: String = ean.filter { it.isDigit() }.trimStart('0')
}

/**
 * Lo que se muestra en la lista: un producto (o el mismo producto en varios súper,
 * agrupado por código de barras).
 */
data class Grupo(
    val clave: String,
    val nombre: String,
    val marca: String,
    val imagen: String,
    val categoria: Categoria,
    val cantidad: Cantidad?,
    /** Ofertas ordenadas de la más barata a la más cara. */
    val ofertas: List<Producto>,
) {
    val mejor: Producto get() = ofertas.first()
    val comparable: Boolean get() = ofertas.map { it.superm }.distinct().size > 1

    /** Diferencia entre la oferta más cara y la más barata, en %. */
    val ahorroPct: Double?
        get() = if (!comparable) null else {
            val max = ofertas.maxOf { it.precio }
            if (max > 0) (max - mejor.precio) / max * 100 else null
        }
}

/** Resultado de buscar en un súper. */
data class ResultadoSuper(
    val superm: Super,
    val productos: List<Producto>,
    val error: String? = null,
    val milis: Long = 0,
)
