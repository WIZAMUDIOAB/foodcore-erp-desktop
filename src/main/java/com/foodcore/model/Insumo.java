package com.foodcore.model;

import java.math.BigDecimal;

public class Insumo {
    private int idInsumo;
    private String codigo;
    private String nombre;
    private String unidadMedida; // GR, UND, ML, KG, LT
    private BigDecimal stockActual;
    private BigDecimal stockMinimo;
    private BigDecimal costoUnitario;

    public Insumo() {}

    public Insumo(int idInsumo, String codigo, String nombre, String unidadMedida, BigDecimal stockActual, BigDecimal stockMinimo, BigDecimal costoUnitario) {
        this.idInsumo = idInsumo;
        this.codigo = codigo;
        this.nombre = nombre;
        this.unidadMedida = unidadMedida;
        this.stockActual = stockActual;
        this.stockMinimo = stockMinimo;
        this.costoUnitario = costoUnitario;
    }

    public int getIdInsumo() { return idInsumo; }
    public void setIdInsumo(int idInsumo) { this.idInsumo = idInsumo; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getUnidadMedida() { return unidadMedida; }
    public void setUnidadMedida(String unidadMedida) { this.unidadMedida = unidadMedida; }

    public BigDecimal getStockActual() { return stockActual; }
    public void setStockActual(BigDecimal stockActual) { this.stockActual = stockActual; }

    public BigDecimal getStockMinimo() { return stockMinimo; }
    public void setStockMinimo(BigDecimal stockMinimo) { this.stockMinimo = stockMinimo; }

    public BigDecimal getCostoUnitario() { return costoUnitario; }
    public void setCostoUnitario(BigDecimal costoUnitario) { this.costoUnitario = costoUnitario; }

    @Override
    public String toString() { return nombre + " [" + stockActual + " " + unidadMedida + "]"; }
}
