package com.foodcore.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DetalleVenta {
    private int idDetalleVenta;
    private int idVenta;
    private int idProducto;
    private int cantidad;
    private BigDecimal precioUnitario;
    private BigDecimal subtotal;

    // Estado y auditoría de devoluciones parciales (v5.2.0)
    private String estado = "COMPLETADO";
    private LocalDateTime fechaDevolucion;
    private String motivoDevolucion;

    // Campo descriptivo y personalización POS (v4.8.0)
    private String nombreProducto;
    private String notasPreparacion;
    private Integer idVariante;
    private String nombreVariante;
    private String modificadoresTexto;
    private List<ProductoModificador> modificadoresSeleccionados = new ArrayList<>();
    private Integer idCombo;
    private String comboComponentesTexto;

    public DetalleVenta() {}

    public DetalleVenta(int idDetalleVenta, int idVenta, int idProducto, int cantidad, BigDecimal precioUnitario, BigDecimal subtotal) {
        this.idDetalleVenta = idDetalleVenta;
        this.idVenta = idVenta;
        this.idProducto = idProducto;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
        this.subtotal = subtotal;
    }

    public DetalleVenta(int idDetalleVenta, int idVenta, int idProducto, String nombreProducto, int cantidad, BigDecimal precioUnitario, BigDecimal subtotal) {
        this(idDetalleVenta, idVenta, idProducto, cantidad, precioUnitario, subtotal);
        this.nombreProducto = nombreProducto;
    }

    public int getIdDetalleVenta() { return idDetalleVenta; }
    public void setIdDetalleVenta(int idDetalleVenta) { this.idDetalleVenta = idDetalleVenta; }

    public int getIdVenta() { return idVenta; }
    public void setIdVenta(int idVenta) { this.idVenta = idVenta; }

    public int getIdProducto() { return idProducto; }
    public void setIdProducto(int idProducto) { this.idProducto = idProducto; }

    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }

    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(BigDecimal precioUnitario) { this.precioUnitario = precioUnitario; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public String getNombreProducto() { return nombreProducto; }
    public void setNombreProducto(String nombreProducto) { this.nombreProducto = nombreProducto; }

    public String getNotasPreparacion() { return notasPreparacion; }
    public void setNotasPreparacion(String notasPreparacion) { this.notasPreparacion = notasPreparacion; }

    public Integer getIdVariante() { return idVariante; }
    public void setIdVariante(Integer idVariante) { this.idVariante = idVariante; }

    public String getNombreVariante() { return nombreVariante; }
    public void setNombreVariante(String nombreVariante) { this.nombreVariante = nombreVariante; }

    public String getModificadoresTexto() { return modificadoresTexto; }
    public void setModificadoresTexto(String modificadoresTexto) { this.modificadoresTexto = modificadoresTexto; }

    public List<ProductoModificador> getModificadoresSeleccionados() {
        return Collections.unmodifiableList(modificadoresSeleccionados);
    }

    public void setModificadoresSeleccionados(List<ProductoModificador> modificadoresSeleccionados) {
        this.modificadoresSeleccionados = modificadoresSeleccionados != null
                ? new ArrayList<>(modificadoresSeleccionados)
                : new ArrayList<>();
    }

    public Integer getIdCombo() { return idCombo; }
    public void setIdCombo(Integer idCombo) { this.idCombo = idCombo; }

    public String getComboComponentesTexto() { return comboComponentesTexto; }
    public void setComboComponentesTexto(String comboComponentesTexto) { this.comboComponentesTexto = comboComponentesTexto; }

    public String getEstado() { return estado != null ? estado : "COMPLETADO"; }
    public void setEstado(String estado) { this.estado = estado != null ? estado : "COMPLETADO"; }

    public LocalDateTime getFechaDevolucion() { return fechaDevolucion; }
    public void setFechaDevolucion(LocalDateTime fechaDevolucion) { this.fechaDevolucion = fechaDevolucion; }

    public String getMotivoDevolucion() { return motivoDevolucion; }
    public void setMotivoDevolucion(String motivoDevolucion) { this.motivoDevolucion = motivoDevolucion; }

    public boolean isDevuelto() { return "DEVUELTO".equalsIgnoreCase(getEstado()); }

    @Override
    public String toString() {
        return cantidad + "x " + nombreProducto + (notasPreparacion != null ? " (" + notasPreparacion + ")" : "") + " - S/ " + subtotal;
    }
}
