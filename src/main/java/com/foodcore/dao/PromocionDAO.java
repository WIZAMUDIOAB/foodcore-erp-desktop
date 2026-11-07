package com.foodcore.dao;

import com.foodcore.model.Promocion;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface PromocionDAO {
    List<Promocion> listarTodas(Connection conn) throws SQLException;
    List<Promocion> listarActivas(Connection conn) throws SQLException;
    Optional<Promocion> buscarPorId(Connection conn, int idPromocion) throws SQLException;
    Optional<Promocion> buscarPorCodigo(Connection conn, String codigo) throws SQLException;
    int crear(Connection conn, Promocion promo) throws SQLException;
    boolean actualizar(Connection conn, Promocion promo) throws SQLException;
    boolean cambiarEstado(Connection conn, int idPromocion, boolean activo) throws SQLException;
}
