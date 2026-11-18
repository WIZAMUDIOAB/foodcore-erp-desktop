-- ============================================================================
-- FoodCore ERP v5.5.0 - Sub-Fase E: Auditoría, Onboarding y Datos Demo
-- 1) Columna mesa.capacidad para Wizard de Configuración Inicial (E2)
-- 2) Columna es_demo TINYINT(1) DEFAULT 0 en tablas afectadas si no existe (E3)
-- ============================================================================

DELIMITER $$

CREATE PROCEDURE sp_agregar_columna_si_no_existe_v24(
    IN p_tabla VARCHAR(64),
    IN p_columna VARCHAR(64),
    IN p_definicion VARCHAR(120)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = p_tabla
          AND column_name = p_columna
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_tabla, '` ADD COLUMN `', p_columna, '` ', p_definicion);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

CREATE PROCEDURE sp_aplicar_migracion_v24()
BEGIN
    CALL sp_agregar_columna_si_no_existe_v24('mesa', 'capacidad', 'INT NOT NULL DEFAULT 4');
    CALL sp_agregar_columna_si_no_existe_v24('categoria', 'es_demo', 'TINYINT(1) NOT NULL DEFAULT 0');
    CALL sp_agregar_columna_si_no_existe_v24('caja', 'es_demo', 'TINYINT(1) NOT NULL DEFAULT 0');
    CALL sp_agregar_columna_si_no_existe_v24('cliente', 'es_demo', 'TINYINT(1) NOT NULL DEFAULT 0');
    CALL sp_agregar_columna_si_no_existe_v24('producto', 'es_demo', 'TINYINT(1) NOT NULL DEFAULT 0');
    CALL sp_agregar_columna_si_no_existe_v24('venta', 'es_demo', 'TINYINT(1) NOT NULL DEFAULT 0');
    CALL sp_agregar_columna_si_no_existe_v24('detalle_venta', 'es_demo', 'TINYINT(1) NOT NULL DEFAULT 0');
END $$

CALL sp_aplicar_migracion_v24() $$

DROP PROCEDURE IF EXISTS sp_aplicar_migracion_v24 $$
DROP PROCEDURE IF EXISTS sp_agregar_columna_si_no_existe_v24 $$

DELIMITER ;
