-- ============================================================================
-- FOODCORE ERP v4.2.0 - SUB-FASE C.1: MODO MOZO Y SEGURIDAD PIN
-- Migración V12: Autenticación por PIN de 4 Dígitos y Auditoría de Creador
-- ============================================================================

-- 1. Columna de hash criptográfico BCrypt para PIN de acceso rápido en tablets
ALTER TABLE usuario
    ADD COLUMN pin_hash VARCHAR(60) NULL;

-- 2. Columna para auditoría y trazabilidad del mozo/operador creador de la comanda
ALTER TABLE pedido
    ADD COLUMN id_usuario_creador INT NULL;

-- 3. Configurar PINs iniciales con hash BCrypt (costo 12) para usuarios operativos
-- PIN "1234" para mozo (y mozo1), cajero y cocina
UPDATE usuario
SET pin_hash = '$2a$12$2RZFZzZjZXIYB8jWU3JpoO/uo4ga733mPW4enX/V68qyYW0rHXKsa',
    pin = '1234'
WHERE username IN ('mozo', 'mozo1', 'cajero', 'cajero1', 'cocina', 'cocinero', 'cocina1');

-- PIN "9999" para administrador
UPDATE usuario
SET pin_hash = '$2a$12$JFii7tvJWtK9aSOPGmHdrOA/lBnHzRAhOwQzxHopH9xSCQJBGTNT2',
    pin = '9999'
WHERE username = 'admin';

-- 4. Asignar id_usuario_creador en pedidos existentes para consistencia histórica
UPDATE pedido
SET id_usuario_creador = id_usuario
WHERE id_usuario_creador IS NULL;

-- 5. Índice para optimizar consultas de auditoría por mozo creador
CREATE INDEX idx_pedido_usuario_creador ON pedido (id_usuario_creador);
