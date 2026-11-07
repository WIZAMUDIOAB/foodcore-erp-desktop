-- ============================================================================
-- V9__salon_reservas_mermas_v2_0_subfase4c.sql — Operaciones de Salón (FoodCore v2.0)
-- SPEC Sub-Fase 4C: Gestión de Mesas (Transferir / Unir), Reservas con WhatsApp,
-- Bloqueo Preventivo en Salón, Pre-Cuenta y Análisis de Mermas/Desperdicio.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Ampliación de la tabla `mesa` y tabla `mesa_union` (§8.1, §8.2)
-- ----------------------------------------------------------------------------
ALTER TABLE mesa
    ADD COLUMN id_pedido_actual INT NULL,
    ADD COLUMN id_mesa_principal BIGINT NULL;

CREATE TABLE IF NOT EXISTS mesa_union (
    id_union INT AUTO_INCREMENT PRIMARY KEY,
    id_mesa_principal BIGINT NOT NULL,
    id_mesa_secundaria BIGINT NOT NULL,
    id_pedido INT NOT NULL,
    activo TINYINT(1) NOT NULL DEFAULT 1,
    fecha_union TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_union_principal FOREIGN KEY (id_mesa_principal) REFERENCES mesa(id_mesa),
    CONSTRAINT fk_union_secundaria FOREIGN KEY (id_mesa_secundaria) REFERENCES mesa(id_mesa),
    CONSTRAINT fk_union_pedido FOREIGN KEY (id_pedido) REFERENCES pedido(id_pedido),
    INDEX idx_union_pedido (id_pedido, activo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 2. Tabla de Reservas de Mesas con WhatsApp (§9.1, §9.2)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reserva_mesa (
    id_reserva INT AUTO_INCREMENT PRIMARY KEY,
    id_mesa BIGINT NOT NULL,
    cliente_nombre VARCHAR(120) NOT NULL,
    cliente_telefono VARCHAR(25) NOT NULL,
    fecha DATE NOT NULL,
    hora TIME NOT NULL,
    personas INT NOT NULL DEFAULT 2,
    estado VARCHAR(20) NOT NULL DEFAULT 'CONFIRMADA', -- 'PENDIENTE', 'CONFIRMADA', 'SENTADA', 'CANCELADA'
    notas VARCHAR(255) NULL,
    id_usuario_registro INT NULL,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reserva_mesa FOREIGN KEY (id_mesa) REFERENCES mesa(id_mesa),
    CONSTRAINT fk_reserva_usuario FOREIGN KEY (id_usuario_registro) REFERENCES usuario(id_usuario),
    INDEX idx_reserva_fecha (fecha, hora, estado)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 3. Tabla de Control y Análisis de Mermas (§10.1, §10.2)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS merma (
    id_merma INT AUTO_INCREMENT PRIMARY KEY,
    id_insumo BIGINT NOT NULL,
    id_usuario INT NOT NULL,
    cantidad DECIMAL(14,4) NOT NULL,
    motivo VARCHAR(50) NOT NULL, -- 'Vencido', 'Quemado', 'Roto', 'Error de cocina', 'Sobrante', 'Otro'
    costo_unitario DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    costo_total DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    observacion VARCHAR(255) NULL,
    fecha_hora TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_merma_insumo FOREIGN KEY (id_insumo) REFERENCES insumo(id_insumo),
    CONSTRAINT fk_merma_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario),
    INDEX idx_merma_fecha (fecha_hora),
    INDEX idx_merma_motivo (motivo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 4. Semillas Demostrativas de Salón, Reservas y Mermas
-- ----------------------------------------------------------------------------
-- Asegurar existencia de las 12 mesas del salón
INSERT INTO mesa (id_mesa, numero, estado, version) VALUES
(1, '01', 'LIBRE', 0),
(2, '02', 'LIBRE', 0),
(3, '03', 'LIBRE', 0),
(4, '04', 'LIBRE', 0),
(5, '05', 'LIBRE', 0),
(6, '06', 'LIBRE', 0),
(7, '07', 'LIBRE', 0),
(8, '08', 'LIBRE', 0),
(9, '09', 'LIBRE', 0),
(10, '10', 'LIBRE', 0),
(11, '11', 'LIBRE', 0),
(12, '12', 'LIBRE', 0)
ON DUPLICATE KEY UPDATE numero = VALUES(numero);

-- Asegurar existencia de insumos para análisis de mermas
INSERT INTO insumo (id_insumo, codigo, nombre, unidad_medida, stock_actual_disponible, stock_minimo, costo_unitario, costo_unitario_actual, activo) VALUES
(1, 'INS-001', 'Lomo Fino de Res', 'KG', 45.0000, 10.0000, 28.5000, 28.5000, 1),
(2, 'INS-002', 'Pechuga de Pollo',  'KG', 50.0000, 10.0000, 12.0000, 12.0000, 1),
(3, 'INS-003', 'Cerveza Cusqueña',  'UND', 60.0000, 12.0000, 8.5000, 8.5000, 1),
(4, 'INS-004', 'Queso Mozzarella',  'KG', 25.0000, 5.0000, 15.0000, 15.0000, 1),
(5, 'INS-005', 'Papa Amarilla',     'KG', 80.0000, 20.0000, 3.2000, 3.2000, 1)
ON DUPLICATE KEY UPDATE nombre = VALUES(nombre);

-- Semillas de Reservas (Hoy y próximos días)
INSERT INTO reserva_mesa (id_mesa, cliente_nombre, cliente_telefono, fecha, hora, personas, estado, notas, id_usuario_registro) VALUES
(3, 'Mariana Valdivia', '987654321', CURRENT_DATE, '20:00:00', 4, 'CONFIRMADA', 'Mesa cerca a la ventana, aniversario', 1),
(5, 'Ing. Carlos Zambrano', '998877665', CURRENT_DATE, '13:30:00', 6, 'CONFIRMADA', 'Almuerzo corporativo Tech Solutions', 1),
(7, 'Familia Rodríguez Gómez', '944332211', CURRENT_DATE, '21:00:00', 8, 'PENDIENTE', 'Requieren 2 sillas para niños', 1),
(2, 'Dra. Elena Bustamante', '911223344', DATE_ADD(CURRENT_DATE, INTERVAL 1 DAY), '19:30:00', 2, 'CONFIRMADA', 'Cena romántica', 1),
(8, 'Grupo BCP Finanzas', '955667788', DATE_ADD(CURRENT_DATE, INTERVAL 2 DAY), '20:30:00', 12, 'CONFIRMADA', 'Celebración de cierre de trimestre', 1);

-- Semillas de Mermas del mes para Análisis y Gráficos
INSERT INTO merma (id_insumo, id_usuario, cantidad, motivo, costo_unitario, costo_total, observacion, fecha_hora) VALUES
(1, 1, 3.5000, 'Quemado', 28.50, 99.75, 'Exceso de fuego en sellado de carne lomo', NOW() - INTERVAL 1 DAY),
(1, 1, 2.0000, 'Error de cocina', 28.50, 57.00, 'Pedido duplicado por error en comanda', NOW() - INTERVAL 2 DAY),
(1, 1, 1.8000, 'Sobrante', 28.50, 51.30, 'Recortes no aprovechables para porcionado', NOW() - INTERVAL 4 DAY),
(1, 1, 2.5000, 'Quemado', 28.50, 71.25, 'Cocción pasada en turno noche', NOW() - INTERVAL 6 DAY),
(2, 1, 5.0000, 'Vencido', 12.00, 60.00, 'Pechuga de pollo con fecha límite de frío vencida', NOW() - INTERVAL 3 DAY),
(2, 1, 3.0000, 'Error de cocina', 12.00, 36.00, 'Plato equivocado en barra caliente', NOW() - INTERVAL 5 DAY),
(3, 1, 12.0000, 'Roto', 8.50, 102.00, 'Rotura de botellas de cerveza artesanal en descarga', NOW() - INTERVAL 7 DAY),
(4, 1, 4.0000, 'Vencido', 15.00, 60.00, 'Queso mozzarella con pérdida de vacío', NOW() - INTERVAL 8 DAY),
(5, 1, 15.0000, 'Sobrante', 3.20, 48.00, 'Papas peladas oxidadas por mala rotación', NOW() - INTERVAL 9 DAY),
(5, 1, 10.0000, 'Vencido', 3.20, 32.00, 'Papa amarilla brotada en almacén', NOW() - INTERVAL 12 DAY);
