-- ============================================================================
-- FOODCORE ERP v2.0 - SUB-FASE 4D: CRECIMIENTO COMERCIAL
-- Migración V10: Promociones Automáticas, Fidelización de Clientes,
-- Cupones de Cumpleaños y Trazabilidad Comercial
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Extensión de Clientes: Puntos Acumulados y Fecha de Nacimiento (§12)
-- ----------------------------------------------------------------------------
ALTER TABLE cliente
    ADD COLUMN puntos_acumulados INT NOT NULL DEFAULT 0,
    ADD COLUMN fecha_nacimiento DATE NULL;

-- Sincronizar puntos acumulados con los puntos de fidelidad existentes
UPDATE cliente SET puntos_acumulados = COALESCE(puntos_fidelidad, 0);

-- ----------------------------------------------------------------------------
-- 2. Tabla de Promociones y Descuentos Automáticos (§11)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS promocion (
    id_promocion INT AUTO_INCREMENT PRIMARY KEY,
    codigo VARCHAR(50) NOT NULL UNIQUE,
    nombre VARCHAR(100) NOT NULL,
    descripcion VARCHAR(255) NULL,
    tipo_promocion ENUM('PORCENTAJE', 'MONTO_FIJO', 'DOS_POR_UNO', 'TRES_POR_DOS') NOT NULL,
    valor_descuento DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    aplica_a ENUM('PRODUCTO', 'CATEGORIA', 'TOTAL_ORDEN') NOT NULL DEFAULT 'TOTAL_ORDEN',
    id_producto_aplica INT NULL,
    id_categoria_aplica INT NULL,
    monto_minimo DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    dias_semana VARCHAR(60) NOT NULL DEFAULT 'LUN,MAR,MIE,JUE,VIE,SAB,DOM',
    hora_inicio TIME NULL,
    hora_fin TIME NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_promo_producto FOREIGN KEY (id_producto_aplica) REFERENCES producto(id_producto),
    CONSTRAINT fk_promo_categoria FOREIGN KEY (id_categoria_aplica) REFERENCES categoria(id_categoria)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 3. Tabla de Movimientos de Fidelización (Puntos y Canjes) (§12)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fidelizacion_movimiento (
    id_movimiento INT AUTO_INCREMENT PRIMARY KEY,
    id_cliente INT NOT NULL,
    id_venta INT NULL,
    tipo ENUM('ACUMULACION', 'CANJE', 'AJUSTE') NOT NULL,
    puntos INT NOT NULL,
    motivo VARCHAR(255) NOT NULL,
    fecha_hora TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_fidel_cliente FOREIGN KEY (id_cliente) REFERENCES cliente(id_cliente),
    CONSTRAINT fk_fidel_venta FOREIGN KEY (id_venta) REFERENCES venta(id_venta)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 4. Tabla de Cupones de Cumpleaños (§12)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS cupon_cumpleanos (
    id_cupon INT AUTO_INCREMENT PRIMARY KEY,
    id_cliente INT NOT NULL,
    codigo_cupon VARCHAR(50) NOT NULL UNIQUE,
    porcentaje_descuento DECIMAL(5,2) NOT NULL DEFAULT 15.00,
    mes_validez INT NOT NULL,
    usado BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_generacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cupon_cliente FOREIGN KEY (id_cliente) REFERENCES cliente(id_cliente)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 5. Semillas de Demostración: Promociones Activas
-- ----------------------------------------------------------------------------
INSERT INTO promocion (codigo, nombre, descripcion, tipo_promocion, valor_descuento, aplica_a, id_producto_aplica, id_categoria_aplica, monto_minimo, dias_semana, hora_inicio, hora_fin, activo) VALUES
('PROMO-2X1-ALITAS', '2x1 Alitas BBQ Crujientes', 'Compra 2 porciones de Alitas y la segunda es gratis', 'DOS_POR_UNO', 0.00, 'PRODUCTO', 2, NULL, 0.00, 'LUN,MAR,MIE,JUE,VIE,SAB,DOM', '00:00:00', '23:59:59', 1),
('PROMO-20-BURGERS', '20% OFF en Hamburguesas Artesanales', 'Descuento especial del 20% en toda la categoría de hamburguesas', 'PORCENTAJE', 20.00, 'CATEGORIA', NULL, 1, 0.00, 'LUN,MAR,MIE,JUE,VIE,SAB,DOM', '00:00:00', '23:59:59', 1),
('PROMO-5-TOTAL', 'S/ 5.00 OFF en Compras Mayores a S/ 50', 'Descuento directo de S/ 5.00 para pedidos mayores a S/ 50.00', 'MONTO_FIJO', 5.00, 'TOTAL_ORDEN', NULL, NULL, 50.00, 'LUN,MAR,MIE,JUE,VIE,SAB,DOM', '00:00:00', '23:59:59', 1),
('PROMO-3X2-BEBIDAS', '3x2 en Bebidas Seleccionadas', 'Pide 3 gaseosas y paga solo 2', 'TRES_POR_DOS', 0.00, 'PRODUCTO', 3, NULL, 0.00, 'LUN,MAR,MIE,JUE,VIE,SAB,DOM', '12:00:00', '23:59:59', 1)
ON DUPLICATE KEY UPDATE nombre = VALUES(nombre);

-- ----------------------------------------------------------------------------
-- 6. Semillas de Fidelización y Clientes
-- ----------------------------------------------------------------------------
UPDATE cliente SET fecha_nacimiento = '1990-11-15', puntos_acumulados = 350, puntos_fidelidad = 350 WHERE id_cliente = 1;
UPDATE cliente SET fecha_nacimiento = '1985-05-20', puntos_acumulados = 180, puntos_fidelidad = 180 WHERE id_cliente = 2;
UPDATE cliente SET fecha_nacimiento = '1992-11-28', puntos_acumulados = 240, puntos_fidelidad = 240 WHERE id_cliente = 3;

-- Cupón de cumpleaños para cliente 1 (cumpleaños en noviembre, mes actual)
INSERT INTO cupon_cumpleanos (id_cliente, codigo_cupon, porcentaje_descuento, mes_validez, usado) VALUES
(1, 'BDAY-CLI1-NOV', 15.00, 11, 0)
ON DUPLICATE KEY UPDATE porcentaje_descuento = VALUES(porcentaje_descuento);

-- Movimientos de fidelización históricos
INSERT INTO fidelizacion_movimiento (id_cliente, id_venta, tipo, puntos, motivo) VALUES
(1, NULL, 'ACUMULACION', 350, 'Puntos acumulados por compras corporativas'),
(2, NULL, 'ACUMULACION', 180, 'Puntos por consumo en restaurante'),
(3, NULL, 'ACUMULACION', 240, 'Puntos acumulados consumo ejecutivo')
ON DUPLICATE KEY UPDATE puntos = VALUES(puntos);
