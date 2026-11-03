-- ============================================================================
-- V4__seed_terminales.sql — Terminales Autorizadas y Catálogo Demo Inicial (§10.4, §14; R-05)
-- ============================================================================

INSERT INTO terminal (codigo, descripcion, secreto_hash, rol_terminal, activo) VALUES
('KDS-01',       'Pantalla de Cocina Principal KDS-01', '$2a$12$BP1ioj65sjAcxPiN9NdVDOir1YPntePUhaOXHItvGRl/8pQOdEXrS', 'KDS', 1),
('POS-01',       'Terminal de Caja Principal POS-01',   '$2a$12$BP1ioj65sjAcxPiN9NdVDOir1YPntePUhaOXHItvGRl/8pQOdEXrS', 'POS', 1),
('KDS-INACTIVA', 'Terminal KDS Inactiva de Prueba',     '$2a$12$BP1ioj65sjAcxPiN9NdVDOir1YPntePUhaOXHItvGRl/8pQOdEXrS', 'KDS', 0)
ON DUPLICATE KEY UPDATE activo = VALUES(activo);

INSERT INTO mesa (id_mesa, numero, estado, version) VALUES
(1, '01', 'LIBRE', 0),
(2, '02', 'LIBRE', 0),
(3, '03', 'LIBRE', 0),
(4, '04', 'LIBRE', 0)
ON DUPLICATE KEY UPDATE estado = VALUES(estado);

INSERT INTO categoria (id_categoria, nombre, descripcion, activo) VALUES
(1, 'Cat Hamburguesas', 'Hamburguesas artesanales a la parrilla', 1),
(2, 'Cat Alitas & Pollo', 'Alitas y piezas crocantes', 1),
(3, 'Cat Bebidas', 'Gaseosas y refrescos naturales', 1)
ON DUPLICATE KEY UPDATE nombre = VALUES(nombre);

INSERT INTO producto (id_producto, id_categoria, codigo, nombre, descripcion, precio_venta, costo_estimado, disponible) VALUES
(1, 1, 'HAM-01', 'Hamburguesa Clasica', 'Carne 150g, queso cheddar y papas', 18.50, 7.50, 1),
(2, 2, 'ALI-01', 'Alitas BBQ x6',       '6 alitas bañadas en salsa BBQ',     22.00, 9.00, 1),
(3, 3, 'BEB-01', 'Gaseosa Personal',    'Botella 500ml helada',               5.00, 2.00, 1)
ON DUPLICATE KEY UPDATE precio_venta = VALUES(precio_venta);

INSERT INTO insumo (id_insumo, codigo, nombre, unidad_medida, stock_actual_disponible, stock_minimo, costo_unitario, costo_unitario_actual, activo) VALUES
(1, 'INS-001', 'Carne de Res 150g', 'UND', 50.0000, 10.0000, 4.5000, 4.5000, 1),
(2, 'INS-002', 'Pan Brioche',       'UND', 50.0000, 10.0000, 1.2000, 1.2000, 1),
(3, 'INS-003', 'Alitas de Pollo',   'UND', 60.0000, 12.0000, 1.5000, 1.5000, 1)
ON DUPLICATE KEY UPDATE nombre = VALUES(nombre);

INSERT INTO receta (id_receta, id_producto, id_insumo, cantidad_necesaria) VALUES
(1, 1, 1, 1.0000),
(2, 1, 2, 1.0000),
(3, 2, 3, 6.0000)
ON DUPLICATE KEY UPDATE cantidad_necesaria = VALUES(cantidad_necesaria);
