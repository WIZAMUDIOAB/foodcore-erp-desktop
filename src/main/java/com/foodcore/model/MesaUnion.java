package com.foodcore.model;

import java.time.LocalDateTime;

public class MesaUnion {

    private int idUnion;
    private long idMesaPrincipal;
    private long idMesaSecundaria;
    private String numeroMesaPrincipal;
    private String numeroMesaSecundaria;
    private int idPedido;
    private boolean activo = true;
    private LocalDateTime fechaUnion = LocalDateTime.now();

    public MesaUnion() {
    }

    public MesaUnion(long idMesaPrincipal, long idMesaSecundaria, int idPedido) {
        this.idMesaPrincipal = idMesaPrincipal;
        this.idMesaSecundaria = idMesaSecundaria;
        this.idPedido = idPedido;
        this.activo = true;
    }

    public int getIdUnion() {
        return idUnion;
    }

    public void setIdUnion(int idUnion) {
        this.idUnion = idUnion;
    }

    public long getIdMesaPrincipal() {
        return idMesaPrincipal;
    }

    public void setIdMesaPrincipal(long idMesaPrincipal) {
        this.idMesaPrincipal = idMesaPrincipal;
    }

    public long getIdMesaSecundaria() {
        return idMesaSecundaria;
    }

    public void setIdMesaSecundaria(long idMesaSecundaria) {
        this.idMesaSecundaria = idMesaSecundaria;
    }

    public String getNumeroMesaPrincipal() {
        return numeroMesaPrincipal;
    }

    public void setNumeroMesaPrincipal(String numeroMesaPrincipal) {
        this.numeroMesaPrincipal = numeroMesaPrincipal;
    }

    public String getNumeroMesaSecundaria() {
        return numeroMesaSecundaria;
    }

    public void setNumeroMesaSecundaria(String numeroMesaSecundaria) {
        this.numeroMesaSecundaria = numeroMesaSecundaria;
    }

    public int getIdPedido() {
        return idPedido;
    }

    public void setIdPedido(int idPedido) {
        this.idPedido = idPedido;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public LocalDateTime getFechaUnion() {
        return fechaUnion;
    }

    public void setFechaUnion(LocalDateTime fechaUnion) {
        this.fechaUnion = fechaUnion;
    }
}
