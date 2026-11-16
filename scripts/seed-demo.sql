-- ============================================================================
-- FoodCore ERP v5.5.0 - Datos de Demostración Descartables (es_demo = 1)
-- Inserta clientes, productos y ventas de prueba de forma idempotente.
-- Todos los registros generados llevan es_demo = 1 para borrado seguro.
-- ============================================================================

INSERT INTO categoria (nombre, descripcion, activo, es_demo)
SELECT 'Menú Demo', 'Categoría temporal de demostración', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM categoria WHERE nombre = 'Menú Demo');

INSERT INTO caja (id_usuario_apertura, nombre_caja, monto_apertura, estado, es_demo)
SELECT 1, 'Caja Demo 01', 200.00, 'ABIERTA', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM caja);

INSERT INTO cliente (
    tipo_documento, numero_documento, tipo_cliente, nombre_razon_social, razon_social,
    contacto, telefono, email, limite_credito, dias_credito, puntos_fidelidad, puntos_acumulados,
    estado, activo, es_demo
)
SELECT
    'DNI', '99000001', 'MINORISTA', 'Cliente Demo Valeria Soto', 'Cliente Demo Valeria Soto',
    'Valeria Soto', '999888001', 'valeria.demo@foodcore.pe', 0.00, 0, 120, 120,
    'ACTIVO', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cliente WHERE numero_documento = '99000001');

INSERT INTO cliente (
    tipo_documento, numero_documento, tipo_cliente, nombre_razon_social, razon_social,
    contacto, telefono, email, limite_credito, dias_credito, puntos_fidelidad, puntos_acumulados,
    estado, activo, es_demo
)
SELECT
    'DNI', '99000002', 'MINORISTA', 'Cliente Demo Andrés Paredes', 'Cliente Demo Andrés Paredes',
    'Andrés Paredes', '999888002', 'andres.demo@foodcore.pe', 0.00, 0, 80, 80,
    'ACTIVO', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cliente WHERE numero_documento = '99000002');

INSERT INTO cliente (
    tipo_documento, numero_documento, tipo_cliente, nombre_razon_social, razon_social,
    contacto, telefono, email, limite_credito, dias_credito, puntos_fidelidad, puntos_acumulados,
    estado, activo, es_demo
)
SELECT
    'RUC', '20990000001', 'CORPORATIVO', 'Corporación Demo Bistro S.A.C.', 'Corporación Demo Bistro S.A.C.',
    'Área Compras Demo', '014409900', 'compras.demo@bistro.pe', 1500.00, 15, 0, 0,
    'ACTIVO', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cliente WHERE numero_documento = '20990000001');

INSERT INTO producto (id_categoria, codigo, nombre, descripcion, precio_venta, costo_estimado, disponible, activo, es_demo)
SELECT c.id_categoria, 'DEMO-01', 'Bowl Demo Pollo Crispy', 'Plato demostrativo con papas nativas y salsa especial', 24.90, 9.50, 1, 1, 1
FROM categoria c WHERE c.nombre = 'Menú Demo'
  AND NOT EXISTS (SELECT 1 FROM producto WHERE codigo = 'DEMO-01');

INSERT INTO producto (id_categoria, codigo, nombre, descripcion, precio_venta, costo_estimado, disponible, activo, es_demo)
SELECT c.id_categoria, 'DEMO-02', 'Burger Demo Trufada', 'Hamburguesa artesanal demostrativa 180g con queso maduro', 29.50, 11.20, 1, 1, 1
FROM categoria c WHERE c.nombre = 'Menú Demo'
  AND NOT EXISTS (SELECT 1 FROM producto WHERE codigo = 'DEMO-02');

INSERT INTO producto (id_categoria, codigo, nombre, descripcion, precio_venta, costo_estimado, disponible, activo, es_demo)
SELECT c.id_categoria, 'DEMO-03', 'Limonada Demo Hierbaluisa', 'Bebida refrescante de prueba 500ml', 9.00, 2.50, 1, 1, 1
FROM categoria c WHERE c.nombre = 'Menú Demo'
  AND NOT EXISTS (SELECT 1 FROM producto WHERE codigo = 'DEMO-03');

INSERT INTO venta (
    id_pedido, id_caja, id_usuario, id_cliente, subtotal, igv, total,
    metodo_pago, monto_recibido, vuelto, estado, descuento, descuento_puntos, idempotency_key, es_demo
)
SELECT
    NULL,
    (SELECT MIN(id_caja) FROM caja),
    1,
    (SELECT id_cliente FROM cliente WHERE numero_documento = '99000001' LIMIT 1),
    45.59, 8.21, 53.80,
    'EFECTIVO', 60.00, 6.20, 'COMPLETADA', 0.00, 0.00, 'demo-vta-0001-0000-0000-000000000001', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM venta WHERE idempotency_key = 'demo-vta-0001-0000-0000-000000000001');

INSERT INTO venta (
    id_pedido, id_caja, id_usuario, id_cliente, subtotal, igv, total,
    metodo_pago, monto_recibido, vuelto, estado, descuento, descuento_puntos, idempotency_key, es_demo
)
SELECT
    NULL,
    (SELECT MIN(id_caja) FROM caja),
    1,
    (SELECT id_cliente FROM cliente WHERE numero_documento = '99000002' LIMIT 1),
    50.00, 9.00, 59.00,
    'TARJETA', 59.00, 0.00, 'COMPLETADA', 0.00, 0.00, 'demo-vta-0002-0000-0000-000000000002', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM venta WHERE idempotency_key = 'demo-vta-0002-0000-0000-000000000002');

INSERT INTO detalle_venta (id_venta, id_producto, cantidad, precio_unitario, subtotal, estado, es_demo)
SELECT
    v.id_venta, p.id_producto, 2, 24.90, 49.80, 'COMPLETADO', 1
FROM venta v
JOIN producto p ON p.codigo = 'DEMO-01'
WHERE v.idempotency_key = 'demo-vta-0001-0000-0000-000000000001'
  AND NOT EXISTS (
      SELECT 1 FROM detalle_venta dv WHERE dv.id_venta = v.id_venta AND dv.id_producto = p.id_producto
  );

INSERT INTO detalle_venta (id_venta, id_producto, cantidad, precio_unitario, subtotal, estado, es_demo)
SELECT
    v.id_venta, p.id_producto, 2, 29.50, 59.00, 'COMPLETADO', 1
FROM venta v
JOIN producto p ON p.codigo = 'DEMO-02'
WHERE v.idempotency_key = 'demo-vta-0002-0000-0000-000000000002'
  AND NOT EXISTS (
      SELECT 1 FROM detalle_venta dv WHERE dv.id_venta = v.id_venta AND dv.id_producto = p.id_producto
  );
