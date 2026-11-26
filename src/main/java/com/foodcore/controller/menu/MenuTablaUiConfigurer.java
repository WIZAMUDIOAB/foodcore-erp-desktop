package com.foodcore.controller.menu;

import com.foodcore.model.Categoria;
import com.foodcore.model.ComboDetalle;
import com.foodcore.model.Producto;
import com.foodcore.model.ProductoModificador;
import com.foodcore.model.ProductoVariante;
import javafx.collections.ObservableList;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public class MenuTablaUiConfigurer {
    public MenuTablaUiConfigurer() {
    }

    public void configurarTablaMenu(TableView<Producto> tabla,
                                   TableColumn<Producto, String> colImagen,
                                   TableColumn<Producto, String> colNombre,
                                   TableColumn<Producto, String> colCategoria,
                                   TableColumn<Producto, String> colPrecio,
                                   TableColumn<Producto, String> colEstado,
                                   TableColumn<Producto, String> colMenuDia,
                                   TableColumn<Producto, String> colOpciones,
                                   ComboBox<Categoria> cmbCategoria,
                                   ObservableList<Producto> productos,
                                   ObservableList<Categoria> categorias,
                                   MenuImagenStorage imageStorage,
                                   Path baseDirUploads,
                                   Function<Producto, String> resumenOpciones,
                                   Consumer<Producto> onSeleccionarEditar,
                                   Consumer<Producto> onEliminar,
                                   Consumer<Producto> onCambiarEstado) {
        colImagen.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                cell.getValue().getImagenUrl()));
        colNombre.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                cell.getValue().getNombre()));
        colCategoria.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                cell.getValue().getNombreCategoria() != null ? cell.getValue().getNombreCategoria() : "General"));
        colPrecio.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                "S/ " + normalizarPrecio(cell.getValue().getPrecioVenta())));
        colEstado.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                cell.getValue().isDisponible() ? "DISPONIBLE" : "AGOTADO"));
        if (colMenuDia != null) {
            colMenuDia.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                    cell.getValue().isEsMenuDelDia() ? "MENÚ DÍA" : "-"));
        }
        if (colOpciones != null) {
            colOpciones.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                    resumenOpciones.apply(cell.getValue())));
        }
        colImagen.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(null);
                setText(null);
                if (empty) {
                    return;
                }
                Path ruta = imageStorage.resolverImagenSegura(baseDirUploads, item);
                if (ruta != null) {
                    ImageView imageView = new ImageView(new Image(ruta.toUri().toString(), 44, 34, true, true));
                    imageView.setFitWidth(44);
                    imageView.setFitHeight(34);
                    setGraphic(imageView);
                } else {
                    Label badge = new Label(item != null && !item.isBlank() ? "FOTO" : "SIN FOTO");
                    badge.getStyleClass().add(item != null && !item.isBlank()
                            ? "badge-pill-accent" : "badge-pill-neutral");
                    setGraphic(badge);
                }
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
        });
        colNombre.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                if (!empty) {
                    getStyleClass().setAll("table-cell", "text-body-strong");
                }
            }
        });
        colCategoria.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                if (!empty) {
                    getStyleClass().setAll("table-cell", "text-caption");
                }
            }
        });
        colPrecio.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                if (!empty) {
                    getStyleClass().setAll("table-cell", "text-mono");
                }
            }
        });
        colEstado.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(null);
                setText(null);
                if (!empty && item != null) {
                    Label badge = new Label(item);
                    badge.getStyleClass().add("DISPONIBLE".equalsIgnoreCase(item)
                            ? "badge-pill-success" : "badge-pill-danger");
                    setGraphic(badge);
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                }
            }
        });
        if (colMenuDia != null) {
            colMenuDia.setCellFactory(column -> new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setGraphic(null);
                    setText(null);
                    if (empty || item == null || "-".equals(item)) {
                        setText(empty ? null : "-");
                        if (!empty) {
                            getStyleClass().setAll("table-cell", "text-caption-muted");
                        }
                    } else {
                        Label badge = new Label("⭐ DÍA");
                        badge.getStyleClass().add("badge-pill-accent");
                        setGraphic(badge);
                        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                    }
                }
            });
        }
        if (colOpciones != null) {
            colOpciones.setCellFactory(column -> new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setGraphic(null);
                    setText(null);
                    if (empty || item == null || "Estándar".equals(item)) {
                        setText(empty ? null : "Estándar");
                        if (!empty) {
                            getStyleClass().setAll("table-cell", "text-caption-muted");
                        }
                    } else if ("COMBO".equalsIgnoreCase(item)) {
                        Label badge = new Label("COMBO");
                        badge.getStyleClass().add("badge-pill-accent");
                        setGraphic(badge);
                        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                    } else {
                        Label badge = new Label(item);
                        badge.getStyleClass().add("badge-pill-success");
                        setGraphic(badge);
                        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                    }
                }
            });
        }
        if (onSeleccionarEditar != null) {
            tabla.setRowFactory(view -> {
                javafx.scene.control.TableRow<Producto> row = new javafx.scene.control.TableRow<>();
                row.setOnMouseClicked(event -> {
                    if (!row.isEmpty() && event.getClickCount() == 1) {
                        onSeleccionarEditar.accept(row.getItem());
                    }
                });
                row.setOnMouseEntered(event -> {
                    if (!row.isEmpty()) {
                        row.setTooltip(new Tooltip("Seleccionar para editar"));
                    }
                });
                return row;
            });
        }
        configurarAccion(onEliminar, tabla, "Eliminar producto");
        configurarAccion(onCambiarEstado, tabla, "Cambiar estado del producto");
        tabla.setItems(productos);
        cmbCategoria.setItems(categorias);
        cmbCategoria.setEditable(true);
        cmbCategoria.setConverter(new StringConverter<>() {
            @Override
            public String toString(Categoria categoria) {
                return categoria != null ? categoria.getNombre() : "";
            }

            @Override
            public Categoria fromString(String string) {
                if (string == null || string.isBlank()) {
                    return null;
                }
                for (Categoria categoria : categorias) {
                    if (categoria.getNombre() != null
                            && categoria.getNombre().trim().equalsIgnoreCase(string.trim())) {
                        return categoria;
                    }
                }
                return new Categoria(0, string.trim(), "Categoría autogenerada", true);
            }
        });
    }

    public void configurarTablasPersonalizacion(TableView<ProductoVariante> tblVariantes,
                                                 TableColumn<ProductoVariante, String> colVarNombre,
                                                 TableColumn<ProductoVariante, String> colVarModo,
                                                 TableColumn<ProductoVariante, String> colVarPrecio,
                                                 ObservableList<ProductoVariante> variantes,
                                                 ComboBox<String> cmbModoVariante,
                                                 ListView<ProductoModificador> lstModificadoresGlobales,
                                                 ObservableList<ProductoModificador> modificadores) {
        if (cmbModoVariante != null) {
            cmbModoVariante.setItems(javafx.collections.FXCollections.observableArrayList(
                    "Precio fijo", "Precio adicional"));
            cmbModoVariante.getSelectionModel().select("Precio fijo");
        }
        if (tblVariantes != null) {
            colVarNombre.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                    cell.getValue().getNombreVariante()));
            colVarModo.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                    cell.getValue().esPrecioFijo() ? "Precio Fijo" : "+ Adicional"));
            colVarPrecio.setCellValueFactory(cell -> {
                ProductoVariante variante = cell.getValue();
                BigDecimal valor = variante.esPrecioFijo()
                        ? variante.getPrecioFijo() : variante.getPrecioAdicional();
                String prefijo = variante.esPrecioFijo() ? "S/ " : "+S/ ";
                return new javafx.beans.property.SimpleStringProperty(prefijo
                        + (valor != null ? valor.setScale(2, RoundingMode.HALF_UP) : "0.00"));
            });
            tblVariantes.setItems(variantes);
        }
        if (lstModificadoresGlobales != null) {
            lstModificadoresGlobales.setItems(modificadores);
        }
    }

    public void configurarTablaCombo(ComboBox<Producto> cmbComboComponente,
                                     ObservableList<Producto> productosSimples,
                                     TableView<ComboDetalle> tblComboComponentes,
                                     TableColumn<ComboDetalle, String> colNombre,
                                     TableColumn<ComboDetalle, String> colCantidad,
                                     ObservableList<ComboDetalle> componentes) {
        if (cmbComboComponente != null) {
            cmbComboComponente.setItems(productosSimples);
            cmbComboComponente.setConverter(new StringConverter<>() {
                @Override
                public String toString(Producto producto) {
                    if (producto == null) {
                        return "";
                    }
                    return producto.getNombre() + " (S/ " + normalizarPrecio(producto.getPrecioVenta()) + ")";
                }

                @Override
                public Producto fromString(String texto) {
                    return null;
                }
            });
        }
        if (tblComboComponentes != null) {
            colNombre.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                    cell.getValue().getNombreProducto() != null ? cell.getValue().getNombreProducto()
                            : "Producto #" + cell.getValue().getIdProducto()));
            colCantidad.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                    String.valueOf(Math.max(1, cell.getValue().getCantidad()))));
            tblComboComponentes.setItems(componentes);
        }
    }

    public void renderizarCheckboxesModificadores(VBox vboxCheckboxesModificadores,
                                                   List<ProductoModificador> modificadores,
                                                   Set<Integer> idsMarcados,
                                                   List<CheckBox> checkboxes,
                                                   Runnable onToggle) {
        if (vboxCheckboxesModificadores == null) {
            return;
        }
        vboxCheckboxesModificadores.getChildren().clear();
        checkboxes.clear();
        if (modificadores.isEmpty()) {
            Label labelVacio = new Label("Sin extras creados aún. Cree uno abajo.");
            labelVacio.getStyleClass().add("text-caption-muted");
            vboxCheckboxesModificadores.getChildren().add(labelVacio);
            return;
        }
        for (ProductoModificador mod : modificadores) {
            CheckBox chk = new CheckBox(mod.formatearParaSelector());
            chk.setId("chkMenuMod_" + mod.getIdModificador());
            chk.setUserData(mod);
            chk.setSelected(idsMarcados != null && idsMarcados.contains(mod.getIdModificador()));
            chk.selectedProperty().addListener((observable, anterior, seleccionado) -> {
                if (onToggle != null) {
                    onToggle.run();
                }
            });
            checkboxes.add(chk);
            vboxCheckboxesModificadores.getChildren().add(chk);
        }
    }

    private static BigDecimal normalizarPrecio(BigDecimal precio) {
        return precio != null ? precio.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private static void configurarAccion(Consumer<Producto> callback, TableView<Producto> tabla, String textoTooltip) {
        if (callback == null) {
            return;
        }
        tabla.setRowFactory(view -> {
            javafx.scene.control.TableRow<Producto> row = new javafx.scene.control.TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getClickCount() == 2) {
                    callback.accept(row.getItem());
                }
            });
            row.setOnMouseEntered(event -> {
                if (!row.isEmpty()) {
                    row.setTooltip(new Tooltip(textoTooltip));
                }
            });
            return row;
        });
    }
}
