package com.foodcore.service;

import com.foodcore.exception.NegocioException;
import com.foodcore.model.Producto;
import com.foodcore.model.ProductoModificador;
import com.foodcore.model.ProductoVariante;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Servicio de negocio para configuración y consulta de Variantes de Producto (Tamaños)
 * y Modificadores / Extras Globales (v4.8.0 Módulos 1 y 2).
 */
public interface PersonalizacionProductoService {

    List<Producto> listarProductosDisponiblesConOpciones() throws SQLException;

    List<Producto> listarProductosDisponiblesConOpciones(Connection conn) throws SQLException;

    List<Producto> listarTodosConOpciones() throws SQLException;

    List<Producto> listarTodosConOpciones(Connection conn) throws SQLException;

    void enriquecerProductoConOpciones(Connection conn, Producto producto) throws SQLException;

    Producto resolverOpcionesProducto(Producto producto);

    List<ProductoVariante> listarVariantesPorProducto(int idProducto) throws SQLException;

    void guardarConfiguracionProducto(int idProducto,
                                      List<ProductoVariante> variantes,
                                      List<Integer> idsModificadoresAsociados)
            throws NegocioException, SQLException;

    List<ProductoModificador> listarModificadoresGlobales() throws SQLException;

    ProductoModificador guardarModificadorGlobal(String nombre, BigDecimal precio)
            throws NegocioException, SQLException;

    void eliminarModificadorGlobal(int idModificador) throws NegocioException, SQLException;

    List<ProductoModificador> listarModificadoresPorProducto(int idProducto) throws SQLException;
}
