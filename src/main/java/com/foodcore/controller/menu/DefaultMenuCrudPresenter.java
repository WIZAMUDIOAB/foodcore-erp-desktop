package com.foodcore.controller.menu;

import com.foodcore.config.AppContainer;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Categoria;
import com.foodcore.model.Producto;
import com.foodcore.model.ProductoModificador;
import com.foodcore.model.ProductoVariante;
import com.foodcore.service.MenuGestionService;
import com.foodcore.service.PersonalizacionProductoService;
import com.foodcore.util.FormatoPersonalizacionUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DefaultMenuCrudPresenter implements MenuCrudPresenter {
    private final MenuGestionService menuGestionService;
    private final PersonalizacionProductoService personalizacionService;

    public DefaultMenuCrudPresenter() {
        this(AppContainer.getDefault().resolve(MenuGestionService.class),
                AppContainer.getDefault().personalizacionProductoService());
    }

    public DefaultMenuCrudPresenter(MenuGestionService menuGestionService,
                                    PersonalizacionProductoService personalizacionService) {
        this.menuGestionService = menuGestionService;
        this.personalizacionService = personalizacionService;
    }

    @Override
    public DatosMenuCarga cargarDatosMenu() {
        try {
            return new DatosMenuCarga(menuGestionService.listarCategoriasProductos(),
                    personalizacionService.listarTodosConOpciones(),
                    personalizacionService.listarModificadoresGlobales());
        } catch (SQLException ex) {
            throw new IllegalStateException("Error cargando menú desde BD", ex);
        }
    }

    @Override
    public ValidacionPlatillo validarFormularioPlatillo(String textoCategoriaEditor, Categoria categoriaValue,
                                                         List<Categoria> categoriasExistentes, String codigoRaw,
                                                         String nombreRaw, String precioRaw) {
        String categoriaNombre = textoCategoriaEditor == null ? "" : textoCategoriaEditor.trim();
        if (categoriaNombre.isBlank() && categoriaValue != null) {
            categoriaNombre = categoriaValue.getNombre();
        }
        if (categoriaNombre == null || categoriaNombre.isBlank()) {
            categoriaNombre = "General";
        }
        Categoria existente = null;
        if (categoriasExistentes != null) {
            for (Categoria categoria : categoriasExistentes) {
                if (categoria.getNombre() != null
                        && categoria.getNombre().trim().equalsIgnoreCase(categoriaNombre)) {
                    existente = categoria;
                    break;
                }
            }
        }
        String codigo = codigoRaw == null ? "PLT-" + (System.currentTimeMillis() % 10000) : codigoRaw.trim();
        String nombre = nombreRaw == null ? "" : nombreRaw.trim();
        if (nombre.isEmpty()) {
            return new ValidacionPlatillo(false, categoriaNombre, existente, codigo, nombre, null,
                    "Ingrese el nombre del platillo.");
        }
        final BigDecimal precio;
        try {
            precio = new BigDecimal(precioRaw.trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (RuntimeException ex) {
            return new ValidacionPlatillo(false, categoriaNombre, existente, codigo, nombre, null,
                    "Ingrese un precio numérico válido.");
        }
        return new ValidacionPlatillo(true, categoriaNombre, existente, codigo, nombre, precio, null);
    }

    @Override
    public MenuGestionService.CategoriaResultado resolverOCrearCategoria(String categoriaNombre) throws SQLException {
        return menuGestionService.resolverOCrearCategoria(categoriaNombre);
    }

    @Override
    public Producto guardarPlatillo(Producto productoEnEdicion, int idCategoria, String codigo, String nombre,
                                    BigDecimal precioVenta, boolean disponible, boolean esMenuDelDia,
                                    String imagenUrl) throws SQLException {
        Producto producto;
        if (productoEnEdicion != null && productoEnEdicion.getIdProducto() > 0) {
            producto = productoEnEdicion;
            producto.setIdCategoria(idCategoria);
            producto.setCodigo(codigo);
            producto.setNombre(nombre);
            producto.setPrecioVenta(precioVenta);
            producto.setDisponible(disponible);
            producto.setEsMenuDelDia(esMenuDelDia);
            producto.setImagenUrl(imagenUrl);
            menuGestionService.actualizarProducto(producto);
        } else {
            producto = new Producto(0, idCategoria, codigo, nombre, "", precioVenta, BigDecimal.ZERO,
                    disponible, imagenUrl, esMenuDelDia, esMenuDelDia ? LocalDate.now() : null);
            menuGestionService.crearProducto(producto);
        }
        return producto;
    }

    @Override
    public Producto guardarPlatilloConOpciones(Producto productoEnEdicion, int idCategoria, String codigo,
                                               String nombre, BigDecimal precioVenta, boolean disponible,
                                               boolean esMenuDelDia, String imagenUrl,
                                               List<ProductoVariante> variantes,
                                               List<Integer> idsModificadores)
            throws SQLException, NegocioException {
        Producto guardado = guardarPlatillo(productoEnEdicion, idCategoria, codigo, nombre, precioVenta,
                disponible, esMenuDelDia, imagenUrl);
        if (guardado != null && guardado.getIdProducto() > 0) {
            personalizacionService.guardarConfiguracionProducto(guardado.getIdProducto(),
                    variantes != null ? variantes : List.of(), idsModificadores != null ? idsModificadores : List.of());
            guardado.setVariantes(personalizacionService.listarVariantesPorProducto(guardado.getIdProducto()));
            guardado.setModificadores(personalizacionService.listarModificadoresPorProducto(guardado.getIdProducto()));
        }
        return guardado;
    }

    @Override
    public ResultadoGuardadoPlatillo guardarDesdeFormulario(ValidacionPlatillo validacion,
                                                             Producto productoEnEdicion, boolean disponible,
                                                             boolean esMenuDelDia, String imagenUrl,
                                                             List<ProductoVariante> variantes,
                                                             List<Integer> idsModificadores)
            throws SQLException, NegocioException {
        if (validacion == null || !validacion.valido()) {
            throw new IllegalArgumentException(validacion == null ? "Formulario no válido." : validacion.mensajeError());
        }
        int idCategoria;
        Categoria nuevaCategoria = null;
        Categoria categoria = validacion.categoriaExistente();
        if (categoria != null && categoria.getIdCategoria() > 0) {
            idCategoria = categoria.getIdCategoria();
        } else {
            MenuGestionService.CategoriaResultado resultado = resolverOCrearCategoria(validacion.categoriaNombre());
            idCategoria = resultado.idCategoria();
            nuevaCategoria = resultado.nuevaCategoriaCreada();
        }
        Producto guardado = guardarPlatilloConOpciones(productoEnEdicion, idCategoria, validacion.codigo(),
                validacion.nombre(), validacion.precio(), disponible, esMenuDelDia, imagenUrl, variantes,
                idsModificadores);
        return new ResultadoGuardadoPlatillo(guardado, nuevaCategoria);
    }

    @Override
    public List<Producto> filtrarProductosSimples(List<Producto> productos) {
        List<Producto> simples = new ArrayList<>();
        if (productos != null) {
            for (Producto producto : productos) {
                if (producto != null && !producto.isEsCombo()) {
                    simples.add(producto);
                }
            }
        }
        return simples;
    }

    @Override
    public String formatearResumenOpcionesProducto(Producto p) {
        if (p == null) {
            return "Estándar";
        }
        if (p.isEsCombo()) {
            return "COMBO";
        }
        return formatearTextoOpciones(p.getVariantes().size(), p.getModificadores().size());
    }

    @Override
    public String formatearTextoOpciones(int numVar, int numMods) {
        return FormatoPersonalizacionUtil.construirResumenOpciones(numVar, numMods, "Estándar");
    }
}
