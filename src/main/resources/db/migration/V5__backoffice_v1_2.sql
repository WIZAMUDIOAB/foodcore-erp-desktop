-- ============================================================================
-- V5__backoffice_v1_2.sql — Evolución de Esquema FOODCORE ERP v1.2 (SPEC-FOODCORE-v1.2 §1.1, §2, §3, §7)
-- ============================================================================

-- 1. Normalizar unidades previas y convertir insumo.unidad_medida a ENUM (§1.1)
UPDATE insumo
SET unidad_medida = 'UNIDAD'
WHERE unidad_medida IN ('UND', 'UNID', 'UNIDADES')
   OR unidad_medida NOT IN ('UNIDAD', 'KG', 'GR', 'ML', 'LT');

ALTER TABLE insumo
    MODIFY COLUMN unidad_medida ENUM('UNIDAD', 'KG', 'GR', 'ML', 'LT', 'UND') NOT NULL DEFAULT 'UNIDAD';

-- 2. Soporte de fotografías de platillos en menú y POS (§2.2, §7)
ALTER TABLE producto
    ADD COLUMN imagen_url VARCHAR(255) NULL;

-- 3. Soporte de descuentos auditados en ventas POS (§3.2)
ALTER TABLE venta
    ADD COLUMN descuento DECIMAL(14,2) NOT NULL DEFAULT 0.00;

-- 4. Permiso RBAC para aplicar descuentos en POS (§3.2: solo Admin/Supervisor)
INSERT INTO permiso (codigo_permiso, descripcion) VALUES
('VENTA_DESCUENTO', 'Autoriza aplicar descuentos porcentuales o de monto fijo en ventas POS')
ON DUPLICATE KEY UPDATE descripcion = VALUES(descripcion);

INSERT IGNORE INTO rol_permiso (codigo_rol, codigo_permiso) VALUES
('ADMIN', 'VENTA_DESCUENTO');

-- 5. Tabla de configuración general del restaurante e impresora térmica (§2.4)
CREATE TABLE IF NOT EXISTS configuracion_restaurante (
    id_config INT PRIMARY KEY DEFAULT 1,
    nombre_restaurante VARCHAR(120) NOT NULL DEFAULT 'FoodCore Fast Food S.A.C.',
    direccion VARCHAR(200) NOT NULL DEFAULT 'Av. Javier Prado Este 2450, San Borja, Lima',
    telefono VARCHAR(40) NOT NULL DEFAULT '(01) 442-8900',
    ruc VARCHAR(20) NULL DEFAULT '20609874512',
    nombre_impresora VARCHAR(100) NOT NULL DEFAULT 'EPSON-TM-T20III',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO configuracion_restaurante (id_config, nombre_restaurante, direccion, telefono, ruc, nombre_impresora)
VALUES (1, 'FoodCore Fast Food S.A.C.', 'Av. Javier Prado Este 2450, San Borja, Lima', '(01) 442-8900', '20609874512', 'EPSON-TM-T20III')
ON DUPLICATE KEY UPDATE nombre_restaurante = VALUES(nombre_restaurante);
