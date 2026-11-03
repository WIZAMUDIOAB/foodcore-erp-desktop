-- ============================================================================
-- V1__init.sql — Esquema Maestro FOODCORE ERP v1.0 (SPEC-FOODCORE-v1.0 §5)
-- ============================================================================

CREATE TABLE IF NOT EXISTS rol (
    id_rol INT AUTO_INCREMENT PRIMARY KEY,
    codigo_rol VARCHAR(30) NOT NULL UNIQUE,
    codigo VARCHAR(30) GENERATED ALWAYS AS (codigo_rol) VIRTUAL,
    nombre VARCHAR(50) NOT NULL UNIQUE,
    descripcion VARCHAR(150) NOT NULL,
    estado TINYINT(1) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS permiso (
    codigo_permiso VARCHAR(60) PRIMARY KEY,
    descripcion VARCHAR(160) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS rol_permiso (
    codigo_rol VARCHAR(30) NOT NULL,
    codigo_permiso VARCHAR(60) NOT NULL,
    PRIMARY KEY (codigo_rol, codigo_permiso),
    CONSTRAINT fk_rp_rol FOREIGN KEY (codigo_rol) REFERENCES rol(codigo_rol),
    CONSTRAINT fk_rp_permiso FOREIGN KEY (codigo_permiso) REFERENCES permiso(codigo_permiso)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS empleado (
    id_empleado INT AUTO_INCREMENT PRIMARY KEY,
    dni VARCHAR(15) NOT NULL UNIQUE,
    nombres VARCHAR(80) NOT NULL,
    apellidos VARCHAR(80) NOT NULL,
    telefono VARCHAR(20),
    cargo VARCHAR(50) NOT NULL,
    sueldo_base DECIMAL(10,2) NOT NULL DEFAULT 1025.00,
    fecha_ingreso DATE NOT NULL,
    estado TINYINT(1) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS usuario (
    id_usuario INT AUTO_INCREMENT PRIMARY KEY,
    id_empleado INT NOT NULL DEFAULT 1,
    id_rol INT NOT NULL DEFAULT 1,
    username VARCHAR(50) NOT NULL UNIQUE,
    nombre_usuario VARCHAR(50) NULL,
    nombre_completo VARCHAR(120) NOT NULL DEFAULT 'Usuario FoodCore',
    password_hash VARCHAR(255) NOT NULL,
    hash_password CHAR(60) NULL,
    codigo_rol VARCHAR(30) NOT NULL DEFAULT 'ADMIN',
    activo TINYINT(1) NOT NULL DEFAULT 1,
    intentos_fallidos INT NOT NULL DEFAULT 0,
    bloqueado_hasta DATETIME NULL,
    cambio_password_requerido TINYINT(1) NOT NULL DEFAULT 1,
    ultimo_login DATETIME NULL,
    fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS categoria (
    id_categoria INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(60) NOT NULL UNIQUE,
    descripcion VARCHAR(200),
    activo TINYINT(1) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS producto (
    id_producto INT AUTO_INCREMENT PRIMARY KEY,
    id_categoria INT NOT NULL,
    codigo VARCHAR(20) NULL UNIQUE,
    nombre VARCHAR(100) NOT NULL,
    descripcion TEXT,
    precio_venta DECIMAL(14,2) NOT NULL,
    costo_estimado DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    stock_disponible INT NOT NULL DEFAULT 100,
    disponible TINYINT(1) NOT NULL DEFAULT 1,
    CONSTRAINT fk_prod_cat FOREIGN KEY (id_categoria) REFERENCES categoria(id_categoria)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS item_vendible (
    id_item BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(120) NOT NULL,
    precio_carta DECIMAL(14,2) NOT NULL,
    activo TINYINT(1) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS cliente (
    id_cliente INT AUTO_INCREMENT PRIMARY KEY,
    tipo_documento VARCHAR(20) DEFAULT 'DNI',
    numero_documento VARCHAR(20) NOT NULL,
    nombre_razon_social VARCHAR(150) NOT NULL,
    direccion VARCHAR(200),
    telefono VARCHAR(20),
    email VARCHAR(100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS insumo (
    id_insumo BIGINT AUTO_INCREMENT PRIMARY KEY,
    codigo VARCHAR(20) NOT NULL UNIQUE,
    nombre VARCHAR(100) NOT NULL,
    unidad_medida VARCHAR(20) NOT NULL,
    stock_actual_disponible DECIMAL(14,4) NOT NULL DEFAULT 0.0000,
    stock_minimo DECIMAL(14,4) NOT NULL DEFAULT 0.0000,
    costo_unitario DECIMAL(14,4) NOT NULL DEFAULT 0.0000,
    costo_unitario_actual DECIMAL(14,4) NOT NULL DEFAULT 0.0000,
    activo TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT ck_insumo_stock CHECK (stock_actual_disponible >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS receta (
    id_receta INT AUTO_INCREMENT PRIMARY KEY,
    id_producto INT NOT NULL,
    id_insumo BIGINT NOT NULL,
    cantidad_necesaria DECIMAL(14,4) NOT NULL,
    CONSTRAINT fk_rec_prod FOREIGN KEY (id_producto) REFERENCES producto(id_producto) ON DELETE CASCADE,
    CONSTRAINT fk_rec_ins FOREIGN KEY (id_insumo) REFERENCES insumo(id_insumo) ON DELETE RESTRICT,
    UNIQUE KEY uq_prod_insumo (id_producto, id_insumo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS receta_insumo (
    id_receta INT NOT NULL,
    id_insumo BIGINT NOT NULL,
    cantidad_requerida DECIMAL(14,4) NOT NULL,
    PRIMARY KEY (id_receta, id_insumo),
    CONSTRAINT fk_ri_insumo FOREIGN KEY (id_insumo) REFERENCES insumo(id_insumo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS kardex_movimiento (
    id_kardex BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_insumo BIGINT NOT NULL,
    tipo_movimiento VARCHAR(20) NOT NULL,
    cantidad DECIMAL(14,4) NOT NULL,
    saldo_anterior DECIMAL(14,4) NOT NULL,
    saldo_posterior DECIMAL(14,4) NOT NULL,
    costo_unitario DECIMAL(14,4) NOT NULL DEFAULT 0.0000,
    referencia_tipo VARCHAR(30) NULL,
    referencia_id BIGINT NULL,
    id_usuario BIGINT NOT NULL DEFAULT 1,
    observacion VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_kardex_insumo FOREIGN KEY (id_insumo) REFERENCES insumo(id_insumo),
    INDEX idx_kardex_insumo_fecha (id_insumo, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS movimiento_inventario (
    id_movimiento_inv INT AUTO_INCREMENT PRIMARY KEY,
    id_insumo BIGINT NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    cantidad DECIMAL(14,4) NOT NULL,
    fecha_hora TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    motivo VARCHAR(200) NOT NULL,
    referencia_id INT NULL,
    CONSTRAINT fk_movinv_insumo FOREIGN KEY (id_insumo) REFERENCES insumo(id_insumo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS caja (
    id_caja INT AUTO_INCREMENT PRIMARY KEY,
    id_usuario_apertura INT NOT NULL,
    id_usuario_cierre INT NULL,
    nombre_caja VARCHAR(50) NOT NULL DEFAULT 'Caja Principal 01',
    monto_apertura DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    monto_cierre DECIMAL(14,2) NULL,
    fecha_apertura TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    fecha_cierre TIMESTAMP NULL,
    estado VARCHAR(15) NOT NULL DEFAULT 'ABIERTA',
    diferencia DECIMAL(14,2) DEFAULT 0.00
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS turno_caja (
    id_turno BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_usuario_apertura BIGINT NOT NULL,
    id_usuario_cierre BIGINT NULL,
    saldo_inicial DECIMAL(14,2) NOT NULL,
    saldo_final_teorico DECIMAL(14,2) NULL,
    saldo_final_contado DECIMAL(14,2) NULL,
    diferencia DECIMAL(14,2) NULL,
    estado VARCHAR(15) NOT NULL DEFAULT 'ABIERTO',
    abierto_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cerrado_en DATETIME NULL,
    observacion VARCHAR(255) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS movimiento_caja (
    id_movimiento INT AUTO_INCREMENT PRIMARY KEY,
    id_caja INT NOT NULL,
    id_usuario INT NOT NULL,
    tipo_movimiento VARCHAR(20) NOT NULL,
    medio_pago VARCHAR(15) NULL DEFAULT 'EFECTIVO',
    monto DECIMAL(14,2) NOT NULL,
    concepto VARCHAR(200) NOT NULL,
    fecha_hora TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_movcaja_caja FOREIGN KEY (id_caja) REFERENCES caja(id_caja)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS mesa (
    id_mesa BIGINT AUTO_INCREMENT PRIMARY KEY,
    numero VARCHAR(10) NOT NULL UNIQUE,
    estado VARCHAR(15) NOT NULL DEFAULT 'LIBRE',
    version INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS pedido (
    id_pedido INT AUTO_INCREMENT PRIMARY KEY,
    id_mesa BIGINT NULL,
    id_usuario INT NOT NULL,
    id_cliente INT NULL,
    numero_mesa VARCHAR(10) NOT NULL,
    fecha_hora TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    estado VARCHAR(20) NOT NULL DEFAULT 'REGISTRADO',
    total DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    idempotency_key CHAR(36) NULL UNIQUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS detalle_pedido (
    id_detalle_pedido INT AUTO_INCREMENT PRIMARY KEY,
    id_pedido INT NOT NULL,
    id_producto INT NOT NULL,
    cantidad INT NOT NULL DEFAULT 1,
    precio_unitario DECIMAL(14,2) NOT NULL,
    subtotal DECIMAL(14,2) NOT NULL,
    notas_preparacion VARCHAR(255) DEFAULT '',
    estado_cocina VARCHAR(15) NOT NULL DEFAULT 'PENDIENTE',
    enviado_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    iniciado_en DATETIME NULL,
    listo_en DATETIME NULL,
    CONSTRAINT fk_detped_ped FOREIGN KEY (id_pedido) REFERENCES pedido(id_pedido) ON DELETE CASCADE,
    CONSTRAINT fk_detped_prod FOREIGN KEY (id_producto) REFERENCES producto(id_producto)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS comanda (
    id_comanda INT AUTO_INCREMENT PRIMARY KEY,
    id_pedido INT NOT NULL,
    numero_comanda VARCHAR(20) NOT NULL,
    fecha_hora_envio TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    tiempo_estimado_min INT DEFAULT 15,
    CONSTRAINT fk_comanda_ped FOREIGN KEY (id_pedido) REFERENCES pedido(id_pedido) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS venta (
    id_venta INT AUTO_INCREMENT PRIMARY KEY,
    id_pedido INT NULL,
    id_caja INT NOT NULL,
    id_usuario INT NOT NULL,
    id_cliente INT NULL,
    fecha_hora TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    subtotal DECIMAL(14,2) NOT NULL,
    igv DECIMAL(14,2) NOT NULL,
    total DECIMAL(14,2) NOT NULL,
    metodo_pago VARCHAR(20) NOT NULL DEFAULT 'EFECTIVO',
    monto_recibido DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    vuelto DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    estado VARCHAR(15) NOT NULL DEFAULT 'COMPLETADA',
    idempotency_key CHAR(36) NULL UNIQUE,
    CONSTRAINT fk_venta_caja FOREIGN KEY (id_caja) REFERENCES caja(id_caja)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS detalle_venta (
    id_detalle_venta INT AUTO_INCREMENT PRIMARY KEY,
    id_venta INT NOT NULL,
    id_producto INT NOT NULL,
    cantidad INT NOT NULL,
    precio_unitario DECIMAL(14,2) NOT NULL,
    subtotal DECIMAL(14,2) NOT NULL,
    CONSTRAINT fk_detven_venta FOREIGN KEY (id_venta) REFERENCES venta(id_venta) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS comprobante_pago (
    id_comprobante INT AUTO_INCREMENT PRIMARY KEY,
    id_venta INT NOT NULL UNIQUE,
    tipo_comprobante VARCHAR(20) NOT NULL DEFAULT 'TICKET',
    serie VARCHAR(10) NOT NULL,
    numero VARCHAR(20) NOT NULL,
    fecha_emision TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    total DECIMAL(14,2) NOT NULL,
    CONSTRAINT fk_comp_venta FOREIGN KEY (id_venta) REFERENCES venta(id_venta) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS outbox_evento (
    id_evento BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id CHAR(36) NULL,
    tipo_evento VARCHAR(40) NOT NULL,
    payload_json JSON NOT NULL,
    estado VARCHAR(15) NOT NULL DEFAULT 'PENDIENTE',
    intentos INT NOT NULL DEFAULT 0,
    proximo_intento DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
    creado_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    enviado_en DATETIME NULL,
    INDEX idx_outbox_pendiente (estado, proximo_intento)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS terminal (
    id_terminal BIGINT AUTO_INCREMENT PRIMARY KEY,
    codigo VARCHAR(30) NOT NULL UNIQUE,
    descripcion VARCHAR(120) NOT NULL,
    secreto_hash CHAR(60) NOT NULL,
    rol_terminal VARCHAR(20) NOT NULL,
    activo TINYINT(1) NOT NULL DEFAULT 1,
    ultima_ip VARCHAR(45) NULL,
    ultimo_acceso DATETIME NULL,
    ultimo_heartbeat DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS asistencia (
    id_asistencia INT AUTO_INCREMENT PRIMARY KEY,
    id_empleado INT NOT NULL,
    fecha DATE NOT NULL,
    hora_ingreso TIME NOT NULL,
    hora_salida TIME NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'ASISTIO'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
