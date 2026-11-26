package com.foodcore.controller.menu;

import com.foodcore.exception.NegocioException;
import com.foodcore.model.Categoria;
import com.foodcore.model.Producto;
import com.foodcore.model.ProductoModificador;
import com.foodcore.model.ProductoVariante;
import com.foodcore.service.MenuGestionService;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

public interface MenuCrudPresenter {
    record DatosMenuCarga(List<Categoria> cats, List<Producto> prods, List<ProductoModificador> mods) { }

    record ValidacionPlatillo(boolean valido, String categoriaNombre, Categoria categoriaExistente, String codigo,
                             String nombre, BigDecimal precio, String mensajeError) { }

    record ResultadoGuardadoPlatillo(Producto producto, Categoria nuevaCategoriaCreada) { }

    DatosMenuCarga cargarDatosMenu();

    ValidacionPlatillo validarFormularioPlatillo(String textoCategoriaEditor, Categoria categoriaValue,
                                                  List<Categoria> categoriasExistentes, String codigoRaw,
                                                  String nombreRaw, String precioRaw);

    MenuGestionService.CategoriaResultado resolverOCrearCategoria(String categoriaNombre) throws SQLException;

    Producto guardarPlatillo(Producto productoEnEdicion, int idCategoria, String codigo, String nombre,
                             BigDecimal precioVenta, boolean disponible, boolean esMenuDelDia, String imagenUrl)
            throws SQLException;

    Producto guardarPlatilloConOpciones(Producto productoEnEdicion, int idCategoria, String codigo, String nombre,
                                        BigDecimal precioVenta, boolean disponible, boolean esMenuDelDia,
                                        String imagenUrl, List<ProductoVariante> variantes,
                                        List<Integer> idsModificadores) throws SQLException, NegocioException;

    ResultadoGuardadoPlatillo guardarDesdeFormulario(ValidacionPlatillo validacion, Producto productoEnEdicion,
                                                      boolean disponible, boolean esMenuDelDia, String imagenUrl,
                                                      List<ProductoVariante> variantes, List<Integer> idsModificadores)
            throws SQLException, NegocioException;

    List<Producto> filtrarProductosSimples(List<Producto> productos);

    String formatearResumenOpcionesProducto(Producto p);

    String formatearTextoOpciones(int numVar, int numMods);
}
