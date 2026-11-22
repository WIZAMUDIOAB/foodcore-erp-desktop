package com.foodcore.controller.reportes;

import com.foodcore.controller.ReportesAvanzadosController;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.ComparativaPeriodos;
import com.foodcore.model.MargenCategoria;
import com.foodcore.model.ReporteCohortes;
import com.foodcore.model.ResultadoReporteAvanzado;
import com.foodcore.model.ResultadoReporteAvanzado.RebanadaPie;
import com.foodcore.service.FinanzasBiService;
import com.foodcore.service.ReporteService;
import com.foodcore.util.FormatoBiUiHelper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DefaultReportesAvanzadosPresenter implements ReportesAvanzadosPresenter {
    @FunctionalInterface
    public interface AnaliticaAvanzadaFn {
        ResultadoReporteAvanzado consultar(String categoria, LocalDate ini, LocalDate fin);
    }

    @FunctionalInterface
    public interface MargenesCategoriaFn {
        List<MargenCategoria> calcular(LocalDate ini, LocalDate fin);
    }

    private static final Logger LOGGER = Logger.getLogger(DefaultReportesAvanzadosPresenter.class.getName());
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final String MARGENES = "Márgenes por Categoría";
    private static final String COHORTES = "Cohortes y Retención de Clientes";
    private final AnaliticaAvanzadaFn analiticaFn;
    private final MargenesCategoriaFn margenesFn;
    private final Function<LocalDate, ReporteCohortes> cohortesFn;
    private final Function<LocalDate, ComparativaPeriodos> comparativaFn;

    public DefaultReportesAvanzadosPresenter() {
        this(new FinanzasBiService(), new ReporteService());
    }

    @FunctionalInterface
    private interface BiOperacion<T> {
        T ejecutar() throws SQLException, NegocioException;
    }

    private static <T> T ejecutarBi(BiOperacion<T> op) {
        try {
            return op.ejecutar();
        } catch (SQLException | NegocioException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    public DefaultReportesAvanzadosPresenter(FinanzasBiService finanzasBiService, ReporteService reporteService) {
        this(reporteService::consultarAnalisisAvanzado,
                (ini, fin) -> ejecutarBi(() -> finanzasBiService.calcularMargenesPorCategoria(ini, fin)),
                fecha -> ejecutarBi(() -> finanzasBiService.generarAnalisisCohortes(fecha)),
                fecha -> ejecutarBi(() -> finanzasBiService.obtenerComparativaPeriodos(fecha)));
    }

    public DefaultReportesAvanzadosPresenter(AnaliticaAvanzadaFn analiticaFn,
            MargenesCategoriaFn margenesFn,
            Function<LocalDate, ReporteCohortes> cohortesFn,
            Function<LocalDate, ComparativaPeriodos> comparativaFn) {
        this.analiticaFn = analiticaFn;
        this.margenesFn = margenesFn;
        this.cohortesFn = cohortesFn;
        this.comparativaFn = comparativaFn;
    }

    @Override
    public ResultadoAnalisisAvanzado consultarAnalisis(String categoria, LocalDate ini, LocalDate fin, LocalDate fechaRef) {
        ComparativaPeriodos comparativa = consultarComparativaSegura(fechaRef);
        if (MARGENES.equals(categoria)) {
            return consultarMargenesPorCategoria(ini, fin, comparativa);
        }
        if (COHORTES.equals(categoria)) {
            return consultarCohortes(fin, comparativa);
        }
        ResultadoReporteAvanzado res = analiticaFn.consultar(categoria, ini, fin);
        List<ReportesAvanzadosController.FilaReporteAvanzado> filas = new ArrayList<>();
        for (String[] fila : res.filas()) {
            filas.add(new ReportesAvanzadosController.FilaReporteAvanzado(fila[0], fila[1], fila[2], fila[3], fila[4]));
        }
        List<PieSlice> slices = new ArrayList<>();
        for (RebanadaPie rebanada : res.slices()) {
            slices.add(new PieSlice(rebanada.etiqueta(), rebanada.valor()));
        }
        return new ResultadoAnalisisAvanzado(res.headers(), filas, slices, res.montoTotal(), res.cantidadTotal(),
                res.topItem(), res.topValor(), comparativa, null, res.kpiMontoTotalText(), res.kpiSubTotalText(),
                res.kpiPromedioText(), res.kpiTopValorText());
    }

    private ComparativaPeriodos consultarComparativaSegura(LocalDate fechaRef) {
        try {
            return comparativaFn.apply(fechaRef);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "No se pudo consultar comparativa de períodos", ex);
            return new ComparativaPeriodos(fechaRef, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    private ResultadoAnalisisAvanzado consultarMargenesPorCategoria(LocalDate ini, LocalDate fin,
            ComparativaPeriodos comparativa) {
        String[] headers = {"Categoría", "Ingresos S/", "Costo Receta S/", "Margen Bruto S/", "Margen %"};
        List<ReportesAvanzadosController.FilaReporteAvanzado> filas = new ArrayList<>();
        List<PieSlice> slices = new ArrayList<>();
        double totalMargen = 0.0;
        double totalIngresos = 0.0;
        int totalCategorias = 0;
        String topItem = "-";
        double topValor = 0.0;
        BigDecimal topMargenPct = BigDecimal.ZERO;
        try {
            List<MargenCategoria> margenes = margenesFn.calcular(ini, fin);
            for (MargenCategoria mc : margenes) {
                filas.add(new ReportesAvanzadosController.FilaReporteAvanzado(mc.nombreCategoria(),
                        "S/ " + fmt2(mc.ingresosVenta()), "S/ " + fmt2(mc.costoReceta()),
                        "S/ " + fmt2(mc.margenBruto()), fmt1(mc.margenPorcentaje()) + "%"));
                slices.add(new PieSlice(mc.nombreCategoria() + " (" + fmt1(mc.margenPorcentaje()) + "%)",
                        Math.max(0.01, mc.margenBruto().doubleValue())));
                totalMargen += mc.margenBruto().doubleValue();
                totalIngresos += mc.ingresosVenta().doubleValue();
                totalCategorias++;
            }
            if (!margenes.isEmpty()) {
                MargenCategoria primero = margenes.get(0);
                topItem = primero.nombreCategoria();
                topValor = primero.margenBruto().doubleValue();
                topMargenPct = primero.margenPorcentaje();
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error calculando márgenes por categoría", ex);
        }
        double margenPromedioPct = totalIngresos > 0.0 ? totalMargen / totalIngresos * 100.0 : 0.0;
        String kpiSub = totalCategorias + String.format(" categorías · Ventas S/ %.2f", totalIngresos);
        String kpiProm = String.format("%.1f%% prom.", margenPromedioPct);
        String kpiTopSub = totalCategorias > 0
                ? String.format("Margen %s%% (S/ %.2f)", fmt1(topMargenPct), topValor)
                : "Sin datos en el rango";
        return new ResultadoAnalisisAvanzado(headers, filas, slices, totalMargen, totalCategorias, topItem, topValor,
                comparativa, null, null, kpiSub, kpiProm, kpiTopSub);
    }

    private ResultadoAnalisisAvanzado consultarCohortes(LocalDate fechaCorte, ComparativaPeriodos comparativa) {
        String[] headers = {"Segmento / Cohorte", "Clientes", "Retención / Mes 2", "Mes 3", "Criterio / Mes 4"};
        List<ReportesAvanzadosController.FilaReporteAvanzado> filas = new ArrayList<>();
        ReporteCohortes reporte;
        try {
            reporte = cohortesFn.apply(fechaCorte);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error generando análisis de cohortes", ex);
            reporte = new ReporteCohortes(fechaCorte, YearMonth.from(fechaCorte), 0, 0, 0, 0, false, List.of(), List.of());
        }
        for (ReporteCohortes.SegmentoRetencionFila seg : reporte.resumenRetencion()) {
            filas.add(new ReportesAvanzadosController.FilaReporteAvanzado(seg.segmento(), seg.cantidad() + " clientes",
                    FormatoBiUiHelper.formatearPorcentajeCohorte(seg.porcentaje()), "-", seg.criterio()));
        }
        for (ReporteCohortes.CohorteMensualFila coh : reporte.cohortes()) {
            filas.add(new ReportesAvanzadosController.FilaReporteAvanzado("Cohorte " + coh.etiquetaCohorte(),
                    coh.tamanoCohorte() + " clientes", FormatoBiUiHelper.formatearPorcentajeCohorte(coh.mes2Pct()),
                    FormatoBiUiHelper.formatearPorcentajeCohorte(coh.mes3Pct()),
                    FormatoBiUiHelper.formatearPorcentajeCohorte(coh.mes4Pct())));
        }
        int activosMes = reporte.clientesNuevosMes() + reporte.clientesRecurrentesMes();
        double tasaRecurrentePct = activosMes > 0 ? reporte.clientesRecurrentesMes() * 100.0 / activosMes : 0.0;
        return new ResultadoAnalisisAvanzado(headers, filas, List.of(), activosMes, reporte.totalClientesIdentificados(),
                reporte.clientesRecurrentesMes() + " recurrentes", reporte.clientesRecurrentesMes(), comparativa, reporte,
                reporte.totalClientesIdentificados() + " clientes",
                reporte.clientesNuevosMes() + " nuevos · " + reporte.clientesPerdidosMas60Dias() + " perdidos >60d",
                String.format("%.1f%% ret.", tasaRecurrentePct),
                reporte.clientesPerdidosMas60Dias() + " perdidos (> 60 días)");
    }

    @Override
    public String determinarModoActivo(String categoria) {
        return MARGENES.equals(categoria) ? "MARGENES" : COHORTES.equals(categoria) ? "COHORTES" : "ANALITICA";
    }

    @Override
    public String sanitizarNombre(String nom) {
        return nom == null ? "REPORTE" : nom.replaceAll("[^a-zA-Z0-9]", "_").toUpperCase(Locale.ROOT);
    }

    @Override
    public String fmt2(BigDecimal val) {
        BigDecimal v = val != null ? val.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return String.format(Locale.US, "%.2f", v.doubleValue());
    }

    @Override
    public String fmt1(BigDecimal val) {
        BigDecimal v = val != null ? val.setScale(1, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        return String.format(Locale.US, "%.1f", v.doubleValue());
    }

    @Override
    public double parsePctSeguro(String textoPct) {
        if (textoPct == null || textoPct.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(textoPct.replace("%", "").trim());
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    @Override
    public String resolverEstiloHeatmap(String item) {
        if ("-".equals(item)) return "-fx-text-fill: #A39D94; -fx-font-weight: normal;";
        double val = parsePctSeguro(item);
        if (val >= 50.0) return "-fx-background-color: #ECFDF5; -fx-text-fill: #059669; -fx-font-weight: bold;";
        if (val > 0.0) return "-fx-background-color: #FFFBEB; -fx-text-fill: #D97706; -fx-font-weight: bold;";
        return "-fx-background-color: #F7F5F1; -fx-text-fill: #5C5650;";
    }
}
