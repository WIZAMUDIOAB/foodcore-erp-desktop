# CHANGELOG — FoodCore ERP Desktop

Todos los cambios notables en este proyecto se documentan en este archivo siguiendo `SPEC-FOODCORE-v1.0` y `REMEDIATION-PLAN-FOODCORE-v1.0`.

## [2.0.0-SNAPSHOT] - Sub-Fase 4D: Crecimiento Comercial (2026-11-06)

### Hotfix 4C.1 (Correcciones Obligatorias Aprobadas)
- **B1. POS Vacío (Catálogo y Productos)**:
  - Corregida la consulta en `ProductoDAOImpl.listarProductosDisponibles` mediante `LEFT JOIN` y tratamiento de disponibilidad (`disponible = 1 OR disponible IS NULL`).
  - Carga síncrona/inmediata en `VentaController` con catálogo de respaldo enriquecido (Hamburguesas, Pizzas, Alitas, Cervezas, Gaseosas, Limonada) garantizando que el mosaico de productos nunca quede en blanco bajo ninguna condición de red o inicio rápido.
- **B2. Reservas Duplicadas**:
  - Incorporado `DISTINCT` en `ReservaDAOImpl.listarTodas` y `listarPorFecha`.
  - Sanitización en fixtures de prueba eliminando duplicados preexistentes de clientes como Carlos Zambrano.
- **B3. Botones Truncados**:
  - En `SalonMesasView.fxml`, sustituidos los botones saturados por un `MenuButton` estilizado de "Operaciones de Mesa" con accesos directos a "Transferir Mesa", "Unir Mesas" y "Libro de Reservas", eliminando textos truncados como `Reserv...` o `Tran...`.
  - En `VentaPOSView.fxml`, alineados los botones superiores de acciones (`Fidelización`, `Dividir Cuenta`, `Descuento`, `Anular Venta`, `Arqueo`) en un contenedor `HBox` con `spacing="10"` y `alignment="CENTER_RIGHT"`.

### Módulos Implementados según SPEC-FOODCORE-v2.0 Sub-Fase 4D (§11, §12, §13, §14, §15)
- **11. Promociones y Descuentos Automáticos (PromocionesView.fxml, PromocionesController.java & PromocionService.java)**:
  - CRUD visual de promociones con 4 KPI cards (Total, Activas, Tipo Popular, Regla de Fidelización), filtros por tipo y buscador reactivo.
  - Soporte de tipos: `2x1` (segunda unidad gratis), `3x2` (tercera unidad gratis), `% Descuento` y `Monto Fijo`.
  - Condiciones granulares: aplicable a producto específico, categoría o total de orden con umbral de monto mínimo, días de semana y rango horario.
  - **Integración POS en tiempo real**: Al modificar ítems de la orden, el POS evalúa automáticamente las reglas comerciales activas e inyecta el descuento en el ticket.
- **12. Programa de Fidelización de Clientes (FidelizacionService.java & FidelizacionDAOImpl.java)**:
  - Regla de acumulación nativa: S/ 1.00 de consumo = 1 punto acumulado en la cuenta del cliente.
  - Extensión de la tabla `cliente` con campos `puntos_acumulados` y `fecha_nacimiento`.
  - Canje de puntos directo en POS (cada 10 puntos = S/ 1.00 de descuento) con modal de búsqueda por DNI/RUC y auditoría en `fidelizacion_movimiento`.
  - **Cupón de Cumpleaños Automático**: Generación de cupón del 15% OFF (`BDAY-CLI...`) para clientes en su mes de cumpleaños, con verificación de uso único.
- **13. Menú Digital QR Dinámico (MenuQrService.java, MenuQrModalView.fxml & MenuQrModalController.java)**:
  - Generación automática de página web responsive moderna `menu_digital.html` con catálogo, fotos, precios en soles y badges de especialidad.
  - Generación de código QR bidimensional mediante biblioteca Google ZXing 3.5.3, con previsualización en pantalla, exportación PNG y comando para ticketera térmica.
- **14. Modo Kiosco de Autoservicio Táctil (KioscoView.fxml & KioscoController.java)**:
  - Pantalla táctil autónoma full-screen para autoatención de clientes: navegación por categorías, tarjetas táctiles de platillos y bandeja interactiva "Mi Pedido" con selectores de cantidad.
  - Al presionar "CONFIRMAR", genera comanda directa al KDS (`tipoPedido = "KIOSCO"`, mesa = turno) y emite ticket térmico con número de turno secuencial (#T-101) sin requerir cajero.
- **15. Integración Delivery Webhook Rappi/PedidosYa (DeliveryWebhookService.java)**:
  - Servidor REST HTTP local embebido escuchando en `http://localhost:8089/api/v1/delivery/webhook`.
  - Procesador de payloads JSON estándar de delivery externo (Rappi / PedidosYa), mapeando ítems, notas y datos del comensal hacia `Pedido` y `DetallePedido` internos para despacho al KDS con la etiqueta "RAPPI".
- **Arquitectura y Calidad**:
  - Migración Flyway `V10__crecimiento_comercial_v2_0_subfase4d.sql` (tablas `promocion`, `fidelizacion_movimiento`, `cupon_cumpleanos`, columnas de fidelización en `cliente` y semillas activas).
  - Suite de pruebas de integración `SpecFoodcoreV20Subfase4DTest` con 7/7 pruebas aprobadas (100% verde).
  - Pipeline `mvn clean verify` 100% verde: 0 violaciones Checkstyle, 0 incidencias SpotBugs, cobertura JaCoCo satisfecha y 34 suites superadas.

## [2.0.0-SNAPSHOT] - Sub-Fase 4C: Operaciones de Salón (2026-11-06)

### Correcciones Previas Sub-Fase 4B (RRHH)
- **AsistenciaView**: Corregida la consulta y desduplicación de marcajes para evitar filas repetidas de un mismo colaborador en una misma jornada.
- **HorariosView**:
  - Panel lateral de "Equipo disponible" blindado con `minWidth="260.0"`, `prefWidth="270.0"` y `maxWidth="280.0"` para evitar contracción o recortes.
  - Tarjetas de turnos en la grilla semanal con texto multi-línea (`setWrapText(true)`) y `Tooltip` descriptivo para nombre, horario y rol.
  - Soporte de scroll horizontal en la grilla semanal (`minWidth="860.0"`, `hbarPolicy="AS_NEEDED"`).

### Módulos Implementados según SPEC-FOODCORE-v2.0-OWNERS-COCKPIT (§8, §9, §10)
- **8. Gestión de Mesas Completa (SalonMesasView.fxml, SalonMesasController.java, VentaPOSView.fxml & SalonService.java)**:
  - **Transferir Mesa**: Reasignación atómica de pedido activo de una mesa a otra, liberando la mesa de origen, ocupando la mesa de destino y registrando auditoría en `auditoria`.
  - **Unir Mesas**: Consolidación de comandas de múltiples mesas secundarias bajo una mesa principal mediante la tabla `mesa_union`; liberación atómica de todas las mesas unidas al concretar el cobro en caja.
  - **Pre-Cuenta Térmica**: Emisión de ticket preliminar no fiscal tanto en Salón como en POS, con desglose de ítems, totales y leyenda obligatoria `*** PRE-CUENTA - NO ES COMPROBANTE DE PAGO ***`.
  - **Notas por Ítem**: Soporte para indicaciones de cocina personalizadas por ítem (ej. "sin cebolla", "término 3/4") visibles en la comanda del POS y en la impresión de pre-cuenta.
- **9. Reservas de Mesas (ReservasView.fxml, ReservasController.java & ReservasService.java)**:
  - Libro de Reservas interactivo con 4 KPI cards (Total, Confirmadas, Pendientes, Sentadas), selector de fecha, buscador de clientes y modal para registrar nuevas reservas.
  - **Bloqueo preventivo en Salón**: Si una mesa cuenta con reserva para la jornada próxima, se renderiza dinámicamente un badge ámbar `RESERVADA [hora]` para advertir al personal y prevenir asignaciones indebidas.
  - **Notificación por WhatsApp**: Generación automática de enlace `https://wa.me/51...` con plantilla de confirmación de reserva pre-llenada.
- **10. Control de Mermas y Desperdicio con Análisis (AnalisisMermasView.fxml, AnalisisMermasController.java & AnalisisMermasService.java)**:
  - Dashboard de mermas con 4 KPI cards (Costo Total del Mes, Eventos Registrados, Mayor Causa y Costo Promedio).
  - Visualización gráfica mediante `BarChart` de pérdidas económicas por mes y `PieChart` de distribución porcentual por motivo (`Quemado`, `Vencido`, `Roto`, `Error de cocina`, `Sobrante`).
  - Ranking Top 5 de insumos con mayor merma económica y banner de alertas preventivas para insumos con $\ge 3$ mermas reincidentes en el mes.
  - Registro modal de merma con descuento automático en inventario y movimiento auditado en Kardex.
- **Arquitectura y Calidad**:
  - Migración Flyway `V9__salon_reservas_mermas_v2_0_subfase4c.sql` (columnas de pedido actual y mesa principal en `mesa`, creación de tablas `mesa_union`, `reserva_mesa` y `merma`, y semillas operativas).
  - Suite de pruebas de integración `SpecFoodcoreV20Subfase4CTest` con 6/6 pruebas aprobadas (100% verde).
  - Pipeline `mvn clean verify` 100% verde: 0 violaciones Checkstyle, 0 incidencias SpotBugs, JaCoCo satisfecho y 33 suites superadas sin errores.

## [2.0.0-SNAPSHOT] - Sub-Fase 4B: Recursos Humanos (2026-11-06)

### Módulos Implementados según SPEC-FOODCORE-v2.0-OWNERS-COCKPIT (§6, §7)
- **6. Control de Asistencia del Personal (RelojAsistenciaView.fxml, AsistenciaView.fxml & AsistenciaService.java)**:
  - Kiosco de Marcaje táctil/interactivo con reloj digital en tiempo real, teclado numérico en pantalla y soporte para DNI (8 dígitos) y PIN (4 dígitos).
  - Detección inteligente de entrada vs salida con cálculo de tardanzas (tolerancia 5 minutos) y horas extra (> 8 horas diarias).
  - Panel de Control de Asistencia con 4 KPI cards (Activos Ahora, Puntuales, Tardanzas y Horas Totales), filtrado por fecha y estado, y semáforo visual de asistencia.
  - Reporte de Planilla Mensual con cálculo de horas laboradas, tardanzas y exportación a Excel nativo `.xlsx` con Apache POI (encabezados ejecutivos, formato moneda y sumas).
- **7. Programación de Turnos y Horarios Semanales (HorariosView.fxml, HorariosService.java & MiHorarioView.fxml)**:
  - Calendario Semanal interactivo de Lunes a Domingo con turnos Mañana, Tarde, Noche y Personalizado.
  - Asignación ágil de colaboradores, publicación formal de turnos y alerta proactiva de déficit de cobertura para turnos sin cubrir en fines de semana y días de alta demanda.
  - Portal de Colaborador (`MiHorarioView.fxml`) con bienvenida personalizada, tarjetas resumen de horas del mes, sueldo base, cronograma visual de la semana y récord individual de asistencias.
- **Ajustes de UI Sub-Fase 4A**:
  - `ReportesContablesView.fxml`: Texto de botón de exportación acortado a "Exportar Compras 8.1" y "Exportar Ventas 14.1".
  - `ClientesView.fxml`: Label de límite de crédito ajustado a "Límite de Crédito".
  - `ProveedoresView.fxml`: Envoltorio en ScrollPane para garantizar visibilidad sin recortes del botón de desactivación/activación.
- **Arquitectura y Calidad**:
  - Migración Flyway `V8__rrhh_v2_0_subfase4b.sql` (columnas laborales en `usuario`, ampliación de `asistencia` y creación de `turno_programado`).
  - Suite de integración y visual `SpecFoodcoreV20Subfase4BTest` (5/5 pruebas verdes).
  - Pipeline `mvn clean verify` 100% verde: 0 violaciones Checkstyle, 0 incidencias SpotBugs, cobertura JaCoCo satisfecha, 33 suites ejecutadas con 0 fallos.

## [2.0.0-SNAPSHOT] - Sub-Fase 4A: Finanzas y Contabilidad (2026-11-06)

### Módulos Implementados según SPEC-FOODCORE-v2.0-OWNERS-COCKPIT
- **1. Reporte de Ventas Exportable al Contador (ReportesContablesView.fxml & ExportacionContableService.java)**:
  - Exportación PLE SUNAT Formato 14.1 (Registro de Ventas e Ingresos) en Excel .xlsx nativo.
  - Exportación PLE SUNAT Formato 8.1 (Registro de Compras) en Excel .xlsx nativo con discriminación de base imponible e IGV.
  - Resumen Mensual de Caja Z en formato PDF ejecutivo estilizado con tablas por turno y medio de pago.
- **2. Gestión de Proveedores y Cuentas por Pagar (ProveedoresView.fxml, CuentasPorPagarView.fxml & FinanzasService.java)**:
  - Maestro de Proveedores con validación de RUC, días de crédito y ficha comercial detallada.
  - Registro de facturas de compras con semáforo de alerta (PENDIENTE, VENCIDA, PAGADA).
  - Abono transaccional multi-caja o bancos con liquidación de saldos y generación de Orden de Compra formal en PDF.
- **3. Cuentas por Cobrar / Clientes Corporativos (ClientesView.fxml, CuentasPorCobrarView.fxml & CobroModalController.java)**:
  - Cartera de Clientes con RUC/DNI, límite de crédito, días de crédito y saldo deudor en tiempo real.
  - Venta en POS a crédito corporativo vinculada automáticamente a cuenta_por_cobrar.
  - Registro de cobros y abonos transaccionales y exportación de Estado de Cuenta formal en PDF.
- **4. Historial de Precios y Auditoría (HistorialPreciosView.fxml)**:
  - Auditoría inmutable de cambios de precio en productos e insumos con motivo obligatorio (≥ 5 caracteres).
  - Cálculo automático de variación porcentual y semáforo visual para cambios > 10%.
- **5. Reportes Avanzados con Exportación Multiformato (ReportesAvanzadosView.fxml)**:
  - Reportes de Ventas por Categoría, Medios de Pago, Platos Estrella (Top 10), Horas Punta y Rentabilidad de Insumos.
  - Exportación instantánea universal a PDF estilizado (OpenPDF), Excel .xlsx (Apache POI) y CSV tabular.
- **Arquitectura y Calidad**:
  - Migración de base de datos Flyway V7__finanzas_v2_0_subfase4a.sql aplicada y validada.
  - Suite de integración E2E SpecFoodcoreV20Subfase4ATest (6 pruebas verdes).
  - Pipeline mvn clean verify 100% verde: 33 suites de test ejecutadas con 0 fallos, 0 errores, 0 violaciones Checkstyle y 0 incidencias SpotBugs.

## [1.0.0-GA] - 2026-11-03

### Fase 1 — Fundaciones (Persistencia, Nomenclatura y Transacciones ACID)
- **[R-06]** Alineación total de nomenclatura al DDL canónico: columna `stock_actual_disponible` (`DECIMAL(14,4)`) y clave primaria `long idInsumo` (`BIGINT`) en `Insumo`, `Receta`, `KardexMovimiento`, `InsumoDAO` e `InsumoDAOImpl`.
- **[R-07]** Eliminación definitiva de `epochMillis` en `Pedido` y `PedidoDAOImpl`. El cálculo de cronómetro se realiza exclusivamente contra `detalle_pedido.enviado_en` en zona horaria `America/Lima`.
- **[R-05]** Incorporación de migraciones versionadas Flyway (`V1__init.sql`, `V2__seed_roles_permisos.sql`, `V3__seed_admin.sql`, `V4__seed_terminales.sql`), ejecutor `FlywayMigrator.migrate()` en bootstrap con `validateOnMigrate=true` y `baselineOnMigrate=false`, y eliminación de `schema.sql`.
- **[R-11]** Jerarquía limpia de excepciones de dominio: `StockInsuficienteException` e `InsumoNoEncontradoException` extienden `RuntimeException` directamente sin envolverse en `SQLException`.
- **[R-09]** Bloqueo pesimista `SELECT ... FOR UPDATE` sobre `insumo` en orden ascendente de `id_insumo` (`insumoDAO.lockearPorIds`) previo al descuento atómico de stock y registro en `kardex_movimiento`.
- **[R-10]** Reintento automático con backoff exponencial (`MAX_RETRIES = 3`, `100ms, 200ms, 400ms`) en `JdbcTransactionManager` ante Deadlocks (`1213`) y Lock Wait Timeouts (`1205`) de MySQL InnoDB, registrando `idOperacion` e `intento`.

### Fase 2 — Integración y Protocolo LAN Autenticado
- **[R-02.1]** Suite de integración `VentaServiceConcurrencyTest`: 2 hilos simultáneos con `CountDownLatch` sobre `stock_actual_disponible = 1` producen exactamente 1 venta, 1 salida en `kardex_movimiento`, stock en `0.0000` y 1 `StockInsuficienteException`.
- **[R-02.2]** Suite de integración `VentaServiceRollbackTest`: venta multi-insumo con fallo en el segundo componente revierte el 100% de la transacción (0 ventas, 0 kardex, primer insumo intacto, 0 movimientos de caja) y propaga `StockInsuficienteException`.
- **[R-02.3]** Suite de integración `KardexIntegridadTest`: verificación del invariante `kardex_movimiento.saldo_posterior == insumo.stock_actual_disponible` y cantidades firmadas tras venta, entrada, merma, ajuste positivo y ajuste negativo.
- **[R-02.4]** Suite de integración `CajaServiceTest`: ciclo completo de apertura (`turno_caja` `ABIERTO`), cobro (`movimiento_caja` `VENTA`), cierre (`saldo_final_teorico`, `saldo_final_contado`, `diferencia`, `CERRADO`) y cuadre de Cierre Z por medio de pago.
- **[R-02.5]** Suite de integración `PedidoServiceTest`: bloqueo optimista de `mesa` por columna `version` (`ConflictoEstadoException`), deduplicación por `idempotency_key` y validación del permiso `PEDIDO_ADICIONAR_POST_ENTREGA` en la transición `ENTREGADO -> ITEMS_ADICIONALES`.
- **[R-13] & [R-02.6]** Implementación de `OutboxRepository` y `OutboxDispatcher` con persistencia `PENDIENTE`, backoff exponencial (`1s, 2s, 4s, ..., 60s`), transición a `ENVIADO` al recibir `ACK`, reenvío automático en reconexión y suite `OutboxDispatcherTest`.
- **[R-14] & [R-02.7]** Implementación de `TerminalAuthService` con handshake `HELLO / WELCOME / RENEW` contra `terminal.secreto_hash` (BCrypt costo 12) y `activo = 1`, sesión efímera en memoria (`sessionSecret` de 32 bytes no persistido en BD), rechazo de tramas > 1 MB y suite `SocketsIntegracionTest`.
- **[R-02.8]** Suite de integración `FlywayMigrationTest`: verificación de migración desde cero (`V1..V4`), re-ejecución con `validateOnMigrate=true` sobre datos previos y validación de seeds obligatorios.

### Fase 3 — Interfaz Gráfica (JavaFX / TestFX)
- **[R-08]** Cronómetro KDS calculado individualmente **por ítem** (`detalle_pedido.enviado_en`) con semáforo de color independiente por tarjeta (`GREEN` < 5 min, `AMBER` 5–10 min, `RED` > 10 min).
- **[R-03]** Suites automatizadas de UI con TestFX/JavaFX (`LoginViewTest`, `VentaPOSViewTest`, `KitchenDisplayViewTest`).

### Fase 4 — Calidad, Empaquetado y Despliegue Seguro
- **[R-12]** Suite `FormatoTicketTest` con 4 pruebas verificando `line.length() <= 48`, línea más larga exactamente de `48` columnas, degradación ASCII de tildes/eñes y bytes ESC/POS de inicialización (`0x1B 0x40`) y corte (`0x1D 0x56 0x41 0x10`).
- **[R-01]** Pipeline `mvn clean verify` integrando Surefire, Failsafe, JaCoCo (`com/foodcore/service/impl` ≥ 0.80, `com/foodcore/dao/impl` ≥ 0.70, `com/foodcore/security` ≥ 0.90), Checkstyle (0 violaciones) y SpotBugs (0 bugs High/Medium).
- **[R-04]** Perfil Maven `release` reproducible basado en `jpackage` generando instaladores `dist/FoodCoreERP-1.0.0.msi` y `dist/FoodCoreERP-1.0.0.deb` con sumas de verificación SHA-256.
- **[R-15]** Endurecimiento de red y base de datos: `application.properties` con usuario de mínimo privilegio `foodcore_app` (`SELECT, INSERT, UPDATE, DELETE`) y contraseña externalizada `${FOODCORE_DB_PASS}`, usuario `foodcore_migrator` separado para bootstrap y `config/my.ini` con `bind-address=127.0.0.1`.
- **[R-16]** Scripts automatizados de respaldo y restauración (`scripts/backup.ps1`, `scripts/backup.sh`, `scripts/restore.ps1`, `scripts/restore.sh`) verificados en staging con bitácora `logs/restore_YYYYMMDD.log`.
- **[R-17]** Sincronización verificada en `origin/develop` y `origin/main`.
- **[R-18]** Bloqueo de cuenta por 15 minutos tras 5 intentos fallidos (`bloqueado_hasta`), expiración de sesión tras 30 minutos (`expiraEn`) en `SesionManager` y cambio obligatorio de contraseña en primer login (`cambio_password_requerido = 1`).

### Notas Técnicas y Deuda Técnica Planificada (v1.0.0 → v1.1.0)
- **Nota técnica de cobertura**: `com.foodcore.dao.VentaDAOImpl` está ubicada temporalmente en el paquete `dao` por compatibilidad con el módulo de ventas. Será movida a `dao.impl` en v1.1.0 (ticket #NNN). La cobertura de línea declarada de 97.80% corresponde a esta clase. `com.foodcore.dao.impl.VentaDAOImpl` es un adaptador delgado (7 líneas). `AuthServiceImpl` (8 líneas) delega en `AuthService`; la cobertura del delegado es 100% y la lógica de negocio reside en la clase principal. Refactor completo planificado para v1.1.0.
- **Runtime embebido en el .deb incluye JDK completo (179 MB instalados)** por ausencia de `jlink --strip-native-commands`. Optimización a JRE mínimo (~70 MB) planificada para v1.1.0. El tamaño actual (52 MB comprimido) es funcional pero subóptimo para distribución masiva.
