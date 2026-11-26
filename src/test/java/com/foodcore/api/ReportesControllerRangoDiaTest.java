package com.foodcore.api;

import com.foodcore.api.ReportesController.RangoDia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Pruebas unitarias determinísticas del cálculo de límites diarios en {@code America/Lima}
 * para el dashboard gerencial en {@link ReportesController}.
 */
class ReportesControllerRangoDiaTest {

    @Test
    @DisplayName("A las 2026-11-26T00:33:00Z (19:33 Lima), el día activo es 2026-11-25 y no el día UTC")
    void rangoDiaEnUtcMedianochePerteneceAlDiaLima() {
        Clock clockUtcNocheLima = Clock.fixed(Instant.parse("2026-11-26T00:33:00Z"), ZoneOffset.UTC);

        RangoDia rango = ReportesController.calcularRangoDiaLima(clockUtcNocheLima);

        LocalDateTime esperadoInicio = LocalDateTime.of(2026, 11, 25, 0, 0, 0);
        LocalDateTime esperadoFin = LocalDateTime.of(2026, 11, 26, 0, 0, 0);
        assertEquals(esperadoInicio, rango.inicio());
        assertEquals(esperadoFin, rango.finExclusivo());
        assertEquals(Timestamp.valueOf(esperadoInicio), rango.inicioTimestamp());
        assertEquals(Timestamp.valueOf(esperadoFin), rango.finExclusivoTimestamp());

        ReportesController controller = new ReportesController(new ApiState(), null, clockUtcNocheLima);
        assertNotNull(controller);
    }

    @Test
    @DisplayName("Respeta fronteras exactas de medianoche America/Lima (04:59:59Z vs 05:00:00Z) y clock nulo")
    void rangoDiaFronterasMedianocheLimaYClockNulo() {
        Clock antesDeMedianocheLima = Clock.fixed(Instant.parse("2026-11-26T04:59:59Z"), ZoneOffset.UTC);
        RangoDia rangoAntes = ReportesController.calcularRangoDiaLima(antesDeMedianocheLima);
        assertEquals(LocalDateTime.of(2026, 11, 25, 0, 0, 0), rangoAntes.inicio());
        assertEquals(LocalDateTime.of(2026, 11, 26, 0, 0, 0), rangoAntes.finExclusivo());

        Clock medianocheLima = Clock.fixed(Instant.parse("2026-11-26T05:00:00Z"), ZoneOffset.UTC);
        RangoDia rangoMedianoche = ReportesController.calcularRangoDiaLima(medianocheLima);
        assertEquals(LocalDateTime.of(2026, 11, 26, 0, 0, 0), rangoMedianoche.inicio());
        assertEquals(LocalDateTime.of(2026, 11, 27, 0, 0, 0), rangoMedianoche.finExclusivo());

        RangoDia rangoSistema = ReportesController.calcularRangoDiaLima(null);
        assertEquals(rangoSistema.inicio().plusDays(1), rangoSistema.finExclusivo());
    }
}
