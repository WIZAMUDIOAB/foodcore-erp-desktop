package com.foodcore.service.venta;

import com.foodcore.exception.InsumoNoEncontradoException;
import com.foodcore.exception.NegocioException;
import com.foodcore.exception.StockInsuficienteException;
import com.foodcore.model.DetalleVenta;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface VentaInventarioKardexHandler {
    record PlanConsumoInsumos(
            Map<Long, BigDecimal> requerimientosPorInsumo,
            List<Long> idsOrdenados,
            Map<Long, BigDecimal> saldosAnteriores) {
        public PlanConsumoInsumos {
            requerimientosPorInsumo = requerimientosPorInsumo != null
                    ? Collections.unmodifiableMap(new LinkedHashMap<>(requerimientosPorInsumo))
                    : Collections.emptyMap();
            idsOrdenados = idsOrdenados != null ? List.copyOf(idsOrdenados) : List.of();
            saldosAnteriores = saldosAnteriores != null
                    ? Collections.unmodifiableMap(new LinkedHashMap<>(saldosAnteriores))
                    : Collections.emptyMap();
        }
    }

    void completarTrazabilidadComboDetalle(Connection conn, DetalleVenta detalle) throws SQLException;

    void acumularInsumosParaLinea(Connection conn, Map<Long, BigDecimal> destino, Integer idCombo,
                                  int idProducto, int cantidadVendida) throws SQLException, NegocioException;

    PlanConsumoInsumos prepararYLockearInsumosVenta(Connection conn, List<DetalleVenta> detalles)
            throws SQLException, NegocioException;

    void descontarStockYRegistrarKardex(Connection conn, int idVenta, long idUsuario, PlanConsumoInsumos plan)
            throws SQLException, StockInsuficienteException, InsumoNoEncontradoException;

    void revertirOMermarInsumosPorAnulacion(Connection conn, int idVenta, boolean platoYaPreparado,
                                             long idUsuarioSolicitante, String glosaMotivo,
                                             Map<Long, BigDecimal> insumosAfectados) throws SQLException;

    void revertirInsumosPorDevolucionParcial(Connection conn, int idVenta, long idUsuario,
                                              String glosaMotivo, Map<Long, BigDecimal> insumosARevertir)
            throws SQLException, NegocioException;
}
