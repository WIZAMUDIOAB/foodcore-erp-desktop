package com.foodcore.controller.reportes;

import com.foodcore.controller.ReportesAvanzadosController;
import com.foodcore.model.ComparativaPeriodos;
import com.foodcore.model.MargenCategoria;
import com.foodcore.model.ReporteCohortes;
import com.foodcore.model.ResultadoReporteAvanzado;
import com.foodcore.model.ResultadoReporteAvanzado.RebanadaPie;
import com.foodcore.util.ExportFileChooserHelper.FormatoExportacion;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ReportesAvanzadosCoordinatorTest {
    private static final LocalDate INICIO = LocalDate.of(2025, 1, 1);
    private static final LocalDate FIN = LocalDate.of(2025, 1, 31);

    @Test
    void consultaAnaliticaEstandarYCapturaDatos() {
        AtomicReference<String> categoriaVista = new AtomicReference<>();
        DefaultReportesAvanzadosPresenter presenter = new DefaultReportesAvanzadosPresenter(
                (categoria, ini, fin) -> {
                    categoriaVista.set(categoria);
                    return new ResultadoReporteAvanzado(new String[]{"a", "b", "c", "d", "e"},
                            List.<String[]>of(new String[]{"1", "2", "3", "4", "5"}),
                            List.of(new RebanadaPie("efectivo", 12.5)), 12.5, 1, "efectivo", 12.5,
                            null, null, "total", "subtotal", "promedio", "top");
                }, (ini, fin) -> List.of(), fecha -> null, fecha -> comparativa(fecha));
        ReportesAvanzadosPresenter.ResultadoAnalisisAvanzado resultado = presenter.consultarAnalisis(
                ReportesAvanzadosController.REPORTE_MEDIO_PAGO, INICIO, FIN, FIN);
        assertEquals(ReportesAvanzadosController.REPORTE_MEDIO_PAGO, categoriaVista.get());
        assertEquals(List.of("a", "b", "c", "d", "e"), List.of(resultado.headers()));
        assertEquals("1", resultado.filas().get(0).c1);
        assertEquals("efectivo", resultado.slices().get(0).label());
        assertEquals(12.5, resultado.montoTotal());
        assertEquals("promedio", resultado.kpiPromedioText());
    }

    @Test
    void margenesCalculaKpisYAdmiteListaVacia() {
        List<MargenCategoria> categorias = List.of(
                margen("A", "100", "40", "60", "60"), margen("B", "50", "20", "30", "60"));
        DefaultReportesAvanzadosPresenter presenter = presenter(categorias, fecha -> null, fecha -> comparativa(fecha));
        ReportesAvanzadosPresenter.ResultadoAnalisisAvanzado resultado = presenter.consultarAnalisis(
                ReportesAvanzadosController.REPORTE_MARGENES_CATEGORIA, INICIO, FIN, FIN);
        assertEquals(90.0, resultado.montoTotal());
        assertEquals(2, resultado.cantidadTotal());
        assertEquals("A", resultado.topItem());
        assertEquals(60.0, resultado.topValor());
        assertEquals("60.0% prom.", resultado.kpiPromedioText());
        assertEquals("Margen 60.0% (S/ 60.00)", resultado.kpiTopValorText());
        assertEquals(2, resultado.filas().size());

        ReportesAvanzadosPresenter.ResultadoAnalisisAvanzado vacio = presenter(List.of(), fecha -> null,
                fecha -> comparativa(fecha)).consultarAnalisis(
                        ReportesAvanzadosController.REPORTE_MARGENES_CATEGORIA, INICIO, FIN, FIN);
        assertEquals(0, vacio.cantidadTotal());
        assertEquals("-", vacio.topItem());
        assertEquals("Sin datos en el rango", vacio.kpiTopValorText());
        assertTrue(vacio.filas().isEmpty());
    }

    @Test
    void cohortesCalculaRetencionYUsaFallbackAnteExcepcion() {
        ReporteCohortes reporte = new ReporteCohortes(FIN, YearMonth.from(FIN), 9, 3, 6, 2, true,
                List.of(new ReporteCohortes.CohorteMensualFila(YearMonth.from(FIN), "2025-01", 4,
                        BigDecimal.valueOf(100), BigDecimal.valueOf(75), BigDecimal.valueOf(50), BigDecimal.valueOf(25))),
                List.of(new ReporteCohortes.SegmentoRetencionFila("Recurrentes", 6,
                        BigDecimal.valueOf(66.7), "Compra repetida")));
        ReportesAvanzadosPresenter.ResultadoAnalisisAvanzado resultado = presenter(List.of(), fecha -> reporte,
                fecha -> comparativa(fecha)).consultarAnalisis(
                        ReportesAvanzadosController.REPORTE_COHORTES_CLIENTES, INICIO, FIN, FIN);
        assertEquals("66.7% ret.", resultado.kpiPromedioText());
        assertEquals("6 recurrentes", resultado.topItem());
        assertEquals(9, resultado.montoTotal());
        assertEquals(9, resultado.cantidadTotal());
        assertEquals(2, resultado.filas().size());

        ReportesAvanzadosPresenter.ResultadoAnalisisAvanzado fallback = presenter(List.of(), fecha -> {
            throw new IllegalStateException("fallo simulado");
        }, fecha -> comparativa(fecha)).consultarAnalisis(
                ReportesAvanzadosController.REPORTE_COHORTES_CLIENTES, INICIO, FIN, FIN);
        assertNotNull(fallback.cohortesReporte());
        assertTrue(fallback.filas().isEmpty());
        assertEquals("0.0% ret.", fallback.kpiPromedioText());
    }

    @Test
    void comparativaFallaConResultadoSeguroYFuncionesAuxiliares() {
        DefaultReportesAvanzadosPresenter presenter = new DefaultReportesAvanzadosPresenter(
                (categoria, ini, fin) -> resultadoVacio(), (ini, fin) -> List.of(), fecha -> null,
                fecha -> { throw new IllegalStateException("fallo simulado"); });
        assertNotNull(presenter.consultarAnalisis(ReportesAvanzadosController.REPORTE_MEDIO_PAGO,
                INICIO, FIN, FIN).comparativa());
        assertEquals("ANALITICA", presenter.determinarModoActivo(ReportesAvanzadosController.REPORTE_MEDIO_PAGO));
        assertEquals("MARGENES", presenter.determinarModoActivo(ReportesAvanzadosController.REPORTE_MARGENES_CATEGORIA));
        assertEquals("COHORTES", presenter.determinarModoActivo(ReportesAvanzadosController.REPORTE_COHORTES_CLIENTES));
        assertEquals("ANALITICA", presenter.determinarModoActivo(null));
        assertEquals("A_B_1", presenter.sanitizarNombre("a-b 1"));
        assertEquals("REPORTE", presenter.sanitizarNombre(null));
        assertEquals("1.24", presenter.fmt2(new BigDecimal("1.235")));
        assertEquals("0.00", presenter.fmt2(null));
        assertEquals("1.3", presenter.fmt1(new BigDecimal("1.25")));
        assertEquals("0.0", presenter.fmt1(null));
        assertEquals(12.5, presenter.parsePctSeguro(" 12.5% "));
        assertEquals(0.0, presenter.parsePctSeguro("inválido"));
        assertEquals(0.0, presenter.parsePctSeguro(null));
        assertEquals("-fx-text-fill: #A39D94; -fx-font-weight: normal;", presenter.resolverEstiloHeatmap("-"));
        assertEquals("-fx-background-color: #ECFDF5; -fx-text-fill: #059669; -fx-font-weight: bold;",
                presenter.resolverEstiloHeatmap("50%"));
        assertEquals("-fx-background-color: #FFFBEB; -fx-text-fill: #D97706; -fx-font-weight: bold;",
                presenter.resolverEstiloHeatmap("0.1%"));
        assertEquals("-fx-background-color: #F7F5F1; -fx-text-fill: #5C5650;",
                presenter.resolverEstiloHeatmap("0%"));
    }

    @Test
    void prepararSolicitudPorFormato() {
        DefaultReporteExportCoordinator coordinator = coordinator((titulo, headers, filas, destino) -> { },
                (titulo, headers, filas, destino) -> { }, (titulo, headers, filas, destino) -> { });
        assertSolicitud(coordinator.prepararSolicitud(FormatoExportacion.PDF, "Ventas & pagos"), "Pdf", ".pdf");
        assertSolicitud(coordinator.prepararSolicitud(FormatoExportacion.EXCEL, "Ventas & pagos"), "Excel", ".xlsx");
        assertSolicitud(coordinator.prepararSolicitud(FormatoExportacion.CSV, "Ventas & pagos"), "Csv", ".csv");
    }

    @Test
    void convertirFilasConservaCincoColumnas() {
        DefaultReporteExportCoordinator coordinator = coordinator((titulo, headers, filas, destino) -> { },
                (titulo, headers, filas, destino) -> { }, (titulo, headers, filas, destino) -> { });
        List<List<String>> filas = coordinator.convertirFilas(List.of(
                new ReportesAvanzadosController.FilaReporteAvanzado("1", "2", "3", "4", "5")));
        assertEquals(List.of(List.of("1", "2", "3", "4", "5")), filas);
    }

    @Test
    void ejecutarExportacionCreaDirectoriosYDelegadaAlFormatoCorrecto(@TempDir Path tempDir) throws Exception {
        for (FormatoExportacion formato : List.of(FormatoExportacion.PDF, FormatoExportacion.EXCEL, FormatoExportacion.CSV)) {
            AtomicInteger llamada = new AtomicInteger();
            AtomicReference<String> titulo = new AtomicReference<>();
            AtomicReference<List<String>> headersRecibidos = new AtomicReference<>();
            AtomicReference<List<List<String>>> filasRecibidas = new AtomicReference<>();
            AtomicReference<File> destinoRecibido = new AtomicReference<>();
            DefaultReporteExportCoordinator coordinator = coordinator(
                    (t, h, f, d) -> capturar(0, llamada, titulo, headersRecibidos, filasRecibidas, destinoRecibido, t, h, f, d),
                    (t, h, f, d) -> capturar(1, llamada, titulo, headersRecibidos, filasRecibidas, destinoRecibido, t, h, f, d),
                    (t, h, f, d) -> capturar(2, llamada, titulo, headersRecibidos, filasRecibidas, destinoRecibido, t, h, f, d));
            File destino = tempDir.resolve(formato.name()).resolve("nested").resolve("out." + formato.name().toLowerCase()).toFile();
            List<String> headers = List.of("h1", "h2");
            List<List<String>> filas = List.of(List.of("a", "b"));
            coordinator.ejecutarExportacion(formato, "Reporte prueba", headers, filas, destino);
            int esperado = formato == FormatoExportacion.PDF ? 0 : formato == FormatoExportacion.EXCEL ? 1 : 2;
            assertEquals(esperado, llamada.get());
            assertEquals("Reporte prueba", titulo.get());
            assertEquals(headers, headersRecibidos.get());
            assertEquals(filas, filasRecibidas.get());
            assertEquals(destino, destinoRecibido.get());
            assertTrue(destino.getParentFile().isDirectory());
        }
    }

    private static void capturar(int id, AtomicInteger llamada, AtomicReference<String> titulo,
            AtomicReference<List<String>> headersRecibidos, AtomicReference<List<List<String>>> filasRecibidas,
            AtomicReference<File> destinoRecibido, String tituloArg, List<String> headers,
            List<List<String>> filas, File destino) {
        llamada.set(id);
        titulo.set(tituloArg);
        headersRecibidos.set(headers);
        filasRecibidas.set(filas);
        destinoRecibido.set(destino);
    }

    private static void assertSolicitud(ReporteExportCoordinator.SolicitudExportacion solicitud,
            String formato, String extension) {
        assertEquals("Guardar Reporte en " + formato, solicitud.tituloDialogo());
        assertEquals("REPORTE_VENTAS___PAGOS" + extension, solicitud.nombreSugerido());
        assertEquals("Exportando " + formato + "...", solicitud.estadoProcesando());
        assertEquals("✓ " + formato + " exportado: ", solicitud.prefijoExito());
        assertEquals("Error al exportar " + formato + ": ", solicitud.prefijoError());
    }

    private static DefaultReporteExportCoordinator coordinator(DefaultReporteExportCoordinator.ExportadorArchivoFn pdf,
            DefaultReporteExportCoordinator.ExportadorArchivoFn excel,
            DefaultReporteExportCoordinator.ExportadorArchivoFn csv) {
        return new DefaultReporteExportCoordinator(pdf, excel, csv);
    }

    private static DefaultReportesAvanzadosPresenter presenter(List<MargenCategoria> margenes,
            java.util.function.Function<LocalDate, ReporteCohortes> cohortes,
            java.util.function.Function<LocalDate, ComparativaPeriodos> comparativa) {
        return new DefaultReportesAvanzadosPresenter((categoria, ini, fin) -> resultadoVacio(),
                (ini, fin) -> margenes, cohortes, comparativa);
    }

    private static MargenCategoria margen(String nombre, String ingresos, String costo, String bruto, String pct) {
        return new MargenCategoria(1, nombre, 1, new BigDecimal(ingresos), new BigDecimal(costo),
                new BigDecimal(bruto), new BigDecimal(pct));
    }

    private static ResultadoReporteAvanzado resultadoVacio() {
        return new ResultadoReporteAvanzado(null, List.of(), List.of(), 0, 0, "-", 0,
                null, null, "", "", "", "");
    }

    private static ComparativaPeriodos comparativa(LocalDate fecha) {
        return new ComparativaPeriodos(fecha, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
