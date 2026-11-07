-- ============================================================================
-- V7__finanzas_v2_0_subfase4a.sql — Módulo Finanzas y Contabilidad (FoodCore v2.0)
-- SPEC Sub-Fase 4A: Reportes PLE SUNAT 14.1/8.1, Proveedores/CxP, Clientes/CxC,
-- Historial Precios y Reportes Avanzados con Exportación PDF/Excel.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Proveedores y Cuentas por Pagar (§2)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS proveedor (
    id_proveedor INT AUTO_INCREMENT PRIMARY KEY,
    ruc VARCHAR(20) NOT NULL UNIQUE,
    razon_social VARCHAR(150) NOT NULL,
    contacto VARCHAR(100) NULL,
    telefono VARCHAR(30) NULL,
    email VARCHAR(100) NULL,
    dias_credito INT NOT NULL DEFAULT 0,
    estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
    activo TINYINT(1) NOT NULL DEFAULT 1,
    fecha_registro TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS cuenta_por_pagar (
    id_cuenta_pagar INT AUTO_INCREMENT PRIMARY KEY,
    id_proveedor INT NOT NULL,
    numero_documento VARCHAR(50) NOT NULL,
    fecha_emision DATE NOT NULL,
    fecha_vencimiento DATE NOT NULL,
    monto_total DECIMAL(12,2) NOT NULL,
    monto_pagado DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    observaciones VARCHAR(255) NULL,
    fecha_registro TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cxp_prov FOREIGN KEY (id_proveedor) REFERENCES proveedor(id_proveedor)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS pago_proveedor (
    id_pago_proveedor INT AUTO_INCREMENT PRIMARY KEY,
    id_cuenta_pagar INT NOT NULL,
    id_caja INT NULL,
    id_usuario INT NOT NULL,
    monto DECIMAL(12,2) NOT NULL,
    metodo_pago VARCHAR(30) NOT NULL DEFAULT 'EFECTIVO',
    fecha_pago TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    nota VARCHAR(200) NULL,
    CONSTRAINT fk_pp_cxp FOREIGN KEY (id_cuenta_pagar) REFERENCES cuenta_por_pagar(id_cuenta_pagar),
    CONSTRAINT fk_pp_usr FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 2. Clientes Corporativos y Cuentas por Cobrar (§3)
-- ----------------------------------------------------------------------------
ALTER TABLE cliente
    MODIFY COLUMN nombre_razon_social VARCHAR(150) NULL,
    ADD COLUMN razon_social VARCHAR(150) NULL,
    ADD COLUMN contacto VARCHAR(100) NULL,
    ADD COLUMN limite_credito DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN dias_credito INT NOT NULL DEFAULT 30,
    ADD COLUMN puntos_fidelidad INT NOT NULL DEFAULT 0,
    ADD COLUMN estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
    ADD COLUMN activo TINYINT(1) NOT NULL DEFAULT 1,
    ADD COLUMN fecha_registro TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE cliente SET razon_social = nombre_razon_social WHERE razon_social IS NULL;

CREATE TABLE IF NOT EXISTS cuenta_por_cobrar (
    id_cuenta_cobrar INT AUTO_INCREMENT PRIMARY KEY,
    id_cliente INT NOT NULL,
    id_venta INT NULL,
    numero_comprobante VARCHAR(50) NOT NULL,
    fecha_emision DATE NOT NULL,
    fecha_vencimiento DATE NOT NULL,
    monto_total DECIMAL(12,2) NOT NULL,
    monto_cobrado DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    observaciones VARCHAR(255) NULL,
    fecha_registro TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cxc_cli FOREIGN KEY (id_cliente) REFERENCES cliente(id_cliente),
    CONSTRAINT fk_cxc_vta FOREIGN KEY (id_venta) REFERENCES venta(id_venta)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS cobro_cliente (
    id_cobro_cliente INT AUTO_INCREMENT PRIMARY KEY,
    id_cuenta_cobrar INT NOT NULL,
    id_caja INT NULL,
    id_usuario INT NOT NULL,
    monto DECIMAL(12,2) NOT NULL,
    metodo_pago VARCHAR(30) NOT NULL DEFAULT 'EFECTIVO',
    fecha_cobro TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    nota VARCHAR(200) NULL,
    CONSTRAINT fk_cc_cxc FOREIGN KEY (id_cuenta_cobrar) REFERENCES cuenta_por_cobrar(id_cuenta_cobrar),
    CONSTRAINT fk_cc_usr FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 3. Historial de Precios y Auditoría de Variación (§4)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS historial_precios (
    id_historial INT AUTO_INCREMENT PRIMARY KEY,
    tipo_entidad VARCHAR(20) NOT NULL, -- 'PRODUCTO' o 'INSUMO'
    id_entidad INT NOT NULL,
    nombre_entidad VARCHAR(150) NOT NULL,
    precio_anterior DECIMAL(12,4) NOT NULL,
    precio_nuevo DECIMAL(12,4) NOT NULL,
    id_usuario INT NOT NULL,
    nombre_usuario VARCHAR(100) NOT NULL,
    motivo VARCHAR(255) NOT NULL,
    fecha_cambio TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_hp_usr FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 4. Compras para Registro PLE SUNAT 8.1 (§1.1)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS compra (
    id_compra INT AUTO_INCREMENT PRIMARY KEY,
    id_proveedor INT NOT NULL,
    serie VARCHAR(10) NOT NULL,
    numero VARCHAR(20) NOT NULL,
    fecha_emision DATE NOT NULL,
    base_imponible DECIMAL(12,2) NOT NULL,
    igv DECIMAL(12,2) NOT NULL,
    total DECIMAL(12,2) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'REGISTRADA',
    id_usuario INT NOT NULL,
    fecha_registro TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cmp_prov FOREIGN KEY (id_proveedor) REFERENCES proveedor(id_proveedor)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 5. Semillas de Demostración Operacional
-- ----------------------------------------------------------------------------
INSERT INTO proveedor (id_proveedor, ruc, razon_social, contacto, telefono, email, dias_credito, estado) VALUES
(1, '20501234567', 'Avícola San Fernando S.A.', 'Carlos Meneses', '981234567', 'ventas@sanfernando.pe', 15, 'ACTIVO'),
(2, '20609876543', 'Cortes & Carnes del Sur E.I.R.L.', 'Marcos Prado', '972345678', 'pedidos@carnesdelsur.com', 30, 'ACTIVO'),
(3, '20487654321', 'Lácteos y Derivados Andinos S.A.C.', 'Lucía Valencia', '993456789', 'lvalencia@lacteosandinos.pe', 15, 'ACTIVO'),
(4, '20123456789', 'Envases & Descartables Perú S.A.', 'Jorge Ríos', '964567890', 'jrios@envasesperu.com', 7, 'ACTIVO')
ON DUPLICATE KEY UPDATE razon_social = VALUES(razon_social);

INSERT INTO cuenta_por_pagar (id_cuenta_pagar, id_proveedor, numero_documento, fecha_emision, fecha_vencimiento, monto_total, monto_pagado, estado, observaciones) VALUES
(1, 1, 'F001-0012450', DATE_SUB(CURRENT_DATE, INTERVAL 20 DAY), DATE_SUB(CURRENT_DATE, INTERVAL 5 DAY), 2450.00, 1000.00, 'VENCIDA', 'Lote de pechuga de pollo y alitas'),
(2, 2, 'F002-0008920', DATE_SUB(CURRENT_DATE, INTERVAL 10 DAY), DATE_ADD(CURRENT_DATE, INTERVAL 20 DAY), 3800.00, 0.00, 'PENDIENTE', 'Cortes de lomo fino y asado de tira'),
(3, 3, 'F001-0004312', DATE_SUB(CURRENT_DATE, INTERVAL 5 DAY), DATE_ADD(CURRENT_DATE, INTERVAL 10 DAY), 1150.00, 1150.00, 'PAGADA', 'Queso cheddar, mozzarella y mantequilla'),
(4, 4, 'B003-0001890', DATE_SUB(CURRENT_DATE, INTERVAL 2 DAY), DATE_ADD(CURRENT_DATE, INTERVAL 5 DAY), 680.00, 200.00, 'PENDIENTE', 'Cajas kraft para delivery y cubiertos biodegradables')
ON DUPLICATE KEY UPDATE observaciones = VALUES(observaciones);

INSERT INTO cliente (id_cliente, tipo_documento, numero_documento, nombre_razon_social, razon_social, contacto, telefono, email, limite_credito, dias_credito, puntos_fidelidad, estado) VALUES
(1, 'RUC', '20456789012', 'Corporación Minera Andina S.A.C.', 'Corporación Minera Andina S.A.C.', 'Dra. Patricia Silva', '988776655', 'psilva@mineraandina.pe', 10000.00, 30, 450, 'ACTIVO'),
(2, 'RUC', '20556677889', 'Consultora IT & Software Systems S.A.', 'Consultora IT & Software Systems S.A.', 'Ing. Javier Solís', '977665544', 'jsolis@itsystems.com', 5000.00, 15, 280, 'ACTIVO'),
(3, 'DNI', '42589632', 'Ing. Roberto Mendoza Gómez', 'Ing. Roberto Mendoza Gómez', 'Roberto Mendoza', '966554433', 'rmendoza@gmail.com', 1500.00, 15, 120, 'ACTIVO')
ON DUPLICATE KEY UPDATE razon_social = VALUES(razon_social), nombre_razon_social = VALUES(nombre_razon_social);

INSERT INTO cuenta_por_cobrar (id_cuenta_cobrar, id_cliente, id_venta, numero_comprobante, fecha_emision, fecha_vencimiento, monto_total, monto_cobrado, estado, observaciones) VALUES
(1, 1, NULL, 'F001-000320', DATE_SUB(CURRENT_DATE, INTERVAL 25 DAY), DATE_SUB(CURRENT_DATE, INTERVAL 2 DAY), 1850.00, 500.00, 'VENCIDA', 'Almuerzo corporativo directorio ejecutivo'),
(2, 2, NULL, 'F001-000345', DATE_SUB(CURRENT_DATE, INTERVAL 8 DAY), DATE_ADD(CURRENT_DATE, INTERVAL 7 DAY), 1240.00, 0.00, 'PENDIENTE', 'Catering aniversario equipo de desarrollo'),
(3, 3, NULL, 'B001-001098', DATE_SUB(CURRENT_DATE, INTERVAL 3 DAY), DATE_ADD(CURRENT_DATE, INTERVAL 12 DAY), 420.00, 420.00, 'COBRADA', 'Consumo mensual ejecutivo')
ON DUPLICATE KEY UPDATE observaciones = VALUES(observaciones);

INSERT INTO compra (id_compra, id_proveedor, serie, numero, fecha_emision, base_imponible, igv, total, estado, id_usuario) VALUES
(1, 1, 'F001', '0012450', DATE_SUB(CURRENT_DATE, INTERVAL 20 DAY), 2076.27, 373.73, 2450.00, 'REGISTRADA', 1),
(2, 2, 'F002', '0008920', DATE_SUB(CURRENT_DATE, INTERVAL 10 DAY), 3220.34, 579.66, 3800.00, 'REGISTRADA', 1),
(3, 3, 'F001', '0004312', DATE_SUB(CURRENT_DATE, INTERVAL 5 DAY), 974.58, 175.42, 1150.00, 'REGISTRADA', 1),
(4, 4, 'B003', '0001890', DATE_SUB(CURRENT_DATE, INTERVAL 2 DAY), 576.27, 103.73, 680.00, 'REGISTRADA', 1)
ON DUPLICATE KEY UPDATE serie = VALUES(serie);

INSERT INTO historial_precios (id_historial, tipo_entidad, id_entidad, nombre_entidad, precio_anterior, precio_nuevo, id_usuario, nombre_usuario, motivo) VALUES
(1, 'INSUMO', 1, 'Carne de Res Molida (KG)', 24.5000, 28.5000, 1, 'admin', 'Incremento de costo mayorista del proveedor cárnico (+16.3%)'),
(2, 'PRODUCTO', 1, 'Hamburguesa Clásica', 18.0000, 20.0000, 1, 'admin', 'Ajuste de margen por alza en insumos base de carne y pan'),
(3, 'INSUMO', 2, 'Queso Cheddar Láminas (KG)', 32.0000, 34.0000, 1, 'admin', 'Ajuste inflacionario trimestral'),
(4, 'PRODUCTO', 2, 'Alitas BBQ x6', 22.0000, 24.0000, 1, 'admin', 'Actualización de carta temporada verano')
ON DUPLICATE KEY UPDATE motivo = VALUES(motivo);
