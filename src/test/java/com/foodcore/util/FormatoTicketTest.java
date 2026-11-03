package com.foodcore.util;

import com.foodcore.exception.NegocioException;
import com.foodcore.model.ComprobantePago;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Venta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas unitarias de formateo de ticket térmico ESC/POS de 48 columnas
 * según SPEC-FOODCORE-v1.0 (§8.6, §15.1; REMEDIATION R-12).
 */
class FormatoTicketTest {

    @TempDir
    Path tempDir;

    private ImpresoraTermicaService impresoraService;

    @BeforeEach
    void setUp() {
        impresoraService = new ImpresoraTermicaService(
            new com.foodcore.security.AutorizacionService(),
            tempDir.resolve("tickets_emitidos.log")
        );
    }

    @Test
    @DisplayName("R-12 (1/4): Todas las líneas del ticket deben cumplir line.length() <= 48 e incluir encabezado e IGV")
    void debeFormatearTicketConAnchoMenorOIgualA48Columnas() throws NegocioException {
        Venta venta = new Venta(105, null, 1, 2, null,
            new BigDecimal("47.29"), new BigDecimal("8.51"), new BigDecimal("55.80"),
            "EFECTIVO", new BigDecimal("60.00"), new BigDecimal("4.20"));

        DetalleVenta item1 = new DetalleVenta(1, 105, 10, "Alitas BBQ x6 Especiales", 1,
            new BigDecimal("27.90"), new BigDecimal("27.90"));
        DetalleVenta item2 = new DetalleVenta(2, 105, 11, "Hamburguesa Royal Artesanal", 1,
            new BigDecimal("27.90"), new BigDecimal("27.90"));

        ComprobantePago comp = new ComprobantePago(1, 105, "BOLETA", "B001", "00000105",
            "72345678", "JUAN PEREZ", new BigDecimal("55.80"));

        List<String> lineas = impresoraService.formatearLineasTicket(venta, List.of(item1, item2), comp, true);

        assertFalse(lineas.isEmpty());
        for (String line : lineas) {
            assertTrue(line.length() <= 48,
                "La línea excede el máximo de 48 columnas (" + line.length() + "): [" + line + "]");
        }

        String ticketCompleto = String.join("\n", lineas);
        assertTrue(ticketCompleto.contains("FOODCORE ERP"));
        assertTrue(ticketCompleto.contains("COMPROBANTE INTERNO NO FISCAL"));
        assertTrue(ticketCompleto.contains("REIMPRESION AUTORIZADA"));
        assertTrue(ticketCompleto.contains("47.29"), "Debe incluir subtotal gravado 47.29");
        assertTrue(ticketCompleto.contains("8.51"), "Debe incluir IGV 8.51");
        assertTrue(ticketCompleto.contains("55.80"), "Debe incluir Total 55.80");
    }

    @Test
    @DisplayName("R-12 (2/4): La línea más larga del ticket debe medir exactamente 48 columnas")
    void laLineaMasLargaDebeMedirExactamente48Columnas() throws NegocioException {
        Venta venta = new Venta(105, null, 1, 2, null,
            new BigDecimal("47.29"), new BigDecimal("8.51"), new BigDecimal("55.80"),
            "EFECTIVO", new BigDecimal("60.00"), new BigDecimal("4.20"));
        DetalleVenta item = new DetalleVenta(1, 105, 10, "Lomo Saltado Clasico", 1,
            new BigDecimal("55.80"), new BigDecimal("55.80"));

        List<String> lineas = impresoraService.formatearLineasTicket(venta, List.of(item), null, false);
        int maxLongitud = lineas.stream().mapToInt(String::length).max().orElse(0);

        assertEquals(48, maxLongitud, "La línea más larga del ticket debe medir exactamente 48 columnas");
    }

    @Test
    @DisplayName("R-12 (3/4): Debe degradar caracteres con tildes y eñes a ASCII limpio cuando CP850 no está disponible")
    void debeDegradarTildesYEnesAAscii() {
        String original = "REIMPRESIÓN: Pollo a la Leña con Ají Limón";
        String ascii = ImpresoraTermicaService.degradarAAscii(original);
        assertEquals("REIMPRESION: Pollo a la Lena con Aji Limon", ascii);
    }

    @Test
    @DisplayName("R-12 (4/4): El flujo binario ESC/POS debe iniciar con 0x1B 0x40 y finalizar con corte 0x1D 0x56 0x41 0x10")
    void debeIncluirSecuenciasEscPosDeInicializacionYCorte() throws NegocioException {
        Venta venta = new Venta(1, null, 1, 1, null,
            new BigDecimal("47.29"), new BigDecimal("8.51"), new BigDecimal("55.80"),
            "EFECTIVO", new BigDecimal("55.80"), BigDecimal.ZERO);
        DetalleVenta det = new DetalleVenta(1, 1, 1, "Chicharron de Pollo Ñandú", 1,
            new BigDecimal("55.80"), new BigDecimal("55.80"));

        List<String> lineas = impresoraService.formatearLineasTicket(venta, List.of(det), null, false);
        byte[] bytes = impresoraService.generarBytesEscPos(lineas, true);

        assertTrue(bytes.length > 6);
        assertEquals((byte) 0x1B, bytes[0]);
        assertEquals((byte) 0x40, bytes[1]);

        int n = bytes.length;
        assertEquals((byte) 0x1D, bytes[n - 4]);
        assertEquals((byte) 0x56, bytes[n - 3]);
        assertEquals((byte) 0x41, bytes[n - 2]);
        assertEquals((byte) 0x10, bytes[n - 1]);
    }
}
