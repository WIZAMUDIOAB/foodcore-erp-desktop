package com.foodcore.dao;

import com.foodcore.model.MesaUnion;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public interface MesaDAO {

    Map<String, String> obtenerEstadoTodasMesas(Connection conn) throws SQLException;

    boolean transferirPedidoMesa(Connection conn, String numeroOrigen, String numeroDestino, int idUsuario) throws SQLException;

    boolean unirMesas(Connection conn, String numeroPrincipal, List<String> numerosSecundarios, int idUsuario) throws SQLException;

    boolean liberarMesasUnidasPorPedido(Connection conn, int idPedido) throws SQLException;

    List<MesaUnion> listarUnionesActivas(Connection conn) throws SQLException;

    List<String> listarMesasSecundariasDePedido(Connection conn, int idPedido) throws SQLException;
}
