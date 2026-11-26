package com.foodcore.controller.reportes;

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
        List<FilaReporteAvanzado> filas = new ArrayList<>();
        for (String[] fila : res.filas()) {
            filas.add(new FilaReporteAvanzado(fila[0], fila[1], fila[2], fila[3], fila[4]));
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
        List<FilaReporteAvanzado> filas = new ArrayList<>();
        List<PieSlice> slices = new ArrayList<>();
        BigDecimal cero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal minSlice = new BigDecimal("0.01");
        BigDecimal cien = new BigDecimal("100");
        BigDecimal totalMargen = cero;
        BigDecimal totalIngresos = cero;
        int totalCategorias = 0;
        String topItem = "-";
        BigDecimal topValor = cero;
        BigDecimal topMargenPct = BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        try {
            List<MargenCategoria> margenes = margenesFn.calcular(ini, fin);
            for (MargenCategoria mc : margenes) {
                BigDecimal ingresos = mc.ingresosVenta() != null ? mc.ingresosVenta().setScale(2, RoundingMode.HALF_UP) : cero;
                BigDecimal costo = mc.costoReceta() != null ? mc.costoReceta().setScale(2, RoundingMode.HALF_UP) : cero;
                BigDecimal margen = mc.margenBruto() != null ? mc.margenBruto().setScale(2, RoundingMode.HALF_UP) : cero;
                filas.add(new FilaReporteAvanzado(mc.nombreCategoria(),
                        "S/ " + fmt2(ingresos), "S/ " + fmt2(costo),
                        "S/ " + fmt2(margen), fmt1(mc.margenPorcentaje()) + "%"));
                slices.add(new PieSlice(mc.nombreCategoria() + " (" + fmt1(mc.margenPorcentaje()) + "%)",
                        margen.max(minSlice)));
                totalMargen = totalMargen.add(margen).setScale(2, RoundingMode.HALF_UP);
                totalIngresos = totalIngresos.add(ingresos).setScale(2, RoundingMode.HALF_UP);
                totalCategorias++;
            }
            if (!margenes.isEmpty()) {
                MargenCategoria primero = margenes.get(0);
                topItem = primero.nombreCategoria();
                topValor = primero.margenBruto() != null ? primero.margenBruto().setScale(2, RoundingMode.HALF_UP) : cero;
                topMargenPct = primero.margenPorcentaje();
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error calculando márgenes por categoría", ex);
        }
        BigDecimal margenPromedioPct = totalIngresos.compareTo(BigDecimal.ZERO) > 0
                ? totalMargen.multiply(cien).divide(totalIngresos, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        String kpiSub = totalCategorias + String.format(Locale.US, " categorías · Ventas S/ %.2f", totalIngresos);
        String kpiProm = String.format(Locale.US, "%.1f%% prom.", margenPromedioPct);
        String kpiTopSub = totalCategorias > 0
                ? String.format(Locale.US, "Margen %s%% (S/ %.2f)", fmt1(topMargenPct), topValor)
                : "Sin datos en el rango";
        return new ResultadoAnalisisAvanzado(headers, filas, slices, totalMargen, totalCategorias, topItem, topValor,
                comparativa, null, null, kpiSub, kpiProm, kpiTopSub);
    }

    private ResultadoAnalisisAvanzado consultarCohortes(LocalDate fechaCorte, ComparativaPeriodos comparativa) {
        String[] headers = {"Segmento / Cohorte", "Clientes", "Retención / Mes 2", "Mes 3", "Criterio / Mes 4"};
        List<FilaReporteAvanzado> filas = new ArrayList<>();
        ReporteCohortes reporte;
        try {
            reporte = cohortesFn.apply(fechaCorte);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error generando análisis de cohortes", ex);
            reporte = new ReporteCohortes(fechaCorte, YearMonth.from(fechaCorte), 0, 0, 0, 0, false, List.of(), List.of());
        }
        for (ReporteCohortes.SegmentoRetencionFila seg : reporte.resumenRetencion()) {
            filas.add(new FilaReporteAvanzado(seg.segmento(), seg.cantidad() + " clientes",
                    FormatoBiUiHelper.formatearPorcentajeCohorte(seg.porcentaje()), "-", seg.criterio()));
        }
        for (ReporteCohortes.CohorteMensualFila coh : reporte.cohortes()) {
            filas.add(new FilaReporteAvanzado("Cohorte " + coh.etiquetaCohorte(),
                    coh.tamanoCohorte() + " clientes", FormatoBiUiHelper.formatearPorcentajeCohorte(coh.mes2Pct()),
                    FormatoBiUiHelper.formatearPorcentajeCohorte(coh.mes3Pct()),
                    FormatoBiUiHelper.formatearPorcentajeCohorte(coh.mes4Pct())));
        }
        int activosMes = reporte.clientesNuevosMes() + reporte.clientesRecurrentesMes();
        BigDecimal tasaRecurrentePct = activosMes > 0
                ? BigDecimal.valueOf(reporte.clientesRecurrentesMes())
                        .multiply(new BigDecimal("100"))
                        .divide(BigDecimal.valueOf(activosMes), 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        return new ResultadoAnalisisAvanzado(headers, filas, List.of(),
                BigDecimal.valueOf(activosMes).setScale(2, RoundingMode.HALF_UP),
                reporte.totalClientesIdentificados(),
                reporte.clientesRecurrentesMes() + " recurrentes",
                BigDecimal.valueOf(reporte.clientesRecurrentesMes()).setScale(2, RoundingMode.HALF_UP),
                comparativa, reporte,
                reporte.totalClientesIdentificados() + " clientes",
                reporte.clientesNuevosMes() + " nuevos · " + reporte.clientesPerdidosMas60Dias() + " perdidos >60d",
                String.format(Locale.US, "%.1f%% ret.", tasaRecurrentePct),
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
        return v.toPlainString();
    }

    @Override
    public String fmt1(BigDecimal val) {
        BigDecimal v = val != null ? val.setScale(1, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        return v.toPlainString();
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
        if ("-".equals(item)) {
            return "-fx-text-fill: #A39D94; -fx-font-weight: normal;";
        }
        double val = parsePctSeguro(item);
        if (val >= 50.0) {
            return "-fx-background-color: #ECFDF5; -fx-text-fill: #059669; -fx-font-weight: bold;";
        }
        if (val > 0.0) {
            return "-fx-background-color: #FFFBEB; -fx-text-fill: #D97706; -fx-font-weight: bold;";
        }
        return "-fx-background-color: #F7F5F1; -fx-text-fill: #5C5650;";
    }
}
