package com.foodcore.service.venta;

import com.foodcore.dao.ComboDAO;
import com.foodcore.dao.InsumoDAO;
import com.foodcore.exception.InsumoNoEncontradoException;
import com.foodcore.exception.NegocioException;
import com.foodcore.exception.StockInsuficienteException;
import com.foodcore.model.Combo;
import com.foodcore.model.ComboDetalle;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Insumo;
import com.foodcore.model.KardexMovimiento;
import com.foodcore.model.Receta;
import com.foodcore.model.DesgloseFiscal;
import com.foodcore.util.FormatoPersonalizacionUtil;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public final class DefaultVentaInventarioKardexHandler implements VentaInventarioKardexHandler {
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    private final InsumoDAO insumoDAO;
    private final ComboDAO comboDAO;

    public DefaultVentaInventarioKardexHandler(InsumoDAO insumoDAO, ComboDAO comboDAO) {
        this.insumoDAO = Objects.requireNonNull(insumoDAO);
        this.comboDAO = Objects.requireNonNull(comboDAO);
    }

    @Override
    public void completarTrazabilidadComboDetalle(Connection conn, DetalleVenta detalle) throws SQLException {
        if (detalle == null || detalle.getIdProducto() <= 0) {
            return;
        }
        Optional<Combo> optCombo = (detalle.getIdCombo() != null && detalle.getIdCombo() > 0)
                ? comboDAO.obtenerPorId(conn, detalle.getIdCombo())
                : comboDAO.obtenerPorIdProducto(conn, detalle.getIdProducto());
        if (optCombo.isEmpty()) {
            return;
        }
        Combo combo = optCombo.get();
        if (detalle.getIdCombo() == null || detalle.getIdCombo() <= 0) {
            detalle.setIdCombo(combo.getIdCombo());
        }
        if (detalle.getComboComponentesTexto() == null || detalle.getComboComponentesTexto().isBlank()) {
            String desglose = FormatoPersonalizacionUtil.formatearDesgloseComboMultilinea(combo.getDetalles());
            if (!desglose.isBlank()) {
                detalle.setComboComponentesTexto(desglose);
            }
        }
    }

    @Override
    public void acumularInsumosParaLinea(Connection conn, Map<Long, BigDecimal> destino, Integer idCombo,
                                         int idProducto, int cantidadVendida)
            throws SQLException, NegocioException {
        List<ComboDetalle> componentesCombo = comboDAO.obtenerComponentesParaVenta(conn, idCombo, idProducto);
        if (!componentesCombo.isEmpty()) {
            for (ComboDetalle comp : componentesCombo) {
                int cantComp = Math.max(1, cantidadVendida) * Math.max(1, comp.getCantidad());
                acumularRecetaProducto(conn, destino, comp.getIdProducto(), cantComp);
            }
            return;
        }
        acumularRecetaProducto(conn, destino, idProducto, Math.max(1, cantidadVendida));
    }

    private void acumularRecetaProducto(Connection conn, Map<Long, BigDecimal> destino, int idProducto, int cantidad)
            throws SQLException, NegocioException {
        List<Receta> receta = insumoDAO.obtenerRecetaPorProducto(conn, idProducto);
        for (Receta rec : receta) {
            BigDecimal cantRequerida = DesgloseFiscal.normalizarStock(
                    rec.getCantidadNecesaria().multiply(BigDecimal.valueOf(cantidad))
            );
            destino.merge(rec.getIdInsumo(), cantRequerida, BigDecimal::add);
        }
    }

    @Override
    public PlanConsumoInsumos prepararYLockearInsumosVenta(Connection conn, List<DetalleVenta> detalles)
            throws SQLException, NegocioException {
        TreeMap<Long, BigDecimal> requerimientosPorInsumo = new TreeMap<>();
        for (DetalleVenta detalle : detalles) {
            completarTrazabilidadComboDetalle(conn, detalle);
            acumularInsumosParaLinea(
                    conn, requerimientosPorInsumo, detalle.getIdCombo(), detalle.getIdProducto(), detalle.getCantidad()
            );
        }
        List<Long> idsOrdenados = new ArrayList<>(requerimientosPorInsumo.keySet());
        Map<Long, BigDecimal> saldosAnteriores = insumoDAO.lockearPorIds(conn, idsOrdenados);
        return new PlanConsumoInsumos(requerimientosPorInsumo, idsOrdenados, saldosAnteriores);
    }

    @Override
    public void descontarStockYRegistrarKardex(Connection conn, int idVenta, long idUsuario, PlanConsumoInsumos plan)
            throws SQLException, StockInsuficienteException, InsumoNoEncontradoException {
        for (Long idInsumo : plan.idsOrdenados()) {
            if (!plan.saldosAnteriores().containsKey(idInsumo)) {
                throw new InsumoNoEncontradoException(idInsumo);
            }
            BigDecimal cantidadADescontar = plan.requerimientosPorInsumo().get(idInsumo)
                    .setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO);
            BigDecimal saldoAnterior = plan.saldosAnteriores().get(idInsumo);
            insumoDAO.descontarStock(conn, idInsumo, cantidadADescontar);
            BigDecimal saldoPosterior = saldoAnterior.subtract(cantidadADescontar)
                    .setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO);
            Insumo ins = insumoDAO.obtenerPorId(conn, idInsumo);
            BigDecimal costoUnit = (ins != null && ins.getCostoUnitario() != null)
                    ? ins.getCostoUnitario() : BigDecimal.ZERO;
            String glosa = "Venta #" + idVenta;
            insumoDAO.registrarMovimientoInventario(conn, idInsumo, "SALIDA", cantidadADescontar, glosa, idVenta);
            KardexMovimiento km = new KardexMovimiento(
                    0L,
                    idInsumo,
                    "SALIDA",
                    cantidadADescontar.negate(),
                    saldoAnterior,
                    saldoPosterior,
                    costoUnit,
                    "VENTA",
                    (long) idVenta,
                    idUsuario > 0 ? idUsuario : 1L,
                    glosa,
                    LocalDateTime.now(ZONA_LIMA)
            );
            insumoDAO.insertarKardex(conn, km);
        }
    }

    @Override
    public void revertirOMermarInsumosPorAnulacion(Connection conn, int idVenta, boolean platoYaPreparado,
                                                    long idUsuarioSolicitante, String glosaMotivo,
                                                    Map<Long, BigDecimal> insumosAfectados) throws SQLException {
        Map<Long, BigDecimal> saldosBloqueadosMerma = Collections.emptyMap();
        if (platoYaPreparado && !insumosAfectados.isEmpty()) {
            saldosBloqueadosMerma = insumoDAO.lockearPorIds(conn, new ArrayList<>(new TreeMap<>(insumosAfectados).keySet()));
        }
        for (Map.Entry<Long, BigDecimal> entry : new TreeMap<>(insumosAfectados).entrySet()) {
            long idIns = entry.getKey();
            BigDecimal cantInsumo = entry.getValue();
            if (!platoYaPreparado) {
                insumoDAO.registrarMovimientoKardexAtomico(
                        conn, idIns, "ENTRADA", cantInsumo, idUsuarioSolicitante,
                        "Reversión por Anulación Venta #" + idVenta + " (No preparado)"
                );
            } else {
                Insumo ins = insumoDAO.obtenerPorId(conn, idIns);
                BigDecimal saldoActual = saldosBloqueadosMerma.getOrDefault(
                        idIns, ins != null ? ins.getStockActualDisponible() : BigDecimal.ZERO);
                BigDecimal costo = ins != null && ins.getCostoUnitario() != null ? ins.getCostoUnitario() : BigDecimal.ZERO;
                String obsMerma = "Merma por Anulación Venta #" + idVenta + " (Plato ya cocinado: " + glosaMotivo + ")";
                insumoDAO.registrarMovimientoInventario(conn, idIns, "MERMA", cantInsumo, obsMerma, idVenta);
                KardexMovimiento kmMerma = new KardexMovimiento(
                        0L, idIns, "MERMA", cantInsumo.negate(), saldoActual, saldoActual,
                        costo, "ANULACION", (long) idVenta, idUsuarioSolicitante, obsMerma, LocalDateTime.now(ZONA_LIMA)
                );
                insumoDAO.insertarKardex(conn, kmMerma);
            }
        }
    }

    @Override
    public void revertirInsumosPorDevolucionParcial(Connection conn, int idVenta, long idUsuario,
                                                    String glosaMotivo, Map<Long, BigDecimal> insumosARevertir)
            throws SQLException, NegocioException {
        if (!insumosARevertir.isEmpty()) {
            List<Long> idsOrdenados = new ArrayList<>(new TreeMap<>(insumosARevertir).keySet());
            Map<Long, BigDecimal> saldosAnteriores = insumoDAO.lockearPorIds(conn, idsOrdenados);
            for (Long idIns : idsOrdenados) {
                BigDecimal cantRevertir = DesgloseFiscal.normalizarStock(insumosARevertir.get(idIns));
                BigDecimal saldoAnterior = saldosAnteriores.getOrDefault(idIns, BigDecimal.ZERO);
                BigDecimal saldoPosterior = DesgloseFiscal.normalizarStock(saldoAnterior.add(cantRevertir));
                insumoDAO.actualizarStock(conn, idIns, saldoPosterior);
                Insumo ins = insumoDAO.obtenerPorId(conn, idIns);
                BigDecimal costoUnit = (ins != null && ins.getCostoUnitario() != null)
                        ? ins.getCostoUnitario() : BigDecimal.ZERO;
                String obsKardex = "DEVOLUCION parcial Venta #" + idVenta + ": " + glosaMotivo;
                insumoDAO.registrarMovimientoInventario(conn, idIns, "ENTRADA", cantRevertir, obsKardex, idVenta);
                KardexMovimiento km = new KardexMovimiento(
                        0L,
                        idIns,
                        "DEVOLUCION",
                        cantRevertir,
                        saldoAnterior,
                        saldoPosterior,
                        costoUnit,
                        "DEVOLUCION",
                        (long) idVenta,
                        idUsuario,
                        obsKardex,
                        LocalDateTime.now(ZONA_LIMA)
                );
                insumoDAO.insertarKardex(conn, km);
            }
        }
    }
}
