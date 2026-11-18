package com.foodcore.exception;

/**
 * Catálogo tipado de códigos de error de negocio según SPEC-FOODCORE-v1.0 (§4.2, §18.6)
 * y REMEDIATION-PLAN-FOODCORE-v1.0 (R-11, R-18).
 */
public enum CodigoError {
    REGLA_NEGOCIO_GENERAL("ERR-000", "Violación general de regla de negocio"),
    STOCK_INSUFICIENTE("ERR-101", "Stock disponible insuficiente para cubrir la receta"),
    INSUMO_NO_ENCONTRADO("ERR-102", "El insumo requerido en la receta no existe en almacén"),
    CAJA_CERRADA("ERR-201", "No existe un turno de caja abierto para operar"),
    CONFLICTO_ESTADO("ERR-301", "Conflicto de concurrencia o transición de estado inválida"),
    PERIODO_CERRADO("ERR-302", "El período contable mensual ya se encuentra cerrado"),
    TERMINAL_NO_AUTORIZADA("ERR-401", "Terminal de red no autorizada o firma HMAC inválida"),
    USUARIO_BLOQUEADO("ERR-402", "Cuenta de usuario temporalmente bloqueada por intentos fallidos"),
    ACCESO_DENEGADO("ERR-403", "El usuario no posee el permiso requerido (RBAC)"),
    RECURSO_NO_ENCONTRADO("ERR-404", "El recurso solicitado no existe"),
    CREDENCIALES_INVALIDAS("ERR-405", "Usuario o contraseña inválidos"),
    CAMBIO_PASSWORD_REQUERIDO("ERR-409", "El usuario debe cambiar su contraseña temporal antes de operar"),
    SESION_EXPIRADA("ERR-440", "La sesión del usuario ha expirado por inactividad"),
    VALIDACION_ENTRADA("ERR-422", "Datos de entrada inválidos"),
    VALIDACION_FISCAL("ERR-501", "Incumplimiento de invariante aritmético o fiscal"),
    ERROR_PERSISTENCIA("ERR-800", "Fallo en operación de persistencia JDBC"),
    ERROR_TRANSACCIONAL("ERR-900", "Fallo durante la ejecución de unidad de trabajo ACID");

    private final String codigo;
    private final String descripcionPorDefecto;

    CodigoError(String codigo, String descripcionPorDefecto) {
        this.codigo = codigo;
        this.descripcionPorDefecto = descripcionPorDefecto;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getDescripcionPorDefecto() {
        return descripcionPorDefecto;
    }
}
