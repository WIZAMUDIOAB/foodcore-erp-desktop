package com.foodcore.service;

import com.foodcore.model.MovimientoPago;
import com.foodcore.model.PlanillaConsolidada;
import com.foodcore.service.impl.PlanillaServiceImpl;
import com.foodcore.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas unitarias e integración para {@link PlanillaServiceImpl}.
 */
class PlanillaServiceTest {

    @BeforeAll
    static void initDb() {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
    }

    @Test
    @DisplayName("Validaciones de movimientos y cálculo exacto Sueldo Base + Bonos/Comisiones - Adelantos/Descuentos")
    void testRegistroMovimientosYConsolidadoPlanilla(@TempDir Path tempDir) throws Exception {
        PlanillaService service = new PlanillaServiceImpl();
        LocalDate fechaMesFuturo = LocalDate.now().plusMonths(3).withDayOfMonth(15);
        String periodoPrueba = YearMonth.from(fechaMesFuturo).toString();

        assertThrows(IllegalArgumentException.class, () -> service.registrarMovimiento(
                0, "BONO", "Test", new BigDecimal("50"), fechaMesFuturo, 1, null));
        assertThrows(IllegalArgumentException.class, () -> service.registrarMovimiento(
                1, "AFP_TEST", "Invalido", new BigDecimal("50"), fechaMesFuturo, 1, null));
        assertThrows(IllegalArgumentException.class, () -> service.registrarMovimiento(
                1, "BONO", " ", new BigDecimal("50"), fechaMesFuturo, 1, null));
        assertThrows(IllegalArgumentException.class, () -> service.registrarMovimiento(
                1, "BONO", "Negativo", new BigDecimal("-10"), fechaMesFuturo, 1, null));

        MovimientoPago m1 = service.registrarMovimiento(
                2, "BONO", "Bono productividad", new BigDecimal("200.00"), fechaMesFuturo, 1, "Obs 1");
        MovimientoPago m2 = service.registrarMovimiento(
                2, "COMISION", "Meta caja", new BigDecimal("100.50"), fechaMesFuturo, 1, null);
        MovimientoPago m3 = service.registrarMovimiento(
                2, "ADELANTO", "Adelanto quincena", new BigDecimal("150.00"), fechaMesFuturo, 1, null);
        MovimientoPago m4 = service.registrarMovimiento(
                2, "DESCUENTO", "Faltante menor", new BigDecimal("20.25"), fechaMesFuturo, 1, null);

        try {
            List<MovimientoPago> movs = service.listarMovimientosPorPeriodo(periodoPrueba);
            assertEquals(4, movs.size());

            List<PlanillaConsolidada> planilla = service.calcularPlanillaConsolidada(periodoPrueba);
            assertFalse(planilla.isEmpty());

            PlanillaConsolidada cajero = planilla.stream()
                    .filter(p -> p.idUsuario() == 2)
                    .findFirst()
                    .orElseThrow();

            BigDecimal netoEsperado = cajero.sueldoBase()
                    .add(new BigDecimal("200.00"))
                    .add(new BigDecimal("100.50"))
                    .subtract(new BigDecimal("150.00"))
                    .subtract(new BigDecimal("20.25"));

            assertEquals(0, new BigDecimal("200.00").compareTo(cajero.bonos()));
            assertEquals(0, new BigDecimal("100.50").compareTo(cajero.comisiones()));
            assertEquals(0, new BigDecimal("150.00").compareTo(cajero.adelantos()));
            assertEquals(0, new BigDecimal("20.25").compareTo(cajero.descuentos()));
            assertEquals(0, netoEsperado.compareTo(cajero.netoPagar()),
                    "Neto = Sueldo Base + Bonos + Comisiones - Adelantos - Descuentos");

            File xlsx = service.exportarPlanillaExcel(
                    planilla, periodoPrueba, tempDir.resolve("planilla_test.xlsx").toFile());
            assertTrue(xlsx.exists() && Files.size(xlsx.toPath()) > 500);

            File pdf = service.exportarPlanillaPdf(
                    planilla, periodoPrueba, tempDir.resolve("planilla_test.pdf").toFile());
            assertTrue(pdf.exists() && Files.size(pdf.toPath()) > 500);
        } finally {
            service.eliminarMovimiento(m1.getIdMovimiento());
            service.eliminarMovimiento(m2.getIdMovimiento());
            service.eliminarMovimiento(m3.getIdMovimiento());
            service.eliminarMovimiento(m4.getIdMovimiento());
        }

        assertTrue(service.listarMovimientosPorPeriodo(periodoPrueba).isEmpty());
    }
}
