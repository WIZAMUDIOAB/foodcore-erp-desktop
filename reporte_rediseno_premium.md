# Reporte Final — Sistema de Diseño Cerrado FoodCore `v2.0` + Corrección UX/UI `v2.2` (Sub-Fase D)

**Rama:** `feature/subfase-d-pwa-premium`  
**Especificaciones:** MD MAESTRO — Sistema de Diseño Cerrado FoodCore (`v2.0`) · MD MAESTRO de Corrección UX/UI (`v2.2`)  
**Fecha:** 2026-11-16  
**Estado:** Aprobado · `mvnw.cmd -B clean verify` en verde (`290 / 290` tests, `0` fallos)

---

## 1. Resumen de Commits Atómicos (`v2.0` y `v2.2`)

| Commit | Alcance | Descripción Verificada |
|---|---|---|
| `00c59dd` | Fuentes locales + JavaFX ERP (`v2.0`) | `Inter` local (`font-1..13.woff2` + `fonts.css`) en `src/main/resources/fonts/` y `pwa/fonts/`; migración de `foodcore.css`, `dark-theme.css` y `DashboardController.java` a tokens `v2.0` (`#F9F7F4` / `#F2EFEA` / `#FFFFFF` / `#EA580C` en claro; `#09090B` / `#121214` / `#18181B` / `#F97316` OLED Zinc en oscuro). |
| `425b6bd` | PWA Base & Login PIN (`v2.0`) | Migración de `pwa/css/styles.css`, `pwa/js/app.js` y HTML PWA a tokens `v2.0`, `fonts.css`, grid `4px` y teclado PIN `3x4` sin inputs visibles. |
| `c930fba` | Menú Digital QR (`v2.0`) | Reescritura de `pwa/menu-digital/` y `MenuQrService.java` a tokens `v2.0`, hero `200px`, `.card` sólida, placeholders neutros con iconos Feather SVG y barra inferior `glass-panel`. |
| `63b8b9d` | PWA KDS & Dashboard Móvil (`v2.0`) | KDS en modo OLED Zinc por defecto (`#09090B`/`#18181B`, cronómetros `28px`) y Dashboard móvil KPIs `12px`/`20px` leyendo tokens vía `getComputedStyle`. |
| `1409c7a` | Baselines & Reporte `v2.0` | Actualización de baselines `toHaveScreenshot()` (`<= 2%`) y reporte `v2.0` (`289 / 289` tests). |
| `da96e13` | CSS Estructural `v2.2` | Clases `.card`, `.modal-card`, `.modal-header`, `.modal-body`, `.modal-footer`, `.edge-to-edge`, `.label-form`, `.h2`, `.h3`, `.price`, `.text-secondary`, `.text-muted`, `.btn-danger`, `.btn-warning`, `.dialog-pane(.card)`, `-fx-graphic-text-gap: 8px` y `.card > .btn-primary { -fx-min-width: 112px; }` en `foodcore.css` / `dark-theme.css` (copiados también bajo `/com/foodcore/css/`). |
| `1cbe6a8` | Diálogos y Helpers `v2.2` | Creación de `DialogService`, `EmptyStateHelper` y `ToastService`; reemplazo de todo `new Alert`/`Dialog`/`TextInputDialog`/`ChoiceDialog` directo en 11 controladores e inyección condicional de tema oscuro en `DialogPane` con `.card` y botones estilizados. |
| `ce62739` | Layouts FXML `v2.2` | Estandarización §2.1 de los 14 modales FXML (`modal-header` + `ScrollPane.edge-to-edge` + `modal-footer`), `<Label styleClass="label-form">` estático encima de todos los inputs FXML, `EmptyState` §2.3 (`fth-inbox` 48px, *"No hay registros"*) en todas las tablas (`MermasView`, `AnalisisMermasView`, `ComandaMesaView`, `MozoOrderView` y 32 vistas adicionales), iconos Feather `FontIcon` con `graphicTextGap="8.0"` y puntos de leyenda 10x10 en `SalonMesasView`. |
| `8a20947` | Gráficos y Comanda Cards `v2.2` | Tarjetas de producto §2.4 (`HBox(16)` `.card` + `VBox(4)` `.h3`/`.text-secondary`/`.price` + `+ Agregar`) en `VentaController`, `MozoOrderController` y `KioscoController`; paleta Donut cerrada de 4 colores (`#EA580C`, `#D97706`, `#DC2626`, `#A39D94`) en `AnalisisMermasController`, `ReportesAvanzadosController` y `ReportesController`, y purga de hex morados/azules inline. |
| `132fa63` | Suite Automatizada Checklist `v2.2` | Aserciones automatizadas del Checklist §5 (items 1–7) y capturas JavaFX `v2.2` en `RedisenoPremiumVisualE2ETest.java`. |
| `eaf955f` | Layout Modales Dinámicos `v2.2` | Ejecución de `applyCss()` y `layout()` al insertar overlays con `ScrollPane` en `ArqueoCajaController`, `RolesController`, `InventarioController` y `DeliveryController` asegurando descubrimiento inmediato en escena y `FinanzasBiV490IT`. |

---

## 2. Sistema de Tokens Cerrados `v2.0` + Correcciones Estructurales `v2.2`

| Token / Regla | Modo Claro (`:root` / `.root`) | Modo Oscuro OLED Zinc (`.dark` / `dark-theme.css`) |
|---|---|---|
| `--bg-canvas` / `-bg-canvas` | `#F9F7F4` | `#09090B` |
| `--bg-sidebar` / `-bg-sidebar` | `#F2EFEA` | `#121214` |
| `--bg-surface` / `-bg-surface` | `#FFFFFF` | `#18181B` |
| `--text-primary` / `-text-primary` | `#1A1817` | `#FAFAFA` |
| `--text-secondary` / `-text-secondary` | `#5C5650` | `#A1A1AA` |
| `--text-muted` / `-text-muted` | `#A39D94` | `#71717A` |
| `--brand-primary` / `-brand-primary` | `#EA580C` | `#F97316` |
| **Paleta Donut / Gráficos (§3 `v2.2`)** | `["#EA580C", "#D97706", "#DC2626", "#A39D94"]` | `["#EA580C", "#D97706", "#DC2626", "#A39D94"]` |

---

## 3. Checklist de Aprobación `v2.2` (§5)

1. **[x] Ningún `AnchorPane` ni `layoutX`/`layoutY` en FXML:** Verificado estáticamente sobre todas las vistas FXML en `RedisenoPremiumVisualE2ETest.verificarChecklistV22LayoutsYComponentes()`.
2. **[x] Cada input (`TextField`, `PasswordField`, `ComboBox`, `DatePicker`) tiene un `Label` estático encima:** Verificado sobre los 51 FXMLs y en controles programáticos vía `DialogService.construirCampoModal(...)`.
3. **[x] Todas las `Alert` y `Dialog` nativos inyectan CSS y clase `card` vía `DialogService`:** Verificado en JavaFX runtime (`DialogService.crearAlerta` / `crearDialogo`) y cero instanciaciones directas fuera de `DialogService.java`.
4. **[x] Gráficos Donut usan solo 4 colores (`#EA580C`, `#D97706`, `#DC2626`, `#A39D94`):** Aplicado y verificado en `AnalisisMermasController`, `ReportesAvanzadosController` y `ReportesController` (`labelsVisible="false"`, `legendSide="BOTTOM"`).
5. **[x] Tablas vacías muestran `EmptyState` (`fth-inbox` 48px, *"No hay registros"*) y nunca `"Tabla sin contenido"`:** Aplicado en `EmptyStateHelper`, `MermasView`, `AnalisisMermasView`, `ComandaMesaView`, `MozoOrderView`, `AsistenciaController` y 32 vistas FXML adicionales.
6. **[x] Modales siguen estructura §2.1 (`card`, Header / ScrollPane `.edge-to-edge` Body / Footer `Cancelar` + `Guardar`):** Aplicado en 14 modales FXML (`styleClass="card, ..."` con coma separadora) y en modales programáticos vía `DialogService.construirModalFormulario(...)`.
7. **[x] Botones con icono usan `graphicTextGap="8"` e iconos Feather `FontIcon`:** Configurado `-fx-graphic-text-gap: 8px;` en CSS y `graphicTextGap="8.0"` + `<FontIcon iconLiteral="fth-..."/>` en botones de sidebar y toolbars.

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
| `dashboard-mobile-light.png` | `390x1631` (fullPage) | `636,090` | `0.3375%` (`2,147 px`) | PASS |
| `dashboard-mobile-dark.png` | `390x1631` (fullPage) | `636,090` | `0.2858%` (`1,818 px`) | PASS |

---

## 5. Resultados de Verificación Completa (`mvnw.cmd -B clean verify`)

Ejecutado contra MySQL 8.0 local (`127.0.0.1:3306/foodcore_test`, `sslMode=REQUIRED`) con JavaFX Monocle headless (`-Dtestfx.headless=true`, tiempo total `34:49 min`, código de salida `0`):

- **Surefire (Unit + JavaFX + Playwright Visual E2E):** `Tests run: 222, Failures: 0, Errors: 0, Skipped: 0`
- **Failsafe (`*IT` Integration Tests):** `Tests run: 68, Failures: 0, Errors: 0, Skipped: 0`
- **Total de pruebas:** `290 / 290` aprobadas (`0` fallos, `0` errores, `0` omitidas)
- **JaCoCo (`jacoco:check`):** `Analyzed bundle 'foodcore-desktop' with 372 classes. All coverage checks have been met.`
- **Checkstyle (`checkstyle:check` / `checkstyle-verify`):** `0` violaciones (`You have 0 Checkstyle violations.`)
- **SpotBugs (`spotbugs:check`):** `0` bugs, `0` errores (`BugInstance size is 0`, `Error size is 0`)
- **OWASP Dependency-Check (`12.1.0`):** Análisis completado (`BUILD SUCCESS`)

---

## 6. Evidencias Visuales Generadas (`target/rediseno-premium-evidence/`)

- **JavaFX `v2.0` + `v2.2`:** `javafx-cockpit-light.png`, `javafx-cockpit-dark.png`, `javafx-dashboard-light.png`, `javafx-dashboard-dark.png`, `javafx-config-light.png`, `javafx-config-dark.png`, `javafx-pos-light.png`, `javafx-mozo-comanda-light.png`, `javafx-mermas-light.png`, `javafx-reportes-light.png`, `javafx-modal-merma-light.png`, `javafx-modal-supervisor-light.png`
- **PWA y Menú Digital QR Modo Claro y Oscuro:** `pin-login-light.png`, `pin-login-dark.png`, `menu-qr-light.png`, `menu-qr-dark.png`, `kds-timers-states.png`, `kds-timers-light.png`, `dashboard-mobile-light.png`, `dashboard-mobile-dark.png`
