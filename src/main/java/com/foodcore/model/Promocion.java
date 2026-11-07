package com.foodcore.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class Promocion {
    private int idPromocion;
    private String codigo;
    private String nombre;
    private String descripcion;
    private String tipoPromocion; // PORCENTAJE, MONTO_FIJO, DOS_POR_UNO, TRES_POR_DOS
    private BigDecimal valorDescuento;
    private String aplicaA; // PRODUCTO, CATEGORIA, TOTAL_ORDEN
    private Integer idProductoAplica;
    private String nombreProductoAplica;
    private Integer idCategoriaAplica;
    private String nombreCategoriaAplica;
    private BigDecimal montoMinimo;
    private String diasSemana; // LUN,MAR,MIE,JUE,VIE,SAB,DOM
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private boolean activo;
    private LocalDateTime fechaCreacion;

    public Promocion() {
        this.tipoPromocion = "PORCENTAJE";
        this.valorDescuento = BigDecimal.ZERO;
        this.aplicaA = "TOTAL_ORDEN";
        this.montoMinimo = BigDecimal.ZERO;
        this.diasSemana = "LUN,MAR,MIE,JUE,VIE,SAB,DOM";
        this.activo = true;
    }

    public Promocion(int idPromocion, String codigo, String nombre, String descripcion,
                     String tipoPromocion, BigDecimal valorDescuento, String aplicaA,
                     Integer idProductoAplica, Integer idCategoriaAplica,
                     BigDecimal montoMinimo, String diasSemana,
                     LocalTime horaInicio, LocalTime horaFin, boolean activo) {
        this.idPromocion = idPromocion;
        this.codigo = codigo;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.tipoPromocion = tipoPromocion;
        this.valorDescuento = valorDescuento != null ? valorDescuento : BigDecimal.ZERO;
        this.aplicaA = aplicaA != null ? aplicaA : "TOTAL_ORDEN";
        this.idProductoAplica = idProductoAplica;
        this.idCategoriaAplica = idCategoriaAplica;
        this.montoMinimo = montoMinimo != null ? montoMinimo : BigDecimal.ZERO;
        this.diasSemana = diasSemana != null ? diasSemana : "LUN,MAR,MIE,JUE,VIE,SAB,DOM";
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
        this.activo = activo;
    }

    public int getIdPromocion() {
        return idPromocion;
    }

    public void setIdPromocion(int idPromocion) {
        this.idPromocion = idPromocion;
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

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getTipoPromocion() {
        return tipoPromocion;
    }

    public void setTipoPromocion(String tipoPromocion) {
        this.tipoPromocion = tipoPromocion;
    }

    public BigDecimal getValorDescuento() {
        return valorDescuento;
    }

    public void setValorDescuento(BigDecimal valorDescuento) {
        this.valorDescuento = valorDescuento;
    }

    public String getAplicaA() {
        return aplicaA;
    }

    public void setAplicaA(String aplicaA) {
        this.aplicaA = aplicaA;
    }

    public Integer getIdProductoAplica() {
        return idProductoAplica;
    }

    public void setIdProductoAplica(Integer idProductoAplica) {
        this.idProductoAplica = idProductoAplica;
    }

    public String getNombreProductoAplica() {
        return nombreProductoAplica;
    }

    public void setNombreProductoAplica(String nombreProductoAplica) {
        this.nombreProductoAplica = nombreProductoAplica;
    }

    public Integer getIdCategoriaAplica() {
        return idCategoriaAplica;
    }

    public void setIdCategoriaAplica(Integer idCategoriaAplica) {
        this.idCategoriaAplica = idCategoriaAplica;
    }

    public String getNombreCategoriaAplica() {
        return nombreCategoriaAplica;
    }

    public void setNombreCategoriaAplica(String nombreCategoriaAplica) {
        this.nombreCategoriaAplica = nombreCategoriaAplica;
    }

    public BigDecimal getMontoMinimo() {
        return montoMinimo;
    }

    public void setMontoMinimo(BigDecimal montoMinimo) {
        this.montoMinimo = montoMinimo;
    }

    public String getDiasSemana() {
        return diasSemana;
    }

    public void setDiasSemana(String diasSemana) {
        this.diasSemana = diasSemana;
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public void setHoraInicio(LocalTime horaInicio) {
        this.horaInicio = horaInicio;
    }

    public LocalTime getHoraFin() {
        return horaFin;
    }

    public void setHoraFin(LocalTime horaFin) {
        this.horaFin = horaFin;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public String getEtiquetaTipo() {
        if ("DOS_POR_UNO".equalsIgnoreCase(tipoPromocion)) {
            return "2x1";
        }
        if ("TRES_POR_DOS".equalsIgnoreCase(tipoPromocion)) {
            return "3x2";
        }
        if ("MONTO_FIJO".equalsIgnoreCase(tipoPromocion)) {
            return "S/ " + valorDescuento + " OFF";
        }
        return valorDescuento + "% OFF";
    }
}
