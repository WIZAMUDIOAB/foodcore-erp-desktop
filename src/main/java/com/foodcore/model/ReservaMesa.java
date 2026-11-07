package com.foodcore.model;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ReservaMesa {

    private int idReserva;
    private long idMesa;
    private String numeroMesa;
    private String clienteNombre;
    private String clienteTelefono;
    private LocalDate fecha;
    private LocalTime hora;
    private int personas = 2;
    private String estado = "CONFIRMADA"; // PENDIENTE, CONFIRMADA, SENTADA, CANCELADA
    private String notas;
    private Integer idUsuarioRegistro;

    public ReservaMesa() {
    }

    public ReservaMesa(long idMesa, String clienteNombre, String clienteTelefono, LocalDate fecha, LocalTime hora, int personas, String notas) {
        this.idMesa = idMesa;
        this.clienteNombre = clienteNombre;
        this.clienteTelefono = clienteTelefono;
        this.fecha = fecha;
        this.hora = hora;
        this.personas = personas;
        this.notas = notas;
    }

    public int getIdReserva() {
        return idReserva;
    }

    public void setIdReserva(int idReserva) {
        this.idReserva = idReserva;
    }

    public long getIdMesa() {
        return idMesa;
    }

    public void setIdMesa(long idMesa) {
        this.idMesa = idMesa;
    }

    public String getNumeroMesa() {
        return numeroMesa != null ? numeroMesa : String.format("%02d", idMesa);
    }

    public void setNumeroMesa(String numeroMesa) {
        this.numeroMesa = numeroMesa;
    }

    public String getClienteNombre() {
        return clienteNombre;
    }

    public void setClienteNombre(String clienteNombre) {
        this.clienteNombre = clienteNombre;
    }

    public String getClienteTelefono() {
        return clienteTelefono;
    }

    public void setClienteTelefono(String clienteTelefono) {
        this.clienteTelefono = clienteTelefono;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public LocalTime getHora() {
        return hora;
    }

    public void setHora(LocalTime hora) {
        this.hora = hora;
    }

    public int getPersonas() {
        return personas;
    }

    public void setPersonas(int personas) {
        this.personas = personas;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getNotas() {
        return notas;
    }

    public void setNotas(String notas) {
        this.notas = notas;
    }

    public Integer getIdUsuarioRegistro() {
        return idUsuarioRegistro;
    }

    public void setIdUsuarioRegistro(Integer idUsuarioRegistro) {
        this.idUsuarioRegistro = idUsuarioRegistro;
    }

    public String getHoraTexto() {
        return hora != null ? hora.format(DateTimeFormatter.ofPattern("HH:mm")) : "00:00";
    }

    public String getFechaTexto() {
        return fecha != null ? fecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "-";
    }

    /**
     * Genera enlace oficial de WhatsApp API con mensaje de confirmación preformateado (§9.2).
     */
    public String generarUrlWhatsApp() {
        String numLimpio = clienteTelefono != null ? clienteTelefono.replaceAll("[^0-9]", "") : "";
        if (!numLimpio.startsWith("51") && numLimpio.length() == 9) {
            numLimpio = "51" + numLimpio;
        }

        String texto = String.format(
            "¡Hola %s! Confirmamos tu reserva en *FoodCore Restobar* para el *%s* a las *%s* (Mesa %s, %d personas). %s¡Te esperamos con gusto!",
            clienteNombre != null ? clienteNombre : "Estimado cliente",
            getFechaTexto(),
            getHoraTexto(),
            getNumeroMesa(),
            personas,
            notas != null && !notas.isBlank() ? "Nota: " + notas + ". " : ""
        );

        return "https://wa.me/" + numLimpio + "?text=" + URLEncoder.encode(texto, StandardCharsets.UTF_8);
    }
}
