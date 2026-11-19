-- ==============================================================================
-- FoodCore ERP v5.7.1 — Inicializacion Segura e Idempotente de Base de Datos
-- Ejecutar como usuario root de MySQL 8.0+
--
-- INSTRUCCIONES OBLIGATORIAS ANTES DE EJECUTAR:
--   1. Reemplace los marcadores <CHANGE_ME_MIGRATOR_PASSWORD> y
--      <CHANGE_ME_APP_PASSWORD> con contrasenas aleatorias fuertes (>= 20 chars)
--      generadas por un gestor de secretos (p. ej. `openssl rand -base64 24`).
--   2. Exporte esas mismas contrasenas en las variables de entorno del servidor:
--        FOODCORE_MIGRATOR_PASS  (solo para migraciones Flyway / backups)
--        FOODCORE_DB_PASS        (aplicacion en tiempo de ejecucion)
--   3. Habilite TLS en el servidor MySQL (`require_secure_transport=ON` en
--      config/my.ini.example). Todas las cuentas exigen `REQUIRE SSL`.
--   4. Principio de minimo privilegio: `foodcore_app` NO tiene `UPDATE` ni
--      `DELETE` sobre las tablas inmutables append-only `auditoria` y
--      `kardex_movimiento` (solo `SELECT, INSERT`).
-- ==============================================================================

CREATE DATABASE IF NOT EXISTS foodcore
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- ------------------------------------------------------------------------------
-- 1. Usuario de migraciones / backups (DDL: Flyway y mysqldump)
--    Restringido a loopback (localhost / 127.0.0.1) con TLS obligatorio.
-- ------------------------------------------------------------------------------
CREATE USER IF NOT EXISTS 'foodcore_migrator'@'localhost'
  IDENTIFIED BY '<CHANGE_ME_MIGRATOR_PASSWORD>'
  REQUIRE SSL;
ALTER USER 'foodcore_migrator'@'localhost'
  IDENTIFIED BY '<CHANGE_ME_MIGRATOR_PASSWORD>'
  REQUIRE SSL;

CREATE USER IF NOT EXISTS 'foodcore_migrator'@'127.0.0.1'
  IDENTIFIED BY '<CHANGE_ME_MIGRATOR_PASSWORD>'
  REQUIRE SSL;
ALTER USER 'foodcore_migrator'@'127.0.0.1'
  IDENTIFIED BY '<CHANGE_ME_MIGRATOR_PASSWORD>'
  REQUIRE SSL;

GRANT ALL PRIVILEGES ON foodcore.* TO 'foodcore_migrator'@'localhost';
GRANT ALL PRIVILEGES ON foodcore.* TO 'foodcore_migrator'@'127.0.0.1';

-- ------------------------------------------------------------------------------
-- 2. Usuario de aplicacion (DML de minimo privilegio)
--    NOTA: En actualizaciones desde versiones previas, ejecute primero:
--      REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'foodcore_app'@'localhost';
--      REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'foodcore_app'@'127.0.0.1';
-- ------------------------------------------------------------------------------
CREATE USER IF NOT EXISTS 'foodcore_app'@'localhost'
  IDENTIFIED BY '<CHANGE_ME_APP_PASSWORD>'
  REQUIRE SSL;
ALTER USER 'foodcore_app'@'localhost'
  IDENTIFIED BY '<CHANGE_ME_APP_PASSWORD>'
  REQUIRE SSL;

CREATE USER IF NOT EXISTS 'foodcore_app'@'127.0.0.1'
  IDENTIFIED BY '<CHANGE_ME_APP_PASSWORD>'
  REQUIRE SSL;
ALTER USER 'foodcore_app'@'127.0.0.1'
  IDENTIFIED BY '<CHANGE_ME_APP_PASSWORD>'
  REQUIRE SSL;

-- Permiso base de lectura e insercion en todo el esquema (incluye tablas
-- inmutables append-only: `auditoria` y `kardex_movimiento`, que NUNCA reciben
-- UPDATE ni DELETE para `foodcore_app`).
GRANT SELECT, INSERT ON foodcore.* TO 'foodcore_app'@'localhost';
GRANT SELECT, INSERT ON foodcore.* TO 'foodcore_app'@'127.0.0.1';

-- Permisos UPDATE y DELETE otorgados explicitamente SOLO sobre tablas
-- operacionales mutables (excluyendo `auditoria`, `kardex_movimiento` y
-- `flyway_schema_history`).
GRANT UPDATE, DELETE ON foodcore.rol TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.permiso TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.rol_permiso TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.empleado TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.usuario TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.categoria TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.producto TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.item_vendible TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.cliente TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.insumo TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.receta TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.receta_insumo TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.movimiento_inventario TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.caja TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.turno_caja TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.movimiento_caja TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.mesa TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.pedido TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.detalle_pedido TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.comanda TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.venta TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.detalle_venta TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.comprobante_pago TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.outbox_evento TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.terminal TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.asistencia TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.configuracion_restaurante TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.configuracion_impresora TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.proveedor TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.cuenta_por_pagar TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.pago_proveedor TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.cuenta_por_cobrar TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.cobro_cliente TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.historial_precios TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.compra TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.turno_programado TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.mesa_union TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.reserva_mesa TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.merma TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.promocion TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.fidelizacion_movimiento TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.cupon_cumpleanos TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.solicitud_ausencia TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.documento_empleado TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.vacaciones TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.movimiento_pago TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.producto_variante TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.producto_modificador TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.producto_modificador_asociado TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.detalle_pedido_modificador TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.combo TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.combo_detalle TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.categoria_gasto TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.historial_puntos TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.token_blacklist TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';
GRANT UPDATE, DELETE ON foodcore.cierre_contable TO 'foodcore_app'@'localhost', 'foodcore_app'@'127.0.0.1';

-- ------------------------------------------------------------------------------
-- OPCIONAL (topologias LAN multi-terminal):
-- Nunca use host '%'. Restrinja estrictamente a la subred cableada POS/KDS
-- (ej. '192.168.10.%') con REQUIRE SSL y los mismos permisos explicitos:
--   CREATE USER IF NOT EXISTS 'foodcore_app'@'192.168.10.%'
--     IDENTIFIED BY '<CHANGE_ME_APP_PASSWORD>' REQUIRE SSL;
--   GRANT SELECT, INSERT ON foodcore.* TO 'foodcore_app'@'192.168.10.%';
--   (y replicar los GRANT UPDATE, DELETE por tabla excluyendo auditoria y kardex_movimiento)
-- ------------------------------------------------------------------------------

FLUSH PRIVILEGES;
