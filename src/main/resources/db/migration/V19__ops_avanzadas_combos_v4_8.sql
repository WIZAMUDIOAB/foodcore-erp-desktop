-- ============================================================================
-- FOODCORE ERP v4.8.0 - Operaciones Avanzadas (Módulo 3: Combos y Empaquetado)
-- Tablas para combos/paquetes, detalle de productos componentes, enlace con
-- catálogo unificado y trazabilidad en pedidos, ventas, Kardex y KDS.
-- ============================================================================

-- 1. Marca en catálogo unificado para identificar combos en POS, Mozo y Backoffice
ALTER TABLE producto
    ADD COLUMN es_combo TINYINT(1) NOT NULL DEFAULT 0;

-- 2. Cabecera de Combos / Paquetes
CREATE TABLE IF NOT EXISTS combo (
    id_combo INT AUTO_INCREMENT PRIMARY KEY,
    id_producto INT NULL UNIQUE,
    nombre VARCHAR(120) NOT NULL,
    precio_combo DECIMAL(14,2) NOT NULL,
    activo TINYINT(1) NOT NULL DEFAULT 1,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_combo_producto FOREIGN KEY (id_producto) REFERENCES producto(id_producto) ON DELETE CASCADE,
    KEY idx_combo_activo (activo, nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 3. Detalle de componentes incluidos en cada combo
CREATE TABLE IF NOT EXISTS combo_detalle (
    id_combo_detalle INT AUTO_INCREMENT PRIMARY KEY,
    id_combo INT NOT NULL,
    id_producto INT NOT NULL,
    cantidad INT NOT NULL DEFAULT 1,
    CONSTRAINT fk_cd_combo FOREIGN KEY (id_combo) REFERENCES combo(id_combo) ON DELETE CASCADE,
    CONSTRAINT fk_cd_producto FOREIGN KEY (id_producto) REFERENCES producto(id_producto) ON DELETE RESTRICT,
    UNIQUE KEY uq_combo_producto (id_combo, id_producto)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 4. Trazabilidad del combo y desglose de preparación en detalle_pedido y detalle_venta
ALTER TABLE detalle_pedido
    ADD COLUMN id_combo INT NULL,
    ADD COLUMN combo_componentes_texto VARCHAR(600) NULL;

ALTER TABLE detalle_venta
    ADD COLUMN id_combo INT NULL,
    ADD COLUMN combo_componentes_texto VARCHAR(600) NULL;

-- 5. Categoría y combo demostrativo inicial (2 Hamburguesa Clasica + 2 Gaseosa Personal)
INSERT INTO categoria (nombre, descripcion, activo)
SELECT 'Combos', 'Paquetes y combos promocionales con empaquetado', 1
WHERE NOT EXISTS (
    SELECT 1 FROM categoria WHERE LOWER(TRIM(nombre)) = 'combos'
);

INSERT INTO producto (id_categoria, codigo, nombre, descripcion, precio_venta, costo_estimado, disponible, activo, es_combo)
SELECT c.id_categoria, 'CMB-DUO', 'Combo Duo Burger', '2x Hamburguesa Clasica + 2x Gaseosa Personal', 42.00, 19.00, 1, 1, 1
FROM categoria c
WHERE LOWER(TRIM(c.nombre)) = 'combos'
  AND NOT EXISTS (SELECT 1 FROM producto WHERE codigo = 'CMB-DUO')
LIMIT 1;

INSERT INTO combo (id_producto, nombre, precio_combo, activo)
SELECT p.id_producto, 'Combo Duo Burger', 42.00, 1
FROM producto p
WHERE p.codigo = 'CMB-DUO'
  AND NOT EXISTS (SELECT 1 FROM combo cb WHERE cb.id_producto = p.id_producto);

INSERT INTO combo_detalle (id_combo, id_producto, cantidad)
SELECT cb.id_combo, comp.id_producto, 2
FROM combo cb
JOIN producto pcombo ON cb.id_producto = pcombo.id_producto AND pcombo.codigo = 'CMB-DUO'
JOIN producto comp ON comp.codigo = 'HAM-01'
WHERE NOT EXISTS (
    SELECT 1 FROM combo_detalle cd WHERE cd.id_combo = cb.id_combo AND cd.id_producto = comp.id_producto
);

INSERT INTO combo_detalle (id_combo, id_producto, cantidad)
SELECT cb.id_combo, comp.id_producto, 2
FROM combo cb
JOIN producto pcombo ON cb.id_producto = pcombo.id_producto AND pcombo.codigo = 'CMB-DUO'
JOIN producto comp ON comp.codigo = 'BEB-01'
WHERE NOT EXISTS (
    SELECT 1 FROM combo_detalle cd WHERE cd.id_combo = cb.id_combo AND cd.id_producto = comp.id_producto
);
