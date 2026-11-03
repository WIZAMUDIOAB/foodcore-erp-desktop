package com.foodcore.model;

import java.time.LocalDateTime;

/**
 * Entidad de evento persistido en la tabla {@code outbox_evento}
 * según SPEC-FOODCORE-v1.0 (§5, §11; REMEDIATION R-13).
 */
public class OutboxEvento {

    private long idEvento;
    private String tipoEvento;
    private String payloadJson;
    private String estado; // PENDIENTE, ENVIADO, FALLIDO
    private int intentos;
    private LocalDateTime proximoIntento;
    private LocalDateTime creadoEn;
    private LocalDateTime enviadoEn;

    public OutboxEvento() {
    }

    public OutboxEvento(long idEvento,
                        String tipoEvento,
                        String payloadJson,
                        String estado,
                        int intentos,
                        LocalDateTime proximoIntento,
                        LocalDateTime creadoEn,
                        LocalDateTime enviadoEn) {
        this.idEvento = idEvento;
        this.tipoEvento = tipoEvento;
        this.payloadJson = payloadJson;
        this.estado = estado;
        this.intentos = intentos;
        this.proximoIntento = proximoIntento;
        this.creadoEn = creadoEn;
        this.enviadoEn = enviadoEn;
    }

    public long getIdEvento() {
        return idEvento;
    }

    public void setIdEvento(long idEvento) {
        this.idEvento = idEvento;
    }

    public String getTipoEvento() {
        return tipoEvento;
    }

    public void setTipoEvento(String tipoEvento) {
        this.tipoEvento = tipoEvento;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public int getIntentos() {
        return intentos;
    }

    public void setIntentos(int intentos) {
        this.intentos = intentos;
    }

    public LocalDateTime getProximoIntento() {
        return proximoIntento;
    }

    public void setProximoIntento(LocalDateTime proximoIntento) {
        this.proximoIntento = proximoIntento;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(LocalDateTime creadoEn) {
        this.creadoEn = creadoEn;
    }

    public LocalDateTime getEnviadoEn() {
        return enviadoEn;
    }

    public void setEnviadoEn(LocalDateTime enviadoEn) {
        this.enviadoEn = enviadoEn;
    }
}
