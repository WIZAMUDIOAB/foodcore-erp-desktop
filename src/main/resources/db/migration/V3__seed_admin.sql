-- ============================================================================
-- V3__seed_admin.sql — Seed de Administrador con BCrypt Costo 12 y cambio_password_requerido=1 (§10.1, §14)
-- ============================================================================

INSERT INTO empleado (id_empleado, dni, nombres, apellidos, telefono, cargo, sueldo_base, fecha_ingreso, estado) VALUES
(1, '70000001', 'Administrador', 'General',  '999000001', 'Administrador ERP', 3500.00, '2026-01-01', 1),
(2, '70000002', 'Maria',         'Cajera',   '999000002', 'Cajera Principal',  1500.00, '2026-01-01', 1),
(3, '70000003', 'Carlos',        'Mozo',     '999000003', 'Mozo de Salón',     1200.00, '2026-01-01', 1),
(4, '70000004', 'Pedro',         'Cocinero', '999000004', 'Jefe de Cocina',    1800.00, '2026-01-01', 1)
ON DUPLICATE KEY UPDATE nombres = VALUES(nombres);

-- Hash BCrypt costo 12 generado para "Admin#2026!" ($2a$12$...) con cambio_password_requerido = 1 en admin
INSERT INTO usuario (
    id_usuario, id_empleado, id_rol, username, nombre_usuario, nombre_completo,
    password_hash, hash_password, codigo_rol, activo, intentos_fallidos, bloqueado_hasta, cambio_password_requerido
) VALUES
(1, 1, 1, 'admin',    'admin',    'Administrador General', '$2a$12$eZvgEUon4XWZNbUonBboAuOUQkoM6bRz4f1EdyZbd8K/ZfhhguepK', '$2a$12$eZvgEUon4XWZNbUonBboAuOUQkoM6bRz4f1EdyZbd8K/ZfhhguepK', 'ADMIN',  1, 0, NULL, 1),
(2, 2, 2, 'cajero',   'cajero',   'Maria Cajera',          '$2a$12$eZvgEUon4XWZNbUonBboAuOUQkoM6bRz4f1EdyZbd8K/ZfhhguepK', '$2a$12$eZvgEUon4XWZNbUonBboAuOUQkoM6bRz4f1EdyZbd8K/ZfhhguepK', 'CAJERO', 1, 0, NULL, 0),
(3, 3, 3, 'mozo',     'mozo',     'Carlos Mozo',           '$2a$12$eZvgEUon4XWZNbUonBboAuOUQkoM6bRz4f1EdyZbd8K/ZfhhguepK', '$2a$12$eZvgEUon4XWZNbUonBboAuOUQkoM6bRz4f1EdyZbd8K/ZfhhguepK', 'MOZO',   1, 0, NULL, 0),
(4, 4, 4, 'cocinero', 'cocinero', 'Pedro Cocinero',        '$2a$12$eZvgEUon4XWZNbUonBboAuOUQkoM6bRz4f1EdyZbd8K/ZfhhguepK', '$2a$12$eZvgEUon4XWZNbUonBboAuOUQkoM6bRz4f1EdyZbd8K/ZfhhguepK', 'COCINA', 1, 0, NULL, 0)
ON DUPLICATE KEY UPDATE cambio_password_requerido = VALUES(cambio_password_requerido);
