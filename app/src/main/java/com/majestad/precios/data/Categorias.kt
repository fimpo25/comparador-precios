package com.majestad.precios.data

import java.text.Normalizer
import java.util.Locale

/** Categorías propias de la app: cada súper usa su árbol y acá se unifican. */
enum class Categoria(val nombre: String, val emoji: String) {
    ALMACEN("Almacén", "🥫"),
    DESAYUNO("Desayuno y merienda", "☕"),
    GOLOSINAS("Golosinas y snacks", "🍫"),
    BEBIDAS("Bebidas", "🥤"),
    LACTEOS("Lácteos y huevos", "🥛"),
    FIAMBRES("Quesos y fiambres", "🧀"),
    CARNES("Carnes y pescados", "🥩"),
    FRUTAS_VERDURAS("Frutas y verduras", "🥬"),
    PANADERIA("Panadería", "🥖"),
    CONGELADOS("Congelados", "🧊"),
    LIMPIEZA("Limpieza", "🧽"),
    PERFUMERIA("Perfumería y cuidado", "🧴"),
    BEBES("Bebés", "🍼"),
    MASCOTAS("Mascotas", "🐾"),
    HOGAR("Hogar y otros", "🏠"),
    OTROS("Otros", "📦"),
}

object Categorias {

    /** Reglas en orden de prioridad: la primera que coincide gana. */
    private val reglas: List<Pair<Categoria, Regex>> = listOf(
        Categoria.MASCOTAS to """\b(mascota|perro|gato|cachorro|felino|canino|piedras sanitarias|alimento para (perro|gato))""",
        Categoria.BEBES to """\b(bebe|bebes|panal|panales|mundo bebe|infantil|mamadera|chupete|toallitas humedas)""",
        Categoria.CONGELADOS to """\b(congelad|helado|hamburguesa|nugget|bocadito|medallon|patitas|vegetales congelados)""",
        Categoria.LIMPIEZA to """\b(limpieza|limpiador|detergente|lavandina|desinfectante|jabon (en polvo|liquido para ropa|para ropa|en pan)|lavado|suavizante|quitamanchas|insecticida|repelente|esponja|trapo|rejilla|papel higienico|rollo de cocina|rollos de cocina|servilleta|bolsas? de residuo|lavavajilla|aromatizante|desodorante de ambiente|desodorantes de ambiente|ambientes|lustramuebles|cera|escoba|secador|limpiavidrio|destapa|papeles)""",
        Categoria.PERFUMERIA to """\b(perfumeria|cuidado personal|cuidado del cabello|cuidado oral|higiene|shampoo|acondicionador|desodorante|antitranspirante|jabon|crema (corporal|facial|de manos|dental)|dental|cepillo de dientes|afeitar|afeitadora|maquinita|toalla femenina|toallas femeninas|tampon|protector (solar|diario)|colonia|perfume|maquillaje|algodon|hisopo|alcohol en gel|preservativo|tintura|coloracion|enjuague bucal|farmacia|botiquin)""",
        Categoria.BEBIDAS to """\b(bebida|gaseosa|\bagua\b|aguas|jugo|cerveza|vino|vinos|fernet|aperitivo|whisky|vodka|ron\b|gin\b|licor|sidra|champagne|champana|espumante|energizante|isotonica|\bsoda\b|amargo|tonica|cola\b|bodega)""",
        Categoria.GOLOSINAS to """\b(golosina|chocolate|alfajor|caramelo|chupetin|chicle|bombon|oblea|turron|snack|papas fritas|\bmani\b|palitos|chizito|confites|pastillas|gomitas|barra de cereal|barrita)""",
        Categoria.DESAYUNO to """\b(desayuno|merienda|cafe|\bte\b|\bté\b|yerba|mate cocido|cacao|galletita|galletas|cereal|copos|avena|mermelada|jalea|\bmiel\b|azucar|edulcorante|budin|magdalena|tostada|bizcocho|vainilla|infusion)""",
        Categoria.LACTEOS to """\b(lacteo|leche|leches|yogur|yoghurt|manteca|crema de leche|dulce de leche|huevo|postre|postres|flan|ricota|queso crema|untable)""",
        Categoria.FIAMBRES to """\b(queso|quesos|fiambre|fiambreria|jamon|salame|salamin|mortadela|salchich|longaniza|bondiola|paleta cocida|lomito|leberwurst|picada|matambre arrollado)""",
        Categoria.CARNES to """\b(carne|carnes|carniceria|pescado|pescaderia|mariscos|pollo|pechuga|cerdo|vacuno|vacuna|asado|vacio|chorizo|morcilla|menudencia|molida|bife|nalga|cuadril|peceto|matambre|milanesa|suprema|merluza|salmon|atun fresco|achuras|embutido)""",
        Categoria.FRUTAS_VERDURAS to """\b(fruta|frutas|verdura|verduras|verduleria|hortaliza|banana|manzana|naranja|mandarina|limon|pera|frutilla|uva|kiwi|durazno|palta|lechuga|tomate perita|tomate redondo|cebolla|zanahoria|zapallo|papa negra|papa blanca|batata|morron|acelga|espinaca|ajo\b|zucchini|berenjena|frutos secos|hongos)""",
        Categoria.PANADERIA to """\b(panaderia|\bpan\b|panes|facturas|medialuna|prepizza|pizzeta|tapas? de empanada|tapas para|grisines|tortitas|bizcochitos|pan rallado)""",
        Categoria.ALMACEN to """\b(almacen|aceite|aceites|arroz|fideo|fideos|pasta|pastas|harina|legumbre|lentejas|porotos|garbanzo|conserva|salsa|pure|aderezo|mayonesa|ketchup|mostaza|\bsal\b|especia|condimento|pimienta|oregano|caldo|sopa|vinagre|aceto|atun|polenta|tomate triturado|pure de tomate|enlatado|aceituna|pickle|encurtido|levadura|reposteria|gelatina|bizcochuelo|premezcla|semola|grasa|snacks salados)""",
        Categoria.HOGAR to """\b(bazar|hogar|textil|electro|electrodomestico|ferreteria|libreria|jugueteria|juguete|automotor|jardin|deporte|tecnologia|\bpila|pilas|lampara|vaso|plato|cocina y comedor|camping|ropa|calzado)""",
    ).map { (cat, patron) -> cat to Regex(patron) }

    fun normalizar(texto: String): String {
        val sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        return sinAcentos.lowercase(Locale.ROOT).replace('ñ', 'n')
    }

    private fun buscar(texto: String): Categoria? {
        if (texto.isBlank()) return null
        val t = normalizar(texto)
        return reglas.firstOrNull { it.second.containsMatchIn(t) }?.first
    }

    /**
     * Primero mira la ruta de categorías del súper, del nivel más específico al más general.
     * Si no alcanza, usa el nombre del producto.
     */
    fun clasificar(rutaOriginal: String, nombre: String): Categoria {
        val niveles = rutaOriginal.split('>', '/', '|').map { it.trim() }.filter { it.isNotEmpty() }
        for (nivel in niveles.asReversed()) {
            buscar(nivel)?.let { return it }
        }
        return buscar(nombre) ?: Categoria.OTROS
    }
}
