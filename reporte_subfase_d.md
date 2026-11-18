# Reporte de Evidencias — Sub-Fase D (`v5.4.0`): PWA y Menú QR Premium

**Rama:** `feature/subfase-d-pwa-premium` (ramificada desde `develop` en `3b51ac2`)  
**Alcance:** Implementación visual y de experiencia de usuario para los 13 ítems (D1–D13) sin alterar la lógica de negocio ni contratos de catálogo.

---

## 1. Historial de Commits Atómicos

```text
e907d5c feat(pwa): targets tactiles 48px y botones KDS 56px (D1)
5b4a543 feat(pwa): feedback tactil scale(0.98) 0.05s y touch-action manipulation (D2)
0f5d258 feat(ui): skeletons de carga shimmer en JavaFX y .skeleton-card en PWA (D3)
5818467 feat(theme): modo oscuro dark-theme.css en JavaFX y prefers-color-scheme en PWA (D4, D7)
3343bf2 feat(kds): contraste AAA minimo 7:1 texto blanco puro sobre fondo oscuro (D5)
a50d25c feat(kds): fuente del cronometro KDS a 28px JetBrains Mono bold (D6)
d09ee09 feat(menu-qr): transicion liquid morph (morphIn/morphOut) al cambiar categoria (D8)
bbb235d feat(menu-qr): entrada en cascada con animation-delay escalonado 50ms en 6 tarjetas (D9)
bd14288 feat(menu-qr): header glassmorphism con backdrop-filter blur(12px) (D10)
64c63fd feat(menu-qr): efecto parallax 0.5x con window.addEventListener scroll (D11)
8170a64 feat(menu-qr): contador animado de precio con requestAnimationFrame 400ms (D12)
56a769c feat(menu-qr): soporte multi-idioma ES/EN con es.json, en.json y localStorage (D13)
38ed51a fix(ui): propagar dark-theme.css en nodos FXML y pulir parallax/sticky en Menu QR
```

*(Además del commit final de cierre con `SubFaseDPwaPremiumTest.java` y este reporte.)*

---

## 2. Matriz de Implementación y Evidencia (D1–D13)

| Ítem | Descripción | Archivos Modificados / Creados | Commit | Verificación Automatizada / Visual |
| :--- | :--- | :--- | :--- | :--- |
| **D1** | Targets táctiles PWA (`min-width`/`min-height: 48px`) y botones KDS (`56px`) | `pwa/css/styles.css`, `pwa/kds/css/kds.css` | `e907d5c` | `d1D2D5CssTactilZeroDelayYAltoContraste`, captura `01_pwa_botones_tactiles_48px.png` |
| **D2** | Feedback táctil `:active { transform: scale(0.98); }` (`0.05s`) y `touch-action: manipulation` global | `pwa/css/styles.css`, `pwa/kds/css/kds.css` | `5b4a543` | `d1D2D5CssTactilZeroDelayYAltoContraste`, Playwright PWA/KDS |
| **D3** | Skeleton loaders con animación shimmer (`@keyframes shimmer`, `1.5s infinite`) en JavaFX y PWA | `src/main/java/com/foodcore/util/SkeletonLoaderHelper.java`, `src/main/resources/css/foodcore.css`, `pwa/css/styles.css` | `0f5d258` | `d3SkeletonLoaderJavaFxYPwa`, captura `03_javafx_skeleton_loader.png` |
| **D4** | Modo oscuro JavaFX (`dark-theme.css`), toggle `#chkModoOscuro` en `ConfiguracionView.fxml` y propagación recursiva en nodos FXML | `src/main/resources/css/dark-theme.css`, `src/main/java/com/foodcore/App.java`, `src/main/resources/com/foodcore/view/ConfiguracionView.fxml`, `src/main/java/com/foodcore/controller/ConfiguracionController.java` | `5818467`, `38ed51a` | `d4D7ModoOscuroJavaFxYPwaAutomatico`, captura `04_javafx_dark_theme_configuracion.png` |
| **D5** | Contraste WCAG AAA mínimo 7:1 en KDS PWA (`#FFFFFF` sobre fondo oscuro `#090D16` / `#050811`) | `pwa/kds/css/kds.css`, `pwa/css/styles.css` | `3343bf2` | `d1D2D5CssTactilZeroDelayYAltoContraste`, captura `02_kds_pwa_alto_contraste_56px.png` |
| **D6** | Cronómetro KDS JavaFX en `JetBrains Mono` a `28px` negrita (`lblTiempo`) | `src/main/java/com/foodcore/controller/KitchenDisplayController.java`, `src/main/java/com/foodcore/controller/ComandaCardController.java`, `src/main/resources/css/foodcore.css` | `a50d25c` | TestFX sobre `lblTiempo` (`28.0px`, `bold`), captura `05_javafx_kds_cronometro_28px.png` |
| **D7** | Modo oscuro automático en PWA mediante `@media (prefers-color-scheme: dark)` | `pwa/css/styles.css` | `5818467` | `d4D7ModoOscuroJavaFxYPwaAutomatico` |
| **D8** | Transición Liquid Morph (`@keyframes morphIn` / `@keyframes morphOut`) al cambiar de categoría en Menú QR | `pwa/menu-digital/index.html`, `pwa/menu-digital/css/menu-qr.css`, `pwa/menu-digital/js/menu-qr.js`, `src/main/java/com/foodcore/service/MenuQrService.java` | `d09ee09` | `d8AD13MenuQrPremiumE2EYEvidenciasPlaywright`, `MenuDigitalDinamicoIT` |
| **D9** | Entrada en cascada con `animation-delay` escalonado de `50ms` en las primeras 6 tarjetas (`0ms`–`250ms`) | `pwa/menu-digital/css/menu-qr.css`, `pwa/menu-digital/js/menu-qr.js`, `src/main/java/com/foodcore/service/MenuQrService.java` | `bbb235d` | `d8AD13MenuQrPremiumE2EYEvidenciasPlaywright`, captura `06_menu_qr_glassmorphism_cascada_es.png` |
| **D10** | Header sticky Glassmorphism (`backdrop-filter: blur(12px)`) en Menú QR | `pwa/menu-digital/css/menu-qr.css`, `pwa/menu-digital/index.html`, `src/main/java/com/foodcore/service/MenuQrService.java` | `bd14288` | `d8AD13MenuQrPremiumE2EYEvidenciasPlaywright`, captura `06_menu_qr_glassmorphism_cascada_es.png` |
| **D11** | Efecto Parallax `0.5x` con `window.addEventListener('scroll')` sobre capa hero `.hero-parallax-layer` e imágenes `.parallax-img` | `pwa/menu-digital/js/menu-qr.js`, `pwa/menu-digital/css/menu-qr.css`, `pwa/menu-digital/index.html`, `src/main/java/com/foodcore/service/MenuQrService.java` | `64c63fd`, `38ed51a` | `d8AD13MenuQrPremiumE2EYEvidenciasPlaywright`, capturas `06` y `07` |
| **D12** | Contador animado de precio `animatePrice(..., 400)` con `requestAnimationFrame` (400ms), selector de tamaño `.size-pills` y `#orderSummaryBar` | `pwa/menu-digital/js/menu-qr.js`, `pwa/menu-digital/css/menu-qr.css`, `pwa/menu-digital/index.html`, `src/main/java/com/foodcore/service/MenuQrService.java` | `8170a64`, `38ed51a` | `d8AD13MenuQrPremiumE2EYEvidenciasPlaywright`, captura `07_menu_qr_multi_idioma_en_precio_animado.png` |
| **D13** | Soporte multi-idioma ES/EN (`es.json`, `en.json`), toggle `#btnLangEs`/`#btnLangEn` y persistencia en `localStorage` | `pwa/menu-digital/es.json`, `pwa/menu-digital/en.json`, `pwa/menu-digital/js/menu-qr.js`, `pwa/menu-digital/index.html`, `pwa/menu-digital/css/menu-qr.css`, `src/main/java/com/foodcore/service/MenuQrService.java` | `56a769c` | `d8AD13MenuQrPremiumE2EYEvidenciasPlaywright`, capturas `06` (ES) y `07` (EN) |

---

## 3. Evidencias Visuales Generadas (`target/v540-evidence/`)

Durante la ejecución de `SubFaseDPwaPremiumTest` (JavaFX Monocle headless + Playwright Chromium headless), se generan las siguientes 7 capturas verificadas visualmente:

1. `target/v540-evidence/01_pwa_botones_tactiles_48px.png` — PWA Salón / Login Mozo en viewport móvil mostrando tarjetas de perfil, keypad numérico y botón CTA con targets táctiles ≥ 48px.
2. `target/v540-evidence/02_kds_pwa_alto_contraste_56px.png` — KDS PWA en modo alto contraste (texto blanco `#FFFFFF` sobre fondo oscuro `#090D16` / `#050811`) y botones de acción ≥ 56px.
3. `target/v540-evidence/03_javafx_skeleton_loader.png` — Componente `SkeletonLoaderHelper` en JavaFX con 4 filas animadas estilo shimmer.
4. `target/v540-evidence/04_javafx_dark_theme_configuracion.png` — `ConfiguracionView` en JavaFX con `dark-theme.css` aplicado a escena y nodos raíz/ScrollPane, toggle `#chkModoOscuro` activo y campos legibles en paleta `#0B0F19` / `#111827`.
5. `target/v540-evidence/05_javafx_kds_cronometro_28px.png` — Tarjeta de comanda KDS en JavaFX con cronómetro `lblTiempo` en `JetBrains Mono`, `28px`, negrita (`00:00 min`).
6. `target/v540-evidence/06_menu_qr_glassmorphism_cascada_es.png` — Menú QR en español (`ES`), header glassmorphic `backdrop-filter: blur(12px)`, hero parallax, pestañas de categoría, tarjetas completas tras cascada y barra de resumen estimada sin solapamiento.
7. `target/v540-evidence/07_menu_qr_multi_idioma_en_precio_animado.png` — Menú QR cambiado a inglés (`EN`) persistido en `localStorage`, variante *Large (+S/ 5)* seleccionada y precio animado con `requestAnimationFrame` (`S/ 29.50`).

---

## 4. Resultado de `mvn -B clean verify`

**Entorno de ejecución:** Windows `MySQL80` local (`127.0.0.1:3306`, base de datos `foodcore_test`, `FOODCORE_DB_SSL_MODE=REQUIRED`) + JavaFX Monocle headless (`-DreuseForks=false -DforkCount=1 -Dtestfx.headless=true -Dtestfx.robot=glass -Dglass.platform=Monocle -Dmonocle.platform=Headless -Dprism.order=sw -Dprism.text=t2k -Djava.awt.headless=true`).

```text
[INFO] --- surefire:3.2.5:test (default-test) @ foodcore-desktop ---
[INFO] Tests run: 218, Failures: 0, Errors: 0, Skipped: 0

[INFO] --- failsafe:3.2.5:integration-test (default) @ foodcore-desktop ---
[INFO] Tests run: 68, Failures: 0, Errors: 0, Skipped: 0

[INFO] --- jacoco:0.8.12:check (jacoco-check) @ foodcore-desktop ---
[INFO] Analyzed bundle 'foodcore-desktop' with 369 classes
[INFO] All coverage checks have been met.

[INFO] --- checkstyle:3.3.1:check (checkstyle-verify) @ foodcore-desktop ---
[INFO] You have 0 Checkstyle violations.

[INFO] --- spotbugs:4.8.5.0:check (spotbugs-verify) @ foodcore-desktop ---
[INFO] BugInstance size is 0
[INFO] Error size is 0
[INFO] No errors/warnings found

[INFO] --- dependency-check:12.1.0:check (owasp-dependency-check) @ foodcore-desktop ---
[INFO] Analysis Complete (14 seconds)

[INFO] BUILD SUCCESS
[INFO] Total time:  37:56 min
```

- **Total de tests ejecutados:** **286** (`218` unitarios/UI Surefire + `68` integración/E2E Failsafe)
- **Fallos / Errores / Omitidos:** **0**
- **Calidad estática y cobertura:** Checkstyle (`0` violaciones), SpotBugs (`0` bugs / `0` errores), JaCoCo (`369` clases analizadas, umbrales cumplidos), OWASP Dependency-Check aprobado.
