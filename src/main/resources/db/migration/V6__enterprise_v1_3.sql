-- ============================================================================
-- V6__enterprise_v1_3.sql — Esquema Enterprise FOODCORE ERP v1.3
-- (Modo Offline, Roles Dinámicos, Delivery/Takeaway, Periféricos y Auditoría)
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Nuevos Roles Predefinidos y Permisos (§2.1, §2.2)
-- ----------------------------------------------------------------------------
INSERT INTO rol (id_rol, codigo_rol, nombre, descripcion, estado) VALUES
(6, 'GERENTE',    'Gerente',    'Acceso a reportes gerenciales, auditoría y backoffice operativo sin configuración de BD', 1),
(7, 'SUPERVISOR', 'Supervisor', 'Operaciones avanzadas: autorización de descuentos, anulaciones y arqueo de turnos', 1)
ON DUPLICATE KEY UPDATE descripcion = VALUES(descripcion);

-- Asegurar rol ALMACENERO / ALMACEN sincronizado
UPDATE rol SET codigo_rol = 'ALMACEN', nombre = 'Almacenero' WHERE id_rol = 5;

-- Permisos granulares enterprise adicionados
INSERT INTO permiso (codigo_permiso, descripcion) VALUES
('ROL_GESTIONAR',     'Autoriza crear y editar roles personalizados y su matriz de permisos'),
('AUDITORIA_VER',     'Autoriza consultar la bitácora inmutable de auditoría y movimientos sospechosos'),
('DELIVERY_GESTIONAR','Autoriza despachar y asignar repartidores a comandas de Delivery')
ON DUPLICATE KEY UPDATE descripcion = VALUES(descripcion);

-- Asignar nuevos permisos a ADMIN
INSERT IGNORE INTO rol_permiso (codigo_rol, codigo_permiso) VALUES
('ADMIN', 'ROL_GESTIONAR'),
('ADMIN', 'AUDITORIA_VER'),
('ADMIN', 'DELIVERY_GESTIONAR');

-- Matriz de permisos para GERENTE
INSERT IGNORE INTO rol_permiso (codigo_rol, codigo_permiso) VALUES
('GERENTE', 'KARDEX_CONSULTAR'),
('GERENTE', 'INSUMO_GESTIONAR'),
('GERENTE', 'AUDITORIA_VER'),
('GERENTE', 'DELIVERY_GESTIONAR'),
('GERENTE', 'TICKET_REIMPRIMIR');

-- Matriz de permisos para SUPERVISOR (anular ventas, descuentos, crear pedidos, caja)
INSERT IGNORE INTO rol_permiso (codigo_rol, codigo_permiso) VALUES
('SUPERVISOR', 'VENTA_ANULAR'),
('SUPERVISOR', 'VENTA_DESCUENTO'),
('SUPERVISOR', 'VENTA_COBRAR'),
('SUPERVISOR', 'PEDIDO_CREAR'),
('SUPERVISOR', 'PEDIDO_ADICIONAR'),
('SUPERVISOR', 'CAJA_APERTURA'),
('SUPERVISOR', 'CAJA_CIERRE'),
('SUPERVISOR', 'CAJA_MOVIMIENTO_MANUAL'),
('SUPERVISOR', 'TICKET_REIMPRIMIR');

-- Matriz de permisos para ALMACENERO / ALMACEN (solo inventario, compras, mermas, kardex)
INSERT IGNORE INTO rol_permiso (codigo_rol, codigo_permiso) VALUES
('ALMACEN', 'INSUMO_GESTIONAR'),
('ALMACEN', 'KARDEX_CONSULTAR');

-- ----------------------------------------------------------------------------
-- 2. Canales de Venta: Delivery y Takeaway (§3.1, §3.2, §3.3)
-- ----------------------------------------------------------------------------
ALTER TABLE pedido
    ADD COLUMN tipo_pedido VARCHAR(20) NOT NULL DEFAULT 'MESA',
    ADD COLUMN cliente_nombre VARCHAR(120) NULL,
    ADD COLUMN cliente_telefono VARCHAR(30) NULL,
    ADD COLUMN cliente_direccion VARCHAR(200) NULL,
    ADD COLUMN cliente_referencia VARCHAR(200) NULL,
    ADD COLUMN costo_envio DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN repartidor VARCHAR(100) NULL;

-- ----------------------------------------------------------------------------
-- 3. Hardware e Impresión Múltiple por Categoría (§4.2)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS configuracion_impresora (
    id_impresora INT AUTO_INCREMENT PRIMARY KEY,
    id_categoria INT NOT NULL,
    nombre_impresora_windows VARCHAR(120) NOT NULL,
    activa TINYINT(1) NOT NULL DEFAULT 1,
    CONSTRAINT fk_ci_cat FOREIGN KEY (id_categoria) REFERENCES categoria(id_categoria) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 4. Auditoría y Bitácora Inmutable (Audit Trail) (§5.2)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auditoria (
    id_auditoria BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_usuario INT NOT NULL,
    accion VARCHAR(50) NOT NULL,
    entidad_afectada VARCHAR(60) NOT NULL,
    datos_anteriores TEXT NULL,
    datos_nuevos TEXT NULL,
    fecha TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_aud_usr FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 5. Soft Delete en tablas maestras: activo = 1 por defecto (§5.1)
-- ----------------------------------------------------------------------------
-- mesa: agregar activo si no existe
ALTER TABLE mesa
    ADD COLUMN activo TINYINT(1) NOT NULL DEFAULT 1;

-- producto: disponible ya existe en V1 (disponible TINYINT(1)), aseguramos columna activo normalizada
ALTER TABLE producto
    ADD COLUMN activo TINYINT(1) NOT NULL DEFAULT 1;
UPDATE producto SET activo = 1 WHERE activo IS NULL;

-- insumo y usuario ya cuentan con activo TINYINT(1) desde V1
