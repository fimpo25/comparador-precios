# Comparador Súper

App Android que busca un producto en **Carrefour, Jumbo, Coto y La Reina** a la vez,
agrupa el mismo producto por código de barras (EAN), marca el más barato y organiza
los resultados por categoría.

- Carrefour y Jumbo: catálogo público VTEX (`/api/catalog_system/pub/products/search`).
- Coto: Coto Digital (Endeca, `categoria?Ntt=...&format=json`). Precio de Coto Digital.
- La Reina: sitio ASP; la app lee el formulario de búsqueda y el `gtag`/tarjetas `PCompra('<EAN>')`.

Cada push a `main` compila el APK firmado con GitHub Actions y lo publica en Releases.
La app se actualiza sola desde ahí (menú ⟳ arriba a la derecha).
