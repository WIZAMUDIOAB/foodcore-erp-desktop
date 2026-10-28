package com.foodcore.model;

import com.foodcore.exception.NegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas Unitarias y de Caracterización Contable para invariantes financieros del ERP.
 */
class DesgloseFiscalTest {

    @Test
    @DisplayName("Debe desglosar IGV (18%) con redondeo HALF_UP para decimales periódicos sin perder céntimos")
    void debeDesglosarIgvConPrecisionExacta() throws NegocioException {
        // S/ 100.00 / 1.18 = 84.7457627... -> HALF_UP = 84.75 | IGV = 15.25
        DesgloseFiscal desglose = DesgloseFiscal.desdeTotalConImpuestos(new BigDecimal("100.00"));

        assertEquals(new BigDecimal("84.75"), desglose.getSubtotalGravado());
        assertEquals(new BigDecimal("15.25"), desglose.getIgv());
        assertEquals(new BigDecimal("100.00"), desglose.getTotal());
        assertEquals(0, desglose.getSubtotalGravado().add(desglose.getIgv()).compareTo(desglose.getTotal()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "9.90", "25.00", "33.33", "47.50", "123.45", "99999.99"})
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
    @DisplayName("Debe rechazar montos cero o negativos para impedir asientos contables corruptos")
    void debeRechazarMontosCeroONegativos(String montoInvalido) {
        assertThrows(NegocioException.class, () ->
            DesgloseFiscal.desdeTotalConImpuestos(new BigDecimal(montoInvalido))
        );
    }

    @Test
    @DisplayName("Debe calcular vuelto exacto en EFECTIVO y rechazar pagos insuficientes")
    void debeValidarCobroEnEfectivoYVuelto() throws NegocioException {
        DesgloseFiscal desglose = DesgloseFiscal.desdeTotalConImpuestos(new BigDecimal("32.50"));

        // Pago con billete de S/ 50.00 -> Vuelto esperado: S/ 17.50
        BigDecimal vuelto = desglose.calcularVuelto(new BigDecimal("50.00"), "EFECTIVO");
        assertEquals(new BigDecimal("17.50"), vuelto);

        // Pago insuficiente con S/ 20.00 -> Excepción de negocio
        assertThrows(NegocioException.class, () ->
            desglose.calcularVuelto(new BigDecimal("20.00"), "EFECTIVO")
        );

        // Pago digital (YAPE_PLIN / TARJETA) -> Vuelto siempre S/ 0.00
        assertEquals(new BigDecimal("0.00"), desglose.calcularVuelto(new BigDecimal("32.50"), "YAPE_PLIN"));
    }

    @Test
    @DisplayName("Debe exigir RUC de 11 dígitos (10 o 20) y Razón Social válida al emitir FACTURA")
    void debeValidarReglasSunatParaFacturaYBoleta() {
        // Factura con DNI genérico debe fallar
        assertThrows(NegocioException.class, () ->
            DesgloseFiscal.validarRequisitosComprobante("FACTURA", "00000000", "CLIENTES VARIOS")
        );

        // Factura con RUC válido pero sin Razón Social real debe fallar
        assertThrows(NegocioException.class, () ->
            DesgloseFiscal.validarRequisitosComprobante("FACTURA", "20608912345", "CLIENTES VARIOS")
        );

        // Factura con RUC 20 válido y Razón Social debe aprobar
        assertDoesNotThrow(() ->
            DesgloseFiscal.validarRequisitosComprobante("FACTURA", "20608912345", "CORPORACION GASTRONOMICA S.A.C.")
        );

        // Boleta con DNI de 8 dígitos debe aprobar
        assertDoesNotThrow(() ->
            DesgloseFiscal.validarRequisitosComprobante("BOLETA", "72345678", "JUAN PEREZ")
        );
    }
}
