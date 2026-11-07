-- ============================================================================
-- FOODCORE ERP v4.2.0 - SUB-FASE A: CATÁLOGO DINÁMICO Y DISPONIBILIDAD
-- Migración V11: Disponibilidad Dinámica, Menú del Día y Fecha de Vigencia
-- ============================================================================

-- 1. Agregar columnas para control de menú del día y vigencia de disponibilidad
ALTER TABLE producto
    ADD COLUMN es_menu_del_dia TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN fecha_disponibilidad DATE NULL;

-- 2. Asegurar consistencia del estado de disponibilidad por defecto
ALTER TABLE producto
    MODIFY COLUMN disponible TINYINT(1) NOT NULL DEFAULT 1;

-- 3. Marcar platillos emblemáticos como parte del Menú del Día inicial
UPDATE producto
SET es_menu_del_dia = 1, fecha_disponibilidad = CURRENT_DATE
WHERE codigo IN ('HAM-01', 'CRIS-01', 'BEB-01') OR id_producto IN (1, 4, 5);

-- 4. Índice para optimizar consultas de catálogo activo y menú del día
CREATE INDEX idx_producto_disponible_menu ON producto (disponible, es_menu_del_dia, activo);
