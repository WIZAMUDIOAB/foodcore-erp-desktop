-- SPEC-FOODCORE-v5.0.0-CRM: CRM Minorista y Fidelización
-- Unifica cliente (CORPORATIVO / MINORISTA), añade historial_puntos y descuento_puntos sin alterar IGV.

ALTER TABLE cliente
    ADD COLUMN tipo_cliente VARCHAR(20) NOT NULL DEFAULT 'CORPORATIVO';

UPDATE cliente
SET tipo_cliente = 'MINORISTA'
WHERE UPPER(COALESCE(tipo_documento, 'DNI')) = 'DNI';

UPDATE cliente
SET puntos_acumulados = GREATEST(COALESCE(puntos_acumulados, 0), COALESCE(puntos_fidelidad, 0)),
    puntos_fidelidad = GREATEST(COALESCE(puntos_acumulados, 0), COALESCE(puntos_fidelidad, 0));

CREATE INDEX idx_cliente_tipo_doc ON cliente (tipo_cliente, numero_documento);
CREATE INDEX idx_cliente_telefono ON cliente (telefono);

ALTER TABLE venta
    ADD COLUMN descuento_puntos DECIMAL(14,2) NOT NULL DEFAULT 0.00;

CREATE TABLE IF NOT EXISTS historial_puntos (
    id_historial BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_cliente INT NOT NULL,
    id_venta INT NULL,
    puntos_ganados INT NOT NULL DEFAULT 0,
    tipo_movimiento VARCHAR(20) NOT NULL DEFAULT 'ACUMULACION',
    descripcion VARCHAR(255) NULL,
    fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_hist_pts_cli_fecha (id_cliente, fecha),
    INDEX idx_hist_pts_venta (id_venta),
    CONSTRAINT fk_hist_pts_cli FOREIGN KEY (id_cliente) REFERENCES cliente(id_cliente) ON DELETE CASCADE,
    CONSTRAINT fk_hist_pts_venta FOREIGN KEY (id_venta) REFERENCES venta(id_venta) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Seed idempotente de clientes minoristas para demo POS y Backoffice (SPEC v5.0.0)
INSERT INTO cliente (
    tipo_documento, numero_documento, tipo_cliente, nombre_razon_social, razon_social,
    contacto, telefono, email, limite_credito, dias_credito,
    puntos_fidelidad, puntos_acumulados, fecha_nacimiento, estado, activo
)
SELECT
    'DNI', '71234567', 'MINORISTA', 'Lucía Fernández Torres', 'Lucía Fernández Torres',
    'Lucía Fernández', '987112233', 'lucia.fernandez@gmail.com', 0.00, 0,
    150, 150, DATE(CONCAT('1995-', LPAD(MONTH(CURRENT_DATE()), 2, '0'), '-15')), 'ACTIVO', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cliente WHERE numero_documento = '71234567');

INSERT INTO cliente (
    tipo_documento, numero_documento, tipo_cliente, nombre_razon_social, razon_social,
    contacto, telefono, email, limite_credito, dias_credito,
    puntos_fidelidad, puntos_acumulados, fecha_nacimiento, estado, activo
)
SELECT
    'DNI', '74568901', 'MINORISTA', 'Carlos Ramírez Quispe', 'Carlos Ramírez Quispe',
    'Carlos Ramírez', '991234567', 'carlos.ramirez@hotmail.com', 0.00, 0,
    220, 220, DATE_SUB(DATE(CONCAT('1992-', LPAD(MONTH(CURRENT_DATE()), 2, '0'), '-20')), INTERVAL 2 MONTH), 'ACTIVO', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cliente WHERE numero_documento = '74568901');

INSERT INTO historial_puntos (id_cliente, id_venta, puntos_ganados, tipo_movimiento, descripcion)
SELECT c.id_cliente, NULL, c.puntos_acumulados, 'ACUMULACION', 'Saldo inicial programa de fidelización CRM v5.0.0'
FROM cliente c
WHERE c.numero_documento IN ('71234567', '74568901')
  AND NOT EXISTS (SELECT 1 FROM historial_puntos hp WHERE hp.id_cliente = c.id_cliente);
