-- ============================================================================
-- FoodCore ERP v4.7.0 - RRHH Administrativo (Módulo A: Expediente y Documentos)
-- Ampliación administrativa de legajo de usuario y gestión de documentos PDF.
-- Sin cálculos legales (AFP/ONP/Renta 5ta).
-- ============================================================================

ALTER TABLE usuario
    ADD COLUMN tipo_documento VARCHAR(20) NOT NULL DEFAULT 'DNI',
    ADD COLUMN direccion VARCHAR(255) NULL,
    ADD COLUMN telefono VARCHAR(25) NULL,
    ADD COLUMN telefono_emergencia VARCHAR(25) NULL,
    ADD COLUMN contacto_emergencia VARCHAR(120) NULL,
    ADD COLUMN fecha_nacimiento DATE NULL,
    ADD COLUMN tipo_contrato VARCHAR(40) NOT NULL DEFAULT 'PLAZO_FIJO',
    ADD COLUMN fecha_ingreso DATE NULL,
    ADD COLUMN cargo VARCHAR(60) NULL;

CREATE TABLE IF NOT EXISTS documento_empleado (
    id_documento BIGINT NOT NULL AUTO_INCREMENT,
    id_usuario INT NOT NULL,
    tipo_documento VARCHAR(60) NOT NULL,
    nombre_archivo VARCHAR(255) NOT NULL,
    ruta_archivo VARCHAR(500) NOT NULL,
    fecha_emision DATE NULL,
    fecha_vencimiento DATE NULL,
    observaciones VARCHAR(500) NULL,
    subido_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_documento),
    KEY idx_doc_empleado_usuario (id_usuario),
    KEY idx_doc_empleado_vencimiento (fecha_vencimiento),
    CONSTRAINT fk_doc_empleado_usuario
        FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Enriquecimiento de datos administrativos semilla para pruebas y demo
UPDATE usuario
SET tipo_documento = 'DNI',
    direccion = 'Av. Principal 120, Miraflores',
    telefono = '999111001',
    telefono_emergencia = '999000111',
    contacto_emergencia = 'Elena Mendoza',
    fecha_nacimiento = '1990-04-12',
    tipo_contrato = 'INDETERMINADO',
    fecha_ingreso = DATE_SUB(CURRENT_DATE, INTERVAL 2 YEAR),
    cargo = 'Administrador General'
WHERE id_usuario = 1;

UPDATE usuario
SET tipo_documento = 'DNI',
    direccion = 'Jr. Los Pinos 345, Surco',
    telefono = '999111002',
    telefono_emergencia = '999000222',
    contacto_emergencia = 'Luis Paredes',
    fecha_nacimiento = '1995-08-21',
    tipo_contrato = 'PLAZO_FIJO',
    fecha_ingreso = DATE_SUB(CURRENT_DATE, INTERVAL 10 MONTH),
    cargo = 'Cajero Principal'
WHERE id_usuario = 2;

UPDATE usuario
SET tipo_documento = 'DNI',
    direccion = 'Calle Las Flores 89, Lince',
    telefono = '999111003',
    telefono_emergencia = '999000333',
    contacto_emergencia = 'María Salazar',
    fecha_nacimiento = '1997-02-14',
    tipo_contrato = 'PLAZO_FIJO',
    fecha_ingreso = DATE_SUB(CURRENT_DATE, INTERVAL 6 MONTH),
    cargo = 'Mozo de Salón'
WHERE id_usuario = 3;

UPDATE usuario
SET tipo_documento = 'DNI',
    direccion = 'Av. Brasil 1540, Jesús María',
    telefono = '999111004',
    telefono_emergencia = '999000444',
    contacto_emergencia = 'Pedro Quispe',
    fecha_nacimiento = '1993-11-05',
    tipo_contrato = 'PLAZO_FIJO',
    fecha_ingreso = DATE_SUB(CURRENT_DATE, INTERVAL 15 MONTH),
    cargo = 'Cocinero Jefe'
WHERE id_usuario = 4;

-- Documentos semilla (incluye un documento por vencer dentro de 30 días para alerta en Dashboard)
INSERT INTO documento_empleado (id_usuario, tipo_documento, nombre_archivo, ruta_archivo, fecha_emision, fecha_vencimiento, observaciones)
SELECT u.id_usuario,
       'CONTRATO_TRABAJO',
       'contrato_admin_v47.pdf',
       'uploads/expedientes/1/contrato_admin_v47.pdf',
       DATE_SUB(CURRENT_DATE, INTERVAL 11 MONTH),
       DATE_ADD(CURRENT_DATE, INTERVAL 365 DAY),
       'Contrato vigente firmado'
FROM usuario u
WHERE u.id_usuario = 1
  AND NOT EXISTS (
      SELECT 1 FROM documento_empleado d WHERE d.id_usuario = u.id_usuario AND d.nombre_archivo = 'contrato_admin_v47.pdf'
  );

INSERT INTO documento_empleado (id_usuario, tipo_documento, nombre_archivo, ruta_archivo, fecha_emision, fecha_vencimiento, observaciones)
SELECT u.id_usuario,
       'CERTIFICADO_MANIPULACION',
       'certificado_manipulacion_cajero.pdf',
       'uploads/expedientes/2/certificado_manipulacion_cajero.pdf',
       DATE_SUB(CURRENT_DATE, INTERVAL 11 MONTH),
       DATE_ADD(CURRENT_DATE, INTERVAL 18 DAY),
       'Renovar certificado sanitario este mes'
FROM usuario u
WHERE u.id_usuario = 2
  AND NOT EXISTS (
      SELECT 1 FROM documento_empleado d WHERE d.id_usuario = u.id_usuario AND d.nombre_archivo = 'certificado_manipulacion_cajero.pdf'
  );
