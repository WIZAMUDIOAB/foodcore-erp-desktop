package com.foodcore.controller.menu;

import com.foodcore.exception.NegocioException;
import com.foodcore.model.ProductoModificador;
import com.foodcore.model.ProductoVariante;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

public interface MenuPersonalizacionCoordinator {
    record ResultadoVarianteUi(boolean ok, boolean actualizadoExistente, String mensajeError) { }

    record ValidacionModificador(boolean valido, String nombre, BigDecimal precio, String mensajeError) { }

    record ResultadoModificadores(ProductoModificador creado, List<ProductoModificador> globales) { }

    record OpcionesProductoCarga(List<ProductoModificador> globales, List<ProductoVariante> variantes,
                                 List<ProductoModificador> asociados) { }

    ResultadoVarianteUi agregarOActualizarVariante(List<ProductoVariante> listaVariantes, int idProd,
                                                    String nombreRaw, String montoRaw, boolean esPrecioFijo);

    ValidacionModificador validarNuevoModificador(String nombreRaw, String precioRaw);

    ResultadoModificadores crearModificadorGlobal(String nombre, BigDecimal precio)
            throws SQLException, NegocioException;

    List<ProductoModificador> eliminarModificadorGlobal(int idModificador) throws NegocioException, SQLException;

    OpcionesProductoCarga cargarOpcionesProducto(int idProducto) throws SQLException;
}
