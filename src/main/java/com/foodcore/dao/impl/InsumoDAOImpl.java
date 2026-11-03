package com.foodcore.dao.impl;

import com.foodcore.model.Insumo;
import com.foodcore.model.KardexMovimiento;
import com.foodcore.model.Receta;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Implementación canónica de {@code InsumoDAO} en paquete {@code com.foodcore.dao.impl}
 * según SPEC-FOODCORE-v1.0 (§15.4, R-01, R-06, R-09, R-11).
 */
public class InsumoDAOImpl extends com.foodcore.dao.InsumoDAOImpl {

    @Override
    public Map<Long, BigDecimal> lockearPorIds(Connection conn, List<Long> idsInsumos) throws SQLException {
        return super.lockearPorIds(conn, idsInsumos);
    }

    @Override
    public void descontarStock(Connection conn, long idInsumo, BigDecimal cantidadADescontar) {
        super.descontarStock(conn, idInsumo, cantidadADescontar);
    }

    @Override
    public long insertarKardex(Connection conn, KardexMovimiento mov) throws SQLException {
        return super.insertarKardex(conn, mov);
    }

    @Override
    public KardexMovimiento registrarMovimientoKardexAtomico(Connection conn,
                                                             long idInsumo,
                                                             String tipoMovimiento,
                                                             BigDecimal magnitudPositiva,
                                                             long idUsuario,
                                                             String observacion) throws SQLException {
        return super.registrarMovimientoKardexAtomico(conn, idInsumo, tipoMovimiento, magnitudPositiva, idUsuario, observacion);
    }

    @Override
    public Insumo obtenerPorId(Connection conn, long idInsumo) throws SQLException {
        return super.obtenerPorId(conn, idInsumo);
    }

    @Override
    public List<Receta> obtenerRecetaPorProducto(Connection conn, int idProducto) throws SQLException {
        return super.obtenerRecetaPorProducto(conn, idProducto);
    }

    @Override
    public long crear(Connection conn, Insumo insumo) throws SQLException {
        return super.crear(conn, insumo);
    }

    @Override
    public List<KardexMovimiento> listarKardexPorInsumo(Connection conn, long idInsumo) throws SQLException {
        return super.listarKardexPorInsumo(conn, idInsumo);
    }
}
