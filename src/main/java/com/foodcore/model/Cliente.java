package com.foodcore.model;

public class Cliente {
    private int idCliente;
    private String tipoDocumento;
    private String numeroDocumento;
    private String nombreRazonSocial;
    private String direccion;
    private String telefono;
    private String email;

    public Cliente() {}

    public Cliente(int idCliente, String tipoDocumento, String numeroDocumento, String nombreRazonSocial, String direccion, String telefono, String email) {
        this.idCliente = idCliente;
        this.tipoDocumento = tipoDocumento;
        this.numeroDocumento = numeroDocumento;
        this.nombreRazonSocial = nombreRazonSocial;
        this.direccion = direccion;
        this.telefono = telefono;
        this.email = email;
    }

    public int getIdCliente() { return idCliente; }
    public void setIdCliente(int idCliente) { this.idCliente = idCliente; }

    public String getTipoDocumento() { return tipoDocumento; }
    public void setTipoDocumento(String tipoDocumento) { this.tipoDocumento = tipoDocumento; }

    public String getNumeroDocumento() { return numeroDocumento; }
    public void setNumeroDocumento(String numeroDocumento) { this.numeroDocumento = numeroDocumento; }

    public String getNombreRazonSocial() { return nombreRazonSocial; }
    public void setNombreRazonSocial(String nombreRazonSocial) { this.nombreRazonSocial = nombreRazonSocial; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    @Override
    public String toString() { return nombreRazonSocial + " (" + numeroDocumento + ")"; }
}
