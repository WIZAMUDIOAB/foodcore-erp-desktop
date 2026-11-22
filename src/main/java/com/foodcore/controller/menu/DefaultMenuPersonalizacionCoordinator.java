package com.foodcore.controller.menu;

import com.foodcore.exception.NegocioException;
import com.foodcore.model.ProductoModificador;
import com.foodcore.model.ProductoVariante;
import com.foodcore.service.PersonalizacionProductoService;
import com.foodcore.service.impl.PersonalizacionProductoServiceImpl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.List;

public class DefaultMenuPersonalizacionCoordinator implements MenuPersonalizacionCoordinator {
    private final PersonalizacionProductoService personalizacionService;

    public DefaultMenuPersonalizacionCoordinator() {
        this(new PersonalizacionProductoServiceImpl());
    }

    public DefaultMenuPersonalizacionCoordinator(PersonalizacionProductoService personalizacionService) {
        this.personalizacionService = personalizacionService;
    }

    @Override
    public ResultadoVarianteUi agregarOActualizarVariante(List<ProductoVariante> listaVariantes, int idProd,
                                                          String nombreRaw, String montoRaw,
                                                          boolean esPrecioFijo) {
        String nombre = nombreRaw == null ? "" : nombreRaw.trim();
        if (nombre.isEmpty()) {
            return new ResultadoVarianteUi(false, false,
                    "Ingrese el nombre de la variante (ej. Mediana, Familiar).");
        }
        final BigDecimal monto;
        try {
            monto = new BigDecimal(montoRaw == null ? "" : montoRaw.trim()).setScale(2, RoundingMode.HALF_UP);
            if (monto.compareTo(BigDecimal.ZERO) < 0) {
                return new ResultadoVarianteUi(false, false, "El monto de la variante no puede ser negativo.");
            }
        } catch (RuntimeException ex) {
            return new ResultadoVarianteUi(false, false, "Ingrese un monto numérico válido para la variante.");
        }
        if (listaVariantes == null) {
            return new ResultadoVarianteUi(false, false, "No se pudo actualizar la lista de variantes.");
        }
        for (ProductoVariante variante : listaVariantes) {
            if (variante.getNombreVariante() != null
                    && variante.getNombreVariante().trim().equalsIgnoreCase(nombre)) {
                variante.setNombreVariante(nombre);
                variante.setPrecioFijo(esPrecioFijo ? monto : null);
                variante.setPrecioAdicional(esPrecioFijo ? BigDecimal.ZERO : monto);
                return new ResultadoVarianteUi(true, true, null);
            }
        }
        int orden = listaVariantes.size() + 1;
        ProductoVariante nueva = esPrecioFijo
                ? ProductoVariante.conPrecioFijo(idProd, nombre, monto, orden)
                : ProductoVariante.conPrecioAdicional(idProd, nombre, monto, orden);
        listaVariantes.add(nueva);
        return new ResultadoVarianteUi(true, false, null);
    }

    @Override
    public ValidacionModificador validarNuevoModificador(String nombreRaw, String precioRaw) {
        String nombre = nombreRaw == null ? "" : nombreRaw.trim();
        if (nombre.isEmpty()) {
            return new ValidacionModificador(false, nombre, null, "Ingrese el nombre del extra (ej. Extra Queso).");
        }
        final BigDecimal precio;
        try {
            String raw = precioRaw == null ? "0" : precioRaw.trim();
            precio = (raw.isEmpty() ? BigDecimal.ZERO : new BigDecimal(raw)).setScale(2, RoundingMode.HALF_UP);
            if (precio.compareTo(BigDecimal.ZERO) < 0) {
                return new ValidacionModificador(false, nombre, null, "El precio del extra no puede ser negativo.");
            }
        } catch (RuntimeException ex) {
            return new ValidacionModificador(false, nombre, null, "Ingrese un precio válido para el extra.");
        }
        return new ValidacionModificador(true, nombre, precio, null);
    }

    @Override
    public ResultadoModificadores crearModificadorGlobal(String nombre, BigDecimal precio)
            throws SQLException, NegocioException {
        ProductoModificador creado = personalizacionService.guardarModificadorGlobal(nombre, precio);
        List<ProductoModificador> globales = personalizacionService.listarModificadoresGlobales();
        return new ResultadoModificadores(creado, globales);
    }

    @Override
    public List<ProductoModificador> eliminarModificadorGlobal(int idModificador)
            throws NegocioException, SQLException {
        personalizacionService.eliminarModificadorGlobal(idModificador);
        return personalizacionService.listarModificadoresGlobales();
    }

    @Override
    public OpcionesProductoCarga cargarOpcionesProducto(int idProducto) throws SQLException {
        return new OpcionesProductoCarga(personalizacionService.listarModificadoresGlobales(),
                personalizacionService.listarVariantesPorProducto(idProducto),
                personalizacionService.listarModificadoresPorProducto(idProducto));
    }
}
