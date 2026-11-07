package com.foodcore.model;

import java.time.LocalDateTime;

/**
 * Entidad de Registro de Auditoría (Audit Trail) conforme a SPEC-FOODCORE-v1.3-ENTERPRISE (§5.2).
 */
public class RegistroAuditoria {

    private long idAuditoria;
    private int idUsuario;
    private String nombreUsuario;
    private String accion;
    private String entidadAfectada;
    private String datosAnteriores;
    private String datosNuevos;
    private LocalDateTime fecha;

    public RegistroAuditoria() {
        this.fecha = LocalDateTime.now();
    }

    public RegistroAuditoria(long idAuditoria, int idUsuario, String nombreUsuario, String accion,
                             String entidadAfectada, String datosAnteriores, String datosNuevos, LocalDateTime fecha) {
        this.idAuditoria = idAuditoria;
        this.idUsuario = idUsuario;
        this.nombreUsuario = nombreUsuario;
        this.accion = accion;
        this.entidadAfectada = entidadAfectada;
        this.datosAnteriores = datosAnteriores;
        this.datosNuevos = datosNuevos;
        this.fecha = fecha != null ? fecha : LocalDateTime.now();
    }

    public long getIdAuditoria() { return idAuditoria; }
    public void setIdAuditoria(long idAuditoria) { this.idAuditoria = idAuditoria; }

    public int getIdUsuario() { return idUsuario; }
    public void setIdUsuario(int idUsuario) { this.idUsuario = idUsuario; }

    public String getNombreUsuario() { return nombreUsuario; }
    public void setNombreUsuario(String nombreUsuario) { this.nombreUsuario = nombreUsuario; }

    public String getAccion() { return accion; }
    public void setAccion(String accion) { this.accion = accion; }

    public String getEntidadAfectada() { return entidadAfectada; }
    public void setEntidadAfectada(String entidadAfectada) { this.entidadAfectada = entidadAfectada; }

    public String getDatosAnteriores() { return datosAnteriores; }
    public void setDatosAnteriores(String datosAnteriores) { this.datosAnteriores = datosAnteriores; }

    public String getDatosNuevos() { return datosNuevos; }
    public void setDatosNuevos(String datosNuevos) { this.datosNuevos = datosNuevos; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}
