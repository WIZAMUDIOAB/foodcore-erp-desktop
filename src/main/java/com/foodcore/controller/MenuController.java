package com.foodcore.controller;

import com.foodcore.dao.ProductoDAO;
import com.foodcore.dao.ProductoDAOImpl;
import com.foodcore.model.Categoria;
import com.foodcore.model.Producto;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ConexionBD;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador de Gestión de Menú y Fotografías según SPEC-FOODCORE-v1.2 (§2.2):
 * Tabla con miniatura (thumbnail), Nombre, Categoría, Precio y Estado;
 * selector {@link FileChooser} que copia la imagen a {@code uploads/platillos/} y almacena
 * la ruta relativa en {@code producto.imagen_url}.
 */
public class MenuController {

    private static final Logger LOGGER = Logger.getLogger(MenuController.class.getName());
    public static final Path DIRECTORIO_UPLOADS = Path.of("uploads", "platillos");

    @FXML private StackPane rootMenuStack;
    @FXML private TableView<Producto> tblMenu;
    @FXML private TableColumn<Producto, String> colImagen;
    @FXML private TableColumn<Producto, String> colNombre;
    @FXML private TableColumn<Producto, String> colCategoria;
    @FXML private TableColumn<Producto, String> colPrecio;
    @FXML private TableColumn<Producto, String> colEstado;
    @FXML private TableColumn<Producto, String> colMenuDia;

    @FXML private Label lblTituloFormulario;
    @FXML private TextField txtCodigo;
    @FXML private TextField txtNombre;
    @FXML private ComboBox<Categoria> cmbCategoria;
    @FXML private TextField txtPrecio;
    @FXML private ImageView imgPreview;
    @FXML private Label lblRutaImagen;
    @FXML private CheckBox chkActivo;
    @FXML private CheckBox chkDisponible;
    @FXML private CheckBox chkMenuDelDia;
    @FXML private Button btnSeleccionarFoto;
    @FXML private Button btnGuardarPlatillo;
    @FXML private Button btnLimpiarForm;

    private final ProductoDAO productoDAO = new ProductoDAOImpl();
    private final ObservableList<Producto> listaProductos = FXCollections.observableArrayList();
    private final ObservableList<Categoria> listaCategorias = FXCollections.observableArrayList();
    private Producto productoEnEdicion;
    private String rutaImagenRelativaActual;

    @FXML
    public void initialize() {
        AnimationUtils.attachTactileFeedback(btnSeleccionarFoto);
        AnimationUtils.attachTactileFeedback(btnGuardarPlatillo);
        AnimationUtils.attachTactileFeedback(btnLimpiarForm);

        configurarTabla();
        tblMenu.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                cargarEnFormulario(newSel);
            }
        });

        cargarDatosMenu();
    }

    private void configurarTabla() {
        colImagen.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getImagenUrl()));
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colCategoria.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getNombreCategoria() != null ? c.getValue().getNombreCategoria() : "General"
        ));
        colPrecio.setCellValueFactory(c -> {
            BigDecimal pr = c.getValue().getPrecioVenta() != null
                ? c.getValue().getPrecioVenta().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            return new SimpleStringProperty("S/ " + pr);
        });
        colEstado.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().isDisponible() ? "DISPONIBLE" : "AGOTADO"
        ));
        if (colMenuDia != null) {
            colMenuDia.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().isEsMenuDelDia() ? "MENÚ DÍA" : "-"
            ));
        }

        colImagen.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                if (item != null && !item.isBlank() && new File(item).exists()) {
                    Image img = new Image(new File(item).toURI().toString(), 44, 34, true, true);
                    ImageView iv = new ImageView(img);
                    iv.setFitWidth(44);
                    iv.setFitHeight(34);
                    setGraphic(iv);
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                } else {
                    Label badge = new Label(item != null && !item.isBlank() ? "FOTO" : "SIN FOTO");
                    badge.getStyleClass().add(item != null && !item.isBlank() ? "badge-pill-accent" : "badge-pill-neutral");
                    setGraphic(badge);
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                }
            }
        });

        colNombre.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                if (!empty) {
                    getStyleClass().setAll("table-cell", "text-body-strong");
                }
            }
        });

        colCategoria.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                if (!empty) {
                    getStyleClass().setAll("table-cell", "text-caption");
                }
            }
        });

        colPrecio.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                if (!empty) {
                    getStyleClass().setAll("table-cell", "text-mono");
                }
            }
        });

        colEstado.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label pill = new Label(item);
                    pill.getStyleClass().add("DISPONIBLE".equalsIgnoreCase(item) ? "badge-pill-success" : "badge-pill-danger");
                    setGraphic(pill);
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                }
            }
        });

        if (colMenuDia != null) {
            colMenuDia.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null || "-".equals(item)) {
                        setGraphic(null);
                        setText(empty ? null : "-");
                        if (!empty) {
                            getStyleClass().setAll("table-cell", "text-caption-muted");
                        }
                    } else {
                        Label pill = new Label("⭐ DÍA");
                        pill.getStyleClass().add("badge-pill-accent");
                        setGraphic(pill);
                        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                    }
                }
            });
        }

        tblMenu.setItems(listaProductos);
        cmbCategoria.setItems(listaCategorias);
    }

    public void cargarDatosMenu() {
        new Thread(() -> {
            List<Categoria> cats = new ArrayList<>();
            List<Producto> prods = new ArrayList<>();
            try (Connection conn = ConexionBD.getConnection()) {
                cats = productoDAO.listarCategorias(conn);
                prods = productoDAO.listarTodos(conn);
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Error cargando menú desde BD", e);
            }
            final List<Categoria> catsFinal = cats;
            final List<Producto> prodsFinal = prods;
            Platform.runLater(() -> {
                listaCategorias.setAll(catsFinal);
                if (!listaCategorias.isEmpty() && cmbCategoria.getValue() == null) {
                    cmbCategoria.getSelectionModel().selectFirst();
                }
                listaProductos.setAll(prodsFinal);
            });
        }).start();
    }

    public void cargarEnFormulario(Producto p) {
        if (p == null) {
            return;
        }
        if (tblMenu != null) {
            for (Producto item : listaProductos) {
                if (item.getIdProducto() == p.getIdProducto()) {
                    tblMenu.getSelectionModel().select(item);
                    break;
                }
            }
        }
        this.productoEnEdicion = p;
        this.rutaImagenRelativaActual = p.getImagenUrl();
        lblTituloFormulario.setText("Editar Platillo #" + p.getIdProducto());
        txtCodigo.setText(p.getCodigo() != null ? p.getCodigo() : "");
        txtNombre.setText(p.getNombre() != null ? p.getNombre() : "");
        txtPrecio.setText(p.getPrecioVenta() != null ? p.getPrecioVenta().toPlainString() : "0.00");
        chkActivo.setSelected(p.isDisponible());
        if (chkDisponible != null) {
            chkDisponible.setSelected(p.isDisponible());
        }
        if (chkMenuDelDia != null) {
            chkMenuDelDia.setSelected(p.isEsMenuDelDia());
        }
        for (Categoria c : listaCategorias) {
            if (c.getIdCategoria() == p.getIdCategoria()) {
                cmbCategoria.getSelectionModel().select(c);
                break;
            }
        }
        actualizarVistaPreviaImagen(p.getImagenUrl());
    }

    private void actualizarVistaPreviaImagen(String rutaRelativa) {
        if (rutaRelativa != null && !rutaRelativa.isBlank()) {
            lblRutaImagen.setText(rutaRelativa);
            File f = new File(rutaRelativa);
            if (f.exists() && imgPreview != null) {
                imgPreview.setImage(new Image(f.toURI().toString(), 72, 56, true, true));
            }
        } else {
            lblRutaImagen.setText("Sin imagen seleccionada");
            if (imgPreview != null) {
                imgPreview.setImage(null);
            }
        }
    }

    /**
     * Copia un archivo de imagen hacia {@code uploads/platillos/} y devuelve la ruta relativa normalizada (§2.2).
     */
    public String copiarImagenAUploads(File archivoOrigen) throws IOException {
        if (archivoOrigen == null || !archivoOrigen.exists()) {
            throw new IOException("El archivo de imagen seleccionado no existe.");
        }
        Files.createDirectories(DIRECTORIO_UPLOADS);
        String nombreLimpio = archivoOrigen.getName().replaceAll("[^a-zA-Z0-9._-]", "_");
        Path destino = DIRECTORIO_UPLOADS.resolve(nombreLimpio);
        Files.copy(archivoOrigen.toPath(), destino, StandardCopyOption.REPLACE_EXISTING);
        String rutaRelativa = "uploads/platillos/" + nombreLimpio;
        this.rutaImagenRelativaActual = rutaRelativa;
        actualizarVistaPreviaImagen(rutaRelativa);
        return rutaRelativa;
    }

    /**
     * Crea o actualiza un platillo en BD incluyendo su {@code imagen_url}, disponibilidad y menú del día (v4.2.0).
     */
    public Producto guardarPlatilloEnBD(int idCategoria,
                                        String codigo,
                                        String nombre,
                                        BigDecimal precioVenta,
                                        boolean disponible,
                                        boolean esMenuDelDia,
                                        String imagenUrl) throws Exception {
        try (Connection conn = ConexionBD.getConnection()) {
            if (productoEnEdicion != null && productoEnEdicion.getIdProducto() > 0) {
                productoEnEdicion.setIdCategoria(idCategoria);
                productoEnEdicion.setCodigo(codigo);
                productoEnEdicion.setNombre(nombre);
                productoEnEdicion.setPrecioVenta(precioVenta);
                productoEnEdicion.setDisponible(disponible);
                productoEnEdicion.setEsMenuDelDia(esMenuDelDia);
                productoEnEdicion.setImagenUrl(imagenUrl);
                productoDAO.actualizar(conn, productoEnEdicion);
                return productoEnEdicion;
            } else {
                Producto nuevo = new Producto(0, idCategoria, codigo, nombre, "", precioVenta, BigDecimal.ZERO, disponible, imagenUrl, esMenuDelDia, esMenuDelDia ? java.time.LocalDate.now() : null);
                productoDAO.crear(conn, nuevo);
                return nuevo;
            }
        }
    }

    public Producto guardarPlatilloEnBD(int idCategoria,
                                        String codigo,
                                        String nombre,
                                        BigDecimal precioVenta,
                                        boolean disponible,
                                        String imagenUrl) throws Exception {
        return guardarPlatilloEnBD(idCategoria, codigo, nombre, precioVenta, disponible, false, imagenUrl);
    }

    @FXML
    public void handleSeleccionarFoto(ActionEvent event) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Seleccionar Fotografía del Platillo");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Imágenes (JPG, PNG)", "*.jpg", "*.jpeg", "*.png")
        );
        File seleccionado = fc.showOpenDialog(rootMenuStack != null && rootMenuStack.getScene() != null
            ? rootMenuStack.getScene().getWindow() : null);
        if (seleccionado != null) {
            try {
                copiarImagenAUploads(seleccionado);
            } catch (IOException e) {
                AnimationUtils.mostrarToast(rootMenuStack, "No se pudo copiar la imagen: " + e.getMessage(), true);
            }
        }
    }

    @FXML
    public void handleNuevoPlatillo(ActionEvent event) {
        this.productoEnEdicion = null;
        this.rutaImagenRelativaActual = null;
        lblTituloFormulario.setText("Nuevo Platillo");
        txtCodigo.clear();
        txtNombre.clear();
        txtPrecio.clear();
        chkActivo.setSelected(true);
        if (chkDisponible != null) {
            chkDisponible.setSelected(true);
        }
        if (chkMenuDelDia != null) {
            chkMenuDelDia.setSelected(false);
        }
        actualizarVistaPreviaImagen(null);
    }

    @FXML
    public void handleGuardarPlatillo(ActionEvent event) {
        try {
            Categoria cat = cmbCategoria.getValue();
            int idCat = cat != null ? cat.getIdCategoria() : 1;
            String cod = txtCodigo.getText() != null ? txtCodigo.getText().trim() : ("PLT-" + (System.currentTimeMillis() % 10000));
            String nom = txtNombre.getText() != null ? txtNombre.getText().trim() : "";
            if (nom.isEmpty()) {
                AnimationUtils.mostrarToast(rootMenuStack, "Ingrese el nombre del platillo.", true);
                return;
            }
            BigDecimal precio = new BigDecimal(txtPrecio.getText().trim()).setScale(2, RoundingMode.HALF_UP);
            boolean disp = (chkDisponible != null) ? chkDisponible.isSelected() : chkActivo.isSelected();
            boolean menuDia = (chkMenuDelDia != null) && chkMenuDelDia.isSelected();
            guardarPlatilloEnBD(idCat, cod, nom, precio, disp, menuDia, rutaImagenRelativaActual);
            AnimationUtils.mostrarToast(rootMenuStack, "Platillo guardado exitosamente.", false);
            handleNuevoPlatillo(null);
            cargarDatosMenu();
        } catch (Exception e) {
            AnimationUtils.mostrarToast(rootMenuStack, "Error al guardar platillo: " + e.getMessage(), true);
        }
    }

    public ObservableList<Producto> getListaProductos() {
        return listaProductos;
    }

    @FXML
    public void handleAbrirMenuQr(ActionEvent event) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/com/foodcore/view/MenuQrModalView.fxml"));
            javafx.scene.Parent root = loader.load();
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("FoodCore — Menú Digital QR");
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            if (rootMenuStack != null && rootMenuStack.getScene() != null) {
                stage.initOwner(rootMenuStack.getScene().getWindow());
            }
            stage.setScene(new javafx.scene.Scene(root));
            stage.showAndWait();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error al abrir modal Menu QR: " + e.getMessage(), e);
            AnimationUtils.mostrarToast(rootMenuStack, "No se pudo abrir el modal del Menu QR: " + e.getMessage(), true);
        }
    }
}