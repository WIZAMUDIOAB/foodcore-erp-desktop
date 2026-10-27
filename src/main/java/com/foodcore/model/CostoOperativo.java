package com.foodcore.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class CostoOperativo {
    private int idCosto;
    private String concepto;
    private String categoriaCosto; // LUZ, AGUA, ALQUILER, MANTENIMIENTO, OTROS
    private BigDecimal monto;
    private LocalDate fechaRegistro;
    private int idUsuario;

    public CostoOperativo() {}

    public CostoOperativo(int idCosto, String concepto, String categoriaCosto, BigDecimal monto, LocalDate fechaRegistro, int idUsuario) {
        this.idCosto = idCosto;
        this.concepto = concepto;
        this.categoriaCosto = categoriaCosto;
        this.monto = monto;
        this.fechaRegistro = fechaRegistro;
        this.idUsuario = idUsuario;
    }

    public int getIdCosto() { return idCosto; }
    public void setIdCosto(int idCosto) { this.idCosto = idCosto; }

    public String getConcepto() { return concepto; }
    public void setConcepto(String concepto) { this.concepto = concepto; }

    public String getCategoriaCosto() { return categoriaCosto; }
    public void setCategoriaCosto(String categoriaCosto) { this.categoriaCosto = categoriaCosto; }

    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }

    public LocalDate getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(LocalDate fechaRegistro) { this.fechaRegistro = fechaRegistro; }

    public int getIdUsuario() { return idUsuario; }
    public void setIdUsuario(int idUsuario) { this.idUsuario = idUsuario; }
}
