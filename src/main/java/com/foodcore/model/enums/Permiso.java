package com.foodcore.model.enums;

/**
 * Permisos granulares RBAC definidos en SPEC-FOODCORE-v1.0 (§10.3) y SPEC-FOODCORE-v1.2 (§3.2).
 */
public enum Permiso {
    VENTA_COBRAR("Autoriza el cobro de comandas y ventas POS"),
    VENTA_ANULAR("Autoriza la anulación auditada de una venta registrada"),
    VENTA_DESCUENTO("Autoriza aplicar descuentos porcentuales o de monto fijo en ventas POS"),
    PEDIDO_CREAR("Autoriza abrir una mesa y crear nuevos pedidos"),
    PEDIDO_ADICIONAR("Autoriza agregar ítems a pedidos abiertos o en preparación"),
    PEDIDO_ADICIONAR_POST_ENTREGA("Autoriza reabrir un pedido entregado para adición extraordinaria"),
    CAJA_APERTURA("Autoriza aperturar un turno de caja con fondo inicial"),
    CAJA_CIERRE("Autoriza ejecutar el arqueo físico y cierre Z del turno"),
    CAJA_MOVIMIENTO_MANUAL("Autoriza registrar ingresos o egresos manuales en caja"),
    KDS_OPERAR("Autoriza transicionar estados de preparación en la pantalla de cocina (KDS)"),
    TICKET_REIMPRIMIR("Autoriza emitir una copia marcada como REIMPRESIÓN de un ticket"),
    USUARIO_GESTIONAR("Autoriza administrar usuarios, roles y desbloqueos"),
    INSUMO_GESTIONAR("Autoriza administrar el catálogo de insumos, recetas y ajustes de stock"),
    KARDEX_CONSULTAR("Autoriza consultar el libro mayor inmutable de movimientos de Kárdex"),
    CONFIG_RED("Autoriza configurar terminales y parámetros de red LAN");

    private final String descripcion;

    Permiso(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
