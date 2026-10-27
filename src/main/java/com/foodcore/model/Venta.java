package com.foodcore.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Venta {
    private int idVenta;
    private Integer idPedido;
    private int idCaja;
    private int idUsuario;
    private Integer idCliente;
    private LocalDateTime fechaHora;
    private BigDecimal subtotal;
    private BigDecimal igv;
    private BigDecimal total;
    private String metodoPago; // EFECTIVO, TARJETA, YAPE_PLIN
    private BigDecimal montoRecibido;
    private BigDecimal vuelto;
    private String estado; // COMPLETADA, ANULADA

    private List<DetalleVenta> detalles = new ArrayList<>();

    public Venta() {}

    public Venta(int idVenta, Integer idPedido, int idCaja, int idUsuario, Integer idCliente, BigDecimal subtotal, BigDecimal igv, BigDecimal total, String metodoPago, BigDecimal montoRecibido, BigDecimal vuelto) {
        this.idVenta = idVenta;
        this.idPedido = idPedido;
        this.idCaja = idCaja;
        this.idUsuario = idUsuario;
        this.idCliente = idCliente;
        this.subtotal = subtotal;
        this.igv = igv;
        this.total = total;
        this.metodoPago = metodoPago;
        this.montoRecibido = montoRecibido;
        this.vuelto = vuelto;
        this.estado = "COMPLETADA";
        this.fechaHora = LocalDateTime.now();
    }

    public int getIdVenta() { return idVenta; }
    public void setIdVenta(int idVenta) { this.idVenta = idVenta; }

    public Integer getIdPedido() { return idPedido; }
    public void setIdPedido(Integer idPedido) { this.idPedido = idPedido; }

    public int getIdCaja() { return idCaja; }
    public void setIdCaja(int idCaja) { this.idCaja = idCaja; }

    public int getIdUsuario() { return idUsuario; }
    public void setIdUsuario(int idUsuario) { this.idUsuario = idUsuario; }

    public Integer getIdCliente() { return idCliente; }
    public void setIdCliente(Integer idCliente) { this.idCliente = idCliente; }

    public LocalDateTime getFechaHora() { return fechaHora; }
    public void setFechaHora(LocalDateTime fechaHora) { this.fechaHora = fechaHora; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getIgv() { return igv; }
    public void setIgv(BigDecimal igv) { this.igv = igv; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public String getMetodoPago() { return metodoPago; }
    public void setMetodoPago(String metodoPago) { this.metodoPago = metodoPago; }

    public BigDecimal getMontoRecibido() { return montoRecibido; }
    public void setMontoRecibido(BigDecimal montoRecibido) { this.montoRecibido = montoRecibido; }

    public BigDecimal getVuelto() { return vuelto; }
    public void setVuelto(BigDecimal vuelto) { this.vuelto = vuelto; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public List<DetalleVenta> getDetalles() { return detalles; }
    public void setDetalles(List<DetalleVenta> detalles) { this.detalles = detalles; }

    @Override
    public String toString() { return "Venta #" + idVenta + " - Total: S/ " + total; }
}
