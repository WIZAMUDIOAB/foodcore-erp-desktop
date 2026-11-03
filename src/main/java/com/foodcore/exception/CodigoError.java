package com.foodcore.exception;

/**
 * Catálogo tipado de códigos de error de negocio según SPEC-FOODCORE-v1.0 (§4.2, §18.6).
 */
public enum CodigoError {
    REGLA_NEGOCIO_GENERAL("ERR-000", "Violación general de regla de negocio"),
    STOCK_INSUFICIENTE("ERR-101", "Stock disponible insuficiente para cubrir la receta"),
    INSUMO_NO_ENCONTRADO("ERR-102", "El insumo requerido en la receta no existe en almacén"),
    CAJA_CERRADA("ERR-201", "No existe un turno de caja abierto para operar"),
    CONFLICTO_ESTADO("ERR-301", "Conflicto de concurrencia o transición de estado inválida"),
    TERMINAL_NO_AUTORIZADA("ERR-401", "Terminal de red no autorizada o firma HMAC inválida"),
    ACCESO_DENEGADO("ERR-403", "El usuario no posee el permiso requerido (RBAC)"),
    VALIDACION_FISCAL("ERR-501", "Incumplimiento de invariante aritmético o fiscal"),
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
