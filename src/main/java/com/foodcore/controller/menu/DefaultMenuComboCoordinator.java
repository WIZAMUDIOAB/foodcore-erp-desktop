package com.foodcore.controller.menu;

import com.foodcore.exception.NegocioException;
import com.foodcore.model.Combo;
import com.foodcore.model.ComboDetalle;
import com.foodcore.model.Producto;
import com.foodcore.service.ComboService;
import com.foodcore.service.impl.ComboServiceImpl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class DefaultMenuComboCoordinator implements MenuComboCoordinator {
    private final ComboService comboService;

    public DefaultMenuComboCoordinator() {
        this(new ComboServiceImpl());
    }

    public DefaultMenuComboCoordinator(ComboService comboService) {
        this.comboService = comboService;
    }

    @Override
    public ResultadoComponenteComboUi agregarComponenteACombo(List<ComboDetalle> componentes, Producto seleccionado,
                                                               String cantidadRaw) {
        if (seleccionado == null) {
            return new ResultadoComponenteComboUi(false, false,
                    "Seleccione un producto para incluir en el combo.");
        }
        final int cantidad;
        try {
            String raw = cantidadRaw == null ? "1" : cantidadRaw.trim();
            cantidad = Integer.parseInt(raw.isEmpty() ? "1" : raw);
            if (cantidad <= 0) {
                return new ResultadoComponenteComboUi(false, false, "La cantidad debe ser mayor a cero.");
            }
        } catch (NumberFormatException ex) {
            return new ResultadoComponenteComboUi(false, false, "Ingrese una cantidad entera válida.");
        }
        if (componentes == null) {
            return new ResultadoComponenteComboUi(false, false, "No se pudo actualizar la lista de componentes.");
        }
        for (ComboDetalle detalle : componentes) {
            if (detalle.getIdProducto() == seleccionado.getIdProducto()) {
                detalle.setCantidad(detalle.getCantidad() + cantidad);
                return new ResultadoComponenteComboUi(true, true, null);
            }
        }
        ComboDetalle nuevo = new ComboDetalle(seleccionado.getIdProducto(), seleccionado.getNombre(), cantidad);
        nuevo.setCodigoProducto(seleccionado.getCodigo());
        nuevo.setPrecioUnitarioReferencia(seleccionado.getPrecioVenta());
        componentes.add(nuevo);
        return new ResultadoComponenteComboUi(true, false, null);
    }

    @Override
    public ValidacionCombo validarFormularioCombo(String codigoRaw, String nombreRaw, String precioRaw,
                                                   List<ComboDetalle> componentes) {
        String codigo = codigoRaw == null ? "" : codigoRaw.trim();
        String nombre = nombreRaw == null ? "" : nombreRaw.trim();
        if (nombre.isEmpty()) {
            return new ValidacionCombo(false, codigo, nombre, null,
                    "Ingrese el nombre del combo (ej. Combo Duo Burger).");
        }
        final BigDecimal precio;
        try {
            precio = new BigDecimal(precioRaw == null ? "" : precioRaw.trim()).setScale(2, RoundingMode.HALF_UP);
            if (precio.compareTo(BigDecimal.ZERO) <= 0) {
                return new ValidacionCombo(false, codigo, nombre, null,
                        "El precio del combo debe ser mayor a S/ 0.00.");
            }
        } catch (RuntimeException ex) {
            return new ValidacionCombo(false, codigo, nombre, null, "Ingrese un precio válido para el combo.");
        }
        if (componentes == null || componentes.isEmpty()) {
            return new ValidacionCombo(false, codigo, nombre, null,
                    "Agregue al menos un producto componente al combo.");
        }
        return new ValidacionCombo(true, codigo, nombre, precio, null);
    }

    @Override
    public Combo guardarCombo(Integer idCombo, Integer idProducto, String codigo, String nombre,
                              BigDecimal precioCombo, boolean activo, List<ComboDetalle> componentes)
            throws NegocioException, SQLException {
        Combo combo = new Combo(idCombo != null ? idCombo : 0, idProducto, nombre, precioCombo, activo);
        combo.setCodigo(codigo);
        combo.setDetalles(componentes);
        return comboService.guardarCombo(combo);
    }

    @Override
    public Optional<Combo> obtenerComboPorProducto(int idProducto) throws SQLException {
        return comboService.obtenerComboPorProducto(idProducto);
    }

    @Override
    public String formatearResumenCombo(List<ComboDetalle> componentes) {
        int productos = componentes == null ? 0 : componentes.size();
        int unidades = 0;
        if (componentes != null) {
            for (ComboDetalle detalle : componentes) {
                unidades += Math.max(1, detalle.getCantidad());
            }
        }
        return productos + " productos (" + unidades + " uds.)";
    }
}
