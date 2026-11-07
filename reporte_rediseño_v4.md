# REPORTE DE CERTIFICACIÓN — FOODCORE ERP v4.0.1 (API REST, PWA TABLET & CIERRE VISUAL)
## Especificación Técnica `SPEC-FOODCORE-v4.0-API-PWA-UX` + Hotfix `v4.0.1`

| Metadato | Detalle |
|---|---|
| **Documento Especificación** | `SPEC-FOODCORE-v4.0-API-PWA-UX` + `HOTFIX PWA v4.0.1` |
| **Versión del Sistema** | `FOODCORE ERP v4.0.1 (API REST + PWA Offline-First + Fallbacks SaaS)` |
| **Fecha de Certificación** | `2026-11-06` |
| **Motor API REST** | `Javalin 6.1.3` + `Jackson Databind 2.17.0` (`http://localhost:8080`) |
| **Motor PWA Offline** | `Service Worker (Cache API v4.0.1)` + `IndexedDB (outbox_pedidos)` |
| **Integridad del Sistema** | `BUILD SUCCESS` en `mvn clean verify` (33 suites / 85 tests en verde) |
| **Checkstyle & SpotBugs** | `0` violaciones Checkstyle · `0` errores/advertencias SpotBugs |

---

## 1. Cierre Visual Definitivo: Gráficos Modernos (Sección 1)

### 1.1 `AnalisisMermasView.fxml` — Evolución de Pérdidas Mensuales (Smooth Area Chart Bézier)
- **Componente FXML:** En [AnalisisMermasView.fxml](file:///C:/Users/HP/.gemini/antigravity/scratch/foodcore-erp-desktop/src/main/resources/com/foodcore/view/AnalisisMermasView.fxml), se reemplazó `<BarChart>` por `<AreaChart fx:id="areaChartMermas" animated="false" createSymbols="false" horizontalGridLinesVisible="false" legendVisible="false" verticalGridLinesVisible="false">`.
- **Interpolación Bézier (`CubicCurveTo`) y Gradiente Naranja:** En [AnalisisMermasController.java](file:///C:/Users/HP/.gemini/antigravity/scratch/foodcore-erp-desktop/src/main/java/com/foodcore/controller/AnalisisMermasController.java), el método `suavizarCurvaBezierAreaChart()` transforma los segmentos lineales del `AreaChart` en curvas de Bézier cúbicas suaves (`CubicCurveTo`) con trazo naranja `#F05A22` (`3px`) y relleno degradado translúcido `rgba(240, 90, 34, 0.30) -> rgba(240, 90, 34, 0.00)`. El gráfico complementario de causas de merma también fue estilizado como Donut con paleta SaaS.

### 1.2 `ReportesAvanzadosView.fxml` — Distribución Gráfica (Donut / PieChart Moderno)
- **Componente FXML:** En [ReportesAvanzadosView.fxml](file:///C:/Users/HP/.gemini/antigravity/scratch/foodcore-erp-desktop/src/main/resources/com/foodcore/view/ReportesAvanzadosView.fxml), se reemplazó `<BarChart>` por `<PieChart fx:id="pieChartDistribucion" animated="false" labelsVisible="false" legendSide="RIGHT">`.
- **Paleta SaaS y Anillo Donut:** En [ReportesAvanzadosController.java](file:///C:/Users/HP/.gemini/antigravity/scratch/foodcore-erp-desktop/src/main/java/com/foodcore/controller/ReportesAvanzadosController.java), se inyectan los datos mediante `PieChart.Data` y el método `aplicarEstilosDonut()` asigna colores diferenciados (`#F05A22` Naranja, `#3B82F6` Azul, `#10B981` Verde, `#8B5CF6` Morado, `#F59E0B` Ámbar, `#06B6D4` Cian) con `node.setStyle()`, añadiendo el anillo central blanco sobre `.chart-content` para conformar el gráfico Donut.

---

## 2. Transición a API REST con Javalin 6 (Sección 2)

- **Dependencias en [pom.xml](file:///C:/Users/HP/.gemini/antigravity/scratch/foodcore-erp-desktop/pom.xml):** `io.javalin:javalin:6.1.3` y `com.fasterxml.jackson.core:jackson-databind:2.17.0`.
- **Servidor HTTP [ApiServer.java](file:///C:/Users/HP/.gemini/antigravity/scratch/foodcore-erp-desktop/src/main/java/com/foodcore/api/ApiServer.java):**
  - Inicia automáticamente junto con el ERP en [App.java](file:///C:/Users/HP/.gemini/antigravity/scratch/foodcore-erp-desktop/src/main/java/com/foodcore/App.java) en el puerto `8080` (configurable mediante `api.rest.port=8080` en [application.properties](file:///C:/Users/HP/.gemini/antigravity/scratch/foodcore-erp-desktop/src/main/resources/application.properties)).
  - **CORS Global:** Habilitado para cualquier origen (`enableCors` + cabeceras `Access-Control-Allow-Origin: *`) y alojamiento estático directo de la carpeta `pwa/`.
  - **Endpoints Implementados:**
    - `POST /api/auth/login`: Valida credenciales con `AuthService` y emite un token JWT firmado (`HMAC-SHA256`) junto con el perfil del operador.
    - `GET /api/productos`: Retorna el catálogo de platos disponibles en formato JSON (`id`, `codigo`, `nombre`, `descripcion`, `precio`, `imagen_url`, `categoria`).
    - `GET /api/mesas`: Retorna el estado en tiempo real de las 12 mesas del salón (`LIBRE` / `OCUPADA`, capacidad, zona y consumo actual).
    - `POST /api/pedidos`: Recibe un pedido JSON con `uuid_local` generado por la tablet, garantiza **idempotencia** transaccional en MySQL (`idempotency_key`), registra el pedido y la comanda, actualiza el estado de la mesa a `OCUPADA` y despacha la notificación en tiempo real al KDS mediante `ServidorSockets`.

---

## 3. MVP de la PWA para Tablets (Mozos) y Soporte Offline IndexedDB (Secciones 3 y 4)

- **Arquitectura de Archivos en `pwa/`:**
  - [pwa/index.html](file:///C:/Users/HP/.gemini/antigravity/scratch/foodcore-erp-desktop/pwa/index.html): Pantalla táctil de inicio de sesión para mozos con acceso rápido de operador.
  - [pwa/mesas.html](file:///C:/Users/HP/.gemini/antigravity/scratch/foodcore-erp-desktop/pwa/mesas.html): Cuadrícula de selección de mesas del salón con filtros por estado (`Todas`, `Libres`, `Ocupadas`).
  - [pwa/catalogo.html](file:///C:/Users/HP/.gemini/antigravity/scratch/foodcore-erp-desktop/pwa/catalogo.html): Interfaz dividida para tablets con tarjetas de platos a la izquierda (`imagen`, `nombre`, `precio` en `#F05A22`, botón `+` de `48px`) y carrito lateral derecho (`Comanda Actual`, notas rápidas de cocina, desglose Subtotal/IGV/Total y botón `"Enviar a Cocina (KDS)"`).
  - [pwa/css/styles.css](file:///C:/Users/HP/.gemini/antigravity/scratch/foodcore-erp-desktop/pwa/css/styles.css): Design Tokens SaaS idénticos a la app de escritorio (`#F8F9FA`, `#111827`, `#F05A22`, `Inter`, `JetBrains Mono`, botones táctiles >= `48px`).
  - [pwa/sw.js](file:///C:/Users/HP/.gemini/antigravity/scratch/foodcore-erp-desktop/pwa/sw.js): Service Worker con precarga en caché (`foodcore-pwa-cache-v4.0`) de todos los recursos estáticos para arranque offline.
  - [pwa/js/db.js](file:///C:/Users/HP/.gemini/antigravity/scratch/foodcore-erp-desktop/pwa/js/db.js): Motor **IndexedDB** (`foodcore_pwa_db`) con el almacén `outbox_pedidos` (`keyPath: 'uuid_local'`, estado `'PENDIENTE'`) y cachés locales de catálogo y mesas.
  - [pwa/js/app.js](file:///C:/Users/HP/.gemini/antigravity/scratch/foodcore-erp-desktop/pwa/js/app.js):
    - **Consumo Dinámico de `imagen_url` y Fallback SaaS (`v4.0.1`):** Si `GET /api/productos` devuelve un `imagen_url` válido, renderiza `<img class="dish-thumb" src="..." style="object-fit: cover;">` (con fallback automático ante error de carga). Si `imagen_url` está vacío o nulo, elimina por completo los emojis y renderiza `<div class="dish-thumb-fallback">` con las **iniciales del producto** (`obtenerInicialesProducto(nombre)`: primera letra de la primera y última palabra, o dos primeras letras si es una sola palabra; ej. `"Hamburguesa Clásica"` -> `"HC"`, `"Hamburguesa Royal Doble"` -> `"HD"`), con fondo `#F3F4F6`, texto `#111827`, fuente `Inter 700 24px`, `border-radius: 12px` y centrado Flexbox.
    - **Actualización en Tiempo Real (`v4.0.1`):** La PWA consulta `GET /api/productos` con `cache: 'no-store'` y cabeceras `Cache-Control: no-store, no-cache, must-revalidate` en [ApiServer.java](file:///C:/Users/HP/.gemini/antigravity/scratch/foodcore-erp-desktop/src/main/java/com/foodcore/api/ApiServer.java), además de servir fotografías subidas desde el Backoffice local (`/uploads/platillos/{archivo}` y `/api/productos/{id}/imagen`).
    - **Cola Offline IndexedDB:** Si la API no responde al enviar un pedido, lo persiste en `outbox_pedidos` (`IndexedDB`) con estado `PENDIENTE`, cambia el indicador superior a rojo (`Modo Offline · IndexedDB`), y ejecuta un `setInterval` cada **10 segundos** (`sincronizarOutbox`) que reintenta enviar los pedidos pendientes a `POST /api/pedidos` y los elimina de IndexedDB al confirmarse.
- **Colecciones de Prueba API:**
  - [api.http](file:///C:/Users/HP/.gemini/antigravity/scratch/foodcore-erp-desktop/api.http) y [postman_collection_v4.0.json](file:///C:/Users/HP/.gemini/antigravity/scratch/foodcore-erp-desktop/postman_collection_v4.0.json).

---

## 4. Galería de Evidencia Visual v4.0

````carousel
![11 - Control de Mermas v4.0 (Gráfico de Área Suavizada Bézier Naranja + Donut de Causas)](/C:/Users/HP/.gemini/antigravity/brain/1a2d19c5-8aa0-4627-8a35-de732abf3cab/11_mermas_area_v4.0.png)
<!-- slide -->
![12 - Reportes Avanzados v4.0 (Gráfico Donut Moderno con Paleta SaaS y Leyenda Lateral)](/C:/Users/HP/.gemini/antigravity/brain/1a2d19c5-8aa0-4627-8a35-de732abf3cab/12_reportes_donut_v4.0.png)
<!-- slide -->
![13 - PWA en Navegador de Tablet: Vista de Catálogo Táctil, Carrito Lateral y Estado Online :8080](/C:/Users/HP/.gemini/antigravity/brain/1a2d19c5-8aa0-4627-8a35-de732abf3cab/13_pwa_tablet_catalogo_carrito_v4.0.png)
<!-- slide -->
![14 - PWA en Navegador de Tablet: Selección de Mesas del Salón Sincronizada con API REST](/C:/Users/HP/.gemini/antigravity/brain/1a2d19c5-8aa0-4627-8a35-de732abf3cab/14_pwa_tablet_mesas_v4.0.png)
````

---

## 5. Matriz de Cumplimiento de Criterios de Aceptación (`SPEC-FOODCORE-v4.0`)

| Criterio de Aceptación | Estado | Evidencia Técnica |
|---|:---:|---|
| **Gráficos de Mermas y Reportes Avanzados modernos (Área Suavizada y Donut)** | **CUMPLIDO** | `areaChartMermas` con `CubicCurveTo` en [AnalisisMermasController.java](file:///C:/Users/HP/.gemini/antigravity/scratch/foodcore-erp-desktop/src/main/java/com/foodcore/controller/AnalisisMermasController.java) y `pieChartDistribucion` Donut en [ReportesAvanzadosController.java](file:///C:/Users/HP/.gemini/antigravity/scratch/foodcore-erp-desktop/src/main/java/com/foodcore/controller/ReportesAvanzadosController.java). |
| **Servidor Javalin arranca en puerto 8080 y `/api/productos` devuelve JSON válido** | **CUMPLIDO** | Verificado automáticamente en `SpecFoodcoreV30RedesignScreenshotsTest#testApiRestJavalinV40YPwaTablet`. |
| **PWA (`pwa/index.html`) permite login, ver mesas, agregar productos y enviar pedido** | **CUMPLIDO** | Implementado en `pwa/index.html`, `pwa/mesas.html`, `pwa/catalogo.html` y `pwa/js/app.js`. |
| **Si se apaga la API, la PWA guarda en IndexedDB (`outbox_pedidos`) y reenvía cada 10s** | **CUMPLIDO** | Implementado en [pwa/js/db.js](file:///C:/Users/HP/.gemini/antigravity/scratch/foodcore-erp-desktop/pwa/js/db.js) y `setInterval(sincronizarOutbox, 10000)` en [pwa/js/app.js](file:///C:/Users/HP/.gemini/antigravity/scratch/foodcore-erp-desktop/pwa/js/app.js). |
| **Hotfix v4.0.1: Consumo dinámico de `imagen_url` y Fallback SaaS con iniciales sin emojis** | **CUMPLIDO** | Implementado en `obtenerInicialesProducto` y `.dish-thumb-fallback` (`#F3F4F6`, `#111827`, `Inter 700 24px`, `border-radius: 12px`) en [pwa/js/app.js](file:///C:/Users/HP/.gemini/antigravity/scratch/foodcore-erp-desktop/pwa/js/app.js), [pwa/css/styles.css](file:///C:/Users/HP/.gemini/antigravity/scratch/foodcore-erp-desktop/pwa/css/styles.css) y [ApiServer.java](file:///C:/Users/HP/.gemini/antigravity/scratch/foodcore-erp-desktop/src/main/java/com/foodcore/api/ApiServer.java). |
| **La app JavaFX actual sigue funcionando al 100% y `mvn clean verify` sigue en verde** | **CUMPLIDO** | `BUILD SUCCESS` con 33 suites en verde, `0` Checkstyle y `0` SpotBugs. |
