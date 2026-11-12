package com.foodcore.service;

import com.foodcore.model.SolicitudAusencia;
import com.foodcore.model.Vacaciones;
import com.foodcore.service.impl.AusenciasVacacionesServiceImpl;
import com.foodcore.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas unitarias y de integración para {@link AusenciasVacacionesServiceImpl}.
 */
class AusenciasVacacionesServiceTest {

    @BeforeAll
    static void initDb() {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
    }

    @Test
    @DisplayName("Cálculo automático de días por antigüedad y duración de rango")
    void testCalculoAutomaticoDiasDisponiblesYDuracion() {
        LocalDate hoy = LocalDate.of(2026, 11, 12);
        assertEquals(15, AusenciasVacacionesServiceImpl.calcularDiasAsignadosPorAntiguedad(null, hoy));
        assertEquals(15, AusenciasVacacionesServiceImpl.calcularDiasAsignadosPorAntiguedad(hoy.minusMonths(4), hoy));
        assertEquals(25, AusenciasVacacionesServiceImpl.calcularDiasAsignadosPorAntiguedad(hoy.minusMonths(10), hoy));
        assertEquals(30, AusenciasVacacionesServiceImpl.calcularDiasAsignadosPorAntiguedad(hoy.minusMonths(24), hoy));

        AusenciasVacacionesService service = new AusenciasVacacionesServiceImpl();
        assertEquals(5, service.calcularDiasSolicitados(hoy, hoy.plusDays(4)));
        assertThrows(IllegalArgumentException.class, () -> service.calcularDiasSolicitados(hoy, hoy.minusDays(1)));
    }

    @Test
    @DisplayName("Solicitud de vacaciones valida saldo, aprueba transaccionalmente y descuenta días")
    void testRegistroYAprobacionDescuentaSaldoVacaciones() {
        AusenciasVacacionesService service = new AusenciasVacacionesServiceImpl();
        int anio = LocalDate.now().getYear();
        Vacaciones saldoAntes = service.obtenerSaldoVacaciones(4, anio);
        int dispAntes = saldoAntes.getDiasDisponibles();
        assertTrue(dispAntes >= 5, "El colaborador 4 debe contar con saldo disponible");

        assertThrows(IllegalArgumentException.class, () -> service.registrarSolicitud(
                4, "VACACIONES",
                LocalDate.of(anio, 12, 1),
                LocalDate.of(anio, 12, 1).plusDays(dispAntes + 5),
                "Exceso de saldo"));

        SolicitudAusencia sol = service.registrarSolicitud(
                4, "VACACIONES",
                LocalDate.of(anio, 11, 20),
                LocalDate.of(anio, 11, 22),
                "Descanso familiar programado");
        assertEquals(3, sol.getDiasSolicitados());
        assertEquals("PENDIENTE", sol.getEstado());

        service.aprobarSolicitud(sol.getIdSolicitud(), "Aprobado por Gerencia Test", 1);

        Vacaciones saldoDespues = service.obtenerSaldoVacaciones(4, anio);
        assertEquals(saldoAntes.getDiasGozados() + 3, saldoDespues.getDiasGozados());
        assertEquals(dispAntes - 3, saldoDespues.getDiasDisponibles());

        assertThrows(IllegalStateException.class,
                () -> service.aprobarSolicitud(sol.getIdSolicitud(), "Doble aprobación", 1));

        SolicitudAusencia permiso = service.registrarSolicitud(
                4, "PERMISO_MEDICO",
                LocalDate.of(anio, 12, 5),
                LocalDate.of(anio, 12, 6),
                "Cita médica");
        service.rechazarSolicitud(permiso.getIdSolicitud(), "Sin cobertura ese turno", 1);
        Vacaciones saldoFinal = service.obtenerSaldoVacaciones(4, anio);
        assertEquals(saldoDespues.getDiasGozados(), saldoFinal.getDiasGozados(),
                "Rechazar permiso no debe descontar días de vacaciones");
    }
}
