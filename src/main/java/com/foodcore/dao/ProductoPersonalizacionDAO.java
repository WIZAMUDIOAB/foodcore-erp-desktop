package com.foodcore.dao;

import com.foodcore.model.ProductoModificador;
import com.foodcore.model.ProductoVariante;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Acceso a datos para variantes de producto y modificadores/extras configurables (v4.8.0).
 */
public interface ProductoPersonalizacionDAO {

    List<ProductoVariante> listarVariantesPorProducto(Connection conn, int idProducto) throws SQLException;

    Map<Integer, List<ProductoVariante>> listarVariantesPorProductos(Connection conn) throws SQLException;

    int crearVariante(Connection conn, ProductoVariante variante) throws SQLException;

    void reemplazarVariantesProducto(Connection conn, int idProducto, List<ProductoVariante> variantes) throws SQLException;

    void eliminarVariante(Connection conn, int idVariante) throws SQLException;

    List<ProductoModificador> listarModificadoresGlobales(Connection conn) throws SQLException;

    ProductoModificador guardarModificadorGlobal(Connection conn, ProductoModificador modificador) throws SQLException;

    void desactivarModificadorGlobal(Connection conn, int idModificador) throws SQLException;

    List<ProductoModificador> listarModificadoresPorProducto(Connection conn, int idProducto) throws SQLException;

    Map<Integer, List<ProductoModificador>> listarModificadoresPorProductos(Connection conn) throws SQLException;

    void reemplazarModificadoresAsociados(Connection conn, int idProducto, List<Integer> idsModificadores) throws SQLException;

    void insertarDetallePedidoModificadores(Connection conn,
                                            int idDetallePedido,
                                            List<ProductoModificador> modificadores) throws SQLException;

    Map<Integer, List<ProductoModificador>> listarModificadoresPorPedido(Connection conn, int idPedido) throws SQLException;

    Map<Integer, List<ProductoModificador>> listarModificadoresDetallesActivosCocina(Connection conn) throws SQLException;
}
