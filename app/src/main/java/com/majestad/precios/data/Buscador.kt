package com.majestad.precios.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

object Buscador {

    private val fuentes: Map<Super, Fuente> = mapOf(
        Super.CARREFOUR to VtexFuente(Super.CARREFOUR),
        Super.JUMBO to VtexFuente(Super.JUMBO),
        Super.COTO to CotoFuente(),
        Super.LA_REINA to LaReinaFuente(),
    )

    /** Busca en paralelo; un súper que falla no frena a los demás. */
    suspend fun buscar(texto: String, supers: Set<Super>, limite: Int = 40): List<ResultadoSuper> =
        coroutineScope {
            supers.sortedBy { it.ordinal }.map { s ->
                async(Dispatchers.IO) {
                    val t0 = System.currentTimeMillis()
                    try {
                        val productos = withTimeout(25_000) {
                            withContext(Dispatchers.IO) { fuentes.getValue(s).buscar(texto, limite) }
                        }
                        ResultadoSuper(s, productos, milis = System.currentTimeMillis() - t0)
                    } catch (e: Exception) {
                        val msg = when (e) {
                            is kotlinx.coroutines.TimeoutCancellationException -> "no respondió a tiempo"
                            is java.net.UnknownHostException -> "sin conexión"
                            is java.net.SocketTimeoutException -> "no respondió a tiempo"
                            is org.json.JSONException -> "respuesta inesperada del sitio"
                            else -> e.message ?: e.javaClass.simpleName
                        }
                        ResultadoSuper(s, emptyList(), error = msg, milis = System.currentTimeMillis() - t0)
                    }
                }
            }.awaitAll()
        }

    /**
     * Junta el mismo producto de distintos súper por código de barras (EAN).
     * Los que no tienen EAN (o no coinciden) quedan solos.
     */
    fun agrupar(productos: List<Producto>): List<Grupo> {
        val porEan = LinkedHashMap<String, MutableList<Producto>>()
        val sueltos = ArrayList<Producto>()
        for (p in productos) {
            if (p.eanNormalizado.length >= 7) porEan.getOrPut(p.eanNormalizado) { ArrayList() } += p
            else sueltos += p
        }
        val grupos = ArrayList<Grupo>()
        for ((ean, lista) in porEan) {
            // Si un súper devuelve el mismo EAN dos veces, se queda con el más barato.
            val ofertas = lista.groupBy { it.superm }.values.map { l -> l.minBy { it.precio } }.sortedBy { it.precio }
            grupos += armarGrupo("ean:$ean", ofertas)
        }
        sueltos.forEachIndexed { i, p -> grupos += armarGrupo("s:${p.superm}:$i", listOf(p)) }
        return grupos
    }

    private fun armarGrupo(clave: String, ofertas: List<Producto>): Grupo {
        // El nombre más descriptivo suele ser el de VTEX o Coto; La Reina va en mayúsculas cortadas.
        val ref = ofertas.sortedWith(compareBy<Producto> { it.superm == Super.LA_REINA }.thenByDescending { it.nombre.length }).first()
        val categoria = ofertas.map { it.categoria }.filter { it != Categoria.OTROS }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: Categoria.OTROS
        return Grupo(
            clave = clave,
            nombre = ref.nombre,
            marca = ofertas.firstOrNull { it.marca.isNotBlank() }?.marca.orEmpty(),
            imagen = ofertas.firstOrNull { it.imagen.isNotBlank() }?.imagen.orEmpty(),
            categoria = categoria,
            cantidad = ofertas.firstNotNullOfOrNull { it.cantidad },
            ofertas = ofertas,
        )
    }
}
