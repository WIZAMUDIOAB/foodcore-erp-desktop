package com.foodcore.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

public class HistorialPrecio {
    private static final int ESCALA_PRECIO = 4;
    private static final int ESCALA_VARIACION = 2;
    private static final BigDecimal CERO_PRECIO = BigDecimal.ZERO.setScale(ESCALA_PRECIO, RoundingMode.HALF_UP);
    private static final BigDecimal CIEN = new BigDecimal("100");

    private int idHistorial;
    private String tipoEntidad; // PRODUCTO o INSUMO
    private int idEntidad;
    private String nombreEntidad;
    private BigDecimal precioAnterior = CERO_PRECIO;
    private BigDecimal precioNuevo = CERO_PRECIO;
    private int idUsuario;
    private String nombreUsuario;
    private String motivo;
    private LocalDateTime fechaCambio;

    public HistorialPrecio() {
    }

    public BigDecimal getVariacionPorcentual() {
        if (precioAnterior == null || precioAnterior.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(ESCALA_VARIACION, RoundingMode.HALF_UP);
        }
        return getPrecioNuevo()
                .subtract(precioAnterior)
                .multiply(CIEN)
                .divide(precioAnterior, ESCALA_VARIACION, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalizarPrecio(BigDecimal valor) {
        return valor != null ? valor.setScale(ESCALA_PRECIO, RoundingMode.HALF_UP) : CERO_PRECIO;
    }

    public int getIdHistorial() {
        return idHistorial;
    }

    public void setIdHistorial(int idHistorial) {
        this.idHistorial = idHistorial;
    }

    public String getTipoEntidad() {
        return tipoEntidad;
    }

    public void setTipoEntidad(String tipoEntidad) {
        this.tipoEntidad = tipoEntidad;
    }

    public int getIdEntidad() {
        return idEntidad;
    }

    public void setIdEntidad(int idEntidad) {
        this.idEntidad = idEntidad;
    }

    public String getNombreEntidad() {
        return nombreEntidad;
    }

    public void setNombreEntidad(String nombreEntidad) {
        this.nombreEntidad = nombreEntidad;
    }

    public BigDecimal getPrecioAnterior() {
        return precioAnterior;
    }

    public void setPrecioAnterior(BigDecimal precioAnterior) {
        this.precioAnterior = normalizarPrecio(precioAnterior);
    }

    public BigDecimal getPrecioNuevo() {
        return precioNuevo;
    }

    public void setPrecioNuevo(BigDecimal precioNuevo) {
        this.precioNuevo = normalizarPrecio(precioNuevo);
    }

    public int getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(int idUsuario) {
        this.idUsuario = idUsuario;
    }

    public String getNombreUsuario() {
        return nombreUsuario;
    }

    public void setNombreUsuario(String nombreUsuario) {
        this.nombreUsuario = nombreUsuario;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public LocalDateTime getFechaCambio() {
        return fechaCambio;
    }

    public void setFechaCambio(LocalDateTime fechaCambio) {
        this.fechaCambio = fechaCambio;
    }
}
