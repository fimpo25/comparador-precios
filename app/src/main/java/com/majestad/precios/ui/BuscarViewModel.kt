package com.majestad.precios.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.majestad.precios.data.Buscador
import com.majestad.precios.data.Categoria
import com.majestad.precios.data.Categorias
import com.majestad.precios.data.Grupo
import com.majestad.precios.data.Producto
import com.majestad.precios.data.ResultadoSuper
import com.majestad.precios.data.Super
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Orden(val etiqueta: String) {
    RELEVANCIA("Relevancia"),
    PRECIO("Menor precio"),
    POR_UNIDAD("Precio por kg / L"),
    AHORRO("Mayor diferencia entre súper"),
}

data class EstadoBusqueda(
    val texto: String = "",
    val buscado: String = "",
    val buscando: Boolean = false,
    val supers: Set<Super> = Super.entries.toSet(),
    val resultados: List<ResultadoSuper> = emptyList(),
    val grupos: List<Grupo> = emptyList(),
    val categoria: Categoria? = null,
    val orden: Orden = Orden.RELEVANCIA,
    val soloComparables: Boolean = false,
    val recientes: List<String> = emptyList(),
) {
    /** Grupos que pasan el filtro de "solo en varios súper", antes de filtrar por categoría. */
    val gruposBase: List<Grupo> get() = if (soloComparables) grupos.filter { it.comparable } else grupos

    val conteoCategorias: List<Pair<Categoria, Int>>
        get() = gruposBase.groupingBy { it.categoria }.eachCount().toList().sortedBy { it.first.ordinal }

    val visibles: List<Grupo>
        get() {
            val base = gruposBase.filter { categoria == null || it.categoria == categoria }
            return when (orden) {
                Orden.RELEVANCIA -> base
                Orden.PRECIO -> base.sortedBy { it.mejor.precio }
                Orden.POR_UNIDAD -> base.sortedWith(compareBy<Grupo, Double?>(nullsLast<Double>()) { it.mejor.precioPorUnidad })
                Orden.AHORRO -> base.sortedWith(compareByDescending<Grupo> { it.ahorroPct ?: -1.0 }.thenBy { it.mejor.precio })
            }
        }

    /** En cuántos productos comparables cada súper es el más barato. */
    val ganadores: List<Pair<Super, Int>>
        get() = gruposBase.filter { it.comparable }.groupingBy { it.mejor.superm }.eachCount()
            .toList().sortedByDescending { it.second }
}

class BuscarViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("comparador", Context.MODE_PRIVATE)
    private val _estado = MutableStateFlow(
        EstadoBusqueda(
            supers = prefs.getStringSet("supers", null)
                ?.mapNotNull { n -> Super.entries.firstOrNull { it.name == n } }?.toSet()
                ?.takeIf { it.isNotEmpty() } ?: Super.entries.toSet(),
            recientes = prefs.getString("recientes", "").orEmpty().split('\n').filter { it.isNotBlank() },
        ),
    )
    val estado: StateFlow<EstadoBusqueda> = _estado.asStateFlow()
    private var trabajo: Job? = null

    fun cambiarTexto(t: String) = _estado.update { it.copy(texto = t) }

    fun alternarSuper(s: Super) {
        _estado.update { e ->
            val nuevos = if (s in e.supers) e.supers - s else e.supers + s
            if (nuevos.isEmpty()) e else e.copy(supers = nuevos)
        }
        prefs.edit().putStringSet("supers", _estado.value.supers.map { it.name }.toSet()).apply()
        // Si ya había una búsqueda, se repite con los súper elegidos.
        if (_estado.value.buscado.isNotBlank()) buscar(_estado.value.buscado)
    }

    fun elegirCategoria(c: Categoria?) = _estado.update { it.copy(categoria = c) }
    fun elegirOrden(o: Orden) = _estado.update { it.copy(orden = o) }
    fun alternarComparables() = _estado.update { it.copy(soloComparables = !it.soloComparables, categoria = null) }

    fun borrarRecientes() {
        prefs.edit().remove("recientes").apply()
        _estado.update { it.copy(recientes = emptyList()) }
    }

    fun buscar(textoPedido: String? = null) {
        val texto = (textoPedido ?: _estado.value.texto).trim()
        if (texto.length < 2) return
        guardarReciente(texto)
        trabajo?.cancel()
        _estado.update { it.copy(texto = texto, buscado = texto, buscando = true, categoria = null) }
        trabajo = viewModelScope.launch {
            val resultados = Buscador.buscar(texto, _estado.value.supers)
            val productos = filtrarRelevantes(texto, intercalar(resultados))
            _estado.update {
                it.copy(buscando = false, resultados = resultados, grupos = Buscador.agrupar(productos))
            }
        }
    }

    /** Mezcla los resultados por posición (1° de cada súper, 2° de cada súper...) para conservar la relevancia. */
    private fun intercalar(resultados: List<ResultadoSuper>): List<Producto> {
        val max = resultados.maxOfOrNull { it.productos.size } ?: 0
        val salida = ArrayList<Producto>()
        for (i in 0 until max) for (r in resultados) r.productos.getOrNull(i)?.let { salida += it }
        return salida
    }

    /**
     * Los buscadores de los súper son "generosos" (buscar leche trae galletitas con leche).
     * Se quedan los productos cuyo nombre o marca contiene todas las palabras buscadas;
     * si eso deja muy poco, se muestran todos.
     */
    private fun filtrarRelevantes(texto: String, productos: List<Producto>): List<Producto> {
        val palabras = Categorias.normalizar(texto).split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 }.map { it.take(5) }
        if (palabras.isEmpty()) return productos
        val filtrados = productos.filter { p ->
            val n = Categorias.normalizar(p.nombre + " " + p.marca)
            palabras.all { n.contains(it) }
        }
        return if (filtrados.size >= minOf(3, productos.size)) filtrados else productos
    }

    private fun guardarReciente(t: String) {
        val nuevos = (listOf(t) + _estado.value.recientes.filterNot { it.equals(t, ignoreCase = true) }).take(12)
        prefs.edit().putString("recientes", nuevos.joinToString("\n")).apply()
        _estado.update { it.copy(recientes = nuevos) }
    }
}
