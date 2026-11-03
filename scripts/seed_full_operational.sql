-- Semilla de datos operacionales completos para prueba de Backup y Restore (N4)
USE foodcore;

DELETE FROM kardex_movimiento;
DELETE FROM detalle_venta;
DELETE FROM venta;
DELETE FROM outbox_evento;
DELETE FROM detalle_pedido;
DELETE FROM pedido;
DELETE FROM movimiento_caja;
DELETE FROM turno_caja;
DELETE FROM caja;

INSERT INTO turno_caja (id_turno, id_usuario_apertura, saldo_inicial, estado)
VALUES (1, 1, 100.00, 'ABIERTO');

INSERT INTO caja (id_caja, id_usuario_apertura, nombre_caja, monto_apertura, estado)
VALUES (1, 1, 'Caja Principal 01', 100.00, 'ABIERTA');

INSERT INTO movimiento_caja (id_caja, id_usuario, tipo_movimiento, medio_pago, monto, concepto)
VALUES (1, 1, 'INGRESO', 'EFECTIVO', 50.00, 'Cobro Venta #1');

INSERT INTO pedido (id_pedido, id_mesa, id_usuario, numero_mesa, estado, total, idempotency_key)
VALUES (1, 1, 1, 'M01', 'REGISTRADO', 50.00, 'IDEM-PED-STAGING-001');

INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario, subtotal, estado_cocina)
VALUES (1, 1, 2, 25.00, 50.00, 'PENDIENTE');

INSERT INTO venta (id_venta, id_pedido, id_caja, id_usuario, subtotal, igv, total, metodo_pago, monto_recibido, vuelto, idempotency_key)
VALUES (1, 1, 1, 1, 42.37, 7.63, 50.00, 'EFECTIVO', 50.00, 0.00, 'IDEM-VENTA-STAGING-001');

INSERT INTO detalle_venta (id_venta, id_producto, cantidad, precio_unitario, subtotal)
VALUES (1, 1, 2, 25.00, 50.00);

INSERT INTO kardex_movimiento (id_insumo, tipo_movimiento, cantidad, saldo_anterior, saldo_posterior, costo_unitario, referencia_tipo, referencia_id, id_usuario, observacion)
VALUES (1, 'SALIDA_VENTA', 2.0000, 50.0000, 48.0000, 5.0000, 'VENTA', 1, 1, 'Descuento receta venta #1');

INSERT INTO outbox_evento (tipo_evento, payload_json, estado, intentos)
VALUES ('NUEVA_COMANDA', '{"idPedido":1,"mesa":"M01"}', 'PENDIENTE', 0);
