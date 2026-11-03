# CHANGELOG — FoodCore ERP Desktop

Todos los cambios notables en este proyecto se documentan en este archivo siguiendo `SPEC-FOODCORE-v1.0` y `REMEDIATION-PLAN-FOODCORE-v1.0`.

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
