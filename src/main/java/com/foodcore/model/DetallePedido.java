package com.foodcore.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Entidad de Detalle de Pedido con estado individual de cocina y marcas temporales
 * por ítem ({@code enviado_en}, {@code iniciado_en}, {@code listo_en}) según SPEC-FOODCORE-v1.0 (§5, §6.5, §8.5; R-08).
 */
public class DetallePedido {
    public static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    private int idDetallePedido;
    private int idPedido;
    private int idProducto;
    private int cantidad;
    private BigDecimal precioUnitario;
    private BigDecimal subtotal;
    private String notasPreparacion;
    private String estadoCocina = "PENDIENTE"; // PENDIENTE | EN_PREPARACION | LISTO | ENTREGADO
    private LocalDateTime enviadoEn;
    private LocalDateTime iniciadoEn;
    private LocalDateTime listoEn;

    // Campos descriptivos para KDS y personalización POS (v4.8.0)
    private String nombreProducto;
    private String numeroMesa;
    private Integer idVariante;
    private String nombreVariante;
    private String modificadoresTexto;
    private List<ProductoModificador> modificadoresSeleccionados = new ArrayList<>();

    public DetallePedido() {
        this.enviadoEn = LocalDateTime.now(ZONA_LIMA);
    }

    public DetallePedido(int idDetallePedido, int idPedido, int idProducto, int cantidad,
                         BigDecimal precioUnitario, BigDecimal subtotal, String notasPreparacion) {
        this.idDetallePedido = idDetallePedido;
        this.idPedido = idPedido;
        this.idProducto = idProducto;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
        this.subtotal = subtotal;
        this.notasPreparacion = notasPreparacion;
        this.estadoCocina = "PENDIENTE";
        this.enviadoEn = LocalDateTime.now(ZONA_LIMA);
    }

    public DetallePedido(int idDetallePedido, int idPedido, int idProducto, String nombreProducto, int cantidad,
                         BigDecimal precioUnitario, BigDecimal subtotal, String notasPreparacion) {
        this(idDetallePedido, idPedido, idProducto, cantidad, precioUnitario, subtotal, notasPreparacion);
        this.nombreProducto = nombreProducto;
    }

    /**
     * Calcula los segundos transcurridos desde {@code enviado_en} contra la hora actual en {@code America/Lima} (R-07, R-08).
     */
    public int calcularSegundosDesdeEnvio(LocalDateTime referenciaLima) {
        if (enviadoEn == null || referenciaLima == null) {
            return 0;
        }
        long diff = Duration.between(enviadoEn, referenciaLima).getSeconds();
        if (diff < 0) {
            return 0;
        }
        return (int) Math.min(diff, 28800L);
    }

    public int getIdDetallePedido() { return idDetallePedido; }
    public void setIdDetallePedido(int idDetallePedido) { this.idDetallePedido = idDetallePedido; }

    public int getIdPedido() { return idPedido; }
    public void setIdPedido(int idPedido) { this.idPedido = idPedido; }

    public int getIdProducto() { return idProducto; }
    public void setIdProducto(int idProducto) { this.idProducto = idProducto; }

    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }

    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(BigDecimal precioUnitario) { this.precioUnitario = precioUnitario; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public String getNotasPreparacion() { return notasPreparacion; }
    public void setNotasPreparacion(String notasPreparacion) { this.notasPreparacion = notasPreparacion; }

    public String getEstadoCocina() { return estadoCocina; }
    public void setEstadoCocina(String estadoCocina) { this.estadoCocina = estadoCocina; }

    public LocalDateTime getEnviadoEn() { return enviadoEn; }
    public void setEnviadoEn(LocalDateTime enviadoEn) { this.enviadoEn = enviadoEn; }

    public LocalDateTime getIniciadoEn() { return iniciadoEn; }
    public void setIniciadoEn(LocalDateTime iniciadoEn) { this.iniciadoEn = iniciadoEn; }

    public LocalDateTime getListoEn() { return listoEn; }
    public void setListoEn(LocalDateTime listoEn) { this.listoEn = listoEn; }

    public String getNombreProducto() { return nombreProducto; }
    public void setNombreProducto(String nombreProducto) { this.nombreProducto = nombreProducto; }

    public String getNumeroMesa() { return numeroMesa; }
    public void setNumeroMesa(String numeroMesa) { this.numeroMesa = numeroMesa; }

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

    @Override
    public String toString() {
        return cantidad + "x " + nombreProducto + " [S/ " + subtotal + "]";
    }
}
