package com.foodcore.model;

import java.math.BigDecimal;

/**
 * Fila editable de Toma de Inventario Físico con stock teórico, stock contado físico y diferencia calculada.
 */
public class ItemInventarioFisico {

    private long idInsumo;
    private String codigo;
    private String nombre;
    private String unidadMedida;
    private BigDecimal stockSistema;
    private BigDecimal stockContado;
    private BigDecimal costoUnitario;
    private String observacion;

    public ItemInventarioFisico() {
    }

    public ItemInventarioFisico(long idInsumo,
                                String codigo,
                                String nombre,
                                String unidadMedida,
                                BigDecimal stockSistema,
                                BigDecimal stockContado,
                                BigDecimal costoUnitario) {
        this.idInsumo = idInsumo;
        this.codigo = codigo;
        this.nombre = nombre;
        this.unidadMedida = unidadMedida;
        this.stockSistema = stockSistema != null
            ? stockSistema.setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO)
            : BigDecimal.ZERO.setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO);
        this.stockContado = stockContado != null
            ? stockContado.setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO)
            : this.stockSistema;
        this.costoUnitario = costoUnitario != null ? costoUnitario : BigDecimal.ZERO;
    }

    public long getIdInsumo() {
        return idInsumo;
    }

    public void setIdInsumo(long idInsumo) {
        this.idInsumo = idInsumo;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getUnidadMedida() {
        return unidadMedida;
    }

    public void setUnidadMedida(String unidadMedida) {
        this.unidadMedida = unidadMedida;
    }

    public BigDecimal getStockSistema() {
        return stockSistema;
    }

    public void setStockSistema(BigDecimal stockSistema) {
        this.stockSistema = stockSistema != null
            ? stockSistema.setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO)
            : BigDecimal.ZERO.setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO);
    }

    public BigDecimal getStockContado() {
        return stockContado;
    }

    public void setStockContado(BigDecimal stockContado) {
        this.stockContado = stockContado != null
            ? stockContado.setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO)
            : BigDecimal.ZERO.setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO);
    }

    public BigDecimal getCostoUnitario() {
        return costoUnitario;
    }

    public void setCostoUnitario(BigDecimal costoUnitario) {
        this.costoUnitario = costoUnitario;
    }

    public String getObservacion() {
        return observacion;
    }

    public void setObservacion(String observacion) {
        this.observacion = observacion;
    }

    public BigDecimal getDiferencia() {
        BigDecimal sys = stockSistema != null ? stockSistema : BigDecimal.ZERO;
        BigDecimal cnt = stockContado != null ? stockContado : sys;
        return cnt.subtract(sys).setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO);
    }

    public boolean tieneDiferencia() {
        return getDiferencia().compareTo(BigDecimal.ZERO) != 0;
    }
}
