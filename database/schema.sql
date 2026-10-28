-- ============================================================================
-- SISTEMA ERP FOODCORE DESKTOP - FAST FOOD S.A.C.
-- Script DDL Normalizado (23 Tablas InnoDB) y Datos Semilla para MySQL 8.0
-- ============================================================================

DROP DATABASE IF EXISTS foodcore_db;
CREATE DATABASE foodcore_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE foodcore_db;

-- ----------------------------------------------------------------------------
-- 1. TABLA: rol
-- ----------------------------------------------------------------------------
CREATE TABLE rol (
    id_rol INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL UNIQUE,
    descripcion VARCHAR(150),
    estado BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 2. TABLA: empleado
-- ----------------------------------------------------------------------------
CREATE TABLE empleado (
    id_empleado INT AUTO_INCREMENT PRIMARY KEY,
    dni VARCHAR(15) NOT NULL UNIQUE,
    nombres VARCHAR(80) NOT NULL,
    apellidos VARCHAR(80) NOT NULL,
    telefono VARCHAR(20),
    cargo VARCHAR(50) NOT NULL,
    sueldo_base DECIMAL(10,2) NOT NULL DEFAULT 1025.00,
    fecha_ingreso DATE NOT NULL,
    estado BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 3. TABLA: usuario
-- ----------------------------------------------------------------------------
CREATE TABLE usuario (
    id_usuario INT AUTO_INCREMENT PRIMARY KEY,
    id_empleado INT NOT NULL,
    id_rol INT NOT NULL,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (id_empleado) REFERENCES empleado(id_empleado) ON DELETE RESTRICT ON UPDATE CASCADE,
    FOREIGN KEY (id_rol) REFERENCES rol(id_rol) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 4. TABLA: categoria
-- ----------------------------------------------------------------------------
CREATE TABLE categoria (
    id_categoria INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(60) NOT NULL UNIQUE,
    descripcion VARCHAR(200),
    activo BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 5. TABLA: producto
-- ----------------------------------------------------------------------------
CREATE TABLE producto (
    id_producto INT AUTO_INCREMENT PRIMARY KEY,
    id_categoria INT NOT NULL,
    codigo VARCHAR(20) NOT NULL UNIQUE,
    nombre VARCHAR(100) NOT NULL,
    descripcion TEXT,
    precio_venta DECIMAL(10,2) NOT NULL,
    costo_estimado DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    disponible BOOLEAN NOT NULL DEFAULT TRUE,
    FOREIGN KEY (id_categoria) REFERENCES categoria(id_categoria) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 6. TABLA: cliente
-- ----------------------------------------------------------------------------
CREATE TABLE cliente (
    id_cliente INT AUTO_INCREMENT PRIMARY KEY,
    tipo_documento ENUM('DNI', 'RUC', 'PASAPORTE', 'OTRO') DEFAULT 'DNI',
    numero_documento VARCHAR(20) NOT NULL,
    nombre_razon_social VARCHAR(150) NOT NULL,
    direccion VARCHAR(200),
    telefono VARCHAR(20),
    email VARCHAR(100)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 7. TABLA: caja
-- ----------------------------------------------------------------------------
CREATE TABLE caja (
    id_caja INT AUTO_INCREMENT PRIMARY KEY,
    id_usuario_apertura INT NOT NULL,
    id_usuario_cierre INT,
    nombre_caja VARCHAR(50) NOT NULL DEFAULT 'Caja Principal 01',
    monto_apertura DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    monto_cierre DECIMAL(10,2),
    fecha_apertura TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    fecha_cierre TIMESTAMP NULL,
    estado ENUM('ABIERTA', 'CERRADA') NOT NULL DEFAULT 'ABIERTA',
    diferencia DECIMAL(10,2) DEFAULT 0.00,
    FOREIGN KEY (id_usuario_apertura) REFERENCES usuario(id_usuario),
    FOREIGN KEY (id_usuario_cierre) REFERENCES usuario(id_usuario)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 8. TABLA: movimiento_caja
-- ----------------------------------------------------------------------------
CREATE TABLE movimiento_caja (
    id_movimiento INT AUTO_INCREMENT PRIMARY KEY,
    id_caja INT NOT NULL,
    id_usuario INT NOT NULL,
    tipo_movimiento ENUM('INGRESO', 'EGRESO', 'VENTA') NOT NULL,
    monto DECIMAL(10,2) NOT NULL,
    concepto VARCHAR(200) NOT NULL,
    fecha_hora TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (id_caja) REFERENCES caja(id_caja) ON DELETE RESTRICT,
    FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 9. TABLA: pedido
-- ----------------------------------------------------------------------------
CREATE TABLE pedido (
    id_pedido INT AUTO_INCREMENT PRIMARY KEY,
    id_usuario INT NOT NULL,
    id_cliente INT,
    numero_mesa VARCHAR(10) NOT NULL,
    fecha_hora TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    estado ENUM('REGISTRADO', 'EN_PREPARACION', 'LISTO', 'ENTREGADO', 'COBRADO', 'ANULADO') NOT NULL DEFAULT 'REGISTRADO',
    total DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario),
    FOREIGN KEY (id_cliente) REFERENCES cliente(id_cliente)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 10. TABLA: detalle_pedido
-- ----------------------------------------------------------------------------
CREATE TABLE detalle_pedido (
    id_detalle_pedido INT AUTO_INCREMENT PRIMARY KEY,
    id_pedido INT NOT NULL,
    id_producto INT NOT NULL,
    cantidad INT NOT NULL DEFAULT 1,
    precio_unitario DECIMAL(10,2) NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL,
    notas_preparacion VARCHAR(200) DEFAULT '',
    FOREIGN KEY (id_pedido) REFERENCES pedido(id_pedido) ON DELETE CASCADE,
    FOREIGN KEY (id_producto) REFERENCES producto(id_producto)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 11. TABLA: comanda
-- ----------------------------------------------------------------------------
CREATE TABLE comanda (
    id_comanda INT AUTO_INCREMENT PRIMARY KEY,
    id_pedido INT NOT NULL,
    numero_comanda VARCHAR(20) NOT NULL,
    fecha_hora_envio TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    estado ENUM('PENDIENTE', 'EN_PREPARACION', 'LISTA', 'DESPACHADA') NOT NULL DEFAULT 'PENDIENTE',
    tiempo_estimado_min INT DEFAULT 15,
    FOREIGN KEY (id_pedido) REFERENCES pedido(id_pedido) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 12. TABLA: venta
-- ----------------------------------------------------------------------------
CREATE TABLE venta (
    id_venta INT AUTO_INCREMENT PRIMARY KEY,
    id_pedido INT,
    id_caja INT NOT NULL,
    id_usuario INT NOT NULL,
    id_cliente INT,
    fecha_hora TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    subtotal DECIMAL(10,2) NOT NULL,
    igv DECIMAL(10,2) NOT NULL,
    total DECIMAL(10,2) NOT NULL,
    metodo_pago ENUM('EFECTIVO', 'TARJETA', 'YAPE_PLIN') NOT NULL DEFAULT 'EFECTIVO',
    monto_recibido DECIMAL(10,2) NOT NULL,
    vuelto DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    estado ENUM('COMPLETADA', 'ANULADA') NOT NULL DEFAULT 'COMPLETADA',
    FOREIGN KEY (id_pedido) REFERENCES pedido(id_pedido),
    FOREIGN KEY (id_caja) REFERENCES caja(id_caja),
    FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario),
    FOREIGN KEY (id_cliente) REFERENCES cliente(id_cliente)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 13. TABLA: detalle_venta
-- ----------------------------------------------------------------------------
CREATE TABLE detalle_venta (
    id_detalle_venta INT AUTO_INCREMENT PRIMARY KEY,
    id_venta INT NOT NULL,
    id_producto INT NOT NULL,
    cantidad INT NOT NULL,
    precio_unitario DECIMAL(10,2) NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL,
    FOREIGN KEY (id_venta) REFERENCES venta(id_venta) ON DELETE CASCADE,
    FOREIGN KEY (id_producto) REFERENCES producto(id_producto)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 14. TABLA: comprobante_pago
-- ----------------------------------------------------------------------------
CREATE TABLE comprobante_pago (
    id_comprobante INT AUTO_INCREMENT PRIMARY KEY,
    id_venta INT NOT NULL UNIQUE,
    tipo_comprobante ENUM('BOLETA', 'FACTURA', 'TICKET') NOT NULL DEFAULT 'TICKET',
    serie VARCHAR(10) NOT NULL,
    numero VARCHAR(20) NOT NULL,
    fecha_emision TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    total DECIMAL(10,2) NOT NULL,
    FOREIGN KEY (id_venta) REFERENCES venta(id_venta) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 15. TABLA: insumo
-- ----------------------------------------------------------------------------
CREATE TABLE insumo (
    id_insumo INT AUTO_INCREMENT PRIMARY KEY,
    codigo VARCHAR(20) NOT NULL UNIQUE,
    nombre VARCHAR(100) NOT NULL,
    unidad_medida ENUM('GR', 'UND', 'ML', 'KG', 'LT') NOT NULL,
    stock_actual DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    stock_minimo DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    costo_unitario DECIMAL(10,4) NOT NULL DEFAULT 0.0000
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 16. TABLA: receta
-- ----------------------------------------------------------------------------
CREATE TABLE receta (
    id_receta INT AUTO_INCREMENT PRIMARY KEY,
    id_producto INT NOT NULL,
    id_insumo INT NOT NULL,
    cantidad_necesaria DECIMAL(10,2) NOT NULL,
    FOREIGN KEY (id_producto) REFERENCES producto(id_producto) ON DELETE CASCADE,
    FOREIGN KEY (id_insumo) REFERENCES insumo(id_insumo) ON DELETE RESTRICT,
    UNIQUE KEY uq_prod_insumo (id_producto, id_insumo)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 17. TABLA: proveedor
-- ----------------------------------------------------------------------------
CREATE TABLE proveedor (
    id_proveedor INT AUTO_INCREMENT PRIMARY KEY,
    ruc VARCHAR(20) NOT NULL UNIQUE,
    razon_social VARCHAR(150) NOT NULL,
    contacto VARCHAR(80),
    telefono VARCHAR(20),
    direccion VARCHAR(200)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 18. TABLA: compra
-- ----------------------------------------------------------------------------
CREATE TABLE compra (
    id_compra INT AUTO_INCREMENT PRIMARY KEY,
    id_proveedor INT NOT NULL,
    id_usuario INT NOT NULL,
    fecha_compra TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    tipo_comprobante VARCHAR(30) NOT NULL DEFAULT 'FACTURA',
    numero_comprobante VARCHAR(50) NOT NULL,
    total DECIMAL(10,2) NOT NULL,
    estado ENUM('REGISTRADA', 'ANULADA') NOT NULL DEFAULT 'REGISTRADA',
    FOREIGN KEY (id_proveedor) REFERENCES proveedor(id_proveedor),
    FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 19. TABLA: detalle_compra
-- ----------------------------------------------------------------------------
CREATE TABLE detalle_compra (
    id_detalle_compra INT AUTO_INCREMENT PRIMARY KEY,
    id_compra INT NOT NULL,
    id_insumo INT NOT NULL,
    cantidad DECIMAL(10,2) NOT NULL,
    costo_unitario DECIMAL(10,4) NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL,
    FOREIGN KEY (id_compra) REFERENCES compra(id_compra) ON DELETE CASCADE,
    FOREIGN KEY (id_insumo) REFERENCES insumo(id_insumo)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 20. TABLA: movimiento_inventario
-- ----------------------------------------------------------------------------
CREATE TABLE movimiento_inventario (
    id_movimiento_inv INT AUTO_INCREMENT PRIMARY KEY,
    id_insumo INT NOT NULL,
    tipo ENUM('ENTRADA', 'SALIDA', 'AJUSTE') NOT NULL,
    cantidad DECIMAL(10,2) NOT NULL,
    fecha_hora TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    motivo VARCHAR(200) NOT NULL,
    referencia_id INT,
    FOREIGN KEY (id_insumo) REFERENCES insumo(id_insumo)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 21. TABLA: asistencia
-- ----------------------------------------------------------------------------
CREATE TABLE asistencia (
    id_asistencia INT AUTO_INCREMENT PRIMARY KEY,
    id_empleado INT NOT NULL,
    fecha DATE NOT NULL,
    hora_ingreso TIME NOT NULL,
    hora_salida TIME,
    estado ENUM('ASISTIO', 'TARDANZA', 'FALTA') NOT NULL DEFAULT 'ASISTIO',
    FOREIGN KEY (id_empleado) REFERENCES empleado(id_empleado)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 22. TABLA: planilla
-- ----------------------------------------------------------------------------
CREATE TABLE planilla (
    id_planilla INT AUTO_INCREMENT PRIMARY KEY,
    id_empleado INT NOT NULL,
    mes INT NOT NULL,
    anio INT NOT NULL,
    dias_trabajados INT NOT NULL DEFAULT 30,
    sueldo_neto DECIMAL(10,2) NOT NULL,
    fecha_pago DATE NOT NULL,
    estado ENUM('PENDIENTE', 'PAGADO') NOT NULL DEFAULT 'PENDIENTE',
    FOREIGN KEY (id_empleado) REFERENCES empleado(id_empleado)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 23. TABLA: costo_operativo
-- ----------------------------------------------------------------------------
CREATE TABLE costo_operativo (
    id_costo INT AUTO_INCREMENT PRIMARY KEY,
    concepto VARCHAR(150) NOT NULL,
    categoria_costo ENUM('LUZ', 'AGUA', 'ALQUILER', 'MANTENIMIENTO', 'OTROS') NOT NULL,
    monto DECIMAL(10,2) NOT NULL,
    fecha_registro DATE NOT NULL,
    id_usuario INT NOT NULL,
    FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)
) ENGINE=InnoDB;


-- ============================================================================
-- DATOS SEMILLA (SEEDS)
-- ============================================================================

-- 1. Roles (5 Roles del Sistema)
INSERT INTO rol (id_rol, nombre, descripcion, estado) VALUES
(1, 'Gerente', 'Control total gerencial, reportes y supervisi�n', TRUE),
(2, 'Administrador', 'Gesti�n t�cnica de caja, almac�n, recetas y personal', TRUE),
(3, 'Mozo', 'Toma de pedidos en sal�n y despacho de comandas', TRUE),
(4, 'Cocinero', 'Visualizaci�n de comandas en KDS y despacho', TRUE),
(5, 'Cajero', 'Atenci�n en terminal POS, apertura y arqueo de caja', TRUE);

-- 2. Empleados y Usuarios Iniciales
INSERT INTO empleado (id_empleado, dni, nombres, apellidos, telefono, cargo, sueldo_base, fecha_ingreso, estado) VALUES
(1, '70001001', 'Carlos', 'Zambrano Ramos', '987654321', 'Gerente General', 3500.00, '2024-01-01', TRUE),
(2, '70001002', 'Lucia', 'Fernandez Torres', '987654322', 'Administradora de Turno', 2200.00, '2024-01-15', TRUE),
(3, '70001003', 'Mateo', 'Quispe Morales', '987654323', 'Mozo Principal', 1200.00, '2024-02-01', TRUE),
(4, '70001004', 'Rosa', 'Medina Sanchez', '987654324', 'Jefa de Cocina', 1800.00, '2024-02-01', TRUE),
(5, '70001005', 'Valeria', 'Rios Castro', '987654325', 'Cajera Principal', 1500.00, '2024-02-15', TRUE);

-- Contrase�as para entorno local (*123)
INSERT INTO usuario (id_usuario, id_empleado, id_rol, username, password_hash, activo) VALUES
(1, 1, 1, 'gerente', 'gerente123', TRUE),
(2, 2, 2, 'admin', 'admin123', TRUE),
(3, 3, 3, 'mozo1', 'mozo123', TRUE),
(4, 4, 4, 'cocina1', 'cocina123', TRUE),
(5, 5, 5, 'cajero', 'cajero123', TRUE),
(6, 3, 3, 'mozo', 'mozo123', TRUE),
(7, 4, 4, 'cocinero', 'cocinero123', TRUE);

-- 3. Clientes Semilla
INSERT INTO cliente (id_cliente, tipo_documento, numero_documento, nombre_razon_social, direccion, telefono, email) VALUES
(1, 'DNI', '00000000', 'CLIENTES VARIOS', 'Local Principal', '000000000', 'ventas@fastfood.pe'),
(2, 'DNI', '45892173', 'Juan Perez Gomez', 'Av. La Marina 1234', '991234567', 'juan.perez@gmail.com');

-- 4. Categorías de Productos
INSERT INTO categoria (id_categoria, nombre, descripcion, activo) VALUES
(1, 'Hamburguesas', 'Hamburguesas artesanales a la parrilla con pan brioche', TRUE),
(2, 'Acompañamientos y Combos', 'Papas fritas, alitas crujientes y combos especiales', TRUE),
(3, 'Bebidas', 'Gaseosas personales, jugos y bebidas refrescantes', TRUE);

-- 5. Insumos Básicos
INSERT INTO insumo (id_insumo, codigo, nombre, unidad_medida, stock_actual, stock_minimo, costo_unitario) VALUES
(1, 'INS-001', 'Pan Hamburguesa Brioche', 'UND', 200.00, 30.00, 0.8000),
(2, 'INS-002', 'Carne Molida de Res 150g', 'GR', 25000.00, 3000.00, 0.0350),
(3, 'INS-003', 'Queso Cheddar Laminado', 'UND', 300.00, 40.00, 0.5000),
(4, 'INS-004', 'Papa Amarilla Seleccionada', 'GR', 60000.00, 10000.00, 0.0050),
(5, 'INS-005', 'Aceite Vegetal de Fritura', 'ML', 40000.00, 5000.00, 0.0080),
(6, 'INS-006', 'Gaseosa Personal 500ml', 'UND', 120.00, 20.00, 2.2000),
(7, 'INS-007', 'Alitas de Pollo Frescas', 'UND', 240.00, 40.00, 1.2000),
(8, 'INS-008', 'Salsa BBQ Ahumada', 'ML', 15000.00, 2000.00, 0.0200);

-- 6. Productos (6 Platos de Comida Rápida)
INSERT INTO producto (id_producto, id_categoria, codigo, nombre, descripcion, precio_venta, costo_estimado, disponible) VALUES
(1, 1, 'HAM-01', 'Hamburguesa Clásica', 'Pan brioche, 150g de carne de res a la parrilla y queso cheddar', 15.00, 6.05, TRUE),
(2, 1, 'HAM-02', 'Hamburguesa Doble Queso', 'Pan brioche, doble porción de carne (250g) y doble queso cheddar', 22.00, 10.55, TRUE),
(3, 2, 'ACO-01', 'Papas Fritas Grandes', '350g de papas amarillas crujientes con toque de sal marina', 9.00, 2.15, TRUE),
(4, 3, 'BEB-01', 'Gaseosa Personal 500ml', 'Botella de 500ml helada (Inca Kola / Coca Cola)', 5.00, 2.20, TRUE),
(5, 2, 'COM-01', 'Combo FastBurger', 'Hamburguesa Clásica + Papas Medianas + Gaseosa 500ml', 25.00, 8.50, TRUE),
(6, 2, 'ACO-02', 'Alitas BBQ x6', '6 alitas bañadas en abundante salsa BBQ ahumada', 18.00, 8.72, TRUE);

-- 7. Recetas (Composición de Platos por Insumos)
-- Hamburguesa Clásica (Pan 1u, Carne 150g, Queso 1u)
INSERT INTO receta (id_producto, id_insumo, cantidad_necesaria) VALUES
(1, 1, 1.00),
(1, 2, 150.00),
(1, 3, 1.00);

-- Hamburguesa Doble Queso (Pan 1u, Carne 250g, Queso 2u)
INSERT INTO receta (id_producto, id_insumo, cantidad_necesaria) VALUES
(2, 1, 1.00),
(2, 2, 250.00),
(2, 3, 2.00);

-- Papas Fritas Grandes (Papa 350g, Aceite 50ml)
INSERT INTO receta (id_producto, id_insumo, cantidad_necesaria) VALUES
(3, 4, 350.00),
(3, 5, 50.00);

-- Gaseosa Personal (Gaseosa 1u)
INSERT INTO receta (id_producto, id_insumo, cantidad_necesaria) VALUES
(4, 6, 1.00);

-- Combo FastBurger (Pan 1u, Carne 150g, Queso 1u, Papa 200g, Aceite 30ml, Gaseosa 1u)
INSERT INTO receta (id_producto, id_insumo, cantidad_necesaria) VALUES
(5, 1, 1.00),
(5, 2, 150.00),
(5, 3, 1.00),
(5, 4, 200.00),
(5, 5, 30.00),
(5, 6, 1.00);

-- Alitas BBQ x6 (Alitas 6u, Salsa BBQ 60ml, Aceite 40ml)
INSERT INTO receta (id_producto, id_insumo, cantidad_necesaria) VALUES
(6, 7, 6.00),
(6, 8, 60.00),
(6, 5, 40.00);

-- 8. Caja Inicial Abierta de Prueba
INSERT INTO caja (id_caja, id_usuario_apertura, nombre_caja, monto_apertura, fecha_apertura, estado) VALUES
(1, 2, 'Caja Principal - Turno Mañana', 150.00, CURRENT_TIMESTAMP, 'ABIERTA');

INSERT INTO movimiento_caja (id_caja, id_usuario, tipo_movimiento, monto, concepto) VALUES
(1, 2, 'INGRESO', 150.00, 'Apertura de caja con fondo inicial para sencillo');
