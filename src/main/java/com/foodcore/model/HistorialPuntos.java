package com.foodcore.model;

import java.time.LocalDateTime;

/**
 * Movimiento registrado en la tabla {@code historial_puntos} (SPEC-FOODCORE-v5.0.0-CRM §2.2).
 */
public class HistorialPuntos {

    private long idHistorial;
    private int idCliente;
    private Integer idVenta;
    private int puntosGanados;
    private String tipoMovimiento; // ACUMULACION, CANJE
    private String descripcion;
    private LocalDateTime fecha;

    public HistorialPuntos() {
        this.tipoMovimiento = "ACUMULACION";
        this.fecha = LocalDateTime.now();
    }

    public HistorialPuntos(long idHistorial,
                           int idCliente,
                           Integer idVenta,
                           int puntosGanados,
                           String tipoMovimiento,
                           String descripcion,
                           LocalDateTime fecha) {
        this.idHistorial = idHistorial;
        this.idCliente = idCliente;
        this.idVenta = idVenta;
        this.puntosGanados = puntosGanados;
        this.tipoMovimiento = tipoMovimiento != null ? tipoMovimiento : "ACUMULACION";
        this.descripcion = descripcion;
        this.fecha = fecha != null ? fecha : LocalDateTime.now();
    }

    public long getIdHistorial() {
        return idHistorial;
    }

    public void setIdHistorial(long idHistorial) {
        this.idHistorial = idHistorial;
    }

    public int getIdCliente() {
        return idCliente;
    }

    public void setIdCliente(int idCliente) {
        this.idCliente = idCliente;
    }

    public Integer getIdVenta() {
        return idVenta;
    }

    public void setIdVenta(Integer idVenta) {
        this.idVenta = idVenta;
    }

    public int getPuntosGanados() {
        return puntosGanados;
    }

    public void setPuntosGanados(int puntosGanados) {
        this.puntosGanados = puntosGanados;
    }

    public String getTipoMovimiento() {
        return tipoMovimiento;
    }

    public void setTipoMovimiento(String tipoMovimiento) {
        this.tipoMovimiento = tipoMovimiento;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }
}
