package com.foodcore.dao.impl;

import com.foodcore.dao.FinanzasBiDAO;
import com.foodcore.model.ComparativaPeriodos;
import com.foodcore.model.EstadoResultadosReporte;
import com.foodcore.model.EstadoResultadosReporte.GastoPorCategoria;
import com.foodcore.model.EstadoResultadosReporte.LineaPnl;
import com.foodcore.model.MargenCategoria;
import com.foodcore.model.ReporteCohortes;
import com.foodcore.model.ReporteCohortes.CohorteMensualFila;
import com.foodcore.model.ReporteCohortes.SegmentoRetencionFila;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Implementación JDBC de consultas analíticas de Finanzas y BI (v4.9.0).
 */
public class FinanzasBiDAOImpl implements FinanzasBiDAO {

    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final DateTimeFormatter FMT_COHORTE =
            DateTimeFormatter.ofPattern("yyyy-MM (MMM)", Locale.forLanguageTag("es-PE"));

    @Override
    public EstadoResultadosReporte generarEstadoResultados(Connection conn, LocalDate desde, LocalDate hasta)
            throws SQLException {
        Date sqlDesde = Date.valueOf(desde);
        Date sqlHasta = Date.valueOf(hasta);

        int cantidadVentas = 0;
        BigDecimal ingresosBrutos = CERO;
        BigDecimal subtotalGravado = CERO;
        BigDecimal igvVentas = CERO;

        String sqlVentas = "SELECT COUNT(*) AS cnt, "
                + "COALESCE(SUM(total), 0.00) AS total_ventas, "
                + "COALESCE(SUM(subtotal), 0.00) AS subtotal_ventas, "
                + "COALESCE(SUM(igv), 0.00) AS igv_ventas "
                + "FROM venta "
                + "WHERE estado = 'COMPLETADA' AND DATE(fecha_hora) BETWEEN ? AND ?";
        try (PreparedStatement ps = conn.prepareStatement(sqlVentas)) {
            ps.setDate(1, sqlDesde);
            ps.setDate(2, sqlHasta);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    cantidadVentas = rs.getInt("cnt");
                    ingresosBrutos = normalizarMoneda(rs.getBigDecimal("total_ventas"));
                    subtotalGravado = normalizarMoneda(rs.getBigDecimal("subtotal_ventas"));
                    igvVentas = normalizarMoneda(rs.getBigDecimal("igv_ventas"));
                }
            }
        }

        BigDecimal costoVentas = calcularCostoVentasPeriodo(conn, sqlDesde, sqlHasta);

        List<GastoTemp> gastosTemp = new ArrayList<>();
        BigDecimal gastosOperativos = CERO;
        String sqlGastos = "SELECT COALESCE(cg.nombre, 'Otros / Sin Categoría') AS categoria, "
                + "COUNT(*) AS ops, COALESCE(SUM(ABS(m.monto)), 0.00) AS monto "
                + "FROM movimiento_caja m "
                + "LEFT JOIN categoria_gasto cg ON m.id_categoria_gasto = cg.id_categoria_gasto "
                + "WHERE m.tipo_movimiento = 'EGRESO' "
                + "AND m.concepto NOT LIKE 'Cierre Z de turno%' "
                + "AND DATE(m.fecha_hora) BETWEEN ? AND ? "
                + "GROUP BY COALESCE(cg.nombre, 'Otros / Sin Categoría') "
                + "ORDER BY monto DESC, categoria ASC";
        try (PreparedStatement ps = conn.prepareStatement(sqlGastos)) {
            ps.setDate(1, sqlDesde);
            ps.setDate(2, sqlHasta);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BigDecimal montoCat = normalizarMoneda(rs.getBigDecimal("monto"));
                    gastosTemp.add(new GastoTemp(rs.getString("categoria"), rs.getInt("ops"), montoCat));
                    gastosOperativos = gastosOperativos.add(montoCat);
                }
            }
        }
        gastosOperativos = normalizarMoneda(gastosOperativos);

        List<GastoPorCategoria> gastosPorCategoria = new ArrayList<>();
        for (GastoTemp g : gastosTemp) {
            BigDecimal partGasto = calcularPorcentaje(g.monto(), gastosOperativos);
            BigDecimal pctIngreso = calcularPorcentaje(g.monto(), ingresosBrutos);
            gastosPorCategoria.add(new GastoPorCategoria(g.categoria(), g.ops(), g.monto(), partGasto, pctIngreso));
        }

        BigDecimal utilidadBruta = normalizarMoneda(ingresosBrutos.subtract(costoVentas));
        BigDecimal utilidadNeta = normalizarMoneda(utilidadBruta.subtract(gastosOperativos));
        BigDecimal margenBrutoPct = calcularPorcentaje(utilidadBruta, ingresosBrutos);
        BigDecimal margenNetoPct = calcularPorcentaje(utilidadNeta, ingresosBrutos);

        List<LineaPnl> lineas = new ArrayList<>();
        lineas.add(new LineaPnl("401", "(+) Ingresos Brutos por Ventas", ingresosBrutos,
                ingresosBrutos.compareTo(BigDecimal.ZERO) > 0 ? new BigDecimal("100.0") : new BigDecimal("0.0"),
                "INGRESO"));
        lineas.add(new LineaPnl("691", "(-) Costo de Ventas (Recetas y Kardex)", costoVentas,
                calcularPorcentaje(costoVentas, ingresosBrutos), "COSTO"));
        lineas.add(new LineaPnl("RES-B", "(=) Utilidad Bruta", utilidadBruta, margenBrutoPct, "SUBTOTAL"));
        lineas.add(new LineaPnl("630", "(-) Gastos Operativos de Caja", gastosOperativos,
                calcularPorcentaje(gastosOperativos, ingresosBrutos), "GASTO"));
        lineas.add(new LineaPnl("RES-N", "(=) Utilidad Neta del Período", utilidadNeta, margenNetoPct, "NETO"));

        return new EstadoResultadosReporte(
                desde,
                hasta,
                cantidadVentas,
                ingresosBrutos,
                subtotalGravado,
                igvVentas,
                costoVentas,
                utilidadBruta,
                margenBrutoPct,
                gastosOperativos,
                utilidadNeta,
                margenNetoPct,
                lineas,
                gastosPorCategoria
        );
    }

    private BigDecimal calcularCostoVentasPeriodo(Connection conn, Date sqlDesde, Date sqlHasta) throws SQLException {
        Map<Integer, BigDecimal> kardexPorVenta = new HashMap<>();
        String sqlKardex = "SELECT k.referencia_id AS id_venta, "
                + "COALESCE(SUM(ABS(k.cantidad) * CASE WHEN k.costo_unitario > 0 "
                + "THEN k.costo_unitario ELSE COALESCE(i.costo_unitario, 0.0000) END), 0.0000) AS costo_kardex "
                + "FROM kardex_movimiento k "
                + "JOIN venta v ON v.id_venta = k.referencia_id "
                + "LEFT JOIN insumo i ON k.id_insumo = i.id_insumo "
                + "WHERE k.referencia_tipo = 'VENTA' "
                + "AND k.tipo_movimiento = 'SALIDA' "
                + "AND v.estado = 'COMPLETADA' "
                + "AND DATE(v.fecha_hora) BETWEEN ? AND ? "
                + "GROUP BY k.referencia_id";
        try (PreparedStatement ps = conn.prepareStatement(sqlKardex)) {
            ps.setDate(1, sqlDesde);
            ps.setDate(2, sqlHasta);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    kardexPorVenta.put(rs.getInt("id_venta"), rs.getBigDecimal("costo_kardex"));
                }
            }
        }

        CostosRecetaCache costosCache = cargarCostosRecetaCache(conn);
        Map<Integer, BigDecimal> recetaPorVenta = new HashMap<>();
        String sqlDetalle = "SELECT v.id_venta, p.id_producto, p.es_combo, dv.id_combo, dv.cantidad, p.costo_estimado "
                + "FROM venta v "
                + "JOIN detalle_venta dv ON v.id_venta = dv.id_venta "
                + "JOIN producto p ON dv.id_producto = p.id_producto "
                + "WHERE v.estado = 'COMPLETADA' AND DATE(v.fecha_hora) BETWEEN ? AND ?";
        try (PreparedStatement ps = conn.prepareStatement(sqlDetalle)) {
            ps.setDate(1, sqlDesde);
            ps.setDate(2, sqlHasta);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int idVenta = rs.getInt("id_venta");
                    int idProducto = rs.getInt("id_producto");
                    boolean esCombo = rs.getBoolean("es_combo");
                    int idComboRaw = rs.getInt("id_combo");
                    Integer idCombo = rs.wasNull() ? null : idComboRaw;
                    int cantidad = Math.max(0, rs.getInt("cantidad"));
                    BigDecimal costoEst = rs.getBigDecimal("costo_estimado");

                    BigDecimal costoUnitario = costosCache.resolverCostoUnitario(
                            idProducto, esCombo, idCombo, costoEst
                    );
                    BigDecimal costoLinea = costoUnitario.multiply(BigDecimal.valueOf(cantidad));
                    recetaPorVenta.merge(idVenta, costoLinea, BigDecimal::add);
                }
            }
        }

        BigDecimal totalCosto = BigDecimal.ZERO;
        for (Map.Entry<Integer, BigDecimal> entry : recetaPorVenta.entrySet()) {
            BigDecimal costoKardex = kardexPorVenta.get(entry.getKey());
            if (costoKardex != null && costoKardex.compareTo(BigDecimal.ZERO) > 0) {
                totalCosto = totalCosto.add(costoKardex);
            } else {
                totalCosto = totalCosto.add(entry.getValue());
            }
        }
        return normalizarMoneda(totalCosto);
    }

    protected CostosRecetaCache cargarCostosRecetaCache(Connection conn) throws SQLException {
        Map<Integer, BigDecimal> recetaPorProducto = new HashMap<>();
        String sqlRec = "SELECT r.id_producto, COALESCE(SUM(r.cantidad_necesaria * i.costo_unitario), 0.0000) AS costo "
                + "FROM receta r "
                + "JOIN insumo i ON r.id_insumo = i.id_insumo "
                + "GROUP BY r.id_producto";
        try (PreparedStatement ps = conn.prepareStatement(sqlRec);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                recetaPorProducto.put(rs.getInt("id_producto"), rs.getBigDecimal("costo"));
            }
        }

        Map<Integer, BigDecimal> costoPorCombo = new HashMap<>();
        Map<Integer, Integer> comboPorProducto = new HashMap<>();
        String sqlCombo = "SELECT cb.id_combo, cb.id_producto AS id_prod_combo, "
                + "cd.id_producto AS id_comp, cd.cantidad, COALESCE(p.costo_estimado, 0.0000) AS est "
                + "FROM combo cb "
                + "JOIN combo_detalle cd ON cb.id_combo = cd.id_combo "
                + "JOIN producto p ON cd.id_producto = p.id_producto";
        try (PreparedStatement ps = conn.prepareStatement(sqlCombo);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int idCombo = rs.getInt("id_combo");
                int idProdCombo = rs.getInt("id_prod_combo");
                if (idProdCombo > 0) {
                    comboPorProducto.put(idProdCombo, idCombo);
                }
                int idComp = rs.getInt("id_comp");
                int qty = Math.max(0, rs.getInt("cantidad"));
                BigDecimal costoComp = recetaPorProducto.get(idComp);
                if (costoComp == null || costoComp.compareTo(BigDecimal.ZERO) <= 0) {
                    costoComp = rs.getBigDecimal("est");
                }
                if (costoComp != null) {
                    costoPorCombo.merge(idCombo, costoComp.multiply(BigDecimal.valueOf(qty)), BigDecimal::add);
                }
            }
        }

        return new CostosRecetaCache(recetaPorProducto, costoPorCombo, comboPorProducto);
    }

    protected static BigDecimal normalizarMoneda(BigDecimal valor) {
        if (valor == null) {
            return CERO;
        }
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public ComparativaPeriodos obtenerComparativaPeriodos(Connection conn, LocalDate fechaReferencia)
            throws SQLException {
        LocalDate hoy = fechaReferencia != null ? fechaReferencia : LocalDate.now();
        LocalDate ayer = hoy.minusDays(1);
        YearMonth mesActualYm = YearMonth.from(hoy);
        YearMonth mesAnteriorYm = mesActualYm.minusMonths(1);

        LocalDate inicioMesActual = mesActualYm.atDay(1);
        LocalDate finMesActual = mesActualYm.atEndOfMonth();
        LocalDate inicioMesAnterior = mesAnteriorYm.atDay(1);
        LocalDate finMesAnterior = mesAnteriorYm.atEndOfMonth();
        LocalDate limiteInferior = ayer.isBefore(inicioMesAnterior) ? ayer : inicioMesAnterior;
        LocalDate limiteSuperior = hoy.isAfter(finMesActual) ? hoy : finMesActual;

        BigDecimal ventasHoy = CERO;
        BigDecimal ventasAyer = CERO;
        BigDecimal ventasMesActual = CERO;
        BigDecimal ventasMesAnterior = CERO;

        String sql = "SELECT "
                + "COALESCE(SUM(CASE WHEN DATE(fecha_hora) = ? THEN total ELSE 0 END), 0.00) AS v_hoy, "
                + "COALESCE(SUM(CASE WHEN DATE(fecha_hora) = ? THEN total ELSE 0 END), 0.00) AS v_ayer, "
                + "COALESCE(SUM(CASE WHEN DATE(fecha_hora) BETWEEN ? AND ? THEN total ELSE 0 END), 0.00) AS v_mes_act, "
                + "COALESCE(SUM(CASE WHEN DATE(fecha_hora) BETWEEN ? AND ? THEN total ELSE 0 END), 0.00) AS v_mes_ant "
                + "FROM venta "
                + "WHERE estado = 'COMPLETADA' AND DATE(fecha_hora) BETWEEN ? AND ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(hoy));
            ps.setDate(2, Date.valueOf(ayer));
            ps.setDate(3, Date.valueOf(inicioMesActual));
            ps.setDate(4, Date.valueOf(finMesActual));
            ps.setDate(5, Date.valueOf(inicioMesAnterior));
            ps.setDate(6, Date.valueOf(finMesAnterior));
            ps.setDate(7, Date.valueOf(limiteInferior));
            ps.setDate(8, Date.valueOf(limiteSuperior));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ventasHoy = normalizarMoneda(rs.getBigDecimal("v_hoy"));
                    ventasAyer = normalizarMoneda(rs.getBigDecimal("v_ayer"));
                    ventasMesActual = normalizarMoneda(rs.getBigDecimal("v_mes_act"));
                    ventasMesAnterior = normalizarMoneda(rs.getBigDecimal("v_mes_ant"));
                }
            }
        }

        BigDecimal varDiaria = calcularVariacionPorcentual(ventasHoy, ventasAyer);
        BigDecimal varMensual = calcularVariacionPorcentual(ventasMesActual, ventasMesAnterior);

        return new ComparativaPeriodos(
                hoy,
                ventasHoy,
                ventasAyer,
                varDiaria,
                ventasMesActual,
                ventasMesAnterior,
                varMensual
        );
    }

    @Override
    public List<MargenCategoria> calcularMargenesPorCategoria(Connection conn, LocalDate desde, LocalDate hasta)
            throws SQLException {
        CostosRecetaCache costosCache = cargarCostosRecetaCache(conn);
        Map<Integer, CategoriaAcumulador> porCategoria = new LinkedHashMap<>();

        String sql = "SELECT c.id_categoria, COALESCE(c.nombre, 'Sin Categoría') AS nombre_cat, "
                + "p.id_producto, p.es_combo, dv.id_combo, dv.cantidad, dv.precio_unitario, dv.subtotal, p.costo_estimado "
                + "FROM detalle_venta dv "
                + "JOIN venta v ON dv.id_venta = v.id_venta "
                + "JOIN producto p ON dv.id_producto = p.id_producto "
                + "JOIN categoria c ON p.id_categoria = c.id_categoria "
                + "WHERE v.estado = 'COMPLETADA' AND DATE(v.fecha_hora) BETWEEN ? AND ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(desde));
            ps.setDate(2, Date.valueOf(hasta));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int idCat = rs.getInt("id_categoria");
                    String nombreCat = rs.getString("nombre_cat");
                    int idProd = rs.getInt("id_producto");
                    boolean esCombo = rs.getBoolean("es_combo");
                    int idComboRaw = rs.getInt("id_combo");
                    Integer idCombo = rs.wasNull() ? null : idComboRaw;
                    int cant = Math.max(0, rs.getInt("cantidad"));
                    BigDecimal ingresoLinea = rs.getBigDecimal("subtotal");
                    if (ingresoLinea == null || ingresoLinea.compareTo(BigDecimal.ZERO) <= 0) {
                        BigDecimal pUnit = rs.getBigDecimal("precio_unitario");
                        ingresoLinea = pUnit != null ? pUnit.multiply(BigDecimal.valueOf(cant)) : BigDecimal.ZERO;
                    }
                    BigDecimal costoUnit = costosCache.resolverCostoUnitario(
                            idProd, esCombo, idCombo, rs.getBigDecimal("costo_estimado")
                    );
                    BigDecimal costoLinea = costoUnit.multiply(BigDecimal.valueOf(cant));

                    CategoriaAcumulador acum = porCategoria.computeIfAbsent(
                            idCat, k -> new CategoriaAcumulador(idCat, nombreCat)
                    );
                    acum.unidades += cant;
                    acum.ingresos = acum.ingresos.add(ingresoLinea);
                    acum.costo = acum.costo.add(costoLinea);
                }
            }
        }

        List<MargenCategoria> resultado = new ArrayList<>();
        for (CategoriaAcumulador a : porCategoria.values()) {
            BigDecimal ing = normalizarMoneda(a.ingresos);
            BigDecimal cost = normalizarMoneda(a.costo);
            BigDecimal margenBruto = normalizarMoneda(ing.subtract(cost));
            BigDecimal margenPct = calcularPorcentaje(margenBruto, ing);
            resultado.add(new MargenCategoria(
                    a.idCategoria, a.nombre, a.unidades, ing, cost, margenBruto, margenPct
            ));
        }

        // Ordenar de mayor a menor margen (% de rentabilidad bruto, desempate por utilidad bruta en S/)
        resultado.sort(Comparator
                .comparing(MargenCategoria::margenPorcentaje, Comparator.reverseOrder())
                .thenComparing(MargenCategoria::margenBruto, Comparator.reverseOrder()));
        return resultado;
    }

    @Override
    public ReporteCohortes generarAnalisisCohortes(Connection conn, LocalDate fechaCorte) throws SQLException {
        LocalDate corte = fechaCorte != null ? fechaCorte : LocalDate.now();
        YearMonth mesAnalizado = YearMonth.from(corte);

        Map<Integer, CompraClienteHistorial> historialPorCliente = new LinkedHashMap<>();
        String sql = "SELECT COALESCE(v.id_cliente, p.id_cliente) AS id_cli, DATE(v.fecha_hora) AS fecha_compra "
                + "FROM venta v "
                + "LEFT JOIN pedido p ON v.id_pedido = p.id_pedido "
                + "WHERE v.estado = 'COMPLETADA' "
                + "AND COALESCE(v.id_cliente, p.id_cliente) IS NOT NULL "
                + "AND DATE(v.fecha_hora) <= ? "
                + "ORDER BY fecha_compra ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(corte));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int idCli = rs.getInt("id_cli");
                    Date fSql = rs.getDate("fecha_compra");
                    if (fSql == null) {
                        continue;
                    }
                    LocalDate fCompra = fSql.toLocalDate();
                    CompraClienteHistorial hist = historialPorCliente.computeIfAbsent(
                            idCli, k -> new CompraClienteHistorial()
                    );
                    hist.registrarCompra(fCompra);
                }
            }
        }

        int nuevosMes = 0;
        int recurrentesMes = 0;
        int perdidos60 = 0;
        Map<YearMonth, Set<Integer>> cohortes = new TreeMap<>();

        for (Map.Entry<Integer, CompraClienteHistorial> entry : historialPorCliente.entrySet()) {
            int idCli = entry.getKey();
            CompraClienteHistorial h = entry.getValue();
            YearMonth ymPrimera = YearMonth.from(h.primeraCompra);
            cohortes.computeIfAbsent(ymPrimera, k -> new HashSet<>()).add(idCli);

            if (ymPrimera.equals(mesAnalizado)) {
                nuevosMes++;
            } else if (h.mesesCompra.contains(mesAnalizado)) {
                recurrentesMes++;
            }

            long diasDesdeUltima = ChronoUnit.DAYS.between(h.ultimaCompra, corte);
            if (diasDesdeUltima > 60) {
                perdidos60++;
            }
        }

        List<CohorteMensualFila> filasCohortes = new ArrayList<>();
        for (Map.Entry<YearMonth, Set<Integer>> cohEntry : cohortes.entrySet()) {
            YearMonth ymCoh = cohEntry.getKey();
            Set<Integer> miembros = cohEntry.getValue();
            int totalCoh = miembros.size();
            BigDecimal m1 = new BigDecimal("100.0");
            BigDecimal m2 = calcularRetencionEnOffset(ymCoh, 1, mesAnalizado, miembros, historialPorCliente);
            BigDecimal m3 = calcularRetencionEnOffset(ymCoh, 2, mesAnalizado, miembros, historialPorCliente);
            BigDecimal m4 = calcularRetencionEnOffset(ymCoh, 3, mesAnalizado, miembros, historialPorCliente);

            filasCohortes.add(new CohorteMensualFila(
                    ymCoh,
                    ymCoh.format(FMT_COHORTE),
                    totalCoh,
                    m1,
                    m2,
                    m3,
                    m4
            ));
        }

        int totalBaseSegmentos = nuevosMes + recurrentesMes + perdidos60;
        List<SegmentoRetencionFila> resumenRetencion = List.of(
                new SegmentoRetencionFila(
                        "Clientes Nuevos del mes",
                        nuevosMes,
                        calcularPorcentaje(BigDecimal.valueOf(nuevosMes), BigDecimal.valueOf(totalBaseSegmentos)),
                        "Primera compra registrada en " + mesAnalizado
                ),
                new SegmentoRetencionFila(
                        "Clientes Recurrentes del mes",
                        recurrentesMes,
                        calcularPorcentaje(BigDecimal.valueOf(recurrentesMes), BigDecimal.valueOf(totalBaseSegmentos)),
                        "Compraron en " + mesAnalizado + " tras cohorte previa"
                ),
                new SegmentoRetencionFila(
                        "Clientes Perdidos (> 60 días)",
                        perdidos60,
                        calcularPorcentaje(BigDecimal.valueOf(perdidos60), BigDecimal.valueOf(totalBaseSegmentos)),
                        "Sin compras en los últimos 61+ días al " + corte
                )
        );

        boolean datosSuficientes = cohortes.size() >= 2 && historialPorCliente.size() >= 2;
        return new ReporteCohortes(
                corte,
                mesAnalizado,
                historialPorCliente.size(),
                nuevosMes,
                recurrentesMes,
                perdidos60,
                datosSuficientes,
                filasCohortes,
                resumenRetencion
        );
    }

    private BigDecimal calcularRetencionEnOffset(
            YearMonth ymCohorte,
            int offsetMeses,
            YearMonth mesAnalizado,
            Set<Integer> miembros,
            Map<Integer, CompraClienteHistorial> historial) {
        YearMonth mesObjetivo = ymCohorte.plusMonths(offsetMeses);
        if (mesObjetivo.isAfter(mesAnalizado) || miembros.isEmpty()) {
            return null;
        }
        int retornaron = 0;
        for (Integer idCli : miembros) {
            CompraClienteHistorial h = historial.get(idCli);
            if (h != null && h.mesesCompra.contains(mesObjetivo)) {
                retornaron++;
            }
        }
        return calcularPorcentaje(BigDecimal.valueOf(retornaron), BigDecimal.valueOf(miembros.size()));
    }

    protected static BigDecimal calcularVariacionPorcentual(BigDecimal actual, BigDecimal anterior) {
        BigDecimal act = actual != null ? actual : BigDecimal.ZERO;
        BigDecimal ant = anterior != null ? anterior : BigDecimal.ZERO;
        if (ant.compareTo(BigDecimal.ZERO) == 0) {
            return act.compareTo(BigDecimal.ZERO) > 0 ? new BigDecimal("100.0") : new BigDecimal("0.0");
        }
        return act.subtract(ant).multiply(CIEN).divide(ant, 1, RoundingMode.HALF_UP);
    }

    private static final class CategoriaAcumulador {
        private final int idCategoria;
        private final String nombre;
        private int unidades;
        private BigDecimal ingresos = BigDecimal.ZERO;
        private BigDecimal costo = BigDecimal.ZERO;

        private CategoriaAcumulador(int idCategoria, String nombre) {
            this.idCategoria = idCategoria;
            this.nombre = nombre;
        }
    }

    private static final class CompraClienteHistorial {
        private LocalDate primeraCompra;
        private LocalDate ultimaCompra;
        private final Set<YearMonth> mesesCompra = new TreeSet<>();

        private void registrarCompra(LocalDate fecha) {
            if (primeraCompra == null || fecha.isBefore(primeraCompra)) {
                primeraCompra = fecha;
            }
            if (ultimaCompra == null || fecha.isAfter(ultimaCompra)) {
                ultimaCompra = fecha;
            }
            mesesCompra.add(YearMonth.from(fecha));
        }
    }

    protected static BigDecimal calcularPorcentaje(BigDecimal numerador, BigDecimal denominador) {
        if (numerador == null || denominador == null || denominador.compareTo(BigDecimal.ZERO) == 0) {
            return new BigDecimal("0.0");
        }
        return numerador.multiply(CIEN).divide(denominador, 1, RoundingMode.HALF_UP);
    }

    private record GastoTemp(String categoria, int ops, BigDecimal monto) {
    }

    /**
     * Cache de costos de receta por producto y combo reutilizable en P&amp;L y Márgenes por Categoría.
     */
    protected static final class CostosRecetaCache {
        private final Map<Integer, BigDecimal> recetaPorProducto;
        private final Map<Integer, BigDecimal> costoPorCombo;
        private final Map<Integer, Integer> comboPorProducto;

        private CostosRecetaCache(
                Map<Integer, BigDecimal> recetaPorProducto,
                Map<Integer, BigDecimal> costoPorCombo,
                Map<Integer, Integer> comboPorProducto) {
            this.recetaPorProducto = recetaPorProducto;
            this.costoPorCombo = costoPorCombo;
            this.comboPorProducto = comboPorProducto;
        }

        public BigDecimal resolverCostoUnitario(
                int idProducto, boolean esCombo, Integer idCombo, BigDecimal costoEstimadoFallback) {
            if (esCombo || idCombo != null) {
                Integer comboId = idCombo != null ? idCombo : comboPorProducto.get(idProducto);
                if (comboId != null) {
                    BigDecimal costoCombo = costoPorCombo.get(comboId);
                    if (costoCombo != null && costoCombo.compareTo(BigDecimal.ZERO) > 0) {
                        return costoCombo;
                    }
                }
            }
            BigDecimal rec = recetaPorProducto.get(idProducto);
            if (rec != null && rec.compareTo(BigDecimal.ZERO) > 0) {
                return rec;
            }
            return costoEstimadoFallback != null && costoEstimadoFallback.compareTo(BigDecimal.ZERO) > 0
                    ? costoEstimadoFallback : BigDecimal.ZERO;
        }
    }
}
