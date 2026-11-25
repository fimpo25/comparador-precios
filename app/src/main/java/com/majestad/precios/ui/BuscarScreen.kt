package com.majestad.precios.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.majestad.precios.data.Cantidad
import com.majestad.precios.data.Categoria
import com.majestad.precios.data.Grupo
import com.majestad.precios.data.Producto
import com.majestad.precios.data.Super
import com.majestad.precios.ui.theme.VerdeAhorro
import com.majestad.precios.update.UpdateManager
import com.majestad.precios.update.UpdateState
import java.text.NumberFormat
import java.util.Locale

private val formatoPesos: NumberFormat = NumberFormat.getCurrencyInstance(Locale("es", "AR")).apply {
    maximumFractionDigits = 2
    minimumFractionDigits = 2
}

fun pesos(v: Double): String = formatoPesos.format(v).replace(' ', ' ')

private fun colorDe(s: Super) = Color(s.color)

@Composable
fun BuscarScreen(onAbrirActualizaciones: () -> Unit, vm: BuscarViewModel = viewModel()) {
    val e by vm.estado.collectAsStateWithLifecycle()
    val update by UpdateManager.state.collectAsStateWithLifecycle()
    val foco = LocalFocusManager.current
    val context = LocalContext.current
    fun abrir(url: String) {
        if (url.isNotBlank()) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Comparador Súper") },
                actions = {
                    IconButton(onClick = onAbrirActualizaciones) {
                        Box {
                            Icon(Icons.Default.SystemUpdate, contentDescription = "Actualizaciones")
                            if (update is UpdateState.Available) {
                                Box(
                                    Modifier.size(8.dp).clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error)
                                        .align(Alignment.TopEnd),
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            // ---- Buscador ----
            item(key = "buscador") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    OutlinedTextField(
                        value = e.texto,
                        onValueChange = vm::cambiarTexto,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Buscá un producto: leche, yerba, aceite…") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            if (e.texto.isNotEmpty()) {
                                IconButton(onClick = { vm.cambiarTexto("") }) { Icon(Icons.Default.Clear, "Borrar") }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { foco.clearFocus(); vm.buscar() }),
                        shape = RoundedCornerShape(28.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Super.entries.forEach { s ->
                            val res = e.resultados.firstOrNull { it.superm == s }
                            val activo = s in e.supers
                            FilterChip(
                                selected = activo,
                                onClick = { vm.alternarSuper(s) },
                                label = {
                                    val extra = when {
                                        !activo || res == null || e.buscando -> ""
                                        res.error != null -> " ⚠"
                                        else -> " · ${res.productos.size}"
                                    }
                                    Text(s.corto + extra)
                                },
                                leadingIcon = {
                                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (activo) colorDe(s) else Color.Gray))
                                },
                            )
                        }
                    }
                }
            }

            if (e.buscando) {
                item(key = "cargando") {
                    Column(Modifier.padding(16.dp)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text("Buscando \"${e.buscado}\" en ${e.supers.size} súper…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // ---- Sin búsqueda: recientes ----
            if (e.buscado.isBlank() && !e.buscando) {
                item(key = "inicio") {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Escribí un producto y la app lo busca en Carrefour, Jumbo, Coto y La Reina a la vez. " +
                                "Los productos con el mismo código de barras se comparan juntos y el más barato queda en verde.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (e.recientes.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.History, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Búsquedas recientes", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                TextButton(onClick = vm::borrarRecientes) { Text("Borrar") }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                e.recientes.forEach { r ->
                                    AssistChip(onClick = { foco.clearFocus(); vm.buscar(r) }, label = { Text(r) })
                                }
                            }
                        }
                    }
                }
            }

            if (e.buscado.isNotBlank() && !e.buscando) {
                // ---- Errores por súper ----
                val errores = e.resultados.filter { it.error != null }
                if (errores.isNotEmpty()) {
                    item(key = "errores") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                errores.forEach { r ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("${r.superm.nombre}: ${r.error}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }

                // ---- Quién gana ----
                if (e.ganadores.isNotEmpty()) {
                    item(key = "ganadores") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Más barato en (productos comparables)", style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.height(4.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    e.ganadores.forEach { (s, n) ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.size(10.dp).clip(CircleShape).background(colorDe(s)))
                                            Spacer(Modifier.width(4.dp))
                                            Text("${s.corto}: $n", fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ---- Filtros ----
                item(key = "filtros") {
                    Filtros(
                        e = e,
                        onCategoria = vm::elegirCategoria,
                        onOrden = vm::elegirOrden,
                        onComparables = vm::alternarComparables,
                    )
                }

                val visibles = e.visibles
                if (visibles.isEmpty()) {
                    item(key = "vacio") {
                        Text(
                            "No hay productos para mostrar con estos filtros.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (e.categoria == null) {
                    // Agrupado por categoría
                    val porCategoria = visibles.groupBy { it.categoria }.toSortedMap(compareBy<Categoria> { it.ordinal })
                    porCategoria.forEach { (cat, grupos) ->
                        stickyHeader(key = "h-${cat.name}") { EncabezadoCategoria(cat, grupos.size) }
                        items(grupos, key = { it.clave }) { g -> TarjetaGrupo(g, ::abrir) }
                    }
                } else {
                    items(visibles, key = { it.clave }) { g -> TarjetaGrupo(g, ::abrir) }
                }
            }
        }
    }
}

@Composable
private fun Filtros(
    e: EstadoBusqueda,
    onCategoria: (Categoria?) -> Unit,
    onOrden: (Orden) -> Unit,
    onComparables: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = e.categoria == null,
                onClick = { onCategoria(null) },
                label = { Text("Todas (${e.gruposBase.size})") },
            )
            e.conteoCategorias.forEach { (c, n) ->
                FilterChip(
                    selected = e.categoria == c,
                    onClick = { onCategoria(if (e.categoria == c) null else c) },
                    label = { Text("${c.emoji} ${c.nombre} ($n)") },
                )
            }
        }
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = e.soloComparables,
                onClick = onComparables,
                label = { Text("Solo en varios súper") },
                leadingIcon = if (e.soloComparables) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null,
            )
            Spacer(Modifier.weight(1f))
            Box {
                TextButton(onClick = { menu = true }) {
                    Icon(Icons.Default.Sort, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(e.orden.etiqueta, maxLines = 1)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    Orden.entries.forEach { o ->
                        DropdownMenuItem(
                            text = { Text(o.etiqueta) },
                            onClick = { onOrden(o); menu = false },
                            leadingIcon = if (o == e.orden) ({ Icon(Icons.Default.Check, null) }) else null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EncabezadoCategoria(c: Categoria, n: Int) {
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${c.emoji}  ${c.nombre}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text("$n", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun sufijoUnidad(c: Cantidad?): String = when (c?.unidad) {
    Cantidad.Unidad.KG -> "/kg"
    Cantidad.Unidad.LITRO -> "/L"
    Cantidad.Unidad.UNIDAD -> "/u"
    null -> ""
}

@Composable
private fun TarjetaGrupo(g: Grupo, abrir: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    if (g.imagen.isNotBlank()) {
                        AsyncImage(
                            model = g.imagen,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(4.dp),
                        )
                    } else {
                        Text(g.categoria.emoji, style = MaterialTheme.typography.headlineSmall)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(g.nombre, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val sub = listOfNotNull(g.marca.takeIf { it.isNotBlank() }, g.cantidad?.toString()).joinToString(" · ")
                    if (sub.isNotEmpty()) {
                        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            g.ofertas.forEachIndexed { i, p ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                FilaOferta(p, masBarato = g.comparable && i == 0, abrir = abrir)
            }
            g.ahorroPct?.takeIf { it >= 0.5 }?.let { pct ->
                Spacer(Modifier.height(4.dp))
                Text(
                    "Diferencia entre súper: ${pesos(g.ofertas.maxOf { it.precio } - g.mejor.precio)} (${"%.0f".format(pct)} %)",
                    style = MaterialTheme.typography.labelMedium,
                    color = VerdeAhorro,
                )
            }
        }
    }
}

@Composable
private fun FilaOferta(p: Producto, masBarato: Boolean, abrir: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (masBarato) VerdeAhorro.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(enabled = p.link.isNotBlank()) { abrir(p.link) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(colorDe(p.superm)))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(p.superm.nombre, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (p.promos.isNotEmpty()) {
                Text(
                    p.promos.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (p.tieneRebaja) {
                    Text(
                        pesos(p.precioLista),
                        style = MaterialTheme.typography.labelSmall,
                        textDecoration = TextDecoration.LineThrough,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    pesos(p.precio),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (masBarato) VerdeAhorro else MaterialTheme.colorScheme.onSurface,
                )
            }
            p.precioPorUnidad?.let { pu ->
                Text(
                    pesos(pu) + sufijoUnidad(p.cantidad),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
