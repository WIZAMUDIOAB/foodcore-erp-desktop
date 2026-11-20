package com.foodcore.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Frame de socket entrante: sobre flexible para que la terminal pueda evolucionar el protocolo
 * sin romper la deserialización en el servidor.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SocketFrameDTO {

    private String type;
    private String codigoTerminal;
    private String secreto;
    private String sessionToken;
    private long idEvento;
    private JsonNode payload;

    public SocketFrameDTO() {
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCodigoTerminal() {
        return codigoTerminal;
    }

    public void setCodigoTerminal(String codigoTerminal) {
        this.codigoTerminal = codigoTerminal;
    }

    public String getSecreto() {
        return secreto;
    }

    public void setSecreto(String secreto) {
        this.secreto = secreto;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public long getIdEvento() {
        return idEvento;
    }

    public void setIdEvento(long idEvento) {
        this.idEvento = idEvento;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public void setPayload(JsonNode payload) {
        this.payload = payload;
    }
}
