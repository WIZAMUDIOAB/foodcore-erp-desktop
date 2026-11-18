package com.foodcore.service;

import com.foodcore.model.SolicitudAusencia;
import com.foodcore.model.Vacaciones;

import java.time.LocalDate;
import java.util.List;

/**
 * Servicio administrativo para cálculo automático de días disponibles,
 * registro de solicitudes de vacaciones/permisos y flujo de aprobación gerencial.
 */
public interface AusenciasVacacionesService {

    Vacaciones obtenerSaldoVacaciones(int idUsuario, int anio);

    int calcularDiasSolicitados(LocalDate inicio, LocalDate fin);

    SolicitudAusencia registrarSolicitud(int idUsuario, String tipo, LocalDate inicio, LocalDate fin, String motivo);

    List<SolicitudAusencia> listarTodas();

    List<SolicitudAusencia> listarPorUsuario(int idUsuario);

    void aprobarSolicitud(int idSolicitud, String observacion, Integer idAprobador);

    void rechazarSolicitud(int idSolicitud, String observacion, Integer idAprobador);
}
