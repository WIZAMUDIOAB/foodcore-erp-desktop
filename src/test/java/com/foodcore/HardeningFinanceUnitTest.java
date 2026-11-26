package com.foodcore;

import com.foodcore.dao.impl.RolDAOImpl;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.ComprobantePago;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Pedido;
import com.foodcore.model.Promocion;
import com.foodcore.model.Usuario;
import com.foodcore.model.Venta;
import com.foodcore.model.dto.PagoSplit;
import com.foodcore.security.AutorizacionService;
import com.foodcore.service.AlertaService;
import com.foodcore.service.AuthService;
import com.foodcore.service.DemoDataService;
import com.foodcore.service.ExportacionContableService;
import com.foodcore.service.FinanzasService;
import com.foodcore.service.ImportacionMasivaService;
import com.foodcore.service.PromocionService;
import com.foodcore.service.VentaService;
import com.foodcore.service.impl.VentaServiceImpl;
import com.foodcore.util.ExportFileChooserHelper;
import com.foodcore.util.ImpresoraTermicaService;
import com.foodcore.util.ToastService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas unitarias puras (sin dependencia de MySQL) para verificar en aislamiento
 * las reglas críticas de endurecimiento v5.7.1.
 */
class HardeningFinanceUnitTest {

    @Test
    @DisplayName("AutorizacionService fail-closed ante fallo de BD")
    void testAutorizacionFailClosed() {
        AutorizacionService authz = new AutorizacionService(
                new RolDAOImpl(),
                () -> {
                    throw new SQLException("Simulated DB outage");
                });
        assertTrue(authz.obtenerPermisosPorRol("ADMIN").isEmpty());
        assertTrue(authz.obtenerPermisosPorRol("SUPERVISOR").isEmpty());
        assertTrue(authz.obtenerPermisosPorRol("").isEmpty());
    }

    @Test
    @DisplayName("FinanzasService y VentaService rechazan montos NaN/Inf/<=0 y spoofing de puntos antes de tocar BD")
    void testValidacionesPreviasFinanzasYVenta() {
        FinanzasService fin = new FinanzasService();
        assertThrows(IllegalArgumentException.class,
                () -> fin.registrarPagoProveedor(1, null, "EFECTIVO", null, 1, "Null"));
        assertThrows(IllegalArgumentException.class,
                () -> fin.registrarPagoProveedor(1, new BigDecimal("-10.00"), "EFECTIVO", null, 1, "Neg"));
        assertThrows(IllegalArgumentException.class,
                () -> fin.registrarCobroCliente(1, BigDecimal.ZERO, "EFECTIVO", null, 1, "Zero"));
        assertThrows(IllegalArgumentException.class,
                () -> fin.registrarCambioPrecioConMotivo("PRODUCTO", 1, "P", new BigDecimal("10.0000"), new BigDecimal("-5.0000"), 1, "Neg", null));

        VentaService vs = new VentaServiceImpl();
        DetalleVenta d50 = new DetalleVenta(0, 0, 1, "Plato", 1, new BigDecimal("50.00"), new BigDecimal("50.00"));
        Venta spoof = new Venta();
        spoof.setIdUsuario(1);
        spoof.setPuntosACanjear(0);
        spoof.setDescuentoPuntos(new BigDecimal("50.00"));
        assertThrows(NegocioException.class, () -> vs.registrarVenta(spoof, List.of(d50), null));

        DetalleVenta dZero = new DetalleVenta(0, 0, 1, "Gratis", 1, BigDecimal.ZERO, BigDecimal.ZERO);
        Venta vOk = new Venta();
        vOk.setIdUsuario(1);
        assertThrows(NegocioException.class, () -> vs.registrarVenta(vOk, List.of(dZero), null));

        assertThrows(NegocioException.class, () -> vs.registrarVentaDividida(
                vOk, List.of(d50), null,
                List.of(new PagoSplit("EFECTIVO", new BigDecimal("60.00")),
                        new PagoSplit("TARJETA", new BigDecimal("-10.00")))));
    }

    @Test
    @DisplayName("ExportacionContableService neutraliza Formula Injection en CSV y Excel")
    void testFormulaInjectionSanitization(@TempDir Path tempDir) throws Exception {
        assertEquals("'=CMD|' /C calc'!A0", ExportacionContableService.sanitizarFormulaCelda("=CMD|' /C calc'!A0"));
        assertEquals("'+1+1", ExportacionContableService.sanitizarFormulaCelda("+1+1"));
        assertEquals("'-2+3", ExportacionContableService.sanitizarFormulaCelda("-2+3"));
        assertEquals("'@SUM(A1)", ExportacionContableService.sanitizarFormulaCelda("@SUM(A1)"));
        assertEquals("'\tTAB", ExportacionContableService.sanitizarFormulaCelda("\tTAB"));
        assertEquals("'\rCR", ExportacionContableService.sanitizarFormulaCelda("\rCR"));
        assertEquals("Normal", ExportacionContableService.sanitizarFormulaCelda("Normal"));

        ExportacionContableService exp = new ExportacionContableService(null, null, null);
        File csv = tempDir.resolve("safe.csv").toFile();
        exp.exportarReporteGenericoCsv(List.of("Col"), List.of(List.of("=1+1"), List.of("+2+2")), csv);
        String txt = Files.readString(csv.toPath(), StandardCharsets.UTF_8);
        assertTrue(txt.contains("\"'=1+1\""));
        assertTrue(txt.contains("\"'+2+2\""));
    }

    @Test
    @DisplayName("ImportacionMasivaService rechaza >10MB y fórmulas Excel sin evaluarlas")
    void testImportacionLimitesYFormulas(@TempDir Path tempDir) throws Exception {
        ImportacionMasivaService imp = new ImportacionMasivaService(null, null, null, null);

        File bigFile = tempDir.resolve("oversized.csv").toFile();
        try (RandomAccessFile raf = new RandomAccessFile(bigFile, "rw")) {
            raf.setLength(ImportacionMasivaService.MAX_FILE_SIZE_BYTES + 1L);
        }
        assertThrows(IllegalArgumentException.class, () -> imp.prevalidarArchivoInsumos(bigFile));

        File formulaXlsx = tempDir.resolve("formula.xlsx").toFile();
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("Insumos");
            Row h = sh.createRow(0);
            String[] cols = {"Codigo_SKU", "Nombre_Insumo", "Unidad_Medida", "Stock_Actual", "Stock_Minimo", "Costo_Unitario"};
            for (int i = 0; i < cols.length; i++) {
                h.createCell(i).setCellValue(cols[i]);
            }
            Row r = sh.createRow(1);
            r.createCell(0).setCellValue("SKU-1");
            r.createCell(1).setCellValue("Insumo");
            r.createCell(2).setCellValue("KG");
            r.createCell(3).setCellFormula("SUM(1,2)");
            r.createCell(4).setCellValue(1);
            r.createCell(5).setCellValue(2);
            try (FileOutputStream fos = new FileOutputStream(formulaXlsx)) {
                wb.write(fos);
            }
        }
        assertThrows(IllegalArgumentException.class, () -> imp.prevalidarArchivoInsumos(formulaXlsx));
    }

    @Test
    @DisplayName("ImpresoraTermicaService neutraliza inyección ESC/POS, preserva fecha original y retorna false en fallo")
    void testImpresoraEscPosYFechaOriginal(@TempDir Path tempDir) throws Exception {
        ImpresoraTermicaService imp = new ImpresoraTermicaService(
                new AutorizacionService(), tempDir.resolve("tickets_unit.log"));
        LocalDateTime fechaOrig = LocalDateTime.of(2026, 3, 21, 19, 15, 30);
        Venta v = new Venta();
        v.setIdVenta(101);
        v.setFechaHora(fechaOrig);
        v.setTotal(new BigDecimal("30.00"));
        v.setMetodoPago("EFECTIVO");

        DetalleVenta dv = new DetalleVenta(1, 101, 1, "Plato\u001Bp\u0000\u0019Evil", 1, new BigDecimal("30.00"), new BigDecimal("30.00"));
        ComprobantePago comp = new ComprobantePago(1, 101, "BOLETA", "B001", "000101", new BigDecimal("30.00"));
        comp.setNombreCliente("Cli\u001B@X");

        List<String> lineas = imp.formatearLineasTicket(v, List.of(dv), comp, true);
        assertTrue(lineas.stream().anyMatch(l -> l.contains("21/03/2026 19:15:30")));
        byte[] bytes = imp.generarBytesEscPos(lineas, true);
        for (int i = 2; i < bytes.length - 4; i++) {
            assertNotEquals((byte) 0x1B, bytes[i]);
        }

        Pedido p = new Pedido();
        p.setIdPedido(5);
        p.setNumeroMesa("01");
        p.setTotal(new BigDecimal("30.00"));
        DetallePedido dp = new DetallePedido(1, 5, 1, 1, new BigDecimal("30.00"), new BigDecimal("30.00"), "");
        dp.setNombreProducto("Lomo");
        assertFalse(imp.imprimirComandaCocina(p, List.of(dp), "NON_EXISTENT_PRINTER_9999"));
        assertFalse(imp.imprimirPreCuenta(p, List.of(dp), "NON_EXISTENT_PRINTER_9999"));
        assertTrue(imp.imprimirComandaCocina(p, List.of(dp), null));
    }

    @Test
    @DisplayName("AlertaService valida email/CRLF, libera monitorLock en red y reintenta si falla envío")
    void testAlertaServiceLockYReintento() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> AlertaService.validarEmailSeguro("x@y.com\r\nDATA", "destinatario"));

        AtomicLong clock = new AtomicLong(1_000_000L);
        AtomicInteger calls = new AtomicInteger(0);
        CountDownLatch inNetwork = new CountDownLatch(1);
        CountDownLatch releaseNetwork = new CountDownLatch(1);

        AlertaService svc = new AlertaService(clock::get, (cfg, dest, subj, body) -> {
            if (calls.incrementAndGet() == 1) {
                inNetwork.countDown();
                try {
                    releaseNetwork.await(3, TimeUnit.SECONDS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                return false;
            }
            return true;
        });
        svc.actualizarConfiguracion(new AlertaService.ConfiguracionAlertaEmail(
                "127.0.0.1", 587, "alertas@foodcore.pe", "", false, "dueno@foodcore.pe", true), false);

        long lowDisk = 1024L * 1024L * 1024L;
        Thread t = new Thread(() -> svc.evaluar(true, 30.0, lowDisk));
        t.start();
        assertTrue(inNetwork.await(3, TimeUnit.SECONDS));

        Thread reader = new Thread(svc::getHistorialAlertas);
        reader.start();
        reader.join(1500L);
        assertFalse(reader.isAlive(), "monitorLock debe estar libre durante la llamada SMTP");

        releaseNetwork.countDown();
        t.join(3000L);

        List<AlertaService.AlertaEmitida> retry = svc.evaluar(true, 30.0, lowDisk);
        assertEquals(1, retry.size());
        assertTrue(retry.get(0).enviadoPorRed());
        assertTrue(svc.evaluar(true, 30.0, lowDisk).isEmpty());
    }

    @Test
    @DisplayName("Promocion nocturna, DemoDataService RBAC, ExportFileChooser filtro único y ToastService.mostrarInfo")
    void testPromosDemoFileChooserYToast(@TempDir Path tempDir) throws Exception {
        Promocion p = new Promocion();
        p.setDiasSemana("VIE");
        p.setHoraInicio(LocalTime.of(22, 0));
        p.setHoraFin(LocalTime.of(2, 0));
        p.setTipoPromocion("MONTO_FIJO");
        p.setValorDescuento(new BigDecimal("10.00"));
        p.setNombre("Happy Hour Nocturno");

        PromocionService ps = new PromocionService(new com.foodcore.dao.impl.PromocionDAOImpl() {
            @Override
            public List<Promocion> listarActivas(Connection conn) {
                return List.of(p);
            }
        }) {
            @Override
            public List<Promocion> listarActivas() {
                return List.of(p);
            }
        };
        DetalleVenta dv = new DetalleVenta(0, 0, 1, "Drink", 2, new BigDecimal("20.00"), new BigDecimal("40.00"));
        assertTrue(ps.evaluarPromociones(List.of(dv), new BigDecimal("40.00"), DayOfWeek.FRIDAY, LocalTime.of(23, 0)).tieneDescuento());
        assertTrue(ps.evaluarPromociones(List.of(dv), new BigDecimal("40.00"), DayOfWeek.SATURDAY, LocalTime.of(1, 30)).tieneDescuento());
        assertFalse(ps.evaluarPromociones(List.of(dv), new BigDecimal("40.00"), DayOfWeek.SATURDAY, LocalTime.of(23, 0)).tieneDescuento());

        DemoDataService demo = new DemoDataService();
        AuthService.logout();
        assertThrows(SecurityException.class, demo::cargarDatosDemo);
        Usuario mozo = new Usuario(3, 3, 3, "mozo1", "h", true);
        mozo.setNombreRol("MOZO");
        AuthService.setUsuarioSesionActual(mozo);
        try {
            assertThrows(SecurityException.class, demo::cargarDatosDemo);
            assertThrows(SecurityException.class, () -> demo.cargarDatosDemo(Path.of("scripts", "seed-demo.sql")));
        } finally {
            AuthService.logout();
        }

        Preferences prefs = Preferences.userRoot().node("fc-unit-" + UUID.randomUUID());
        try {
            ExportFileChooserHelper helper = ExportFileChooserHelper.conPreferencias(prefs);
            AtomicReference<ExportFileChooserHelper.FileChooserRequest> reqRef = new AtomicReference<>();
            ExportFileChooserHelper.setTestSelectorOverride(req -> {
                reqRef.set(req);
                return Optional.of(tempDir.resolve("out.pdf").toFile());
            });
            Optional<File> f = helper.seleccionarArchivoGuardar(
                    null, "Exportar", "rep", ExportFileChooserHelper.FormatoExportacion.EXCEL);
            assertTrue(f.isPresent());
            assertEquals(1, reqRef.get().filtros().size());
            assertEquals(List.of("*.xlsx"), reqRef.get().filtros().get(0).getExtensions());
            assertTrue(f.get().getName().endsWith(".xlsx"));
        } finally {
            ExportFileChooserHelper.clearTestSelectorOverride();
            prefs.removeNode();
        }

        ToastService.mostrarInfo(null, "Info test");
        assertEquals(ToastService.TipoToast.INFO, ToastService.getUltimoTipoToast());
    }

    @Test
    @DisplayName("ExportacionContableService genera PDF válido sin Stream Closed y VentaService acota medio_pago <= 15")
    void testExportacionPdfValidoYNormalizacionMedioPago(@TempDir Path tempDir) throws Exception {
        ExportacionContableService exp = new ExportacionContableService();
        File pdf = tempDir.resolve("reporte-prueba.pdf").toFile();
        exp.exportarReporteGenericoPdf(
                "Reporte Hardening",
                List.of("Columna", "Valor"),
                List.of(List.of("Item 1", "S/ 45.00"), List.of("Item 2", "S/ 55.00")),
                pdf
        );
        byte[] bytes = Files.readAllBytes(pdf.toPath());
        assertTrue(bytes.length > 100, "El PDF exportado debe contener contenido válido");
        assertEquals("%PDF-", new String(bytes, 0, 5, StandardCharsets.US_ASCII));

        assertEquals("CREDITO", VentaService.normalizarMedioPagoMovimientoCaja("CREDITO_CORPORATIVO"));
        assertEquals("EFECTIVO", VentaService.normalizarMedioPagoMovimientoCaja(null));
        assertEquals("EFECTIVO", VentaService.normalizarMedioPagoMovimientoCaja("   "));
        assertTrue(VentaService.normalizarMedioPagoMovimientoCaja("MEDIO_EXTREMADAMENTE_LARGO").length() <= 15);
    }
}
