package com.foodcore.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Agregado estructurado del Dashboard Gerencial (SPEC-FOODCORE v3.0.3 / v4.4.0):
 * KPIs del día, comparativas de período, ventas por hora, ranking por mozo,
 * rentabilidad por receta y alertas de vencimiento de personal.
 *
 * @param comparativa        Comparativas Hoy vs. Ayer y Mes Actual vs. Anterior.
 * @param seguridadHoy       Resumen de alertas de seguridad operacional del día.
 * @param ventasDia          Total facturado de hoy (S/).
 * @param numTransacciones   Cantidad de comprobantes completados.
 * @param ticketPromedio     Ticket promedio (S/).
 * @param platoTop           Plato más despachado del día.
 * @param platoTopUnidades   Unidades despachadas del plato top.
 * @param ventasPorHora      Monto vendido agrupado por hora ("HH:00").
 * @param filasMozo          Ranking de ventas por mozo / operador.
 * @param filasRentabilidad  Rentabilidad por receta de la carta.
 * @param alertasVencimiento Vencimientos de personal en los próximos 30 días.
 */
public record ResumenDashboard(
        ComparativaPeriodos comparativa,
        ResumenSeguridadHoy seguridadHoy,
        BigDecimal ventasDia,
        int numTransacciones,
        BigDecimal ticketPromedio,
        String platoTop,
        int platoTopUnidades,
        Map<String, BigDecimal> ventasPorHora,
        List<FilaVentasMozo> filasMozo,
        List<FilaRentabilidad> filasRentabilidad,
        List<AlertaVencimiento> alertasVencimiento) {

    /**
     * Normaliza colecciones a copias no nulas para preservar inmutabilidad del registro.
     */
    public ResumenDashboard {
        ventasPorHora = ventasPorHora != null ? Map.copyOf(ventasPorHora) : Map.of();
        filasMozo = filasMozo != null ? List.copyOf(filasMozo) : List.of();
        filasRentabilidad = filasRentabilidad != null ? List.copyOf(filasRentabilidad) : List.of();
        alertasVencimiento = alertasVencimiento != null ? List.copyOf(alertasVencimiento) : List.of();
        platoTop = platoTop != null ? platoTop : "Sin ventas registradas";
    }

    /**
     * Indica la fecha de referencia Lima usada para el cálculo del resumen.
     *
     * @param fechaReferencia Fecha de corteAmerica/Lima.
     * @return Fecha de corte.
     */
    public LocalDate fechaReferencia(LocalDate fechaReferencia) {
        return fechaReferencia;
    }
}
