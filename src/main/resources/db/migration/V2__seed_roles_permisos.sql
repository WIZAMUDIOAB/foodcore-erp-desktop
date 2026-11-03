-- ============================================================================
-- V2__seed_roles_permisos.sql — Roles y 14 Permisos Oficiales (SPEC-FOODCORE-v1.0 §10.3, §14)
-- ============================================================================

INSERT INTO rol (id_rol, codigo_rol, nombre, descripcion, estado) VALUES
(1, 'ADMIN',   'Administrador', 'Control total del sistema ERP, seguridad, usuarios y red', 1),
(2, 'CAJERO',  'Cajero',        'Operación de POS, apertura/cierre de turno y arqueo de caja', 1),
(3, 'MOZO',    'Mozo',          'Apertura de mesas y toma de comandas en salón', 1),
(4, 'COCINA',  'Cocinero',      'Operación de línea de cocina KDS y despacho de ítems', 1),
(5, 'ALMACEN', 'Almacenero',    'Control de insumos, recetas y consulta de Kárdex', 1)
ON DUPLICATE KEY UPDATE descripcion = VALUES(descripcion);

INSERT INTO permiso (codigo_permiso, descripcion) VALUES
('VENTA_COBRAR',                  'Autoriza el cobro de comandas y ventas POS'),
('VENTA_ANULAR',                  'Autoriza la anulación auditada de una venta registrada'),
('PEDIDO_CREAR',                  'Autoriza abrir una mesa y crear nuevos pedidos'),
('PEDIDO_ADICIONAR',              'Autoriza agregar ítems a pedidos abiertos o en preparación'),
('PEDIDO_ADICIONAR_POST_ENTREGA', 'Autoriza reabrir un pedido entregado para adición extraordinaria'),
('CAJA_APERTURA',                 'Autoriza aperturar un turno de caja con fondo inicial'),
('CAJA_CIERRE',                   'Autoriza ejecutar el arqueo físico y cierre Z del turno'),
('CAJA_MOVIMIENTO_MANUAL',        'Autoriza registrar ingresos o egresos manuales en caja'),
('KDS_OPERAR',                    'Autoriza transicionar estados de preparación en la pantalla KDS'),
('TICKET_REIMPRIMIR',             'Autoriza emitir una copia marcada como REIMPRESIÓN de un ticket'),
('USUARIO_GESTIONAR',             'Autoriza administrar usuarios, roles y desbloqueos'),
('INSUMO_GESTIONAR',              'Autoriza administrar el catálogo de insumos, recetas y ajustes de stock'),
('KARDEX_CONSULTAR',              'Autoriza consultar el libro mayor inmutable de movimientos de Kárdex'),
('CONFIG_RED',                    'Autoriza configurar terminales y parámetros de red LAN')
ON DUPLICATE KEY UPDATE descripcion = VALUES(descripcion);

-- Asignación de los 14 permisos al rol ADMIN
INSERT IGNORE INTO rol_permiso (codigo_rol, codigo_permiso)
SELECT 'ADMIN', codigo_permiso FROM permiso;

-- Asignación al rol CAJERO
INSERT IGNORE INTO rol_permiso (codigo_rol, codigo_permiso) VALUES
('CAJERO', 'VENTA_COBRAR'),
('CAJERO', 'VENTA_ANULAR'),
('CAJERO', 'CAJA_APERTURA'),
('CAJERO', 'CAJA_CIERRE'),
('CAJERO', 'CAJA_MOVIMIENTO_MANUAL'),
('CAJERO', 'TICKET_REIMPRIMIR'),
('CAJERO', 'PEDIDO_CREAR'),
('CAJERO', 'PEDIDO_ADICIONAR');

-- Asignación al rol MOZO
INSERT IGNORE INTO rol_permiso (codigo_rol, codigo_permiso) VALUES
('MOZO', 'PEDIDO_CREAR'),
('MOZO', 'PEDIDO_ADICIONAR');

-- Asignación al rol COCINA
INSERT IGNORE INTO rol_permiso (codigo_rol, codigo_permiso) VALUES
('COCINA', 'KDS_OPERAR');
