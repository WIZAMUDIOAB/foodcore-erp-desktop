package com.foodcore.model;

import com.foodcore.util.UnidadMedidaFormatter;

import java.math.BigDecimal;

/**
 * Modelo de dominio de Insumo alineado al DDL de SPEC-FOODCORE-v1.0 (§5, R-06) y SPEC-FOODCORE-v1.2 (§1.1, §2.1):
 * {@code id_insumo BIGINT}, {@code unidad_medida ENUM('UNIDAD','KG','GR','ML','LT')} y {@code stock_actual_disponible DECIMAL(14,4)}.
 */
public class Insumo {
    private long idInsumo;
    private String codigo;
    private String nombre;
    private String unidadMedida; // UNIDAD, KG, GR, ML, LT
    private BigDecimal stockActualDisponible;
    private BigDecimal stockMinimo;
    private BigDecimal costoUnitario;
    private boolean activo = true;
    private int version = 0;

    public Insumo() {}

    public Insumo(long idInsumo, String nombre, String unidadMedida,
                  BigDecimal stockActualDisponible, BigDecimal stockMinimo, BigDecimal costoUnitario) {
        this(idInsumo, null, nombre, unidadMedida, stockActualDisponible, stockMinimo, costoUnitario);
    }

    public Insumo(long idInsumo, String codigo, String nombre, String unidadMedida,
                  BigDecimal stockActualDisponible, BigDecimal stockMinimo, BigDecimal costoUnitario) {
        this.idInsumo = idInsumo;
        this.codigo = codigo;
        this.nombre = nombre;
        this.unidadMedida = unidadMedida;
        this.stockActualDisponible = stockActualDisponible;
        this.stockMinimo = stockMinimo;
        this.costoUnitario = costoUnitario;
    }

    public long getIdInsumo() { return idInsumo; }
    public void setIdInsumo(long idInsumo) { this.idInsumo = idInsumo; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getUnidadMedida() { return unidadMedida; }
    public void setUnidadMedida(String unidadMedida) { this.unidadMedida = unidadMedida; }

    public BigDecimal getStockActualDisponible() { return stockActualDisponible; }
    public void setStockActualDisponible(BigDecimal stockActualDisponible) { this.stockActualDisponible = stockActualDisponible; }

    public BigDecimal getStockActual() { return stockActualDisponible; }
    public void setStockActual(BigDecimal stockActual) { this.stockActualDisponible = stockActual; }

    public BigDecimal getStockMinimo() { return stockMinimo; }
    public void setStockMinimo(BigDecimal stockMinimo) { this.stockMinimo = stockMinimo; }

    public BigDecimal getCostoUnitario() { return costoUnitario; }
    public void setCostoUnitario(BigDecimal costoUnitario) { this.costoUnitario = costoUnitario; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    /**
     * Semáforo de alerta (§2.1): devuelve {@code true} si {@code stock_actual <= stock_minimo}.
     */
    public boolean requiereReabastecimiento() {
        if (stockActualDisponible == null || stockMinimo == null) {
            return false;
        }
        return stockActualDisponible.compareTo(stockMinimo) <= 0;
    }

    @Override
    public String toString() {
        return nombre + " [" + UnidadMedidaFormatter.formatear(stockActualDisponible, unidadMedida) + "]";
    }
}
