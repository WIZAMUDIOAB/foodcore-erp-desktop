package com.foodcore.dao;

import com.foodcore.model.Insumo;
import com.foodcore.model.KardexMovimiento;
import com.foodcore.model.Receta;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public interface InsumoDAO {
    List<Insumo> listarTodos(Connection conn) throws SQLException;
    Insumo obtenerPorId(Connection conn, long idInsumo) throws SQLException;
    List<Receta> obtenerRecetaPorProducto(Connection conn, int idProducto) throws SQLException;
    Map<Long, BigDecimal> lockearPorIds(Connection conn, List<Long> idsInsumos) throws SQLException;
    long crear(Connection conn, Insumo insumo) throws SQLException;
    void actualizar(Connection conn, Insumo insumo) throws SQLException;
    void actualizarStock(Connection conn, long idInsumo, BigDecimal nuevoStock) throws SQLException;
    void descontarStock(Connection conn, long idInsumo, BigDecimal cantidadADescontar);
    void registrarMovimientoInventario(Connection conn, long idInsumo, String tipo, BigDecimal cantidad, String motivo, Integer referenciaId) throws SQLException;
    long insertarKardex(Connection conn, KardexMovimiento mov) throws SQLException;
    KardexMovimiento registrarMovimientoKardexAtomico(Connection conn, long idInsumo, String tipoMovimiento, BigDecimal magnitudPositiva, long idUsuario, String observacion) throws SQLException;
    List<KardexMovimiento> listarKardexPorInsumo(Connection conn, long idInsumo) throws SQLException;
    void eliminar(Connection conn, long idInsumo) throws SQLException;
}
