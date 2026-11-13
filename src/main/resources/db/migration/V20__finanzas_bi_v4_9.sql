-- ============================================================================
-- FOODCORE ERP v4.9.0 - Finanzas y Business Intelligence (BI)
-- Fase 1: Tabla de categorías de gasto operativo, semillas maestras y vínculo
-- directo con movimiento_caja para trazabilidad en Estado de Resultados (P&L).
-- ============================================================================

CREATE TABLE IF NOT EXISTS categoria_gasto (
    id_categoria_gasto INT AUTO_INCREMENT PRIMARY KEY,
    codigo VARCHAR(30) NOT NULL UNIQUE,
    nombre VARCHAR(80) NOT NULL UNIQUE,
    descripcion VARCHAR(200) NULL,
    activo TINYINT(1) NOT NULL DEFAULT 1,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO categoria_gasto (codigo, nombre, descripcion, activo) VALUES
('ALQUILER',      'Alquiler',                 'Arrendamiento de local comercial y áreas operativas', 1),
('LUZ',           'Pago de Luz',              'Consumo eléctrico de salón, cocina y refrigeración',  1),
('AGUA',          'Pago de Agua',             'Servicio de agua potable y saneamiento',              1),
('SUELDOS',       'Sueldos',                  'Pagos administrativos de personal, bonos y adelantos', 1),
('MARKETING',     'Marketing',                'Campañas publicitarias, redes y promociones',       1),
('MANTENIMIENTO', 'Mantenimiento',            'Gas, limpieza, fumigación y reparaciones menores',    1),
('OTROS',         'Otros Gastos Operativos',  'Movilidad, caja chica y egresos varios autorizados',  1)
ON DUPLICATE KEY UPDATE
    nombre = VALUES(nombre),
    descripcion = VALUES(descripcion),
    activo = VALUES(activo);

ALTER TABLE movimiento_caja
    ADD COLUMN id_categoria_gasto INT NULL,
    ADD CONSTRAINT fk_movcaja_cat_gasto FOREIGN KEY (id_categoria_gasto) REFERENCES categoria_gasto(id_categoria_gasto),
    ADD INDEX idx_movcaja_tipo_fecha (tipo_movimiento, fecha_hora);
