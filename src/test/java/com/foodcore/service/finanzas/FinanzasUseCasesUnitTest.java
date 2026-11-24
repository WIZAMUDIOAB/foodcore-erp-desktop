package com.foodcore.service.finanzas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.foodcore.model.Venta;
import com.foodcore.service.FinanzasService;
import java.io.File;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FinanzasUseCasesUnitTest {
    @Test
    void cuentaCorrienteValidaEntradasAntesDeAbrirTransaccion() {
        DefaultCuentaCorrienteUseCase useCase = new DefaultCuentaCorrienteUseCase(null, null, null, null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> useCase.registrarPagoProveedor(1, null, "", null, 1, null));
        assertThrows(IllegalArgumentException.class, () -> useCase.registrarPagoProveedor(1, new BigDecimal("-5.00"), "", null, 1, null));
        assertThrows(IllegalArgumentException.class, () -> useCase.registrarCobroCliente(1, BigDecimal.ZERO, "", null, 1, null));
        assertThrows(IllegalArgumentException.class, () -> useCase.registrarVentaCreditoCorporativo(null, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> useCase.registrarVentaCreditoCorporativo(new Venta(), 0, 1));
    }

    @Test
    void cambioPrecioValidaCamposObligatorios() {
        DefaultCambioPrecioUseCase useCase = new DefaultCambioPrecioUseCase(null, null);
        BigDecimal uno = new BigDecimal("1.0000");
        BigDecimal dos = new BigDecimal("2.0000");
        assertThrows(IllegalArgumentException.class, () -> useCase.registrarCambioPrecioConMotivo(" ", 1, "x", uno, dos, 1, "u", "motivo"));
        assertThrows(IllegalArgumentException.class, () -> useCase.registrarCambioPrecioConMotivo("PRODUCTO", 1, "x", new BigDecimal("-1.0000"), dos, 1, "u", "motivo"));
        assertThrows(IllegalArgumentException.class, () -> useCase.registrarCambioPrecioConMotivo("PRODUCTO", 1, "x", uno, BigDecimal.ZERO, 1, "u", "motivo"));
        assertThrows(IllegalArgumentException.class, () -> useCase.registrarCambioPrecioConMotivo("PRODUCTO", 1, "x", null, dos, 1, "u", "motivo"));
        assertThrows(IllegalArgumentException.class, () -> useCase.registrarCambioPrecioConMotivo("PRODUCTO", 1, "x", uno, null, 1, "u", "motivo"));
        assertThrows(IllegalArgumentException.class, () -> useCase.registrarCambioPrecioConMotivo("PRODUCTO", 1, "x", uno, dos, 1, "u", "abc"));
    }

    @Test
    void fachadaDelegaLosSeisMetodosPublicos() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CuentaCorrienteUseCase cuentas = new CuentaCorrienteUseCase() {
            public boolean registrarPagoProveedor(int id, BigDecimal monto, String metodo, Integer caja, int usuario, String nota) { calls.incrementAndGet(); return true; }
            public boolean registrarCobroCliente(int id, BigDecimal monto, String metodo, Integer caja, int usuario, String nota) { calls.incrementAndGet(); return true; }
            public int registrarVentaCreditoCorporativo(Venta venta, int cliente, int dias) { calls.incrementAndGet(); return 17; }
        };
        CambioPrecioUseCase precios = (tipo, id, nombre, anterior, nuevo, usuario, nombreUsuario, motivo) -> { calls.incrementAndGet(); return true; };
        FinanzasDocumentoPdfGenerator pdf = new FinanzasDocumentoPdfGenerator() {
            public void generarOrdenCompraPdf(int id, List<com.foodcore.model.Insumo> insumos, File destino) { calls.incrementAndGet(); }
            public void generarEstadoCuentaClientePdf(int id, File destino) { calls.incrementAndGet(); }
        };
        FinanzasService service = new FinanzasService(cuentas, precios, pdf);
        assertTrue(service.registrarPagoProveedor(1, BigDecimal.ONE, "EFECTIVO", null, 1, null));
        assertTrue(service.registrarCobroCliente(1, BigDecimal.ONE, "EFECTIVO", null, 1, null));
        assertEquals(17, service.registrarVentaCreditoCorporativo(new Venta(), 1, 30));
        assertTrue(service.registrarCambioPrecioConMotivo("PRODUCTO", 1, "x", BigDecimal.ONE, new BigDecimal("2.0000"), 1, "u", "motivo"));
        service.generarOrdenCompraPdf(1, null, new File("unused.pdf"));
        service.generarEstadoCuentaClientePdf(1, new File("unused.pdf"));
        assertEquals(6, calls.get());
    }
}
