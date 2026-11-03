package com.foodcore.model;

import com.foodcore.exception.CodigoError;
import com.foodcore.exception.NegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Suite de Pruebas Unitarias para invariantes financieros del ERP según SPEC-FOODCORE-v1.0 (§8.1, §15.1).
 */
class DesgloseFiscalTest {

    @Test
    @DisplayName("Contrato §8.1: total = 55.80 debe desglosar subtotal = 47.29 e igv = 8.51 con HALF_EVEN")
    void debeCumplirContratoEspecificacion55Con80() throws NegocioException {
        // S/ 55.80 / 1.18 = 47.28813559... -> HALF_EVEN(2) = 47.29 | IGV = 55.80 - 47.29 = 8.51
        DesgloseFiscal desglose = DesgloseFiscal.desdeTotalConImpuestos(new BigDecimal("55.80"));

        assertEquals(new BigDecimal("47.29"), desglose.getSubtotalGravado());
        assertEquals(new BigDecimal("8.51"), desglose.getIgv());
        assertEquals(new BigDecimal("55.80"), desglose.getTotal());
        assertEquals(0, desglose.getSubtotalGravado().add(desglose.getIgv()).compareTo(desglose.getTotal()));
    }

    @Test
    @DisplayName("Debe aplicar redondeo bancario HALF_EVEN en moneda (escala 2) y stock (escala 4)")
    void debeNormalizarMonedaYStockConHalfEven() throws NegocioException {
        // Redondeo al par más cercano: 10.125 -> 10.12 (par), 10.135 -> 10.14 (par)
        assertEquals(new BigDecimal("10.12"), DesgloseFiscal.normalizarMoneda(new BigDecimal("10.125")));
        assertEquals(new BigDecimal("10.14"), DesgloseFiscal.normalizarMoneda(new BigDecimal("10.135")));

        // Stock a 4 decimales: 0.25005 -> 0.2500 (par), 0.25015 -> 0.2502 (par)
        assertEquals(new BigDecimal("0.2500"), DesgloseFiscal.normalizarStock(new BigDecimal("0.25005")));
        assertEquals(new BigDecimal("0.2502"), DesgloseFiscal.normalizarStock(new BigDecimal("0.25015")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "9.90", "25.00", "33.33", "47.50", "55.80", "100.00", "123.45", "99999.99"})
    @DisplayName("Invariante Contable: Subtotal Gravado + IGV debe ser idéntico al Total en todos los casos de borde")
    void debeCumplirInvarianteAditivoEnCasosDeBorde(String montoStr) throws NegocioException {
        BigDecimal esperado = new BigDecimal(montoStr);
        DesgloseFiscal desglose = DesgloseFiscal.desdeTotalConImpuestos(esperado);

        BigDecimal sumaReconstruida = desglose.getSubtotalGravado().add(desglose.getIgv());
        assertEquals(esperado, sumaReconstruida,
            "Fuga de céntimos detectada para el monto S/ " + montoStr);
        assertEquals(2, desglose.getSubtotalGravado().scale());
        assertEquals(2, desglose.getIgv().scale());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-0.01", "-50.00"})
    @DisplayName("Debe rechazar montos cero o negativos con CodigoError.VALIDACION_FISCAL")
    void debeRechazarMontosCeroONegativos(String montoInvalido) {
        NegocioException ex = assertThrows(NegocioException.class, () ->
            DesgloseFiscal.desdeTotalConImpuestos(new BigDecimal(montoInvalido))
        );
        assertEquals(CodigoError.VALIDACION_FISCAL, ex.getCodigoError());
    }

    @Test
    @DisplayName("Debe calcular vuelto exacto en EFECTIVO y rechazar pagos insuficientes")
    void debeValidarCobroEnEfectivoYVuelto() throws NegocioException {
        DesgloseFiscal desglose = DesgloseFiscal.desdeTotalConImpuestos(new BigDecimal("32.50"));

        BigDecimal vuelto = desglose.calcularVuelto(new BigDecimal("50.00"), "EFECTIVO");
        assertEquals(new BigDecimal("17.50"), vuelto);

        assertThrows(NegocioException.class, () ->
            desglose.calcularVuelto(new BigDecimal("20.00"), "EFECTIVO")
        );

        assertEquals(new BigDecimal("0.00"), desglose.calcularVuelto(new BigDecimal("32.50"), "YAPE_PLIN"));
    }

    @Test
    @DisplayName("Debe exigir RUC de 11 dígitos (10 o 20) y Razón Social válida al emitir FACTURA")
    void debeValidarReglasParaFacturaYBoleta() {
        assertThrows(NegocioException.class, () ->
            DesgloseFiscal.validarRequisitosComprobante("FACTURA", "00000000", "CLIENTES VARIOS")
        );

        assertThrows(NegocioException.class, () ->
            DesgloseFiscal.validarRequisitosComprobante("FACTURA", "20608912345", "CLIENTES VARIOS")
        );

        assertDoesNotThrow(() ->
            DesgloseFiscal.validarRequisitosComprobante("FACTURA", "20608912345", "CORPORACION GASTRONOMICA S.A.C.")
        );

        assertDoesNotThrow(() ->
            DesgloseFiscal.validarRequisitosComprobante("BOLETA", "72345678", "JUAN PEREZ")
        );
    }
}
