package com.foodcore.controller.menu;

import com.foodcore.exception.NegocioException;
import com.foodcore.model.Combo;
import com.foodcore.model.ComboDetalle;
import com.foodcore.model.Producto;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface MenuComboCoordinator {
    record ResultadoComponenteComboUi(boolean ok, boolean actualizadoExistente, String mensajeError) { }

    record ValidacionCombo(boolean valido, String codigo, String nombre, BigDecimal precio, String mensajeError) { }

    ResultadoComponenteComboUi agregarComponenteACombo(List<ComboDetalle> componentes, Producto seleccionado,
                                                        String cantidadRaw);

    ValidacionCombo validarFormularioCombo(String codigoRaw, String nombreRaw, String precioRaw,
                                            List<ComboDetalle> componentes);

    Combo guardarCombo(Integer idCombo, Integer idProducto, String codigo, String nombre, BigDecimal precioCombo,
                       boolean activo, List<ComboDetalle> componentes) throws NegocioException, SQLException;

    Optional<Combo> obtenerComboPorProducto(int idProducto) throws SQLException;

    String formatearResumenCombo(List<ComboDetalle> componentes);
}
