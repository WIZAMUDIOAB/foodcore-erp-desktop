package com.foodcore.crm;

import com.foodcore.model.Cliente;
import com.foodcore.model.DesgloseFiscal;
import com.foodcore.service.FidelizacionService;
import com.foodcore.util.ConfiguracionFidelizacion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas unitarias de reglas de CRM Minorista y Fidelización (SPEC-FOODCORE-v5.0.0-CRM).
 */
class CrmFidelizacionReglasTest {

    @AfterEach
    void limpiarOverrides() {
        ConfiguracionFidelizacion.clearOverridesForTest();
    }

    @Test
    @DisplayName("v5.0.0 (1/3): Lee parámetros configurables de fidelización desde application.properties y overrides")
    void debeLeerConfiguracionFidelizacionPorDefectoYPersonalizada() {
        assertEquals(1, ConfiguracionFidelizacion.getPuntosPorSol());
        assertEquals(100, ConfiguracionFidelizacion.getPuntosPorCanje());
        assertEquals(new BigDecimal("5.00"), ConfiguracionFidelizacion.getDescuentoPorCanjeSoles());
        assertEquals(new BigDecimal("10.00"), ConfiguracionFidelizacion.getCumpleanosDescuentoPorcentaje());

        ConfiguracionFidelizacion.setOverridesForTest(2, 50, new BigDecimal("4.00"), new BigDecimal("15.00"));
        assertEquals(2, ConfiguracionFidelizacion.getPuntosPorSol());
        assertEquals(50, ConfiguracionFidelizacion.getPuntosPorCanje());
        assertEquals(new BigDecimal("4.00"), ConfiguracionFidelizacion.getDescuentoPorCanjeSoles());
        assertEquals(new BigDecimal("15.00"), ConfiguracionFidelizacion.getCumpleanosDescuentoPorcentaje());
    }

    @Test
    @DisplayName("v5.0.0 (2/3): Canje por bloques calcula descuento sin exceder venta ni alterar IGV")
    void debeCalcularCanjePorBloquesSinAlterarIgv() throws Exception {
        FidelizacionService service = new FidelizacionService();
        // Venta de S/ 59.00 -> Base imponible S/ 50.00 + IGV S/ 9.00
        BigDecimal basePreCanje = new BigDecimal("59.00");
        DesgloseFiscal desgloseAntesCanje = DesgloseFiscal.desdeTotalConImpuestos(basePreCanje);
        assertEquals(new BigDecimal("50.00"), desgloseAntesCanje.getSubtotalGravado());
        assertEquals(new BigDecimal("9.00"), desgloseAntesCanje.getIgv());

        // Cliente con 220 pts -> máximo 2 bloques (200 pts = S/ 10.00). Si elige 1 bloque: 100 pts -> S/ 5.00
        FidelizacionService.BeneficioCanje beneficio = service.calcularCanjePorBloques(220, 1, basePreCanje);
        assertEquals(2, beneficio.bloquesDisponibles());
        assertEquals(100, beneficio.puntosUtilizados());
        assertEquals(new BigDecimal("5.00"), beneficio.descuentoAplicadoSoles());

        BigDecimal totalTrasCanje = basePreCanje.subtract(beneficio.descuentoAplicadoSoles());
        assertEquals(new BigDecimal("54.00"), totalTrasCanje);
        // IGV permanece S/ 9.00
        assertEquals(new BigDecimal("9.00"), desgloseAntesCanje.getIgv());
    }

    @Test
    @DisplayName("v5.0.0 (3/3): Detecta mes de cumpleaños y calcula descuento 10% configurable")
    void debeDetectarCumpleanosYCalcularDescuento10PorCiento() {
        FidelizacionService service = new FidelizacionService();
        Cliente cumpleanero = new Cliente();
        cumpleanero.setTipoDocumento("DNI");
        cumpleanero.setTipoCliente("MINORISTA");
        cumpleanero.setFechaNacimiento(LocalDate.of(1995, LocalDate.now().getMonthValue(), 12));

        assertTrue(cumpleanero.isMinorista());
        assertTrue(service.esCumpleanosEnMesActual(cumpleanero));
        assertEquals(new BigDecimal("8.00"), service.calcularDescuentoCumpleanosPos(new BigDecimal("80.00")));

        Cliente noCumpleanero = new Cliente();
        noCumpleanero.setFechaNacimiento(LocalDate.now().minusMonths(3));
        assertFalse(service.esCumpleanosEnMesActual(noCumpleanero));
    }
}
