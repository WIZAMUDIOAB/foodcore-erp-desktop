package com.foodcore.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Reporte consolidado de cumplimiento de turnos programados versus asistencia real, con detalle de atrasos y faltas.
 */
public record ReporteCumplimientoTurno(
        LocalDate desde,
        LocalDate hasta,
        int totalTurnosEvaluados,
        int totalPuntuales,
        int totalAtrasos,
        int totalFaltas,
        List<CumplimientoTurnoFila> filasAtrasos,
        List<CumplimientoTurnoFila> filasFaltas,
        List<CumplimientoTurnoFila> todasLasFilas
) {
    public ReporteCumplimientoTurno {
        filasAtrasos = filasAtrasos != null ? List.copyOf(filasAtrasos) : List.of();
        filasFaltas = filasFaltas != null ? List.copyOf(filasFaltas) : List.of();
        todasLasFilas = todasLasFilas != null ? List.copyOf(todasLasFilas) : List.of();
    }
}
