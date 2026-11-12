-- ============================================================================
-- FoodCore ERP v4.7.0 - RRHH Administrativo (Módulo C: Pagos y Movimientos)
-- Registro administrativo de Bonos, Comisiones, Adelantos y Descuentos.
-- Planilla consolidada interna: Sueldo Base + Bonos + Comisiones - Adelantos - Descuentos.
-- Sin cálculos legales (AFP/ONP/Renta 5ta).
-- ============================================================================

CREATE TABLE IF NOT EXISTS movimiento_pago (
    id_movimiento BIGINT NOT NULL AUTO_INCREMENT,
    id_usuario INT NOT NULL,
    periodo_mes VARCHAR(7) NOT NULL,
    tipo_movimiento VARCHAR(20) NOT NULL,
    concepto VARCHAR(160) NOT NULL,
    monto DECIMAL(12,2) NOT NULL,
    fecha_movimiento DATE NOT NULL,
    registrado_por INT NULL,
    observaciones VARCHAR(255) NULL,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_movimiento),
    KEY idx_mov_pago_periodo_usuario (periodo_mes, id_usuario),
    KEY idx_mov_pago_tipo (tipo_movimiento),
    CONSTRAINT fk_mov_pago_usuario
        FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Movimientos de demostración del período actual
INSERT INTO movimiento_pago (id_usuario, periodo_mes, tipo_movimiento, concepto, monto, fecha_movimiento, registrado_por, observaciones)
SELECT 2, DATE_FORMAT(CURRENT_DATE, '%Y-%m'), 'BONO', 'Bono por puntualidad y arqueo exacto', 150.00, CURRENT_DATE, 1, 'Cierre mensual sin descuadres'
WHERE EXISTS (SELECT 1 FROM usuario WHERE id_usuario = 2)
  AND NOT EXISTS (SELECT 1 FROM movimiento_pago WHERE id_usuario = 2 AND concepto = 'Bono por puntualidad y arqueo exacto');

INSERT INTO movimiento_pago (id_usuario, periodo_mes, tipo_movimiento, concepto, monto, fecha_movimiento, registrado_por, observaciones)
SELECT 2, DATE_FORMAT(CURRENT_DATE, '%Y-%m'), 'ADELANTO', 'Adelanto de quincena autorizado', 300.00, CURRENT_DATE, 1, 'Entrega en efectivo caja chica'
WHERE EXISTS (SELECT 1 FROM usuario WHERE id_usuario = 2)
  AND NOT EXISTS (SELECT 1 FROM movimiento_pago WHERE id_usuario = 2 AND concepto = 'Adelanto de quincena autorizado');

INSERT INTO movimiento_pago (id_usuario, periodo_mes, tipo_movimiento, concepto, monto, fecha_movimiento, registrado_por, observaciones)
SELECT 3, DATE_FORMAT(CURRENT_DATE, '%Y-%m'), 'COMISION', 'Comisión por meta de postres y bebidas', 220.00, CURRENT_DATE, 1, 'Incentivo salón mes actual'
WHERE EXISTS (SELECT 1 FROM usuario WHERE id_usuario = 3)
  AND NOT EXISTS (SELECT 1 FROM movimiento_pago WHERE id_usuario = 3 AND concepto = 'Comisión por meta de postres y bebidas');

INSERT INTO movimiento_pago (id_usuario, periodo_mes, tipo_movimiento, concepto, monto, fecha_movimiento, registrado_por, observaciones)
SELECT 4, DATE_FORMAT(CURRENT_DATE, '%Y-%m'), 'DESCUENTO', 'Descuento administrativo por rotura de menaje', 45.00, CURRENT_DATE, 1, 'Acta interna de cocina'
WHERE EXISTS (SELECT 1 FROM usuario WHERE id_usuario = 4)
  AND NOT EXISTS (SELECT 1 FROM movimiento_pago WHERE id_usuario = 4 AND concepto = 'Descuento administrativo por rotura de menaje');
