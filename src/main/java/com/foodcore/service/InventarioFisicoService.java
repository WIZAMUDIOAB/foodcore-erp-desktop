package com.foodcore.service;

import com.foodcore.dao.InsumoDAO;
import com.foodcore.dao.InsumoDAOImpl;
import com.foodcore.dao.impl.AuditoriaDAOImpl;
import com.foodcore.exception.CodigoError;
import com.foodcore.exception.InsumoNoEncontradoException;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.DesgloseFiscal;
import com.foodcore.model.Insumo;
import com.foodcore.model.ItemInventarioFisico;
import com.foodcore.model.KardexMovimiento;
import com.foodcore.tx.TransactionManager;
import com.foodcore.tx.impl.JdbcTransactionManager;
import com.foodcore.util.ConexionBD;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Servicio transaccional de Toma de Inventario Físico (v5.2.0): compara el stock del sistema
 * contra el conteo físico y genera movimientos de ajuste {@code AJUSTE_INV} en el Kárdex.
 */
public class InventarioFisicoService {

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    public record ResultadoAjusteInventarioFisico(
            int totalEvaluados,
            int insumosAjustados,
            BigDecimal sumaDiferenciaNeta,
            List<String> detallesAjuste
    ) {
        public ResultadoAjusteInventarioFisico {
            detallesAjuste = detallesAjuste != null ? List.copyOf(detallesAjuste) : List.of();
        }
    }

    private final TransactionManager tx;
    private final InsumoDAO insumoDAO;
    private final AuditoriaDAOImpl auditoriaDAO;

    public InventarioFisicoService() {
        this(new JdbcTransactionManager(), new InsumoDAOImpl(), new AuditoriaDAOImpl());
    }

    public InventarioFisicoService(TransactionManager tx,
                                   InsumoDAO insumoDAO,
                                   AuditoriaDAOImpl auditoriaDAO) {
        this.tx = tx;
        this.insumoDAO = insumoDAO;
        this.auditoriaDAO = auditoriaDAO;
    }

    public List<ItemInventarioFisico> listarInsumosParaConteo() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            List<Insumo> insumos = insumoDAO.listarTodos(conn);
            List<ItemInventarioFisico> items = new ArrayList<>();
            for (Insumo ins : insumos) {
                BigDecimal stock = ins.getStockActualDisponible() != null
                        ? ins.getStockActualDisponible() : BigDecimal.ZERO;
                items.add(new ItemInventarioFisico(
                        ins.getIdInsumo(),
                        ins.getCodigo(),
                        ins.getNombre(),
                        ins.getUnidadMedida(),
                        stock,
                        stock,
                        ins.getCostoUnitario()
                ));
            }
            return items;
        }
    }

    public ResultadoAjusteInventarioFisico aplicarAjustesInventarioFisico(
            List<ItemInventarioFisico> conteos,
            int idUsuario,
            String motivoGeneral) throws NegocioException, SQLException {
        if (conteos == null || conteos.isEmpty()) {
            throw new NegocioException(CodigoError.VALIDACION_ENTRADA,
                    "Debe proporcionar al menos un insumo en la toma de inventario físico.");
        }

        List<ItemInventarioFisico> conDiferencia = new ArrayList<>();
        for (ItemInventarioFisico item : conteos) {
            if (item == null || item.getIdInsumo() <= 0 || item.getStockContado() == null) {
                continue;
            }
            if (item.getStockContado().compareTo(BigDecimal.ZERO) < 0) {
                throw new NegocioException(CodigoError.VALIDACION_ENTRADA,
                        "El stock contado no puede ser negativo (" + item.getNombre() + ").");
            }
            if (item.tieneDiferencia()) {
                conDiferencia.add(item);
            }
        }

        if (conDiferencia.isEmpty()) {
            throw new NegocioException(CodigoError.VALIDACION_ENTRADA,
                    "No se detectaron diferencias entre el stock del sistema y el stock contado.");
        }

        String motivoBase = (motivoGeneral != null && !motivoGeneral.isBlank())
                ? motivoGeneral.trim() : "Toma de inventario físico";
        long idUsr = idUsuario > 0 ? idUsuario : 1L;

        return tx.executeChecked(conn -> {
            List<Long> idsOrdenados = conDiferencia.stream()
                    .map(ItemInventarioFisico::getIdInsumo)
                    .distinct()
                    .sorted()
                    .toList();
            Map<Long, BigDecimal> saldosBloqueados = insumoDAO.lockearPorIds(conn, idsOrdenados);

            int ajustados = 0;
            BigDecimal neta = BigDecimal.ZERO.setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO);
            List<String> detalles = new ArrayList<>();
            LocalDateTime ahora = LocalDateTime.now(ZONA_LIMA);

            for (ItemInventarioFisico item : conDiferencia) {
                long idIns = item.getIdInsumo();
                if (!saldosBloqueados.containsKey(idIns)) {
                    throw new InsumoNoEncontradoException(idIns);
                }
                BigDecimal saldoAnterior = saldosBloqueados.get(idIns)
                        .setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO);
                BigDecimal contado = item.getStockContado()
                        .setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO);
                BigDecimal delta = contado.subtract(saldoAnterior)
                        .setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO);
                if (delta.compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }

                insumoDAO.actualizarStock(conn, idIns, contado);
                Insumo ins = insumoDAO.obtenerPorId(conn, idIns);
                BigDecimal costo = ins != null && ins.getCostoUnitario() != null
                        ? ins.getCostoUnitario() : BigDecimal.ZERO;
                String obsItem = (item.getObservacion() != null && !item.getObservacion().isBlank())
                        ? motivoBase + " - " + item.getObservacion().trim()
                        : motivoBase + " (Sistema: " + saldoAnterior + " -> Contado: " + contado + ")";

                insumoDAO.registrarMovimientoInventario(conn, idIns, "AJUSTE_INV", delta, obsItem, null);
                KardexMovimiento km = new KardexMovimiento(
                        0L,
                        idIns,
                        "AJUSTE_INV",
                        delta,
                        saldoAnterior,
                        contado,
                        costo,
                        "AJUSTE_INV",
                        null,
                        idUsr,
                        obsItem,
                        ahora
                );
                insumoDAO.insertarKardex(conn, km);

                ajustados++;
                neta = neta.add(delta).setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO);
                detalles.add((item.getNombre() != null ? item.getNombre() : "Insumo #" + idIns)
                        + ": " + saldoAnterior + " -> " + contado + " (Dif: " + delta + ")");
                saldosBloqueados.put(idIns, contado);
            }

            if (ajustados == 0) {
                throw new NegocioException(CodigoError.VALIDACION_ENTRADA,
                        "El stock en base de datos ya coincide con las cantidades contadas.");
            }

            auditoriaDAO.registrarEventoSeguro(
                    conn,
                    (int) idUsr,
                    "AJUSTE_INV_FISICO",
                    "kardex_movimiento",
                    "Evaluados: " + conteos.size(),
                    "Ajustados: " + ajustados + " | Dif. neta: " + neta + " | " + motivoBase
            );

            return new ResultadoAjusteInventarioFisico(conteos.size(), ajustados, neta, detalles);
        });
    }
}
