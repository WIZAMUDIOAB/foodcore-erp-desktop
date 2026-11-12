package com.foodcore.controller;

import com.foodcore.dao.CategoriaDAO;
import com.foodcore.dao.CategoriaDAOImpl;
import com.foodcore.dao.ProductoDAO;
import com.foodcore.dao.ProductoDAOImpl;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Categoria;
import com.foodcore.model.Combo;
import com.foodcore.model.ComboDetalle;
import com.foodcore.model.Producto;
import com.foodcore.model.ProductoModificador;
import com.foodcore.model.ProductoVariante;
import com.foodcore.service.ComboService;
import com.foodcore.service.ImportacionMasivaService;
import com.foodcore.service.PersonalizacionProductoService;
import com.foodcore.service.impl.ComboServiceImpl;
import com.foodcore.service.impl.PersonalizacionProductoServiceImpl;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.ImportacionUIHelper;
import com.foodcore.util.UiAsyncExecutor;
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
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
    private static final String MODO_PRECIO_FIJO = "Precio Fijo (S/)";
    private static final String MODO_PRECIO_ADICIONAL = "+ Adicional (S/)";

    @FXML private StackPane rootMenuStack;
    @FXML private TableView<Producto> tblMenu;
    @FXML private TableColumn<Producto, String> colImagen;
    @FXML private TableColumn<Producto, String> colNombre;
    @FXML private TableColumn<Producto, String> colCategoria;
    @FXML private TableColumn<Producto, String> colPrecio;
    @FXML private TableColumn<Producto, String> colEstado;
    @FXML private TableColumn<Producto, String> colMenuDia;
    @FXML private TableColumn<Producto, String> colOpciones;

    @FXML private Label lblTituloFormulario;
    @FXML private Label lblBadgePersonalizacion;
    @FXML private TabPane tabPaneFichaPlatillo;
    @FXML private Tab tabDatosGenerales;
    @FXML private Tab tabVariantes;
    @FXML private Tab tabModificadores;
    @FXML private Tab tabCombo;
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
    @FXML private Button btnNuevoCombo;
    @FXML private Button btnDescargarPlantillaMenu;
    @FXML private Button btnImportarMenu;
    @FXML private ProgressIndicator pbImportacionMenu;
    @FXML private Label lblResumenImportacionMenu;

    @FXML private TextField txtComboCodigo;
    @FXML private TextField txtComboNombre;
    @FXML private TextField txtComboPrecio;
    @FXML private ComboBox<Producto> cmbComboComponente;
    @FXML private TextField txtComboCantidad;
    @FXML private Button btnAgregarComponenteCombo;
    @FXML private Button btnQuitarComponenteCombo;
    @FXML private TableView<ComboDetalle> tblComboComponentes;
    @FXML private TableColumn<ComboDetalle, String> colComboCompNombre;
    @FXML private TableColumn<ComboDetalle, String> colComboCompCantidad;
    @FXML private CheckBox chkComboActivo;
    @FXML private Label lblComboResumen;
    @FXML private Button btnGuardarCombo;

    @FXML private TextField txtNombreVariante;
    @FXML private ComboBox<String> cmbModoVariante;
    @FXML private TextField txtMontoVariante;
    @FXML private Button btnAgregarVariante;
    @FXML private Button btnQuitarVariante;
    @FXML private TableView<ProductoVariante> tblVariantes;
    @FXML private TableColumn<ProductoVariante, String> colVarNombre;
    @FXML private TableColumn<ProductoVariante, String> colVarModo;
    @FXML private TableColumn<ProductoVariante, String> colVarPrecio;

    @FXML private VBox vboxCheckboxesModificadores;
    @FXML private TextField txtNuevoModNombre;
    @FXML private TextField txtNuevoModPrecio;
    @FXML private Button btnCrearModificadorGlobal;
    @FXML private Button btnEliminarModificadorGlobal;
    @FXML private ListView<ProductoModificador> lstModificadoresGlobales;

    private final ProductoDAO productoDAO = new ProductoDAOImpl();
    private final CategoriaDAO categoriaDAO = new CategoriaDAOImpl();
    private final ImportacionMasivaService importacionMasivaService = new ImportacionMasivaService();
    private final PersonalizacionProductoService personalizacionService = new PersonalizacionProductoServiceImpl();
    private final ComboService comboService = new ComboServiceImpl();
    private final ObservableList<Producto> listaProductos = FXCollections.observableArrayList();
    private final ObservableList<Producto> listaProductosSimples = FXCollections.observableArrayList();
    private final ObservableList<Categoria> listaCategorias = FXCollections.observableArrayList();
    private final ObservableList<ProductoVariante> listaVariantesEnEdicion = FXCollections.observableArrayList();
    private final ObservableList<ProductoModificador> listaModificadoresGlobales = FXCollections.observableArrayList();
    private final ObservableList<ComboDetalle> listaComboComponentesEnEdicion = FXCollections.observableArrayList();
    private final List<CheckBox> checkboxesModificadores = new ArrayList<>();
    private Producto productoEnEdicion;
    private Integer idComboEnEdicion;
    private Integer idComboProductoEnEdicion;
    private String rutaImagenRelativaActual;

    @FXML
    public void initialize() {
        AnimationUtils.attachTactileFeedback(btnSeleccionarFoto);
        AnimationUtils.attachTactileFeedback(btnGuardarPlatillo);
        AnimationUtils.attachTactileFeedback(btnLimpiarForm);
        AnimationUtils.attachTactileFeedback(btnNuevoCombo);
        AnimationUtils.attachTactileFeedback(btnDescargarPlantillaMenu);
        AnimationUtils.attachTactileFeedback(btnImportarMenu);
        AnimationUtils.attachTactileFeedback(btnAgregarVariante);
        AnimationUtils.attachTactileFeedback(btnCrearModificadorGlobal);
        AnimationUtils.attachTactileFeedback(btnAgregarComponenteCombo);
        AnimationUtils.attachTactileFeedback(btnGuardarCombo);

        configurarTabla();
        configurarSeccionPersonalizacion();
        configurarSeccionCombos();
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
        if (colOpciones != null) {
            colOpciones.setCellValueFactory(c -> new SimpleStringProperty(
                formatearResumenOpcionesProducto(c.getValue())
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

        if (colOpciones != null) {
            colOpciones.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null || "Estándar".equals(item)) {
                        setGraphic(null);
                        setText(empty ? null : "Estándar");
                        if (!empty) {
                            getStyleClass().setAll("table-cell", "text-caption-muted");
                        }
                    } else if ("COMBO".equalsIgnoreCase(item)) {
                        Label pill = new Label("COMBO");
                        pill.setStyle("-fx-background-color: #EEF2FF; -fx-text-fill: #4338CA; "
                                + "-fx-border-color: #A5B4FC; -fx-border-radius: 10; -fx-background-radius: 10; "
                                + "-fx-padding: 2 9; -fx-font-weight: 800; -fx-font-size: 10.5px;");
                        setGraphic(pill);
                        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                    } else {
                        Label pill = new Label(item);
                        pill.getStyleClass().add("badge-pill-success");
                        setGraphic(pill);
                        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                    }
                }
            });
        }

        tblMenu.setItems(listaProductos);
        cmbCategoria.setItems(listaCategorias);
        cmbCategoria.setEditable(true);
        cmbCategoria.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Categoria c) {
                return c != null ? c.getNombre() : "";
            }

            @Override
            public Categoria fromString(String string) {
                if (string == null || string.isBlank()) return null;
                for (Categoria c : listaCategorias) {
                    if (c.getNombre() != null && c.getNombre().trim().equalsIgnoreCase(string.trim())) {
                        return c;
                    }
                }
                return new Categoria(0, string.trim(), "Categoría autogenerada", true);
            }
        });
    }

    public void cargarDatosMenu() {
        final Set<Integer> seleccionadosPrevios = obtenerIdsModificadoresSeleccionadosSet();
        new Thread(() -> {
            List<Categoria> cats = new ArrayList<>();
            List<Producto> prods = new ArrayList<>();
            List<ProductoModificador> modsGlobales = new ArrayList<>();
            try (Connection conn = ConexionBD.getConnection()) {
                cats = productoDAO.listarCategorias(conn);
                prods = personalizacionService.listarTodosConOpciones(conn);
                modsGlobales = personalizacionService.listarModificadoresGlobales();
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Error cargando menú desde BD", e);
            }
            final List<Categoria> catsFinal = cats;
            final List<Producto> prodsFinal = prods;
            final List<ProductoModificador> modsFinal = modsGlobales;
            Platform.runLater(() -> {
                listaCategorias.setAll(catsFinal);
                if (!listaCategorias.isEmpty() && cmbCategoria.getValue() == null) {
                    cmbCategoria.getSelectionModel().selectFirst();
                }
                listaProductos.setAll(prodsFinal);
                actualizarListaProductosSimples();
                listaModificadoresGlobales.setAll(modsFinal);
                if (productoEnEdicion != null && productoEnEdicion.getIdProducto() > 0) {
                    for (Producto pAct : prodsFinal) {
                        if (pAct.getIdProducto() == productoEnEdicion.getIdProducto()) {
                            productoEnEdicion = pAct;
                            listaVariantesEnEdicion.setAll(pAct.getVariantes());
                            break;
                        }
                    }
                    Set<Integer> idsProducto = new LinkedHashSet<>();
                    for (ProductoModificador pm : productoEnEdicion.getModificadores()) {
                        idsProducto.add(pm.getIdModificador());
                    }
                    renderizarCheckboxesModificadores(idsProducto);
                } else {
                    renderizarCheckboxesModificadores(seleccionadosPrevios);
                }
                actualizarBadgePersonalizacion();
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

        if (p.isEsCombo()) {
            cargarComboEnTabEditor(p);
            return;
        }
        if (tabPaneFichaPlatillo != null && tabCombo != null
                && tabPaneFichaPlatillo.getSelectionModel().getSelectedItem() == tabCombo
                && tabDatosGenerales != null) {
            tabPaneFichaPlatillo.getSelectionModel().select(tabDatosGenerales);
        }

        listaVariantesEnEdicion.setAll(p.getVariantes());
        Set<Integer> idsAsociados = new LinkedHashSet<>();
        for (ProductoModificador pm : p.getModificadores()) {
            idsAsociados.add(pm.getIdModificador());
        }
        if (listaModificadoresGlobales.isEmpty() && p.getIdProducto() > 0) {
            UiAsyncExecutor.ejecutarAsync(
                () -> {
                    List<ProductoModificador> globales = personalizacionService.listarModificadoresGlobales();
                    List<ProductoVariante> vars = personalizacionService.listarVariantesPorProducto(p.getIdProducto());
                    List<ProductoModificador> asoc = personalizacionService.listarModificadoresPorProducto(p.getIdProducto());
                    return new Object[]{globales, vars, asoc};
                },
                res -> {
                    if (productoEnEdicion == null || productoEnEdicion.getIdProducto() != p.getIdProducto()) {
                        return;
                    }
                    @SuppressWarnings("unchecked")
                    List<ProductoModificador> globales = (List<ProductoModificador>) res[0];
                    @SuppressWarnings("unchecked")
                    List<ProductoVariante> vars = (List<ProductoVariante>) res[1];
                    @SuppressWarnings("unchecked")
                    List<ProductoModificador> asoc = (List<ProductoModificador>) res[2];
                    listaModificadoresGlobales.setAll(globales);
                    listaVariantesEnEdicion.setAll(vars);
                    Set<Integer> ids = new LinkedHashSet<>();
                    for (ProductoModificador pm : asoc) {
                        ids.add(pm.getIdModificador());
                    }
                    renderizarCheckboxesModificadores(ids);
                    actualizarBadgePersonalizacion();
                },
                ex -> LOGGER.log(Level.FINE, "Aviso cargando opciones de platillo #" + p.getIdProducto(), ex)
            );
        } else {
            renderizarCheckboxesModificadores(idsAsociados);
            actualizarBadgePersonalizacion();
        }
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
                                        String imagenUrl) throws SQLException {
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

    public Producto guardarPlatilloEnBDConOpciones(int idCategoria,
                                                   String codigo,
                                                   String nombre,
                                                   BigDecimal precioVenta,
                                                   boolean disponible,
                                                   boolean esMenuDelDia,
                                                   String imagenUrl,
                                                   List<ProductoVariante> variantes,
                                                   List<Integer> idsModificadores)
            throws SQLException, NegocioException {
        Producto guardado = guardarPlatilloEnBD(idCategoria, codigo, nombre, precioVenta, disponible, esMenuDelDia, imagenUrl);
        if (guardado != null && guardado.getIdProducto() > 0) {
            personalizacionService.guardarConfiguracionProducto(
                    guardado.getIdProducto(),
                    variantes != null ? variantes : List.of(),
                    idsModificadores != null ? idsModificadores : List.of()
            );
            guardado.setVariantes(personalizacionService.listarVariantesPorProducto(guardado.getIdProducto()));
            guardado.setModificadores(personalizacionService.listarModificadoresPorProducto(guardado.getIdProducto()));
        }
        return guardado;
    }

    public Producto guardarPlatilloEnBD(int idCategoria,
                                        String codigo,
                                        String nombre,
                                        BigDecimal precioVenta,
                                        boolean disponible,
                                        String imagenUrl) throws java.sql.SQLException {
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
        if (tabPaneFichaPlatillo != null && tabDatosGenerales != null
                && tabPaneFichaPlatillo.getSelectionModel().getSelectedItem() == tabCombo) {
            tabPaneFichaPlatillo.getSelectionModel().select(tabDatosGenerales);
        }
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
        if (cmbCategoria.getEditor() != null) {
            cmbCategoria.getEditor().clear();
        }
        if (!listaCategorias.isEmpty()) {
            cmbCategoria.getSelectionModel().selectFirst();
        }
        actualizarVistaPreviaImagen(null);
        listaVariantesEnEdicion.clear();
        renderizarCheckboxesModificadores(Set.of());
        actualizarBadgePersonalizacion();
    }

    @FXML
    public void handleGuardarPlatillo(ActionEvent event) {
        String textoCat = null;
        if (cmbCategoria.getEditor() != null && cmbCategoria.getEditor().getText() != null) {
            textoCat = cmbCategoria.getEditor().getText().trim();
        }
        if ((textoCat == null || textoCat.isBlank()) && cmbCategoria.getValue() != null) {
            textoCat = cmbCategoria.getValue().getNombre();
        }
        if (textoCat == null || textoCat.isBlank()) {
            textoCat = "General";
        }
        final String categoriaNombre = textoCat;

        Categoria catExistente = null;
        for (Categoria c : listaCategorias) {
            if (c.getNombre() != null && c.getNombre().trim().equalsIgnoreCase(categoriaNombre)) {
                catExistente = c;
                break;
            }
        }

        String cod = txtCodigo.getText() != null ? txtCodigo.getText().trim() : ("PLT-" + (System.currentTimeMillis() % 10000));
        String nom = txtNombre.getText() != null ? txtNombre.getText().trim() : "";
        if (nom.isEmpty()) {
            AnimationUtils.mostrarToast(rootMenuStack, "Ingrese el nombre del platillo.", true);
            return;
        }
        final BigDecimal precio;
        try {
            precio = new BigDecimal(txtPrecio.getText().trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (RuntimeException ex) {
            AnimationUtils.mostrarToast(rootMenuStack, "Ingrese un precio numérico válido.", true);
            return;
        }
        final boolean disp = (chkDisponible != null) ? chkDisponible.isSelected() : chkActivo.isSelected();
        final boolean menuDia = (chkMenuDelDia != null) && chkMenuDelDia.isSelected();
        final String imagenUrl = rutaImagenRelativaActual;
        final Categoria catInicial = catExistente;
        final List<ProductoVariante> variantesSnapshot = new ArrayList<>(listaVariantesEnEdicion);
        final List<Integer> idsModificadoresSnapshot = new ArrayList<>(obtenerIdsModificadoresSeleccionadosSet());

        if (btnGuardarPlatillo != null) {
            btnGuardarPlatillo.setDisable(true);
        }
        UiAsyncExecutor.ejecutarAsync(
            () -> {
                int idCat;
                Categoria nuevaCatCreada = null;
                if (catInicial != null && catInicial.getIdCategoria() > 0) {
                    idCat = catInicial.getIdCategoria();
                } else {
                    Categoria enListaBd = null;
                    try (Connection conn = ConexionBD.getConnection()) {
                        List<Categoria> enBD = categoriaDAO.listarTodas(conn);
                        for (Categoria c : enBD) {
                            if (c.getNombre() != null && c.getNombre().trim().equalsIgnoreCase(categoriaNombre)) {
                                enListaBd = c;
                                break;
                            }
                        }
                        if (enListaBd != null && enListaBd.getIdCategoria() > 0) {
                            idCat = enListaBd.getIdCategoria();
                        } else {
                            Categoria nuevaCat = new Categoria(0, categoriaNombre, "Categoría creada dinámicamente", true);
                            idCat = categoriaDAO.crear(conn, nuevaCat);
                            nuevaCatCreada = nuevaCat;
                        }
                    }
                }
                guardarPlatilloEnBDConOpciones(
                        idCat, cod, nom, precio, disp, menuDia, imagenUrl,
                        variantesSnapshot, idsModificadoresSnapshot);
                return nuevaCatCreada;
            },
            nuevaCat -> {
                if (btnGuardarPlatillo != null) {
                    btnGuardarPlatillo.setDisable(false);
                }
                if (nuevaCat != null) {
                    listaCategorias.add(nuevaCat);
                    cmbCategoria.setValue(nuevaCat);
                }
                AnimationUtils.mostrarToast(rootMenuStack, "Platillo guardado exitosamente.", false);
                handleNuevoPlatillo(null);
                cargarDatosMenu();
            },
            e -> {
                if (btnGuardarPlatillo != null) {
                    btnGuardarPlatillo.setDisable(false);
                }
                AnimationUtils.mostrarToast(
                        rootMenuStack,
                        "Error al guardar platillo: " + (e != null ? e.getMessage() : "desconocido"),
                        true);
            }
        );
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

    @FXML
    public void handleDescargarPlantillaExcel(ActionEvent event) {
        ImportacionUIHelper.descargarPlantilla(
                btnDescargarPlantillaMenu != null ? btnDescargarPlantillaMenu : rootMenuStack,
                rootMenuStack,
                lblResumenImportacionMenu,
                "Guardar Plantilla Excel de Menú",
                "plantilla_menu.xlsx",
                importacionMasivaService::exportarPlantillaMenu);
    }

    @FXML
    public void handleImportarDesdeExcel(ActionEvent event) {
        ImportacionUIHelper.ejecutarImportacion(
                btnImportarMenu != null ? btnImportarMenu : rootMenuStack,
                rootMenuStack,
                btnImportarMenu,
                pbImportacionMenu,
                lblResumenImportacionMenu,
                "Importar Catálogo de Menú (Excel / CSV)",
                importacionMasivaService::importarMenu,
                this::cargarDatosMenu);
    }

    private void configurarSeccionPersonalizacion() {
        if (cmbModoVariante != null) {
            cmbModoVariante.setItems(FXCollections.observableArrayList(MODO_PRECIO_FIJO, MODO_PRECIO_ADICIONAL));
            cmbModoVariante.getSelectionModel().select(MODO_PRECIO_FIJO);
        }
        if (tblVariantes != null) {
            colVarNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombreVariante()));
            colVarModo.setCellValueFactory(c -> new SimpleStringProperty(
                    c.getValue().esPrecioFijo() ? "Precio Fijo" : "+ Adicional"));
            colVarPrecio.setCellValueFactory(c -> {
                BigDecimal val = c.getValue().esPrecioFijo()
                        ? c.getValue().getPrecioFijo()
                        : c.getValue().getPrecioAdicional();
                String pref = c.getValue().esPrecioFijo() ? "S/ " : "+S/ ";
                return new SimpleStringProperty(pref + (val != null ? val.setScale(2, RoundingMode.HALF_UP) : "0.00"));
            });
            tblVariantes.setItems(listaVariantesEnEdicion);
        }
        if (lstModificadoresGlobales != null) {
            lstModificadoresGlobales.setItems(listaModificadoresGlobales);
        }
        actualizarBadgePersonalizacion();
    }

    @FXML
    public void handleAgregarVariante(ActionEvent event) {
        String nombreVar = txtNombreVariante != null && txtNombreVariante.getText() != null
                ? txtNombreVariante.getText().trim() : "";
        if (nombreVar.isEmpty()) {
            AnimationUtils.mostrarToast(rootMenuStack, "Ingrese el nombre de la variante (ej. Mediana, Familiar).", true);
            return;
        }
        BigDecimal monto;
        try {
            String rawMonto = txtMontoVariante != null && txtMontoVariante.getText() != null
                    ? txtMontoVariante.getText().trim() : "";
            monto = new BigDecimal(rawMonto).setScale(2, RoundingMode.HALF_UP);
            if (monto.compareTo(BigDecimal.ZERO) < 0) {
                AnimationUtils.mostrarToast(rootMenuStack, "El monto de la variante no puede ser negativo.", true);
                return;
            }
        } catch (RuntimeException ex) {
            AnimationUtils.mostrarToast(rootMenuStack, "Ingrese un monto numérico válido para la variante.", true);
            return;
        }

        boolean esFijo = cmbModoVariante == null || MODO_PRECIO_FIJO.equals(cmbModoVariante.getValue());
        ProductoVariante existente = null;
        for (ProductoVariante v : listaVariantesEnEdicion) {
            if (v.getNombreVariante() != null && v.getNombreVariante().trim().equalsIgnoreCase(nombreVar)) {
                existente = v;
                break;
            }
        }

        int idProd = productoEnEdicion != null ? productoEnEdicion.getIdProducto() : 0;
        if (existente != null) {
            existente.setNombreVariante(nombreVar);
            existente.setPrecioFijo(esFijo ? monto : null);
            existente.setPrecioAdicional(esFijo ? BigDecimal.ZERO : monto);
            if (tblVariantes != null) {
                tblVariantes.refresh();
            }
        } else {
            int orden = listaVariantesEnEdicion.size() + 1;
            ProductoVariante nueva = esFijo
                    ? ProductoVariante.conPrecioFijo(idProd, nombreVar, monto, orden)
                    : ProductoVariante.conPrecioAdicional(idProd, nombreVar, monto, orden);
            listaVariantesEnEdicion.add(nueva);
        }

        if (txtNombreVariante != null) {
            txtNombreVariante.clear();
        }
        if (txtMontoVariante != null) {
            txtMontoVariante.clear();
        }
        actualizarBadgePersonalizacion();
    }

    @FXML
    public void handleQuitarVariante(ActionEvent event) {
        if (tblVariantes == null) {
            return;
        }
        ProductoVariante sel = tblVariantes.getSelectionModel().getSelectedItem();
        if (sel == null) {
            AnimationUtils.mostrarToast(rootMenuStack, "Seleccione un tamaño de la lista para quitarlo.", true);
            return;
        }
        listaVariantesEnEdicion.remove(sel);
        actualizarBadgePersonalizacion();
    }

    @FXML
    public void handleCrearModificadorGlobal(ActionEvent event) {
        String nombre = txtNuevoModNombre != null && txtNuevoModNombre.getText() != null
                ? txtNuevoModNombre.getText().trim() : "";
        if (nombre.isEmpty()) {
            AnimationUtils.mostrarToast(rootMenuStack, "Ingrese el nombre del extra (ej. Extra Queso).", true);
            return;
        }
        BigDecimal precio;
        try {
            String raw = txtNuevoModPrecio != null && txtNuevoModPrecio.getText() != null
                    ? txtNuevoModPrecio.getText().trim() : "0";
            precio = (raw.isEmpty() ? BigDecimal.ZERO : new BigDecimal(raw)).setScale(2, RoundingMode.HALF_UP);
            if (precio.compareTo(BigDecimal.ZERO) < 0) {
                AnimationUtils.mostrarToast(rootMenuStack, "El precio del extra no puede ser negativo.", true);
                return;
            }
        } catch (RuntimeException ex) {
            AnimationUtils.mostrarToast(rootMenuStack, "Ingrese un precio válido para el extra.", true);
            return;
        }

        final Set<Integer> seleccionados = obtenerIdsModificadoresSeleccionadosSet();
        if (btnCrearModificadorGlobal != null) {
            btnCrearModificadorGlobal.setDisable(true);
        }
        UiAsyncExecutor.ejecutarAsync(
            () -> {
                ProductoModificador guardado = personalizacionService.guardarModificadorGlobal(nombre, precio);
                List<ProductoModificador> globales = personalizacionService.listarModificadoresGlobales();
                return new Object[]{guardado, globales};
            },
            res -> {
                if (btnCrearModificadorGlobal != null) {
                    btnCrearModificadorGlobal.setDisable(false);
                }
                ProductoModificador creado = (ProductoModificador) res[0];
                @SuppressWarnings("unchecked")
                List<ProductoModificador> globales = (List<ProductoModificador>) res[1];
                listaModificadoresGlobales.setAll(globales);
                if (creado != null && creado.getIdModificador() > 0) {
                    seleccionados.add(creado.getIdModificador());
                }
                renderizarCheckboxesModificadores(seleccionados);
                if (txtNuevoModNombre != null) {
                    txtNuevoModNombre.clear();
                }
                if (txtNuevoModPrecio != null) {
                    txtNuevoModPrecio.clear();
                }
                actualizarBadgePersonalizacion();
                AnimationUtils.mostrarToast(rootMenuStack, "Extra '" + nombre + "' guardado en catálogo global.", false);
            },
            ex -> {
                if (btnCrearModificadorGlobal != null) {
                    btnCrearModificadorGlobal.setDisable(false);
                }
                AnimationUtils.mostrarToast(rootMenuStack,
                        "No se pudo guardar el extra: " + (ex != null ? ex.getMessage() : "desconocido"), true);
            }
        );
    }

    @FXML
    public void handleEliminarModificadorGlobal(ActionEvent event) {
        if (lstModificadoresGlobales == null) {
            return;
        }
        ProductoModificador sel = lstModificadoresGlobales.getSelectionModel().getSelectedItem();
        if (sel == null) {
            AnimationUtils.mostrarToast(rootMenuStack, "Seleccione un extra del catálogo global para eliminar.", true);
            return;
        }
        final Set<Integer> seleccionados = obtenerIdsModificadoresSeleccionadosSet();
        seleccionados.remove(sel.getIdModificador());
        UiAsyncExecutor.ejecutarAsync(
            () -> {
                personalizacionService.eliminarModificadorGlobal(sel.getIdModificador());
                return personalizacionService.listarModificadoresGlobales();
            },
            globales -> {
                listaModificadoresGlobales.setAll(globales);
                renderizarCheckboxesModificadores(seleccionados);
                actualizarBadgePersonalizacion();
                AnimationUtils.mostrarToast(rootMenuStack, "Extra eliminado del catálogo.", false);
            },
            ex -> AnimationUtils.mostrarToast(rootMenuStack,
                    "Error al eliminar extra: " + (ex != null ? ex.getMessage() : "desconocido"), true)
        );
    }

    private void renderizarCheckboxesModificadores(Set<Integer> idsMarcados) {
        if (vboxCheckboxesModificadores == null) {
            return;
        }
        vboxCheckboxesModificadores.getChildren().clear();
        checkboxesModificadores.clear();
        if (listaModificadoresGlobales.isEmpty()) {
            Label emptyLbl = new Label("Sin extras creados aún. Cree uno abajo.");
            emptyLbl.getStyleClass().add("text-caption-muted");
            vboxCheckboxesModificadores.getChildren().add(emptyLbl);
            return;
        }
        for (ProductoModificador mod : listaModificadoresGlobales) {
            CheckBox chk = new CheckBox(mod.formatearParaSelector());
            chk.setId("chkMenuMod_" + mod.getIdModificador());
            chk.setUserData(mod);
            chk.setSelected(idsMarcados != null && idsMarcados.contains(mod.getIdModificador()));
            chk.selectedProperty().addListener((obs, oldV, newV) -> actualizarBadgePersonalizacion());
            checkboxesModificadores.add(chk);
            vboxCheckboxesModificadores.getChildren().add(chk);
        }
    }

    private Set<Integer> obtenerIdsModificadoresSeleccionadosSet() {
        Set<Integer> ids = new LinkedHashSet<>();
        for (CheckBox chk : checkboxesModificadores) {
            if (chk.isSelected() && chk.getUserData() instanceof ProductoModificador pm && pm.getIdModificador() > 0) {
                ids.add(pm.getIdModificador());
            }
        }
        return ids;
    }

    private void actualizarBadgePersonalizacion() {
        if (lblBadgePersonalizacion == null) {
            return;
        }
        int numVar = listaVariantesEnEdicion.size();
        int numMods = obtenerIdsModificadoresSeleccionadosSet().size();
        lblBadgePersonalizacion.setText(formatearTextoOpciones(numVar, numMods));
    }

    private static String formatearResumenOpcionesProducto(Producto p) {
        if (p == null) {
            return "Estándar";
        }
        if (p.isEsCombo()) {
            return "COMBO";
        }
        return formatearTextoOpciones(p.getVariantes().size(), p.getModificadores().size());
    }

    private static String formatearTextoOpciones(int numVar, int numMods) {
        return com.foodcore.util.FormatoPersonalizacionUtil.construirResumenOpciones(numVar, numMods, "Estándar");
    }

    private void configurarSeccionCombos() {
        if (tabPaneFichaPlatillo != null && tabCombo != null) {
            tabPaneFichaPlatillo.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
                boolean enCombo = newTab == tabCombo;
                if (btnGuardarPlatillo != null) {
                    btnGuardarPlatillo.setVisible(!enCombo);
                    btnGuardarPlatillo.setManaged(!enCombo);
                }
            });
        }
        if (cmbComboComponente != null) {
            cmbComboComponente.setItems(listaProductosSimples);
            cmbComboComponente.setConverter(new javafx.util.StringConverter<>() {
                @Override
                public String toString(Producto p) {
                    if (p == null) {
                        return "";
                    }
                    BigDecimal pr = p.getPrecioVenta() != null
                            ? p.getPrecioVenta().setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                    return p.getNombre() + " (S/ " + pr + ")";
                }

                @Override
                public Producto fromString(String s) {
                    return null;
                }
            });
        }
        if (tblComboComponentes != null) {
            colComboCompNombre.setCellValueFactory(c -> new SimpleStringProperty(
                    c.getValue().getNombreProducto() != null
                            ? c.getValue().getNombreProducto()
                            : ("Producto #" + c.getValue().getIdProducto())));
            colComboCompCantidad.setCellValueFactory(c -> new SimpleStringProperty(
                    String.valueOf(Math.max(1, c.getValue().getCantidad()))));
            tblComboComponentes.setItems(listaComboComponentesEnEdicion);
        }
        actualizarResumenCombo();
    }

    private void actualizarListaProductosSimples() {
        List<Producto> simples = new ArrayList<>();
        for (Producto p : listaProductos) {
            if (p != null && !p.isEsCombo()) {
                simples.add(p);
            }
        }
        listaProductosSimples.setAll(simples);
        if (cmbComboComponente != null && cmbComboComponente.getValue() == null && !listaProductosSimples.isEmpty()) {
            cmbComboComponente.getSelectionModel().selectFirst();
        }
    }

    private void cargarComboEnTabEditor(Producto p) {
        this.idComboEnEdicion = p.getIdCombo();
        this.idComboProductoEnEdicion = p.getIdProducto();
        if (lblTituloFormulario != null) {
            lblTituloFormulario.setText("Editar Combo #" + (p.getIdCombo() != null ? p.getIdCombo() : p.getIdProducto()));
        }
        if (lblBadgePersonalizacion != null) {
            lblBadgePersonalizacion.setText("COMBO");
        }
        if (txtComboCodigo != null) {
            txtComboCodigo.setText(p.getCodigo() != null ? p.getCodigo() : "");
        }
        if (txtComboNombre != null) {
            txtComboNombre.setText(p.getNombre() != null ? p.getNombre() : "");
        }
        if (txtComboPrecio != null) {
            txtComboPrecio.setText(p.getPrecioVenta() != null ? p.getPrecioVenta().toPlainString() : "0.00");
        }
        if (chkComboActivo != null) {
            chkComboActivo.setSelected(p.isDisponible());
        }
        listaComboComponentesEnEdicion.setAll(p.getComboComponentes());
        actualizarResumenCombo();
        if (tabPaneFichaPlatillo != null && tabCombo != null) {
            tabPaneFichaPlatillo.getSelectionModel().select(tabCombo);
        }
        if (p.getComboComponentes().isEmpty() && p.getIdProducto() > 0) {
            final int idProdTarget = p.getIdProducto();
            UiAsyncExecutor.ejecutarAsync(
                () -> comboService.obtenerComboPorProducto(idProdTarget).orElse(null),
                combo -> {
                    if (combo != null && Integer.valueOf(idProdTarget).equals(this.idComboProductoEnEdicion)) {
                        this.idComboEnEdicion = combo.getIdCombo();
                        listaComboComponentesEnEdicion.setAll(combo.getDetalles());
                        actualizarResumenCombo();
                    }
                },
                ex -> LOGGER.log(Level.FINE, "Aviso cargando detalle del combo #" + idProdTarget, ex)
            );
        }
    }

    @FXML
    public void handleNuevoCombo(ActionEvent event) {
        this.productoEnEdicion = null;
        this.idComboEnEdicion = null;
        this.idComboProductoEnEdicion = null;
        actualizarListaProductosSimples();
        if (lblTituloFormulario != null) {
            lblTituloFormulario.setText("Crear Combo");
        }
        if (lblBadgePersonalizacion != null) {
            lblBadgePersonalizacion.setText("COMBO");
        }
        if (txtComboCodigo != null) {
            txtComboCodigo.clear();
        }
        if (txtComboNombre != null) {
            txtComboNombre.clear();
        }
        if (txtComboPrecio != null) {
            txtComboPrecio.clear();
        }
        if (txtComboCantidad != null) {
            txtComboCantidad.setText("1");
        }
        if (chkComboActivo != null) {
            chkComboActivo.setSelected(true);
        }
        listaComboComponentesEnEdicion.clear();
        actualizarResumenCombo();
        if (tabPaneFichaPlatillo != null && tabCombo != null) {
            tabPaneFichaPlatillo.getSelectionModel().select(tabCombo);
        }
        if (txtComboNombre != null) {
            txtComboNombre.requestFocus();
        }
    }

    @FXML
    public void handleAgregarComponenteCombo(ActionEvent event) {
        Producto seleccionado = cmbComboComponente != null ? cmbComboComponente.getValue() : null;
        if (seleccionado == null && !listaProductosSimples.isEmpty()) {
            seleccionado = listaProductosSimples.get(0);
        }
        if (seleccionado == null) {
            AnimationUtils.mostrarToast(rootMenuStack, "Seleccione un producto para incluir en el combo.", true);
            return;
        }
        int qty;
        try {
            String raw = txtComboCantidad != null && txtComboCantidad.getText() != null
                    ? txtComboCantidad.getText().trim() : "1";
            qty = Integer.parseInt(raw.isEmpty() ? "1" : raw);
            if (qty <= 0) {
                AnimationUtils.mostrarToast(rootMenuStack, "La cantidad debe ser mayor a cero.", true);
                return;
            }
        } catch (NumberFormatException ex) {
            AnimationUtils.mostrarToast(rootMenuStack, "Ingrese una cantidad entera válida.", true);
            return;
        }
        ComboDetalle existente = null;
        for (ComboDetalle d : listaComboComponentesEnEdicion) {
            if (d.getIdProducto() == seleccionado.getIdProducto()) {
                existente = d;
                break;
            }
        }
        if (existente != null) {
            existente.setCantidad(existente.getCantidad() + qty);
            if (tblComboComponentes != null) {
                tblComboComponentes.refresh();
            }
        } else {
            ComboDetalle nuevo = new ComboDetalle(seleccionado.getIdProducto(), seleccionado.getNombre(), qty);
            nuevo.setCodigoProducto(seleccionado.getCodigo());
            nuevo.setPrecioUnitarioReferencia(seleccionado.getPrecioVenta());
            listaComboComponentesEnEdicion.add(nuevo);
        }
        if (txtComboCantidad != null) {
            txtComboCantidad.setText("1");
        }
        actualizarResumenCombo();
    }

    @FXML
    public void handleQuitarComponenteCombo(ActionEvent event) {
        if (tblComboComponentes == null) {
            return;
        }
        ComboDetalle sel = tblComboComponentes.getSelectionModel().getSelectedItem();
        if (sel == null) {
            AnimationUtils.mostrarToast(rootMenuStack, "Seleccione un componente para quitar.", true);
            return;
        }
        listaComboComponentesEnEdicion.remove(sel);
        actualizarResumenCombo();
    }

    @FXML
    public void handleGuardarCombo(ActionEvent event) {
        String nombre = txtComboNombre != null && txtComboNombre.getText() != null
                ? txtComboNombre.getText().trim() : "";
        if (nombre.isEmpty()) {
            AnimationUtils.mostrarToast(rootMenuStack, "Ingrese el nombre del combo (ej. Combo Duo Burger).", true);
            return;
        }
        BigDecimal precio;
        try {
            String raw = txtComboPrecio != null && txtComboPrecio.getText() != null
                    ? txtComboPrecio.getText().trim() : "";
            precio = new BigDecimal(raw).setScale(2, RoundingMode.HALF_UP);
            if (precio.compareTo(BigDecimal.ZERO) <= 0) {
                AnimationUtils.mostrarToast(rootMenuStack, "El precio del combo debe ser mayor a S/ 0.00.", true);
                return;
            }
        } catch (RuntimeException ex) {
            AnimationUtils.mostrarToast(rootMenuStack, "Ingrese un precio válido para el combo.", true);
            return;
        }
        if (listaComboComponentesEnEdicion.isEmpty()) {
            AnimationUtils.mostrarToast(rootMenuStack, "Agregue al menos un producto componente al combo.", true);
            return;
        }

        String codigo = txtComboCodigo != null && txtComboCodigo.getText() != null
                ? txtComboCodigo.getText().trim() : "";
        boolean activo = chkComboActivo == null || chkComboActivo.isSelected();
        List<ComboDetalle> snapshot = new ArrayList<>(listaComboComponentesEnEdicion);
        Integer idComboActual = this.idComboEnEdicion;
        Integer idProdActual = this.idComboProductoEnEdicion;

        if (btnGuardarCombo != null) {
            btnGuardarCombo.setDisable(true);
        }
        UiAsyncExecutor.ejecutarAsync(
            () -> guardarComboEnBD(idComboActual, idProdActual, codigo, nombre, precio, activo, snapshot),
            guardado -> {
                if (btnGuardarCombo != null) {
                    btnGuardarCombo.setDisable(false);
                }
                AnimationUtils.mostrarToast(rootMenuStack,
                        "Combo '" + guardado.getNombre() + "' guardado en catálogo POS.", false);
                handleNuevoCombo(null);
                cargarDatosMenu();
            },
            e -> {
                if (btnGuardarCombo != null) {
                    btnGuardarCombo.setDisable(false);
                }
                AnimationUtils.mostrarToast(rootMenuStack,
                        "Error al guardar combo: " + (e != null ? e.getMessage() : "desconocido"), true);
            }
        );
    }

    public Combo guardarComboEnBD(Integer idCombo,
                                  Integer idProducto,
                                  String codigo,
                                  String nombre,
                                  BigDecimal precioCombo,
                                  boolean activo,
                                  List<ComboDetalle> componentes) throws NegocioException, SQLException {
        Combo combo = new Combo(idCombo != null ? idCombo : 0, idProducto, nombre, precioCombo, activo);
        combo.setCodigo(codigo);
        combo.setDetalles(componentes);
        return comboService.guardarCombo(combo);
    }

    private void actualizarResumenCombo() {
        if (lblComboResumen == null) {
            return;
        }
        int totalUnidades = 0;
        for (ComboDetalle d : listaComboComponentesEnEdicion) {
            totalUnidades += Math.max(1, d.getCantidad());
        }
        lblComboResumen.setText(listaComboComponentesEnEdicion.size() + " productos (" + totalUnidades + " uds.)");
    }

    public ObservableList<ComboDetalle> getListaComboComponentesEnEdicion() {
        return listaComboComponentesEnEdicion;
    }

    public ObservableList<ProductoVariante> getListaVariantesEnEdicion() {
        return listaVariantesEnEdicion;
    }

    public ObservableList<ProductoModificador> getListaModificadoresGlobales() {
        return listaModificadoresGlobales;
    }
}