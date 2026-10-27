package com.foodcore.model;

import java.time.LocalDateTime;

public class Comanda {
    private int idComanda;
    private int idPedido;
    private String numeroComanda;
    private LocalDateTime fechaHoraEnvio;
    private String estado; // PENDIENTE, EN_PREPARACION, LISTA, DESPACHADA
    private int tiempoEstimadoMin;

    public Comanda() {}

    public Comanda(int idComanda, int idPedido, String numeroComanda, String estado, int tiempoEstimadoMin) {
        this.idComanda = idComanda;
        this.idPedido = idPedido;
        this.numeroComanda = numeroComanda;
        this.estado = estado;
        this.tiempoEstimadoMin = tiempoEstimadoMin;
        this.fechaHoraEnvio = LocalDateTime.now();
    }

    public int getIdComanda() { return idComanda; }
    public void setIdComanda(int idComanda) { this.idComanda = idComanda; }

    public int getIdPedido() { return idPedido; }
    public void setIdPedido(int idPedido) { this.idPedido = idPedido; }

    public String getNumeroComanda() { return numeroComanda; }
    public void setNumeroComanda(String numeroComanda) { this.numeroComanda = numeroComanda; }

    public LocalDateTime getFechaHoraEnvio() { return fechaHoraEnvio; }
    public void setFechaHoraEnvio(LocalDateTime fechaHoraEnvio) { this.fechaHoraEnvio = fechaHoraEnvio; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public int getTiempoEstimadoMin() { return tiempoEstimadoMin; }
    public void setTiempoEstimadoMin(int tiempoEstimadoMin) { this.tiempoEstimadoMin = tiempoEstimadoMin; }

    @Override
    public String toString() { return "Comanda " + numeroComanda + " (" + estado + ")"; }
}
