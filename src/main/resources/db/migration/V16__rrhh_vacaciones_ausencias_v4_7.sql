-- ============================================================================
-- FoodCore ERP v4.7.0 - RRHH Administrativo (Módulo B: Vacaciones y Permisos)
-- Ampliación de solicitud_ausencia y control administrativo de saldo de vacaciones.
-- Sin cálculos legales (AFP/ONP/Renta 5ta).
-- ============================================================================

ALTER TABLE solicitud_ausencia
    ADD COLUMN dias_solicitados INT NOT NULL DEFAULT 1 AFTER fecha_fin,
    ADD COLUMN aprobado_por INT NULL AFTER observacion_gerente,
    ADD COLUMN fecha_respuesta TIMESTAMP NULL AFTER aprobado_por;

UPDATE solicitud_ausencia
SET dias_solicitados = GREATEST(1, DATEDIFF(fecha_fin, fecha_inicio) + 1);

CREATE TABLE IF NOT EXISTS vacaciones (
    id_vacacion BIGINT NOT NULL AUTO_INCREMENT,
    id_usuario INT NOT NULL,
    periodo_anio INT NOT NULL,
    dias_asignados INT NOT NULL DEFAULT 30,
    dias_gozados INT NOT NULL DEFAULT 0,
    fecha_corte DATE NOT NULL,
    observaciones VARCHAR(255) NULL,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id_vacacion),
    UNIQUE KEY uk_vacaciones_usuario_anio (id_usuario, periodo_anio),
    CONSTRAINT fk_vacaciones_usuario
        FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed inicial de saldo vacacional por colaborador activo (regla administrativa interna:
-- 2.5 días acumulados por mes laborado, mínimo referencial 15d y tope anual 30d)
INSERT INTO vacaciones (id_usuario, periodo_anio, dias_asignados, dias_gozados, fecha_corte, observaciones)
SELECT u.id_usuario,
       YEAR(CURRENT_DATE),
       LEAST(30, GREATEST(15, FLOOR(TIMESTAMPDIFF(MONTH, COALESCE(u.fecha_ingreso, DATE_SUB(CURRENT_DATE, INTERVAL 6 MONTH)), CURRENT_DATE) * 2.5))),
       COALESCE((
           SELECT SUM(s.dias_solicitados)
           FROM solicitud_ausencia s
           WHERE s.id_usuario = u.id_usuario
             AND s.tipo = 'VACACIONES'
             AND s.estado = 'APROBADA'
             AND YEAR(s.fecha_inicio) = YEAR(CURRENT_DATE)
       ), 0),
       CURRENT_DATE,
       'Saldo inicial calculado automáticamente por antigüedad'
FROM usuario u
WHERE u.activo = 1
  AND NOT EXISTS (
      SELECT 1 FROM vacaciones v
      WHERE v.id_usuario = u.id_usuario AND v.periodo_anio = YEAR(CURRENT_DATE)
  );
