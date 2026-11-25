package com.foodcore.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.Pedido;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Carga útil de sincronización offline: un {@link Pedido} cabecera junto con sus detalles.
 * Se transporta en el {@code payload} del frame de socket ({@link SocketFrameDTO}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OfflineOrderPayloadDTO {

    private Pedido pedido;
    private List<DetallePedido> detalles;

    public OfflineOrderPayloadDTO() {
        this.detalles = new ArrayList<>();
    }

    public OfflineOrderPayloadDTO(Pedido pedido, List<DetallePedido> detalles) {
        this.pedido = pedido;
        this.detalles = detalles != null ? new ArrayList<>(detalles) : new ArrayList<>();
    }

    public Pedido getPedido() {
        return pedido;
    }

    public void setPedido(Pedido pedido) {
        this.pedido = pedido;
    }

    public List<DetallePedido> getDetalles() {
        return Collections.unmodifiableList(detalles);
    }

    public void setDetalles(List<DetallePedido> detalles) {
        this.detalles = detalles != null ? new ArrayList<>(detalles) : new ArrayList<>();
    }
}
