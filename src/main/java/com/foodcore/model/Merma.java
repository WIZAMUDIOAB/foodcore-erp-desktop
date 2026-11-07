package com.foodcore.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Merma {

    private int idMerma;
    private long idInsumo;
    private String nombreInsumo;
    private String unidadMedida;
    private int idUsuario;
    private String nombreUsuario;
    private BigDecimal cantidad = BigDecimal.ZERO;
    private String motivo = "Quemado"; // 'Vencido', 'Quemado', 'Roto', 'Error de cocina', 'Sobrante', 'Otro'
    private BigDecimal costoUnitario = BigDecimal.ZERO;
    private BigDecimal costoTotal = BigDecimal.ZERO;
    private String observacion;
    private LocalDateTime fechaHora = LocalDateTime.now();

    public Merma() {
    }

    public Merma(long idInsumo, int idUsuario, BigDecimal cantidad, String motivo, BigDecimal costoUnitario, String observacion) {
        this.idInsumo = idInsumo;
        this.idUsuario = idUsuario;
        this.cantidad = cantidad;
        this.motivo = motivo;
        this.costoUnitario = costoUnitario;
        this.costoTotal = cantidad.multiply(costoUnitario);
        this.observacion = observacion;
    }

    public int getIdMerma() {
        return idMerma;
    }

    public void setIdMerma(int idMerma) {
        this.idMerma = idMerma;
    }

    public long getIdInsumo() {
        return idInsumo;
    }

    public void setIdInsumo(long idInsumo) {
        this.idInsumo = idInsumo;
    }

    public String getNombreInsumo() {
        return nombreInsumo;
    }

    public void setNombreInsumo(String nombreInsumo) {
        this.nombreInsumo = nombreInsumo;
    }

    public String getUnidadMedida() {
        return unidadMedida;
    }

    public void setUnidadMedida(String unidadMedida) {
        this.unidadMedida = unidadMedida;
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

    public BigDecimal getCantidad() {
        return cantidad;
    }

    public void setCantidad(BigDecimal cantidad) {
        this.cantidad = cantidad;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public BigDecimal getCostoUnitario() {
        return costoUnitario;
    }

    public void setCostoUnitario(BigDecimal costoUnitario) {
        this.costoUnitario = costoUnitario;
    }

    public BigDecimal getCostoTotal() {
        return costoTotal;
    }

    public void setCostoTotal(BigDecimal costoTotal) {
        this.costoTotal = costoTotal;
    }

    public String getObservacion() {
        return observacion;
    }

    public void setObservacion(String observacion) {
        this.observacion = observacion;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }

    public String getFechaTexto() {
        return fechaHora != null ? fechaHora.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "-";
    }
}
