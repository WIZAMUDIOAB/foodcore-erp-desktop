-- ============================================================================
-- FoodCore ERP v5.7.1 — Hardening de Seguridad, Rendimiento e Integridad BD
-- 1) Limpieza definitiva de PINs en texto plano (conservando BCrypt pin_hash).
-- 2) Índices compuestos idempotentes sobre tablas transaccionales críticas.
-- 3) Normalización de collation utf8mb4_unicode_ci -> utf8mb4_0900_ai_ci.
-- ============================================================================

-- 1. Eliminar PINs en texto plano (la autenticación usa exclusivamente pin_hash BCrypt)
UPDATE usuario
SET pin = NULL
WHERE pin IS NOT NULL;

-- 2. Índices compuestos idempotentes (evita errores de clave duplicada en MySQL 8)
DELIMITER $$

CREATE PROCEDURE sp_crear_indice_si_no_existe_v25(
    IN p_tabla VARCHAR(64),
    IN p_indice VARCHAR(64),
    IN p_columnas VARCHAR(200)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = p_tabla
          AND index_name = p_indice
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_tabla, '` ADD INDEX `', p_indice, '` (', p_columnas, ')');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

CREATE PROCEDURE sp_aplicar_indices_v25()
BEGIN
    -- venta
    CALL sp_crear_indice_si_no_existe_v25('venta', 'idx_venta_estado_fecha', 'estado, fecha_hora');
    CALL sp_crear_indice_si_no_existe_v25('venta', 'idx_venta_cliente_fecha', 'id_cliente, fecha_hora');
    CALL sp_crear_indice_si_no_existe_v25('venta', 'idx_venta_caja_estado', 'id_caja, estado');

    -- pedido
    CALL sp_crear_indice_si_no_existe_v25('pedido', 'idx_pedido_estado_fecha', 'estado, fecha_hora');
    CALL sp_crear_indice_si_no_existe_v25('pedido', 'idx_pedido_mesa_estado', 'id_mesa, estado');
    CALL sp_crear_indice_si_no_existe_v25('pedido', 'idx_pedido_nummesa_estado', 'numero_mesa, estado');

    -- detalle_venta (idx_detven_venta_estado ya existe en V23)
    CALL sp_crear_indice_si_no_existe_v25('detalle_venta', 'idx_detven_producto_estado', 'id_producto, estado');

    -- asistencia
    CALL sp_crear_indice_si_no_existe_v25('asistencia', 'idx_asistencia_usuario_fecha', 'id_usuario, fecha, hora_salida');
    CALL sp_crear_indice_si_no_existe_v25('asistencia', 'idx_asistencia_fecha_salida', 'fecha, hora_salida');

    -- auditoria
    CALL sp_crear_indice_si_no_existe_v25('auditoria', 'idx_auditoria_fecha_accion', 'fecha, accion');
    CALL sp_crear_indice_si_no_existe_v25('auditoria', 'idx_auditoria_entidad_fecha', 'entidad_afectada, fecha');

    -- cuenta_por_cobrar
    CALL sp_crear_indice_si_no_existe_v25('cuenta_por_cobrar', 'idx_cxc_cliente_estado', 'id_cliente, estado');
    CALL sp_crear_indice_si_no_existe_v25('cuenta_por_cobrar', 'idx_cxc_estado_vencimiento', 'estado, fecha_vencimiento');

    -- cuenta_por_pagar
    CALL sp_crear_indice_si_no_existe_v25('cuenta_por_pagar', 'idx_cxp_proveedor_estado', 'id_proveedor, estado');
    CALL sp_crear_indice_si_no_existe_v25('cuenta_por_pagar', 'idx_cxp_estado_vencimiento', 'estado, fecha_vencimiento');

    -- kardex_movimiento (idx_kardex_insumo_fecha ya existe en V1)
    CALL sp_crear_indice_si_no_existe_v25('kardex_movimiento', 'idx_kardex_ref_tipo', 'referencia_tipo, referencia_id, tipo_movimiento');
END $$

CALL sp_aplicar_indices_v25() $$

DROP PROCEDURE IF EXISTS sp_aplicar_indices_v25 $$
DROP PROCEDURE IF EXISTS sp_crear_indice_si_no_existe_v25 $$

DELIMITER ;

-- 3. Normalizar collation de tablas utf8mb4_unicode_ci hacia utf8mb4_0900_ai_ci (FKs únicamente enteras)
ALTER TABLE documento_empleado CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE vacaciones CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE movimiento_pago CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE token_blacklist CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
