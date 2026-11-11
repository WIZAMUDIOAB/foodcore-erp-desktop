-- ============================================================================
-- V13__rrhh_flujos_y_pos_v4_4.sql — Flujos KDS, POS Multicanal, Ausencias y Alertas RRHH
-- FoodCore ERP v4.4.0
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Tabla de Gestión de Ausencias y Solicitudes de Personal (§4 RRHH)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS solicitud_ausencia (
    id_solicitud INT AUTO_INCREMENT PRIMARY KEY,
    id_usuario INT NOT NULL,
    tipo VARCHAR(30) NOT NULL, -- 'VACACIONES', 'PERMISO_MEDICO', 'ASUNTO_PERSONAL', 'LICENCIA'
    fecha_inicio DATE NOT NULL,
    fecha_fin DATE NOT NULL,
    motivo VARCHAR(255) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE', -- 'PENDIENTE', 'APROBADA', 'RECHAZADA'
    observacion_gerente VARCHAR(255) NULL,
    fecha_solicitud TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sol_ausencia_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario),
    INDEX idx_sol_usuario_estado (id_usuario, estado)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 2. Fechas de Vencimiento de Contrato y Carné de Sanidad (Alertas 30 días)
-- ----------------------------------------------------------------------------
ALTER TABLE empleado
    ADD COLUMN fecha_vencimiento_contrato DATE NULL,
    ADD COLUMN fecha_vencimiento_carnet_sanidad DATE NULL;

ALTER TABLE usuario
    ADD COLUMN fecha_vencimiento_contrato DATE NULL,
    ADD COLUMN fecha_vencimiento_carnet_sanidad DATE NULL;

-- Sembrar alertas próximas (<30 días) para validación de Dashboard
UPDATE empleado SET
    fecha_vencimiento_contrato = DATE_ADD(CURRENT_DATE, INTERVAL 14 DAY),
    fecha_vencimiento_carnet_sanidad = DATE_ADD(CURRENT_DATE, INTERVAL 22 DAY)
WHERE id_empleado = 1;

UPDATE empleado SET
    fecha_vencimiento_contrato = DATE_ADD(CURRENT_DATE, INTERVAL 28 DAY),
    fecha_vencimiento_carnet_sanidad = DATE_ADD(CURRENT_DATE, INTERVAL 9 DAY)
WHERE id_empleado = 2;

UPDATE usuario SET
    fecha_vencimiento_contrato = DATE_ADD(CURRENT_DATE, INTERVAL 14 DAY),
    fecha_vencimiento_carnet_sanidad = DATE_ADD(CURRENT_DATE, INTERVAL 22 DAY)
WHERE id_usuario = 1;

UPDATE usuario SET
    fecha_vencimiento_contrato = DATE_ADD(CURRENT_DATE, INTERVAL 28 DAY),
    fecha_vencimiento_carnet_sanidad = DATE_ADD(CURRENT_DATE, INTERVAL 9 DAY)
WHERE id_usuario = 2;

-- ----------------------------------------------------------------------------
-- 3. Seguridad y Permisos RBAC: Anulación y Descuento solo ADMIN/SUPERVISOR
-- ----------------------------------------------------------------------------
DELETE FROM rol_permiso WHERE codigo_rol = 'CAJERO' AND codigo_permiso = 'VENTA_ANULAR';

-- Asegurar rol SUPERVISOR con permisos de anulación y descuento
INSERT IGNORE INTO rol_permiso (codigo_rol, codigo_permiso) VALUES
('SUPERVISOR', 'VENTA_ANULAR'),
('SUPERVISOR', 'VENTA_DESCUENTO');

-- ----------------------------------------------------------------------------
-- 4. Semillas de Solicitudes de Demostración
-- ----------------------------------------------------------------------------
INSERT INTO solicitud_ausencia (id_usuario, tipo, fecha_inicio, fecha_fin, motivo, estado) VALUES
(2, 'VACACIONES', DATE_ADD(CURRENT_DATE, INTERVAL 7 DAY), DATE_ADD(CURRENT_DATE, INTERVAL 21 DAY), 'Vacaciones anuales programadas de ley', 'PENDIENTE'),
(3, 'PERMISO_MEDICO', DATE_ADD(CURRENT_DATE, INTERVAL 2 DAY), DATE_ADD(CURRENT_DATE, INTERVAL 3 DAY), 'Consulta médica odontológica programada', 'PENDIENTE');
