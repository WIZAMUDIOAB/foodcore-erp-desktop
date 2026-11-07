# Guía de Sistema de Diseño UI/UX — FoodCore ERP v1.1

| Campo | Valor |
|---|---|
| Especificación Base | `UI-REDESIGN-SPEC-FOODCORE-v1.1` |
| Tema Base | AtlantaFX `PrimerLight` + extensión `src/main/resources/css/foodcore.css` |
| Referencia Visual | POS de producción (Toast, Square, Lightspeed) + patrones SaaS B2B |

---

## 1. Paleta de Colores (Design Tokens)

Todos los colores están centralizados como variables en `.root` dentro de `src/main/resources/css/foodcore.css`. Está prohibido hardcodear valores hexadecimales en archivos `.fxml`.

| Rol | Token CSS | Hex | Uso |
|---|---|---|---|
| **Fondo general** | `-fc-bg-canvas` | `#F6F7F9` | Canvas principal de la aplicación |
| **Superficie** | `-fc-bg-surface` | `#FFFFFF` | Tarjetas, paneles, modales e inputs |
| **Superficie sutil** | `-fc-bg-subtle` | `#EEF0F3` | Filas alternas de tabla, divisores y hover |
| **Borde** | `-fc-border` | `#DDE1E6` | Inputs, tarjetas y separadores |
| **Texto primario** | `-fc-text-primary` | `#1A1D21` | Títulos, texto principal y paneles oscuros de marca |
| **Texto secundario** | `-fc-text-secondary` | `#5C6470` | Labels auxiliares, subtítulos |
| **Texto tenue** | `-fc-text-muted` | `#8A929E` | Placeholders, captions y notas secundarias |
| **Acento (marca)** | `-fc-accent` | `#F05A22` | Botón primario, borde de foco, indicadores activos |
| **Acento hover** | `-fc-accent-hover` | `#D94E1A` | Estado hover del botón primario |
| **Acento suave** | `-fc-accent-soft` | `#FDEBE4` | Fondo de chips activos y badges de marca |
| **Éxito / KDS < 5 min** | `-fc-success` | `#1F9D55` | Confirmaciones, SLA en tiempo, cuadre exacto |
| **Advertencia / KDS 5–10 min** | `-fc-warning` | `#D97706` | SLA en atención, mesas por cobrar |
| **Peligro / KDS > 10 min** | `-fc-danger` | `#DC2626` | SLA crítico, errores de validación, diferencias de caja |
| **Fondo KDS** | `-fc-kds-bg` | `#121417` | Viewport oscuro de alto contraste para cocina |
| **Superficie KDS** | `-fc-kds-surface` | `#1E2228` | Tarjetas individuales de ítems en KDS |

### Reglas de Color
- **Naranja (`#F05A22`)** reservado exclusivamente para acciones primarias, estado activo y foco de teclado.
- **Rojo (`#DC2626`)** reservado para errores de validación, descuadres de caja, acción destructiva y SLA crítico (> 10 min).
- **Verde (`#1F9D55`)** reservado para confirmaciones operativas y SLA en tiempo (< 5 min).

---

## 2. Tipografía y Escala Jerárquica

- **Familia principal UI:** `Inter` (cargada desde `/fonts/` con fallback automático a `Segoe UI`).
- **Familia numérica y financiera:** `JetBrains Mono` (con fallback a `Consolas`). Todas las cifras, precios, cantidades, saldos y cronómetros usan fuente monoespaciada para evitar *jitter* visual.

| Estilo | Clase CSS | Tamaño | Peso | Uso |
|---|---|---|---|---|
| **Display** | `.text-display` / `.text-display-inverse` | `32px` | `700` | Título de login y marca |
| **H1** | `.text-h1` / `.view-heading` | `24px` | `700` | Títulos de vista, modales y número de mesa |
| **H2** | `.text-h2` | `18px` | `600` | Encabezados de sección y tarjetas |
| **Body** | `.text-body` / `.text-body-secondary` | `15px` | `400` | Texto general e inputs |
| **Body Strong** | `.text-body-strong` | `15px` | `600` | Labels de formulario y nombres de ítems |
| **Caption** | `.text-caption` / `.text-caption-muted` | `13px` | `400` | Timestamps, tiempos de ocupación y ayudas |
| **Overline** | `.text-overline` / `.kds-column-overline` | `11px` | `600` | Encabezados Kanban y badges de estado |
| **Mono LG** | `.text-mono-lg` / `.text-mono-lg-accent` | `22px` | `700` | Total a pagar en POS, arqueo y Cierre Z |
| **Mono** | `.text-mono` / `.text-mono-accent` | `16px` | `600` | Precios en tiles, tablas, Kardex y cronómetro KDS |
| **Mono SM** | `.text-mono-sm` | `13px` | `600` | Cantidades y subtotales secundarios |

---

## 3. Espaciado, Radios y Sombras

### Escala de Espaciado (Base 4px)
- `XS`: `4px` (Icono ↔ texto, padding de badge)
- `SM`: `8px` (Gap inline entre chips o botones)
- `MD`: `12px` (Gap por defecto en listas y grillas)
- `LG`: `16px` (Padding interno de tarjetas)
- `XL`: `24px` (Padding de paneles y modales)
- `2XL`: `32px` (Separación de bloques)
- `3XL`: `48px` (Padding de paneles de login)

### Radios y Elevación
- **Radio SM (`6px`):** Inputs (`.input-field`) y chips de atajo (`.shortcut-chip`).
- **Radio MD (`10px`):** Tarjetas (`.surface-card`), botones (`.btn-primary`, `.btn-secondary`) y tiles POS (`.pos-menu-tile`).
- **Radio LG (`16px`):** Modales (`.modal-dialog-surface`) y contenedor de login (`.login-shell-card`).
- **Radio Pill (`999px`):** Chips de categoría (`.category-chip`) y badges de estado (`.badge-pill-*`).
- **Sombra 1:** `dropshadow(gaussian, rgba(26, 29, 33, 0.06), 4, 0, 0, 1)`
- **Sombra 2:** `dropshadow(gaussian, rgba(26, 29, 33, 0.08), 8, 0, 0, 2)`
- **Sombra 3 (Modales):** `dropshadow(gaussian, rgba(26, 29, 33, 0.12), 24, 0, 0, 8)`

---

## 4. Targets Táctiles y Componentes Reutilizables

| Componente | Clase CSS | Dimensiones y Comportamiento |
|---|---|---|
| **Botón primario** | `.btn-primary` | Alto `48px`, radio `10px`, fondo `#F05A22`, hover `#D94E1A`, texto blanco `600`. |
| **Botón secundario** | `.btn-secondary` | Alto `44px`, radio `10px`, fondo `#FFFFFF`, borde `#DDE1E6`, texto `#5C6470`. |
| **Botón destructivo** | `.btn-destructive` / `.btn-rose` | Alto `48px`, radio `10px`, fondo `#DC2626`, texto blanco `600`. |
| **Botón acción POS** | `.btn-pos-action-primary` / `.btn-pos-action-secondary` | Alto `56px`, radio `10px`, para acciones rápidas `Enviar` / `Cobrar` (`F5`/`F6`/`F7`). |
| **Input de texto / clave** | `.input-field` / `.text-field` / `.password-field` | Alto `48px`, radio `6px`, borde `#DDE1E6`, foco `#F05A22` (`2px`) con halo cálido. |
| **Tile de menú POS** | `.pos-menu-tile` | Mínimo `96px` de alto, radio `10px`, nombre `15px 600` y precio monoespaciado alineado a la derecha. |
| **Tarjeta de mesa** | `.mesa-card-libre` / `.mesa-card-ocupada` / `.mesa-card-cobrar` | Mínimo `120×120px`, borde superior de `4px` según estado, número en `H1` centrado y hover `150ms`. |
| **Tarjeta KDS** | `.kds-card-green` / `.kds-card-amber` / `.kds-card-red` | Fondo `#1E2228`, barra superior de `4px` según SLA (`<5m` verde, `5-10m` ámbar, `>10m` rojo con pulso `1.5s`). |
| **Badge de estado** | `.badge-pill-success` / `.badge-pill-warning` / `.badge-pill-danger` / `.badge-pill-accent` | Pill `999px`, alto `24px`, tipografía `11px 600` en mayúsculas. |
| **Toast** | `.toast-container` + `AnimationUtils.mostrarToast(...)` | Esquina inferior derecha, radio `10px`, sombra 3, auto-cierre a los `4s`. |
