package com.majestad.precios.data

import java.util.Locale

/** Contenido de un envase llevado a kilos, litros o unidades. */
data class Cantidad(val valor: Double, val unidad: Unidad) {
    enum class Unidad(val etiqueta: String) { KG("kg"), LITRO("L"), UNIDAD("u") }

    override fun toString(): String = when (unidad) {
        Unidad.KG -> if (valor < 1) "${fmt(valor * 1000)} g" else "${fmt(valor)} kg"
        Unidad.LITRO -> if (valor < 1) "${fmt(valor * 1000)} ml" else "${fmt(valor)} L"
        Unidad.UNIDAD -> "${fmt(valor)} u"
    }

    companion object {
        private fun fmt(v: Double): String =
            if (v == Math.floor(v)) v.toLong().toString() else String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')

        // "500 gr", "1,5 lt", "2.25 L", "900cc", "1 kg"
        private val reCant = Regex(
            """(\d+(?:[.,]\d+)?)\s*(kgs?|kilos?|kilogramos?|grs?|g|gramos?|lts?|l|litros?|ml|mls|cc|cm3)(?![a-z])""",
            RegexOption.IGNORE_CASE,
        )

        private val rePackAntes = Regex("""\b(\d{1,2})\s*x\s*${'$'}""")
        private val rePackDespues = Regex("""^\s*x\s*(\d{1,2})\b""")
        private val reUnidades = Regex("""\b(\d{1,3})\s*(?:u|un|unid|unidades)\b""", RegexOption.IGNORE_CASE)

        fun desdeNombre(nombre: String): Cantidad? {
            val texto = nombre.lowercase(Locale.ROOT)
            val m = reCant.findAll(texto).lastOrNull()
            if (m != null) {
                val num = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
                if (num <= 0) return null
                val u = m.groupValues[2].lowercase(Locale.ROOT)
                var cant = when {
                    u.startsWith("k") -> Cantidad(num, Unidad.KG)
                    u.startsWith("g") -> Cantidad(num / 1000, Unidad.KG)
                    u == "ml" || u == "mls" || u == "cc" || u == "cm3" -> Cantidad(num / 1000, Unidad.LITRO)
                    else -> Cantidad(num, Unidad.LITRO)
                }
                // Multiplicador de pack pegado al contenido: "6 x 1 lt" o "1 lt x 6"
                val antes = texto.substring(0, m.range.first)
                val despues = texto.substring(m.range.last + 1)
                val pack = (rePackAntes.find(antes)?.groupValues?.get(1)
                    ?: rePackDespues.find(despues)?.groupValues?.get(1))?.toIntOrNull()
                if (pack != null && pack in 2..48) cant = cant.copy(valor = cant.valor * pack)
                return cant
            }
            val unidades = reUnidades.find(texto)?.groupValues?.get(1)?.toIntOrNull()
            if (unidades != null && unidades in 2..200) return Cantidad(unidades.toDouble(), Unidad.UNIDAD)
            return null
        }
    }
}
