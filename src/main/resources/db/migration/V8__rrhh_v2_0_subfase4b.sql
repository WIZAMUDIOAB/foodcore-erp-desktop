-- ============================================================================
-- V8__rrhh_v2_0_subfase4b.sql — Módulo Recursos Humanos (FoodCore v2.0)
-- SPEC Sub-Fase 4B: Control de Asistencia, Kiosco de Marcaje (DNI/PIN),
-- Planilla Mensual, Programación Semanal de Turnos y Alertas de Cobertura.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Ampliación de la tabla `usuario` para RRHH (§6.1, §6.3)
-- ----------------------------------------------------------------------------
ALTER TABLE usuario
    ADD COLUMN dni VARCHAR(15) NULL,
    ADD COLUMN pin VARCHAR(10) NULL,
    ADD COLUMN sueldo_base DECIMAL(12,2) NOT NULL DEFAULT 1200.00,
    ADD COLUMN horario_laboral VARCHAR(50) NOT NULL DEFAULT '08:00-16:00',
    ADD COLUMN hora_entrada_programada TIME NOT NULL DEFAULT '08:00:00',
    ADD COLUMN hora_salida_programada TIME NOT NULL DEFAULT '16:00:00';

-- ----------------------------------------------------------------------------
-- 2. Evolución de la Tabla `asistencia` (§6.1, §6.2)
-- ----------------------------------------------------------------------------
ALTER TABLE asistencia
    MODIFY COLUMN id_empleado INT NULL,
    MODIFY COLUMN hora_ingreso TIME NULL,
    MODIFY COLUMN hora_salida TIMESTAMP NULL,
    ADD COLUMN id_usuario INT NULL,
    ADD COLUMN hora_entrada TIMESTAMP NULL,
    ADD COLUMN horas_trabajadas DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN horas_extra DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN tardanza_minutos INT NOT NULL DEFAULT 0,
    ADD COLUMN tipo_marcaje VARCHAR(20) NOT NULL DEFAULT 'DNI', -- 'DNI' o 'PIN'
    ADD COLUMN observaciones VARCHAR(255) NULL,
    ADD COLUMN fecha_registro TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- ----------------------------------------------------------------------------
-- 3. Tabla de Programación de Turnos y Horarios Semanales (§7.1)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS turno_programado (
    id_turno_prog INT AUTO_INCREMENT PRIMARY KEY,
    id_usuario INT NOT NULL,
    dia_semana VARCHAR(15) NOT NULL, -- 'LUNES', 'MARTES', 'MIERCOLES', 'JUEVES', 'VIERNES', 'SABADO', 'DOMINGO'
    fecha_especifica DATE NULL,
    turno VARCHAR(20) NOT NULL DEFAULT 'MANANA', -- 'MANANA', 'TARDE', 'NOCHE', 'PERSONALIZADO'
    hora_inicio TIME NOT NULL DEFAULT '08:00:00',
    hora_fin TIME NOT NULL DEFAULT '16:00:00',
    publicado TINYINT(1) NOT NULL DEFAULT 1,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_turnoprog_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario),
    INDEX idx_turno_dia (dia_semana, turno)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 4. Semillas y Datos Operativos de Demostración
-- ----------------------------------------------------------------------------
-- Actualizar datos de personal existente
UPDATE usuario SET dni = '45892147', pin = '1001', sueldo_base = 3200.00, horario_laboral = '08:00-17:00', hora_entrada_programada = '08:00:00', hora_salida_programada = '17:00:00' WHERE username = 'admin';
UPDATE usuario SET dni = '71258963', pin = '2001', sueldo_base = 1400.00, horario_laboral = '08:00-16:00', hora_entrada_programada = '08:00:00', hora_salida_programada = '16:00:00' WHERE username = 'cajero1';
UPDATE usuario SET dni = '73654129', pin = '3001', sueldo_base = 1350.00, horario_laboral = '12:00-20:00', hora_entrada_programada = '12:00:00', hora_salida_programada = '20:00:00' WHERE username = 'mozo1';
UPDATE usuario SET dni = '74125896', pin = '4001', sueldo_base = 1600.00, horario_laboral = '10:00-18:00', hora_entrada_programada = '10:00:00', hora_salida_programada = '18:00:00' WHERE username = 'cocina1';

-- Asistencias de demostración (Hoy y días anteriores)
INSERT INTO asistencia (id_usuario, id_empleado, fecha, hora_entrada, hora_ingreso, hora_salida, horas_trabajadas, horas_extra, tardanza_minutos, estado, tipo_marcaje, observaciones) VALUES
(1, 1, CURRENT_DATE, CONCAT(CURRENT_DATE, ' 07:55:00'), '07:55:00', NULL, 0.00, 0.00, 0, 'PRESENTE', 'DNI', 'Ingreso puntual administrador'),
(2, 2, CURRENT_DATE, CONCAT(CURRENT_DATE, ' 08:14:00'), '08:14:00', NULL, 0.00, 0.00, 14, 'TARDE', 'DNI', 'Tardanza de 14 min por tráfico'),
(4, 4, CURRENT_DATE, CONCAT(CURRENT_DATE, ' 09:58:00'), '09:58:00', NULL, 0.00, 0.00, 0, 'PRESENTE', 'PIN', 'Marcaje por PIN en cocina'),
(3, 3, DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY), CONCAT(DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY), ' 11:58:00'), '11:58:00', CONCAT(DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY), ' 21:00:00'), 9.03, 1.03, 0, 'FINALIZADO', 'DNI', 'Turno extendido con horas extra'),
(2, 2, DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY), CONCAT(DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY), ' 08:02:00'), '08:02:00', CONCAT(DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY), ' 16:05:00'), 8.05, 0.05, 0, 'FINALIZADO', 'DNI', 'Jornada regular completa'),
(1, 1, DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY), CONCAT(DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY), ' 07:50:00'), '07:50:00', CONCAT(DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY), ' 17:10:00'), 9.33, 1.33, 0, 'FINALIZADO', 'DNI', 'Supervisión de inventario fin de semana');

-- Turnos programados de la semana (Lunes a Domingo)
INSERT INTO turno_programado (id_usuario, dia_semana, turno, hora_inicio, hora_fin, publicado) VALUES
(1, 'LUNES', 'MANANA', '08:00:00', '16:00:00', 1),
(2, 'LUNES', 'MANANA', '08:00:00', '16:00:00', 1),
(4, 'LUNES', 'MANANA', '08:00:00', '16:00:00', 1),
(3, 'LUNES', 'TARDE', '14:00:00', '22:00:00', 1),

(1, 'MARTES', 'MANANA', '08:00:00', '16:00:00', 1),
(2, 'MARTES', 'MANANA', '08:00:00', '16:00:00', 1),
(4, 'MARTES', 'MANANA', '08:00:00', '16:00:00', 1),
(3, 'MARTES', 'TARDE', '14:00:00', '22:00:00', 1),

(2, 'MIERCOLES', 'MANANA', '08:00:00', '16:00:00', 1),
(4, 'MIERCOLES', 'MANANA', '08:00:00', '16:00:00', 1),
(3, 'MIERCOLES', 'TARDE', '14:00:00', '22:00:00', 1),

(1, 'JUEVES', 'MANANA', '08:00:00', '16:00:00', 1),
(2, 'JUEVES', 'MANANA', '08:00:00', '16:00:00', 1),
(4, 'JUEVES', 'MANANA', '08:00:00', '16:00:00', 1),
(3, 'JUEVES', 'TARDE', '14:00:00', '22:00:00', 1),

(1, 'VIERNES', 'MANANA', '08:00:00', '16:00:00', 1),
(2, 'VIERNES', 'MANANA', '08:00:00', '16:00:00', 1),
(4, 'VIERNES', 'TARDE', '14:00:00', '22:00:00', 1),
(3, 'VIERNES', 'NOCHE', '18:00:00', '02:00:00', 1),

(2, 'SABADO', 'MANANA', '08:00:00', '16:00:00', 1),
(4, 'SABADO', 'TARDE', '14:00:00', '22:00:00', 1),
(3, 'SABADO', 'NOCHE', '18:00:00', '02:00:00', 1),

(4, 'DOMINGO', 'MANANA', '09:00:00', '17:00:00', 1),
(3, 'DOMINGO', 'TARDE', '12:00:00', '20:00:00', 1);
