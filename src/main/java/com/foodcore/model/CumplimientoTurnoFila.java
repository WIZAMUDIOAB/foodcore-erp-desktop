package com.foodcore.model;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Fila del cruce diario entre turnos programados ({@code turno_programado}) y marcajes reales ({@code asistencia}).
 */
public record CumplimientoTurnoFila(
        int idUsuario,
        String nombreColaborador,
        String rol,
        LocalDate fecha,
        String diaSemana,
        String turno,
        LocalTime horaInicioProgramada,
        LocalTime horaFinProgramada,
        LocalTime horaIngresoReal,
        LocalTime horaSalidaReal,
        int minutosAtraso,
        String estadoCumplimiento
) {
    public String horarioProgramadoTexto() {
        if (horaInicioProgramada == null || horaFinProgramada == null) {
            return "Sin turno";
        }
        return horaInicioProgramada + " - " + horaFinProgramada + (turno != null ? " (" + turno + ")" : "");
    }
}
