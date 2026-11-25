package com.foodcore.controller.venta;

import com.foodcore.controller.MenuController;
import com.foodcore.model.Categoria;
import com.foodcore.model.Producto;
import com.foodcore.service.VentaPosConsultaService;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.FormatoPersonalizacionUtil;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

public class VentaCatalogoPresenter {
    private final VentaPosConsultaService posConsultaService;
    private final Supplier<List<Categoria>> categoriasSupplier;
    private final Supplier<List<Producto>> productosSupplier;

    public VentaCatalogoPresenter(VentaPosConsultaService posConsultaService) {
        this(posConsultaService, () -> {
                    try {
                        return posConsultaService.listarCategoriasProductos();
                    } catch (SQLException ex) {
                        throw new IllegalStateException(ex);
                    }
                },
                posConsultaService::listarProductosDisponiblesConOpciones);
    }

    public VentaCatalogoPresenter(Supplier<List<Categoria>> categoriasSupplier, Supplier<List<Producto>> productosSupplier) {
        this(null, categoriasSupplier, productosSupplier);
    }

    private VentaCatalogoPresenter(VentaPosConsultaService service, Supplier<List<Categoria>> categorias,
                                   Supplier<List<Producto>> productos) {
        posConsultaService = service;
        categoriasSupplier = Objects.requireNonNull(categorias);
        productosSupplier = Objects.requireNonNull(productos);
    }

    public List<Categoria> listarCategoriasProductos() throws SQLException {
        try {
            List<Categoria> categorias = categoriasSupplier.get();
            return categorias == null ? List.of() : new ArrayList<>(categorias);
        } catch (RuntimeException ex) {
            if (ex.getCause() instanceof SQLException sqlEx) {
                throw sqlEx;
            }
            if (posConsultaService != null) {
                throw new SQLException("No se pudieron cargar las categorías", ex);
            }
            return List.of();
        }
    }

    public List<Producto> cargarProductosConFallbackDemo() {
        try {
            List<Producto> productos = productosSupplier.get();
            if (productos != null && !productos.isEmpty()) {
                return new ArrayList<>(productos);
            }
        } catch (RuntimeException ex) {
            return crearProductosDemostracion();
        }
        return crearProductosDemostracion();
    }

    public List<Producto> crearProductosDemostracion() {
        List<Producto> productos = new ArrayList<>();
        productos.add(new Producto(1, 1, "HAM-01", "Hamburguesa Clásica", "Carne 180g, lechuga, tomate", new BigDecimal("18.00"), new BigDecimal("7.50"), true));
        productos.add(new Producto(2, 1, "HAM-02", "Hamburguesa Doble Queso", "Doble carne, cheddar, bacon", new BigDecimal("24.00"), new BigDecimal("10.00"), true));
        productos.add(new Producto(3, 2, "PIZ-01", "Pizza Artesanal Familiar", "Mozzarella fior di latte, albahaca", new BigDecimal("35.00"), new BigDecimal("14.00"), true));
        productos.add(new Producto(4, 3, "ALI-01", "Alitas BBQ x12", "Alitas crujientes con salsa BBQ ahumada", new BigDecimal("22.00"), new BigDecimal("9.00"), true));
        productos.add(new Producto(5, 3, "PAP-01", "Papas Rústicas Nativas", "Con salsa de queso huancaína", new BigDecimal("12.00"), new BigDecimal("4.00"), true));
        productos.add(new Producto(6, 4, "BEB-01", "Cerveza Artesanal IPA", "Botella 330ml", new BigDecimal("14.00"), new BigDecimal("5.50"), true));
        productos.add(new Producto(7, 4, "BEB-02", "Gaseosa Personal 500ml", "Inca Kola / Coca Cola", new BigDecimal("6.00"), new BigDecimal("2.50"), true));
        productos.add(new Producto(8, 4, "BEB-03", "Limonada Frozen 1L", "Jarra clásica", new BigDecimal("10.00"), new BigDecimal("3.00"), true));
        return productos;
    }

    public List<Producto> filtrarProductos(List<Producto> todos, int categoriaFiltro, String filtroTexto) {
        String filtro = filtroTexto == null ? "" : filtroTexto.trim().toLowerCase(Locale.ROOT);
        List<Producto> filtrados = new ArrayList<>();
        if (todos == null) {
            return filtrados;
        }
        for (Producto producto : todos) {
            if (!producto.isDisponible() || (categoriaFiltro != 0 && producto.getIdCategoria() != categoriaFiltro)) {
                continue;
            }
            String nombre = producto.getNombre() == null ? "" : producto.getNombre().toLowerCase(Locale.ROOT);
            String codigo = producto.getCodigo() == null ? "" : producto.getCodigo().toLowerCase(Locale.ROOT);
            if (filtro.isEmpty() || nombre.contains(filtro) || codigo.contains(filtro)) {
                filtrados.add(producto);
            }
        }
        return filtrados;
    }

    public String construirDescripcionProducto(Producto p) {
        if (p.isEsCombo()) {
            return "COMBO · " + (p.getCodigo() == null ? "Paquete" : p.getCodigo());
        }
        if (p.tieneOpcionesPersonalizacion()) {
            return FormatoPersonalizacionUtil.construirResumenOpciones(p);
        }
        return p.getDescripcion() != null && !p.getDescripcion().isBlank()
                ? p.getDescripcion() : (p.getCodigo() == null ? "Platillo" : p.getCodigo());
    }

    public void renderizarChipsCategorias(HBox boxCategorias, List<Categoria> categorias, IntConsumer onSeleccionarCategoria) {
        boxCategorias.getChildren().clear();
        Button todos = new Button("Todos");
        todos.getStyleClass().addAll("category-chip", "category-chip-active");
        AnimationUtils.attachTactileFeedback(todos);
        todos.setOnAction(event -> {
            activarChip(boxCategorias, todos);
            onSeleccionarCategoria.accept(0);
        });
        boxCategorias.getChildren().add(todos);
        if (categorias == null) {
            return;
        }
        for (Categoria categoria : categorias) {
            Button chip = new Button(categoria.getNombre());
            chip.getStyleClass().add("category-chip");
            AnimationUtils.attachTactileFeedback(chip);
            chip.setOnAction(event -> {
                activarChip(boxCategorias, chip);
                onSeleccionarCategoria.accept(categoria.getIdCategoria());
            });
            boxCategorias.getChildren().add(chip);
        }
    }

    public void renderizarTarjetasCatalogo(TilePane tileProductos, List<Producto> productosFiltrados,
                                           Consumer<Producto> onAgregarProducto) {
        tileProductos.getChildren().clear();
        for (Producto p : productosFiltrados) {
            HBox card = new HBox(16.0);
            card.setId("btnProd_" + p.getIdProducto());
            card.setAlignment(Pos.CENTER_LEFT);
            card.setMinWidth(295.0);
            card.setPrefWidth(310.0);
            card.getStyleClass().addAll("card", "pos-menu-tile");
            card.setPadding(new Insets(16.0));
            VBox content = new VBox(4.0);
            content.setMinWidth(0.0);
            content.setPrefWidth(150.0);
            content.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(content, Priority.ALWAYS);
            PathThumb.agregar(content, p.getImagenUrl());
            Label nombre = new Label(p.getNombre());
            nombre.setMinWidth(0.0);
            nombre.setWrapText(true);
            nombre.getStyleClass().add("h3");
            Label descripcion = new Label(construirDescripcionProducto(p));
            descripcion.setMinWidth(0.0);
            descripcion.setWrapText(true);
            descripcion.getStyleClass().add("text-secondary");
            Label precio = new Label("S/ " + p.getPrecioVenta());
            precio.setMinWidth(Region.USE_PREF_SIZE);
            precio.getStyleClass().add("price");
            content.getChildren().addAll(nombre, descripcion, precio);
            Button agregar = new Button("+ Agregar");
            agregar.setMinWidth(Region.USE_PREF_SIZE);
            agregar.getStyleClass().add("btn-primary");
            agregar.setGraphicTextGap(8.0);
            AnimationUtils.attachTactileFeedback(agregar);
            agregar.setOnAction(event -> {
                event.consume();
                onAgregarProducto.accept(p);
            });
            card.getChildren().addAll(content, agregar);
            AnimationUtils.attachTactileFeedback(card);
            card.setOnMouseClicked(event -> onAgregarProducto.accept(p));
            tileProductos.getChildren().add(card);
        }
    }

    private void activarChip(HBox box, Button activo) {
        box.getChildren().forEach(node -> {
            if (node instanceof Button button) {
                button.getStyleClass().remove("category-chip-active");
                if (button == activo) {
                    button.getStyleClass().add("category-chip-active");
                }
            }
        });
    }

    private static final class PathThumb {
        private PathThumb() { }

        static void agregar(VBox content, String imagenUrl) {
            if (imagenUrl == null || imagenUrl.isBlank()) {
                return;
            }
            java.nio.file.Path ruta = MenuController.resolverImagenPlatilloSegura(imagenUrl);
            if (ruta != null && java.nio.file.Files.isRegularFile(ruta)) {
                ImageView view = new ImageView(new Image(ruta.toUri().toString(), 42, 32, true, true));
                view.setFitWidth(42);
                view.setFitHeight(32);
                view.setPreserveRatio(true);
                content.getChildren().add(view);
            }
        }
    }
}
