package com.foodcore.controller.dashboard;

import javafx.scene.control.Label;
import javafx.scene.layout.Region;

public class DashboardSyncIndicatorPresenter {
    public record EstadoIndicadorRed(String dotStyleClass, String badgeStyleClass, String texto) {
    }

    public EstadoIndicadorRed construirEstadoRed(boolean online, int pendientes) {
        if (online) {
            return new EstadoIndicadorRed("status-dot-online", "badge-pill-success",
                    pendientes > 0 ? "ONLINE (" + pendientes + ")" : "ONLINE");
        }
        return new EstadoIndicadorRed("status-dot-danger", "badge-pill-danger",
                pendientes > 0 ? "OFFLINE (" + pendientes + " pend)" : "OFFLINE");
    }

    public void aplicarEstado(Region dotNetworkStatus, Label lblNetworkStatus, boolean online, int pendientes) {
        EstadoIndicadorRed estado = construirEstadoRed(online, pendientes);
        if (dotNetworkStatus != null) {
            dotNetworkStatus.getStyleClass().removeAll("status-dot-online", "status-dot-danger", "status-dot-warning");
            if (!dotNetworkStatus.getStyleClass().contains(estado.dotStyleClass())) {
                dotNetworkStatus.getStyleClass().add(estado.dotStyleClass());
            }
        }
        if (lblNetworkStatus != null) {
            lblNetworkStatus.getStyleClass().removeAll("badge-pill-success", "badge-pill-danger", "badge-pill-warning");
            if (!lblNetworkStatus.getStyleClass().contains(estado.badgeStyleClass())) {
                lblNetworkStatus.getStyleClass().add(estado.badgeStyleClass());
            }
            lblNetworkStatus.setText(estado.texto());
        }
    }
}