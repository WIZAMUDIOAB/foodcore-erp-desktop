-- ============================================================================
-- FoodCore ERP v5.5.0 - Sub-Fase E: Auditoría, Onboarding y Datos Demo
-- 1) Columna mesa.capacidad para Wizard de Configuración Inicial (E2)
-- ============================================================================

DELIMITER $$

CREATE PROCEDURE sp_aplicar_migracion_v24()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'mesa'
          AND column_name = 'capacidad'
    ) THEN
        ALTER TABLE mesa ADD COLUMN capacidad INT NOT NULL DEFAULT 4;
    END IF;
END $$

CALL sp_aplicar_migracion_v24() $$

DROP PROCEDURE IF EXISTS sp_aplicar_migracion_v24 $$

DELIMITER ;
