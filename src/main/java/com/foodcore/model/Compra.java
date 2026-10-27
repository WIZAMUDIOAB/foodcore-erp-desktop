package com.foodcore.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Compra {
    private int idCompra;
    private int idProveedor;
    private int idUsuario;
    private LocalDateTime fechaCompra;
    private String tipoComprobante;
    private String numeroComprobante;
    private BigDecimal total;
    private String estado; // REGISTRADA, ANULADA

    private List<DetalleCompra> detalles = new ArrayList<>();

    public Compra() {}

    public Compra(int idCompra, int idProveedor, int idUsuario, String tipoComprobante, String numeroComprobante, BigDecimal total, String estado) {
        this.idCompra = idCompra;
        this.idProveedor = idProveedor;
        this.idUsuario = idUsuario;
        this.tipoComprobante = tipoComprobante;
        this.numeroComprobante = numeroComprobante;
        this.total = total;
        this.estado = estado;
        this.fechaCompra = LocalDateTime.now();
    }

    public int getIdCompra() { return idCompra; }
    public void setIdCompra(int idCompra) { this.idCompra = idCompra; }

    public int getIdProveedor() { return idProveedor; }
    public void setIdProveedor(int idProveedor) { this.idProveedor = idProveedor; }

    public int getIdUsuario() { return idUsuario; }
    public void setIdUsuario(int idUsuario) { this.idUsuario = idUsuario; }

    public LocalDateTime getFechaCompra() { return fechaCompra; }
    public void setFechaCompra(LocalDateTime fechaCompra) { this.fechaCompra = fechaCompra; }

    public String getTipoComprobante() { return tipoComprobante; }
    public void setTipoComprobante(String tipoComprobante) { this.tipoComprobante = tipoComprobante; }

    public String getNumeroComprobante() { return numeroComprobante; }
    public void setNumeroComprobante(String numeroComprobante) { this.numeroComprobante = numeroComprobante; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public List<DetalleCompra> getDetalles() { return detalles; }
    public void setDetalles(List<DetalleCompra> detalles) { this.detalles = detalles; }
}
