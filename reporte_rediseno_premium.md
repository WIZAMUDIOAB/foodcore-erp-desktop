# Reporte Final — Sistema de Diseño Cerrado FoodCore v2.0 (Sub-Fase D)

**Rama:** `feature/subfase-d-pwa-premium`  
**Especificación:** MD MAESTRO — Sistema de Diseño Cerrado FoodCore (`v2.0`)  
**Fecha:** 2026-11-16  
**Estado:** Aprobado · `mvnw.cmd -B clean verify` en verde (`289 / 289` tests, `0` fallos)

---

## 1. Resumen de Commits Atómicos (`v2.0`)

| Commit | Alcance | Descripción Verificada |
|---|---|---|
| `00c59dd` | Fuentes locales + JavaFX ERP | Incorporación de `Inter` local (`font-1..13.woff2` + `fonts.css`) en `src/main/resources/fonts/` y `pwa/fonts/`; migración de `foodcore.css`, `dark-theme.css` y `DashboardController.java` a tokens `v2.0` (`#F9F7F4` / `#F2EFEA` / `#FFFFFF` / `#EA580C` en claro; `#09090B` / `#121214` / `#18181B` / `#F97316` OLED Zinc en oscuro), sombras tight `--shadow-sm`/`md`/`lg`, radios `6/8/12/16px`, filas `44px` y eliminación de `"Segoe UI"`. |
| `425b6bd` | PWA Base & Login PIN | Migración de `pwa/css/styles.css`, `pwa/js/app.js` y `meta[name="theme-color"]` en `pwa/{pin-login,index,mesas,catalogo}.html` a tokens `v2.0`, `../fonts/fonts.css`, grid `--space-*` (`4px`), `--ease-out` (`<= 300ms`) y teclado PIN `3x4` (`64px`, `4` puntos `12px`, cero inputs visibles). |
| `c930fba` | Menú Digital QR | Reescritura de `pwa/menu-digital/{css/menu-qr.css,index.html,js/menu-qr.js}` y `MenuQrService.java` a tokens `v2.0` con `/fonts/fonts.css`, hero `200px` `radius-xl`, tarjetas sólidas `.card` (`padding: 16px`, sin borde), placeholders degradados neutros (`--bg-sidebar` -> `--bg-canvas`) con iconos SVG Feather (`createDishPlaceholder`, sin círculos SVG marrones), barra fija inferior `glass-panel` y animación `morphIn 300ms`. |
| `63b8b9d` | PWA KDS & Dashboard Móvil | Reescritura de `pwa/kds/{css/kds.css,index.html,js/kds.js}` a modo OLED Zinc por defecto (`#09090B` / `#121214` / `#18181B` / `#F97316`) con toggle claro (`#F9F7F4`), `radius-md: 8px`, bordes semánticos `4px` y cronómetros `28px` `JetBrains Mono`; migración de `pwa/dashboard/{css/dashboard.css,index.html,js/dashboard.js}` a tarjetas KPI `12px`/`20px`, `tabular-nums` y gráficos leyendo tokens vía `getComputedStyle`. |
| *Actual* | Baselines v2.0 & Reporte | Actualización de `SubFaseDPwaPremiumTest.java`, `RedisenoPremiumVisualE2ETest.java`, regeneración de las 8 baselines en `src/test/resources/visual-baselines/` y reporte final tras `mvnw.cmd -B clean verify`. |

---

## 2. Sistema de Tokens Cerrados `v2.0`

| Token | Modo Claro (`:root` / `.root`) | Modo Oscuro OLED Zinc (`.dark` / `dark-theme.css`) |
|---|---|---|
| `--bg-canvas` / `-bg-canvas` | `#F9F7F4` | `#09090B` |
| `--bg-sidebar` / `-bg-sidebar` | `#F2EFEA` | `#121214` |
| `--bg-surface` / `-bg-surface` | `#FFFFFF` | `#18181B` |
| `--bg-surface-hover` / `-bg-surface-hover` | `#F7F5F1` | `#27272A` |
| `--border-subtle` / `-border-subtle` | `#E5E0D8` | `#27272A` |
| `--border-strong` / `-border-strong` | `#D1CCC2` | `#3F3F46` |
| `--text-primary` / `-text-primary` | `#1A1817` | `#FAFAFA` |
| `--text-secondary` / `-text-secondary` | `#5C5650` | `#A1A1AA` |
| `--text-muted` / `-text-muted` | `#A39D94` | `#71717A` |
| `--brand-primary` / `-brand-primary` | `#EA580C` | `#F97316` |
| `--brand-hover` / `-brand-hover` | `#C2410C` | `#FB923C` |
| `--brand-soft` / `-brand-soft` | `rgba(234, 88, 12, 0.10)` | `rgba(249, 115, 22, 0.15)` |
| `--status-success` / `--status-success-bg` | `#059669` / `#ECFDF5` | `#34D399` / `rgba(52, 211, 153, 0.12)` |
| `--status-warning` / `--status-warning-bg` | `#D97706` / `#FFFBEB` | `#FBBF24` / `rgba(251, 191, 36, 0.12)` |
| `--status-danger` / `--status-danger-bg` | `#DC2626` / `#FEF2F2` | `#F87171` / `rgba(248, 113, 113, 0.12)` |
| `--shadow-sm` / `--shadow-md` / `--shadow-lg` | Tight low-spread (`0 1px 2px rgba(..., 0.06)` / `0 2px 4px` / `0 4px 8px`) | Elevación por luminosidad (`0 1px 2px rgba(0,0,0,0.4)` / `0.5` / `0.6`) |
| `--radius-sm/md/lg/xl` | `6px` / `8px` / `12px` / `16px` | `6px` / `8px` / `12px` / `16px` |

---

## 3. Checklist de Aceptación (§9)

- [x] **¿El modo oscuro usa `#09090B` de fondo y `#18181B` en tarjetas? (No marrón, no `#000`):** Verificado en `dark-theme.css`, `pwa/css/styles.css`, `pwa/menu-digital/css/menu-qr.css`, `pwa/kds/css/kds.css`, `pwa/dashboard/css/dashboard.css`, `MenuQrService.java` y aserciones `getComputedStyle` en `RedisenoPremiumVisualE2ETest` y `SubFaseDPwaPremiumTest`.
- [x] **¿El modo claro usa `#F9F7F4` de fondo y `#FFFFFF` en tarjetas con `box-shadow: var(--shadow-sm)`?:** Verificado en CSS claro, `foodcore.css`, `getComputedStyle("body", "backgroundColor") == rgb(249, 247, 244)` y renders JavaFX/Playwright.
- [x] **¿La fuente es `Inter` en todo el proyecto (no Arial, no `system-ui` como fuente principal)?:** Verificado mediante `fonts.css` (`font-1..13.woff2`) en `src/main/resources/fonts/` y `pwa/fonts/`, carga de fuentes en `App.java` y ausencia de `"Segoe UI"` / Google Fonts CDN (`system-ui, sans-serif` únicamente como último fallback).
- [x] **¿Los números de precios y KPIs tienen `font-variant-numeric: tabular-nums`?:** Aplicado en KPIs de `dashboard.css`, precios del carrito/catálogo en `styles.css`, precios y carrito de `menu-qr.css` / `MenuQrService.java`, cronómetros/contadores en `kds.css` y tablas/KPIs JavaFX.
- [x] **¿Los márgenes y paddings son múltiplos de `4px`?:** Estandarizado mediante variables `--space-1` (`4px`) a `--space-12` (`48px`) del grid de `4px` (incluyendo `padding: 20px` en `.kpi-card`, `16px` en cards de Menú QR, `12px`/`8px` en inputs y botones).
- [x] **¿El glassmorphism solo se usa en header, sidebar y modales (NO en tablas ni tarjetas de producto)?:** `.glass-panel` restringido a barra de navegación/resumen inferior fija y chrome superior; tarjetas de producto, KPIs y tablas utilizan superficies sólidas (`--bg-surface`).
- [x] **¿Las animaciones duran `<= 300ms` y usan `ease-out`?:** Definido `--ease-out: cubic-bezier(0.23, 1, 0.32, 1)` con transiciones `150ms`–`300ms` y animación `morphIn` (`300ms`).
- [x] **¿El Login PIN no tiene inputs de texto visibles?:** Verificado en `pwa/pin-login.html` (`0` `<input>` visibles, `4` `.pin-dot` `12px`, teclado `3x4` táctil `64px`).
- [x] **¿Las imágenes del Menú QR son reales o degradados neutros con icono (NO círculos marrones)?:** Eliminado `buildFallbackDishSvg`; reemplazado por degradado neutro `var(--bg-sidebar)` -> `var(--bg-canvas)` con icono SVG Feather (`createDishPlaceholder`) y verificado por `RedisenoPremiumVisualE2ETest` (`0` `<circle>` en SVGs fallback).
- [x] **¿Se actualizaron todas las baselines en `src/test/resources/visual-baselines/`?:** Las 8 baselines fueron regeneradas bajo `v2.0` y verificadas en ejecuciones posteriores dentro del umbral `2.00%`.
- [x] **¿`mvn clean verify` pasa con `0` fallos y los `289` tests en verde?:** Ejecutado completo tras las ediciones finales (`221` Surefire + `68` Failsafe = `289`, `0` fallos).

---

## 4. Resultados de Regresión Visual Playwright (`toHaveScreenshot`)

Umbral máximo configurado (`MAX_DIFF_PIXEL_RATIO`): `2.00%` (`0.02`). Resultados registrados durante `mvnw.cmd -B clean verify`:

| Vista / Baseline | Resolución | Píxeles Totales | Diferencia Observada | Estado |
|---|---|---:|---:|---|
| `pin-login-light.png` | `390x844` | `329,160` | `0.0000%` (`0 px`) | PASS |
| `pin-login-dark.png` | `390x844` | `329,160` | `0.0000%` (`0 px`) | PASS |
| `menu-qr-light.png` | `430x932` | `400,760` | `0.0000%` (`0 px`) | PASS |
| `menu-qr-dark.png` | `430x932` | `400,760` | `0.0000%` (`0 px`) | PASS |
| `kds-timers-states.png` | `1280x800` | `1,024,000` | `0.0000%` (`0 px`) | PASS |
| `kds-timers-light.png` | `1280x800` | `1,024,000` | `0.0000%` (`0 px`) | PASS |
| `dashboard-mobile-light.png` | `390x1631` (fullPage) | `636,090` | `0.2162%` (`1,375 px`) | PASS |
| `dashboard-mobile-dark.png` | `390x1631` (fullPage) | `636,090` | `0.2963%` (`1,885 px`) | PASS |

---

## 5. Resultados de Verificación Completa (`mvnw.cmd -B clean verify`)

Ejecutado contra MySQL 8.0 local (`127.0.0.1:3306/foodcore_test`, `sslMode=REQUIRED`) con JavaFX Monocle headless (`-Dtestfx.headless=true`, tiempo total `35:36 min`, código de salida `0`):

- **Surefire (Unit + JavaFX + Playwright Visual E2E):** `Tests run: 221, Failures: 0, Errors: 0, Skipped: 0`
- **Failsafe (`*IT` Integration Tests):** `Tests run: 68, Failures: 0, Errors: 0, Skipped: 0`
- **Total de pruebas:** `289 / 289` aprobadas (`0` fallos, `0` errores, `0` omitidas)
- **JaCoCo (`jacoco:check`):** `Analyzed bundle 'foodcore-desktop' with 369 classes. All coverage checks have been met.`
- **Checkstyle (`checkstyle:check`):** `0` violaciones (`You have 0 Checkstyle violations.`)
- **SpotBugs (`spotbugs:check`):** `0` bugs, `0` errores (`BugInstance size is 0`)
- **OWASP Dependency-Check (`12.1.0`):** Análisis completado (`BUILD SUCCESS`)

---

## 6. Evidencias Visuales Generadas (`target/rediseno-premium-evidence/`)

- **JavaFX Modo Claro y Oscuro OLED Zinc:** `javafx-cockpit-light.png`, `javafx-cockpit-dark.png`, `javafx-dashboard-light.png`, `javafx-dashboard-dark.png`, `javafx-config-light.png`, `javafx-config-dark.png`
- **PWA y Menú Digital QR Modo Claro y Oscuro:** `pin-login-light.png`, `pin-login-dark.png`, `menu-qr-light.png`, `menu-qr-dark.png`, `kds-timers-states.png`, `kds-timers-light.png`, `dashboard-mobile-light.png`, `dashboard-mobile-dark.png`
