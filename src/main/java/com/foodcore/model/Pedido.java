package com.foodcore.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entidad de Pedido alineada a SPEC-FOODCORE-v1.0 (§5, §6.2; REMEDIATION R-07).
 * Sin campos redundantes de marca temporal; incluye {@code idempotencyKey} y {@code version}.
 */
public class Pedido {
    public static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    private int idPedido;
    private Integer idMesa;
    private int idUsuario;
    private Integer idCliente;
    private String numeroMesa;
    private LocalDateTime fechaHora;
    private String estado; // ABIERTO, REGISTRADO, EN_PREPARACION, LISTO, ENTREGADO, POR_COBRAR, CERRADO, COBRADO, ANULADO
    private BigDecimal total;
    private String idempotencyKey = UUID.randomUUID().toString();
    private int version = 0;

    private List<DetallePedido> detalles = new ArrayList<>();

    public Pedido() {
        this.fechaHora = LocalDateTime.now(ZONA_LIMA);
    }

    public Pedido(int idPedido, int idUsuario, Integer idCliente, String numeroMesa, String estado, BigDecimal total) {
        this.idPedido = idPedido;
        this.idUsuario = idUsuario;
        this.idCliente = idCliente;
        this.numeroMesa = numeroMesa;
        this.estado = estado;
        this.total = total;
        this.fechaHora = LocalDateTime.now(ZONA_LIMA);
        this.idempotencyKey = UUID.randomUUID().toString();
    }

    public int getIdPedido() { return idPedido; }
    public void setIdPedido(int idPedido) { this.idPedido = idPedido; }

    public Integer getIdMesa() { return idMesa; }
    public void setIdMesa(Integer idMesa) { this.idMesa = idMesa; }

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

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = (idempotencyKey == null || idempotencyKey.isBlank())
            ? UUID.randomUUID().toString()
            : idempotencyKey.trim();
    }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public List<DetallePedido> getDetalles() { return detalles; }
    public void setDetalles(List<DetallePedido> detalles) { this.detalles = detalles; }

    @Override
    public String toString() { return "Pedido #" + idPedido + " - Mesa " + numeroMesa + " (" + estado + ")"; }
}
