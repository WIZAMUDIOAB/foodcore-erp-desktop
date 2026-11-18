-- ============================================================================
-- FOODCORE ERP v4.8.0 - Operaciones Avanzadas (Módulo 1 y Módulo 2)
-- Tablas para Variantes de Producto (Tamaños), Modificadores / Extras Globales,
-- asociación por producto y trazabilidad completa en pedidos, ventas y KDS.
-- ============================================================================

-- 1. Variantes de producto (Tamaños: Mediana, Familiar, etc.)
CREATE TABLE IF NOT EXISTS producto_variante (
    id_variante INT AUTO_INCREMENT PRIMARY KEY,
    id_producto INT NOT NULL,
    nombre_variante VARCHAR(80) NOT NULL,
    precio_fijo DECIMAL(14,2) NULL,
    precio_adicional DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    orden INT NOT NULL DEFAULT 0,
    activo TINYINT(1) NOT NULL DEFAULT 1,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pv_producto FOREIGN KEY (id_producto) REFERENCES producto(id_producto) ON DELETE CASCADE,
    KEY idx_pv_producto_activo (id_producto, activo, orden)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2. Modificadores / Extras globales (Extra Queso, Tocino, Sin Cebolla, etc.)
CREATE TABLE IF NOT EXISTS producto_modificador (
    id_modificador INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    precio DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    activo TINYINT(1) NOT NULL DEFAULT 1,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_pm_nombre (nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 3. Tabla puente producto <-> modificadores permitidos
CREATE TABLE IF NOT EXISTS producto_modificador_asociado (
    id_producto INT NOT NULL,
    id_modificador INT NOT NULL,
    orden INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id_producto, id_modificador),
    CONSTRAINT fk_pma_producto FOREIGN KEY (id_producto) REFERENCES producto(id_producto) ON DELETE CASCADE,
    CONSTRAINT fk_pma_modificador FOREIGN KEY (id_modificador) REFERENCES producto_modificador(id_modificador) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 4. Trazabilidad en detalle_pedido (comanda POS / Mozo / KDS) y detalle_venta
ALTER TABLE detalle_pedido
    ADD COLUMN id_variante INT NULL,
    ADD COLUMN nombre_variante VARCHAR(80) NULL,
    ADD COLUMN modificadores_texto VARCHAR(500) NULL,
    ADD CONSTRAINT fk_dp_variante FOREIGN KEY (id_variante) REFERENCES producto_variante(id_variante) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS detalle_pedido_modificador (
    id_detalle_modificador INT AUTO_INCREMENT PRIMARY KEY,
    id_detalle_pedido INT NOT NULL,
    id_modificador INT NOT NULL,
    nombre_modificador VARCHAR(100) NOT NULL,
    precio_aplicado DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    CONSTRAINT fk_dpm_detped FOREIGN KEY (id_detalle_pedido) REFERENCES detalle_pedido(id_detalle_pedido) ON DELETE CASCADE,
    CONSTRAINT fk_dpm_modificador FOREIGN KEY (id_modificador) REFERENCES producto_modificador(id_modificador) ON DELETE RESTRICT,
    KEY idx_dpm_detped (id_detalle_pedido)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE detalle_venta
    ADD COLUMN id_variante INT NULL,
    ADD COLUMN nombre_variante VARCHAR(80) NULL,
    ADD COLUMN modificadores_texto VARCHAR(500) NULL;

-- 5. Catálogo global inicial de Modificadores / Extras
INSERT INTO producto_modificador (nombre, precio, activo) VALUES
('Extra Queso',          3.00, 1),
('Sin Cebolla',          0.00, 1),
('Tocino',               4.00, 1),
('Huevo Frito',          2.50, 1),
('Salsa Barbacoa Extra', 1.50, 1)
ON DUPLICATE KEY UPDATE precio = VALUES(precio), activo = 1;

-- 6. Platillos demostrativos con Variantes (Tamaños) y Extras sin alterar HAM-01/ALI-01/BEB-01 base
INSERT INTO producto (id_categoria, codigo, nombre, descripcion, precio_venta, costo_estimado, disponible, activo)
SELECT c.id_categoria, 'PIZ-ART', 'Pizza Artesanal', 'Masa madre a la piedra, salsa pomodoro y mozzarella', 25.00, 10.50, 1, 1
FROM categoria c
WHERE c.activo = 1
  AND NOT EXISTS (SELECT 1 FROM producto WHERE codigo = 'PIZ-ART')
ORDER BY c.id_categoria ASC
LIMIT 1;

INSERT INTO producto (id_categoria, codigo, nombre, descripcion, precio_venta, costo_estimado, disponible, activo)
SELECT c.id_categoria, 'HAM-ESP', 'Hamburguesa Gourmet Custom', 'Carne angus 180g personalizable con adicionales de la casa', 22.00, 9.00, 1, 1
FROM categoria c
WHERE c.activo = 1
  AND NOT EXISTS (SELECT 1 FROM producto WHERE codigo = 'HAM-ESP')
ORDER BY c.id_categoria ASC
LIMIT 1;

INSERT INTO producto_variante (id_producto, nombre_variante, precio_fijo, precio_adicional, orden, activo)
SELECT p.id_producto, 'Mediana', 25.00, 0.00, 1, 1
FROM producto p
WHERE p.codigo = 'PIZ-ART'
  AND NOT EXISTS (
      SELECT 1 FROM producto_variante pv WHERE pv.id_producto = p.id_producto AND pv.nombre_variante = 'Mediana'
  );

INSERT INTO producto_variante (id_producto, nombre_variante, precio_fijo, precio_adicional, orden, activo)
SELECT p.id_producto, 'Familiar', 40.00, 0.00, 2, 1
FROM producto p
WHERE p.codigo = 'PIZ-ART'
  AND NOT EXISTS (
      SELECT 1 FROM producto_variante pv WHERE pv.id_producto = p.id_producto AND pv.nombre_variante = 'Familiar'
  );

INSERT INTO producto_modificador_asociado (id_producto, id_modificador, orden)
SELECT p.id_producto, m.id_modificador,
       CASE m.nombre WHEN 'Extra Queso' THEN 1 WHEN 'Tocino' THEN 2 WHEN 'Sin Cebolla' THEN 3 ELSE 4 END
FROM producto p
JOIN producto_modificador m ON m.nombre IN ('Extra Queso', 'Tocino', 'Sin Cebolla')
WHERE p.codigo IN ('PIZ-ART', 'HAM-ESP')
  AND NOT EXISTS (
      SELECT 1 FROM producto_modificador_asociado pma
      WHERE pma.id_producto = p.id_producto AND pma.id_modificador = m.id_modificador
  );
