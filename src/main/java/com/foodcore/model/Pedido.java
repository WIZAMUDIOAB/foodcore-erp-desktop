package com.foodcore.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Pedido {
    private int idPedido;
    private int idUsuario;
    private Integer idCliente;
    private String numeroMesa;
    private LocalDateTime fechaHora;
    private String estado; // REGISTRADO, EN_PREPARACION, LISTO, ENTREGADO, COBRADO, ANULADO
    private BigDecimal total;

    private List<DetallePedido> detalles = new ArrayList<>();

    public Pedido() {}

    public Pedido(int idPedido, int idUsuario, Integer idCliente, String numeroMesa, String estado, BigDecimal total) {
        this.idPedido = idPedido;
        this.idUsuario = idUsuario;
        this.idCliente = idCliente;
        this.numeroMesa = numeroMesa;
        this.estado = estado;
        this.total = total;
        this.fechaHora = LocalDateTime.now();
    }

    public int getIdPedido() { return idPedido; }
    public void setIdPedido(int idPedido) { this.idPedido = idPedido; }

    public int getIdUsuario() { return idUsuario; }
    public void setIdUsuario(int idUsuario) { this.idUsuario = idUsuario; }

    public Integer getIdCliente() { return idCliente; }
    public void setIdCliente(Integer idCliente) { this.idCliente = idCliente; }

    public String getNumeroMesa() { return numeroMesa; }
    public void setNumeroMesa(String numeroMesa) { this.numeroMesa = numeroMesa; }

    public LocalDateTime getFechaHora() { return fechaHora; }
    public void setFechaHora(LocalDateTime fechaHora) { this.fechaHora = fechaHora; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public List<DetallePedido> getDetalles() { return detalles; }
    public void setDetalles(List<DetallePedido> detalles) { this.detalles = detalles; }

    @Override
    public String toString() { return "Pedido #" + idPedido + " - Mesa " + numeroMesa + " (" + estado + ")"; }
}
