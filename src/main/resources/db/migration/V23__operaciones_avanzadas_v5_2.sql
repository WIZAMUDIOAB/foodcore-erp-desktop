-- ============================================================================
-- FoodCore ERP v5.2.0 - Sub-Fase B: Operaciones Avanzadas
-- 1) Múltiples Impresoras Simultáneas por Categoría (configuracion_impresora de V6)
-- 2) Devoluciones Parciales por ítem (detalle_venta.estado y auditoría de devolución)
-- 3) KDS con Priorización Manual (detalle_pedido.prioridad)
-- 4) Cierre Contable Mensual (cierre_contable)
-- ============================================================================

-- 1. Enrutamiento único de impresora Windows por categoría (extiende tabla configuracion_impresora de V6)
ALTER TABLE configuracion_impresora
    MODIFY COLUMN nombre_impresora_windows VARCHAR(180) NOT NULL,
    ADD COLUMN actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    ADD UNIQUE KEY uk_ci_categoria (id_categoria);

-- 2. Estado de devolución por ítem de venta para devoluciones parciales
ALTER TABLE detalle_venta
    ADD COLUMN estado VARCHAR(20) NOT NULL DEFAULT 'COMPLETADO',
    ADD COLUMN fecha_devolucion DATETIME NULL,
    ADD COLUMN motivo_devolucion VARCHAR(200) NULL,
    ADD INDEX idx_detven_venta_estado (id_venta, estado);

-- 3. Priorización manual de tarjetas KDS en cocina (orden descendente)
ALTER TABLE detalle_pedido
    ADD COLUMN prioridad INT NOT NULL DEFAULT 0,
    ADD INDEX idx_detped_kds_prioridad (estado_cocina, prioridad, id_detalle_pedido);

-- 4. Cierre Contable Mensual (bloqueo de ventas, devoluciones y egresos en mes cerrado)
CREATE TABLE IF NOT EXISTS cierre_contable (
    id_cierre INT AUTO_INCREMENT PRIMARY KEY,
    anio INT NOT NULL,
    mes INT NOT NULL,
    fecha_cierre DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_usuario INT NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'CERRADO',
    observaciones VARCHAR(255) NULL,
    UNIQUE KEY uk_cierre_contable_periodo (anio, mes),
    CONSTRAINT fk_cierre_contable_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
