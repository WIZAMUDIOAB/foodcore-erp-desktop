package com.foodcore.model;

import java.math.BigDecimal;

public class Receta {
    private int idReceta;
    private int idProducto;
    private int idInsumo;
    private BigDecimal cantidadNecesaria;

    // Campos descriptivos
    private String nombreProducto;
    private String nombreInsumo;
    private String unidadMedidaInsumo;

    public Receta() {}

    public Receta(int idReceta, int idProducto, int idInsumo, BigDecimal cantidadNecesaria) {
        this.idReceta = idReceta;
        this.idProducto = idProducto;
        this.idInsumo = idInsumo;
        this.cantidadNecesaria = cantidadNecesaria;
    }

    public int getIdReceta() { return idReceta; }
    public void setIdReceta(int idReceta) { this.idReceta = idReceta; }

    public int getIdProducto() { return idProducto; }
    public void setIdProducto(int idProducto) { this.idProducto = idProducto; }

    public int getIdInsumo() { return idInsumo; }
    public void setIdInsumo(int idInsumo) { this.idInsumo = idInsumo; }

    public BigDecimal getCantidadNecesaria() { return cantidadNecesaria; }
    public void setCantidadNecesaria(BigDecimal cantidadNecesaria) { this.cantidadNecesaria = cantidadNecesaria; }

    public String getNombreProducto() { return nombreProducto; }
    public void setNombreProducto(String nombreProducto) { this.nombreProducto = nombreProducto; }

    public String getNombreInsumo() { return nombreInsumo; }
    public void setNombreInsumo(String nombreInsumo) { this.nombreInsumo = nombreInsumo; }

    public String getUnidadMedidaInsumo() { return unidadMedidaInsumo; }
    public void setUnidadMedidaInsumo(String unidadMedidaInsumo) { this.unidadMedidaInsumo = unidadMedidaInsumo; }

    @Override
    public String toString() {
        return nombreInsumo + " : " + cantidadNecesaria + " " + unidadMedidaInsumo;
    }
}
