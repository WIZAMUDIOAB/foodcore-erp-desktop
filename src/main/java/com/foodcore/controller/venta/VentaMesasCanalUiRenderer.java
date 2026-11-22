package com.foodcore.controller.venta;

import com.foodcore.model.Pedido;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

public class VentaMesasCanalUiRenderer {
    public void renderizarGridMesasPos(TilePane tileMesasPos, Label lblResumenMesasPos,
                                      Map<String, Pedido> pedidosPorMesa, int totalMesas,
                                      Consumer<String> onSeleccionarMesa) {
        if (tileMesasPos == null) return;
        tileMesasPos.getChildren().clear();
        int ocupadas = 0;
        LocalDateTime ahora = LocalDateTime.now(ZoneId.of("America/Lima"));
        for (int i = 1; i <= totalMesas; i++) {
            String mesa = String.format("%02d", i);
            Pedido pedido = pedidosPorMesa == null ? null : pedidosPorMesa.get(mesa);
            boolean ocupada = pedido != null;
            if (ocupada) ocupadas++;
            VBox card = new VBox(6.0);
            card.setPrefSize(158, 118);
            card.setMinSize(148, 110);
            card.getStyleClass().add(ocupada ? "pos-mesa-card-ocupada" : "pos-mesa-card-libre");
            card.setId("posMesaCard_" + mesa);
            HBox header = new HBox(6.0);
            header.setAlignment(Pos.CENTER_LEFT);
            Label nombre = new Label("Mesa " + mesa);
            nombre.getStyleClass().add("text-body-strong");
            Region espacio = new Region();
            HBox.setHgrow(espacio, Priority.ALWAYS);
            Label badge = new Label(ocupada ? "OCUPADA" : "LIBRE");
            badge.getStyleClass().add(ocupada ? "badge-pill-warning" : "badge-pill-success");
            header.getChildren().addAll(nombre, espacio, badge);
            BigDecimal monto = ocupada && pedido.getTotal() != null ? pedido.getTotal() : BigDecimal.ZERO;
            Label total = new Label("S/ " + monto.setScale(2, RoundingMode.HALF_UP));
            total.getStyleClass().add(ocupada ? "text-mono-accent" : "text-mono");
            long minutos = ocupada && pedido.getFechaHora() != null
                    ? Math.max(0, ChronoUnit.MINUTES.between(pedido.getFechaHora(), ahora)) : 0;
            Label tiempo = new Label(ocupada ? minutos + " min" : "Disponible");
            tiempo.getStyleClass().add("text-caption-muted");
            card.getChildren().addAll(header, total, tiempo);
            card.setOnMouseClicked(event -> { if (onSeleccionarMesa != null) onSeleccionarMesa.accept(mesa); });
            tileMesasPos.getChildren().add(card);
        }
        if (lblResumenMesasPos != null) lblResumenMesasPos.setText(ocupadas + " Ocupadas · " + (totalMesas - ocupadas) + " Libres");
    }

    public void renderizarPedidosMulticanal(FlowPane flowPedidosMulticanal, List<Pedido> externos,
                                            VentaMesasCanalCoordinator coord, Consumer<Pedido> onCargarPedido) {
        if (flowPedidosMulticanal == null) return;
        flowPedidosMulticanal.getChildren().clear();
        if (externos == null || externos.isEmpty()) {
            Label vacio = new Label("Sin pedidos de Kiosco o Delivery pendientes de cobro.");
            vacio.getStyleClass().add("text-caption-muted");
            flowPedidosMulticanal.getChildren().add(vacio);
            return;
        }
        for (Pedido pedido : externos) {
            String canal = coord.determinarCanalPedido(pedido);
            String referencia = pedido.getNumeroMesa() != null && !pedido.getNumeroMesa().isBlank()
                    ? pedido.getNumeroMesa() : "#" + pedido.getIdPedido();
            BigDecimal total = pedido.getTotal() == null ? BigDecimal.ZERO : pedido.getTotal();
            Button boton = new Button(coord.construirTextoBadgeCanal(pedido) + " " + referencia + " · S/ "
                    + total.setScale(2, RoundingMode.HALF_UP));
            boton.getStyleClass().add("KIOSCO".equals(canal) ? "badge-canal-kiosco" : "badge-canal-delivery");
            boton.setOnAction(event -> onCargarPedido.accept(pedido));
            flowPedidosMulticanal.getChildren().add(boton);
        }
    }

    public void renderizarBarraRapidaOrdenes(HBox boxOrdenesEntrantes, List<Pedido> activos,
                                             String mesaSeleccionadaActual, VentaMesasCanalCoordinator coord,
                                             Consumer<Pedido> onCargarPedido) {
        if (boxOrdenesEntrantes == null) return;
        boxOrdenesEntrantes.getChildren().clear();
        if (activos == null || activos.isEmpty()) {
            Label info = new Label("Orden manual lista · Mesa " + mesaSeleccionadaActual);
            info.getStyleClass().add("text-caption-muted");
            boxOrdenesEntrantes.getChildren().add(info);
            return;
        }
        for (Pedido pedido : activos) {
            String canal = coord.determinarCanalPedido(pedido);
            BigDecimal total = pedido.getTotal() == null ? BigDecimal.ZERO : pedido.getTotal();
            Button boton = new Button(coord.construirTextoBadgeCanal(pedido) + " S/ " + total.setScale(2, RoundingMode.HALF_UP));
            boton.setPadding(new Insets(3, 8, 3, 8));
            boton.getStyleClass().add(switch (canal) {
                case "KIOSCO" -> "badge-canal-kiosco";
                case "DELIVERY" -> "badge-canal-delivery";
                default -> "badge-canal-mesa";
            });
            boton.setOnAction(event -> onCargarPedido.accept(pedido));
            boxOrdenesEntrantes.getChildren().add(boton);
        }
    }

    public void actualizarCabeceraCanal(Label lblOrdenHeader, Label lblCanalBadge,
                                        VentaMesasCanalCoordinator.CabeceraCanalPos cabecera) {
        if (cabecera == null) return;
        if (lblOrdenHeader != null) lblOrdenHeader.setText(cabecera.headerTexto());
        if (lblCanalBadge != null) {
            lblCanalBadge.setText(cabecera.badgeTexto());
            lblCanalBadge.getStyleClass().removeAll("badge-canal-mesa", "badge-canal-kiosco", "badge-canal-delivery");
            lblCanalBadge.getStyleClass().add(switch (cabecera.canal()) {
                case "KIOSCO" -> "badge-canal-kiosco";
                case "DELIVERY" -> "badge-canal-delivery";
                default -> "badge-canal-mesa";
            });
        }
    }
}
