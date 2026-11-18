-- ============================================================================
-- V22: Seguridad y Estabilidad (v5.1.0 - Sub-fase A)
-- 1) Campos 2FA TOTP e invalidación masiva de sesiones JWT en tabla usuario.
-- 2) Tabla token_blacklist para revocación explícita de tokens JTI.
-- ============================================================================

ALTER TABLE usuario
    ADD COLUMN totp_secret VARCHAR(64) NULL COMMENT 'Secreto TOTP Base32 para 2FA de usuarios ADMIN',
    ADD COLUMN totp_habilitado TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1 si 2FA TOTP está activo para el usuario',
    ADD COLUMN tokens_invalidados_antes BIGINT NOT NULL DEFAULT 0 COMMENT 'Epoch ms: cualquier JWT emitido <= este valor queda revocado';

CREATE TABLE IF NOT EXISTS token_blacklist (
    id_blacklist BIGINT AUTO_INCREMENT PRIMARY KEY,
    token_jti VARCHAR(64) NOT NULL,
    id_usuario INT NOT NULL,
    revocado_por INT NULL,
    motivo VARCHAR(160) NULL,
    expira_en TIMESTAMP NULL,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_token_blacklist_jti (token_jti),
    KEY idx_token_blacklist_usuario (id_usuario),
    KEY idx_token_blacklist_expira (expira_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
