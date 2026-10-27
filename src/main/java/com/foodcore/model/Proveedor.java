package com.foodcore.model;

public class Proveedor {
    private int idProveedor;
    private String ruc;
    private String razonSocial;
    private String contacto;
    private String telefono;
    private String direccion;

    public Proveedor() {}

    public Proveedor(int idProveedor, String ruc, String razonSocial, String contacto, String telefono, String direccion) {
        this.idProveedor = idProveedor;
        this.ruc = ruc;
        this.razonSocial = razonSocial;
        this.contacto = contacto;
        this.telefono = telefono;
        this.direccion = direccion;
    }

    public int getIdProveedor() { return idProveedor; }
    public void setIdProveedor(int idProveedor) { this.idProveedor = idProveedor; }

    public String getRuc() { return ruc; }
    public void setRuc(String ruc) { this.ruc = ruc; }

    public String getRazonSocial() { return razonSocial; }
    public void setRazonSocial(String razonSocial) { this.razonSocial = razonSocial; }

    public String getContacto() { return contacto; }
    public void setContacto(String contacto) { this.contacto = contacto; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    @Override
    public String toString() { return razonSocial + " (" + ruc + ")"; }
}
