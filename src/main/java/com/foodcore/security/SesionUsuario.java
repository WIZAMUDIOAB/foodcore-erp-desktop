package com.foodcore.security;

import com.foodcore.model.enums.Permiso;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Contexto de sesión de usuario inmutable por login (SPEC-FOODCORE-v1.0 §10.2; REMEDIATION R-18).
 * Incluye {@code expiraEn = iniciadaEn + 30 min}.
 */
public final class SesionUsuario {

    public static final long MINUTOS_INACTIVIDAD_DEFAULT = 30L;

    private final long idUsuario;
    private final String nombreCompleto;
    private final String codigoRol;
    private final Set<Permiso> permisos;
    private final String idTerminal;
    private final LocalDateTime iniciadaEn;
    private final LocalDateTime expiraEn;

    public SesionUsuario(long idUsuario,
                         String nombreCompleto,
                         String codigoRol,
                         Set<Permiso> permisos,
                         String idTerminal,
                         LocalDateTime iniciadaEn) {
        this.idUsuario = idUsuario;
        this.nombreCompleto = Objects.requireNonNull(nombreCompleto, "El nombre completo no puede ser nulo.");
        this.codigoRol = codigoRol != null ? codigoRol.trim().toUpperCase() : "DESCONOCIDO";
        this.permisos = (permisos == null || permisos.isEmpty())
            ? Collections.emptySet()
            : Collections.unmodifiableSet(EnumSet.copyOf(permisos));
        this.idTerminal = idTerminal != null ? idTerminal : "TERM-LOCAL";
        this.iniciadaEn = iniciadaEn != null ? iniciadaEn : LocalDateTime.now();
        this.expiraEn = this.iniciadaEn.plusMinutes(MINUTOS_INACTIVIDAD_DEFAULT);
    }

    public boolean tienePermiso(Permiso permiso) {
        return permiso != null && permisos.contains(permiso);
    }

    public boolean haExpirado(LocalDateTime ahora, long minutosMaximos) {
        if (ahora == null) {
            return true;
        }
        return Duration.between(iniciadaEn, ahora).toMinutes() >= minutosMaximos;
    }

    public boolean haExpirado(LocalDateTime ahora) {
        if (ahora == null) {
            return true;
        }
        return !ahora.isBefore(expiraEn);
    }

    public long getIdUsuario() {
        return idUsuario;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public String getCodigoRol() {
        return codigoRol;
    }

    public Set<Permiso> getPermisos() {
        return permisos;
    }

    public String getIdTerminal() {
        return idTerminal;
    }

    public LocalDateTime getIniciadaEn() {
        return iniciadaEn;
    }

    public LocalDateTime getExpiraEn() {
        return expiraEn;
    }
}
