package com.foodcore.controller;

import com.foodcore.config.AppContainer;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Categoria;
import com.foodcore.model.Combo;
import com.foodcore.model.ComboDetalle;
import com.foodcore.model.Producto;
import com.foodcore.model.ProductoModificador;
import com.foodcore.model.ProductoVariante;
import com.foodcore.controller.menu.DefaultMenuCrudPresenter;
import com.foodcore.controller.menu.DefaultMenuComboCoordinator;
import com.foodcore.controller.menu.DefaultMenuPersonalizacionCoordinator;
import com.foodcore.controller.menu.LocalMenuImagenStorage;
import com.foodcore.controller.menu.MenuComboCoordinator;
import com.foodcore.controller.menu.MenuCrudPresenter;
import com.foodcore.controller.menu.MenuImagenStorage;
import com.foodcore.controller.menu.MenuPersonalizacionCoordinator;
import com.foodcore.controller.menu.MenuTablaUiConfigurer;
import com.foodcore.service.ComboService;
import com.foodcore.service.ImportacionMasivaService;
import com.foodcore.service.MenuGestionService;
import com.foodcore.service.PersonalizacionProductoService;
import com.foodcore.util.AnimationUtils;
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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Controlador de Gestión de Menú y Fotografías según SPEC-FOODCORE-v1.2 (§2.2):
 * Tabla con miniatura (thumbnail), Nombre, Categoría, Precio y Estado;
 * selector {@link FileChooser} que copia la imagen a {@code uploads/platillos/} y almacena
 * la ruta relativa en {@code producto.imagen_url}.
 */
public class MenuController {

    private static final Logger LOGGER = Logger.getLogger(MenuController.class.getName());
    public static final Path DIRECTORIO_UPLOADS = Path.of("uploads", "platillos");
    private static final MenuImagenStorage IMAGEN_STORAGE_STATIC = new LocalMenuImagenStorage();
    private static final MenuCrudPresenter CRUD_STATICS = new DefaultMenuCrudPresenter();
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

    private final MenuGestionService menuGestionService;
    private final ImportacionMasivaService importacionMasivaService;
    private final PersonalizacionProductoService personalizacionService;
    private final ComboService comboService;
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
    private final MenuImagenStorage imagenStorage;
    private final MenuCrudPresenter crudPresenter;
    private final MenuPersonalizacionCoordinator personalizacionCoordinator;
    private final MenuComboCoordinator comboCoordinator;
    private final MenuTablaUiConfigurer tablaUiConfigurer;

    public MenuController() {
        this(AppContainer.getDefault().resolve(MenuGestionService.class),
                AppContainer.getDefault().resolve(ImportacionMasivaService.class),
                AppContainer.getDefault().personalizacionProductoService(),
                AppContainer.getDefault().comboService());
    }

    public MenuController(MenuGestionService menuGestionService,
                          ImportacionMasivaService importacionMasivaService,
                          PersonalizacionProductoService personalizacionService,
                          ComboService comboService) {
        this.menuGestionService = java.util.Objects.requireNonNull(menuGestionService, "menuGestionService");
        this.importacionMasivaService =
                java.util.Objects.requireNonNull(importacionMasivaService, "importacionMasivaService");
        this.personalizacionService =
                java.util.Objects.requireNonNull(personalizacionService, "personalizacionService");
        this.comboService = java.util.Objects.requireNonNull(comboService, "comboService");
        this.imagenStorage = new LocalMenuImagenStorage();
        this.crudPresenter = new DefaultMenuCrudPresenter(this.menuGestionService, this.personalizacionService);
        this.personalizacionCoordinator = new DefaultMenuPersonalizacionCoordinator(this.personalizacionService);
        this.comboCoordinator = new DefaultMenuComboCoordinator(this.comboService);
        this.tablaUiConfigurer = new MenuTablaUiConfigurer();
    }

    public MenuController(MenuImagenStorage imagenStorage, MenuCrudPresenter crudPresenter,
                          MenuPersonalizacionCoordinator personalizacionCoordinator,
                          MenuComboCoordinator comboCoordinator, MenuTablaUiConfigurer tablaUiConfigurer) {
        AppContainer c = AppContainer.getDefault();
        this.imagenStorage = imagenStorage;
        this.crudPresenter = crudPresenter;
        this.personalizacionCoordinator = personalizacionCoordinator;
        this.comboCoordinator = comboCoordinator;
        this.tablaUiConfigurer = tablaUiConfigurer;
        this.menuGestionService = c.resolve(MenuGestionService.class);
        this.importacionMasivaService = c.resolve(ImportacionMasivaService.class);
        this.personalizacionService = c.personalizacionProductoService();
        this.comboService = c.comboService();
    }

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
        tablaUiConfigurer.configurarTablaMenu(tblMenu, colImagen, colNombre, colCategoria, colPrecio,
                colEstado, colMenuDia, colOpciones, cmbCategoria, listaProductos, listaCategorias,
                imagenStorage, DIRECTORIO_UPLOADS, MenuController::formatearResumenOpcionesProducto,
                this::cargarEnFormulario, null, null);
    }


    public void cargarDatosMenu() {
        final Set<Integer> seleccionadosPrevios = obtenerIdsModificadoresSeleccionadosSet();
        UiAsyncExecutor.ejecutarAsync(
            crudPresenter::cargarDatosMenu,
            datos -> {
                listaCategorias.setAll(datos.cats());
                if (!listaCategorias.isEmpty() && cmbCategoria.getValue() == null) {
                    cmbCategoria.getSelectionModel().selectFirst();
                }
                listaProductos.setAll(datos.prods());
                actualizarListaProductosSimples();
                listaModificadoresGlobales.setAll(datos.mods());
                if (productoEnEdicion != null && productoEnEdicion.getIdProducto() > 0) {
                    for (Producto pAct : datos.prods()) {
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
            },
            e -> LOGGER.log(Level.FINE, "Error cargando menú desde BD", e)
        );
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
        if (listaModificadoresGlobales.isEmpty() && p.getIdProducto() > 0) {
            UiAsyncExecutor.ejecutarAsync(
                () -> {
                    return personalizacionCoordinator.cargarOpcionesProducto(p.getIdProducto());
                },
                res -> {
                    if (productoEnEdicion == null || productoEnEdicion.getIdProducto() != p.getIdProducto()) {
                        return;
                    }
                    MenuPersonalizacionCoordinator.OpcionesProductoCarga opciones =
                            (MenuPersonalizacionCoordinator.OpcionesProductoCarga) res;
                    listaModificadoresGlobales.setAll(opciones.globales());
                    listaVariantesEnEdicion.setAll(opciones.variantes());
                    Set<Integer> ids = new LinkedHashSet<>();
                    for (ProductoModificador pm : opciones.asociados()) {
                        ids.add(pm.getIdModificador());
                    }
                    renderizarCheckboxesModificadores(ids);
                    actualizarBadgePersonalizacion();
                },
                ex -> LOGGER.log(Level.FINE, "Aviso cargando opciones de platillo #" + p.getIdProducto(), ex)
            );
        } else {
            renderizarCheckboxesModificadores(p.getModificadores().stream()
                    .map(ProductoModificador::getIdModificador).collect(java.util.stream.Collectors.toSet()));
            actualizarBadgePersonalizacion();
        }
    }

    /**
     * Resuelve de forma confinada una imagen de platillo estrictamente dentro de {@code uploads/platillos},
     * enumerando entradas reales del directorio para rechazar path traversal ({@code ..}), symlinks externos
     * o extensiones no permitidas.
     */
    public static Path resolverImagenPlatilloSegura(String imagenUrl) {
        return IMAGEN_STORAGE_STATIC.resolverImagenSegura(DIRECTORIO_UPLOADS, imagenUrl);
    }

    private void actualizarVistaPreviaImagen(String rutaRelativa) {
        if (rutaRelativa != null && !rutaRelativa.isBlank()) {
            lblRutaImagen.setText(rutaRelativa);
            Path rutaSegura = resolverImagenPlatilloSegura(rutaRelativa);
            if (rutaSegura != null && imgPreview != null) {
                imgPreview.setImage(new Image(rutaSegura.toUri().toString(), 72, 56, true, true));
            } else if (imgPreview != null) {
                imgPreview.setImage(null);
            }
        } else {
            lblRutaImagen.setText("Sin imagen seleccionada");
            if (imgPreview != null) {
                imgPreview.setImage(null);
            }
        }
    }


    /**
     * Copia un archivo de imagen hacia {@code uploads/platillos/} con nombre aleatorio seguro
     * y devuelve la ruta relativa normalizada (§2.2).
     */
    public String copiarImagenAUploads(File archivoOrigen) throws IOException {
        String rutaRelativa = imagenStorage.copiarImagen(DIRECTORIO_UPLOADS, archivoOrigen);
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
        return crudPresenter.guardarPlatillo(productoEnEdicion, idCategoria, codigo, nombre, precioVenta,
                disponible, esMenuDelDia, imagenUrl);
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
        return crudPresenter.guardarPlatilloConOpciones(productoEnEdicion, idCategoria, codigo, nombre,
                precioVenta, disponible, esMenuDelDia, imagenUrl, variantes, idsModificadores);
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
        MenuCrudPresenter.ValidacionPlatillo validacion = crudPresenter.validarFormularioPlatillo(
                cmbCategoria.getEditor() != null ? cmbCategoria.getEditor().getText() : null,
                cmbCategoria.getValue(), listaCategorias, txtCodigo.getText(), txtNombre.getText(), txtPrecio.getText());
        if (!validacion.valido()) {
            AnimationUtils.mostrarToast(rootMenuStack, validacion.mensajeError(), true);
            return;
        }
        final boolean disp = (chkDisponible != null) ? chkDisponible.isSelected() : chkActivo.isSelected();
        final boolean menuDia = (chkMenuDelDia != null) && chkMenuDelDia.isSelected();
        final String imagenUrl = rutaImagenRelativaActual;
        final List<ProductoVariante> variantesSnapshot = new ArrayList<>(listaVariantesEnEdicion);
        final List<Integer> idsModificadoresSnapshot = new ArrayList<>(obtenerIdsModificadoresSeleccionadosSet());

        if (btnGuardarPlatillo != null) {
            btnGuardarPlatillo.setDisable(true);
        }
        UiAsyncExecutor.ejecutarAsync(
            () -> crudPresenter.guardarDesdeFormulario(validacion, productoEnEdicion, disp, menuDia,
                    imagenUrl, variantesSnapshot, idsModificadoresSnapshot),
            resultado -> {
                if (btnGuardarPlatillo != null) {
                    btnGuardarPlatillo.setDisable(false);
                }
                Categoria nuevaCat = resultado.nuevaCategoriaCreada();
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
            javafx.fxml.FXMLLoader loader = AppContainer.getDefault().createLoader(
                    getClass().getResource("/com/foodcore/view/MenuQrModalView.fxml"));
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
        tablaUiConfigurer.configurarTablasPersonalizacion(tblVariantes, colVarNombre, colVarModo, colVarPrecio,
                listaVariantesEnEdicion, cmbModoVariante, lstModificadoresGlobales, listaModificadoresGlobales);
        actualizarBadgePersonalizacion();
    }

    @FXML
    public void handleAgregarVariante(ActionEvent event) {
        String nombreVar = txtNombreVariante != null ? txtNombreVariante.getText() : "";
        String montoRaw = txtMontoVariante != null ? txtMontoVariante.getText() : "";
        boolean esFijo = cmbModoVariante == null || MODO_PRECIO_FIJO.equals(cmbModoVariante.getValue());
        int idProd = productoEnEdicion != null ? productoEnEdicion.getIdProducto() : 0;
        MenuPersonalizacionCoordinator.ResultadoVarianteUi resultado =
                personalizacionCoordinator.agregarOActualizarVariante(listaVariantesEnEdicion, idProd,
                        nombreVar, montoRaw, esFijo);
        if (!resultado.ok()) {
            AnimationUtils.mostrarToast(rootMenuStack, resultado.mensajeError(), true);
            return;
        }
        if (resultado.actualizadoExistente() && tblVariantes != null) {
            tblVariantes.refresh();
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
        MenuPersonalizacionCoordinator.ValidacionModificador validacion = personalizacionCoordinator
                .validarNuevoModificador(txtNuevoModNombre != null ? txtNuevoModNombre.getText() : "",
                        txtNuevoModPrecio != null ? txtNuevoModPrecio.getText() : "0");
        if (!validacion.valido()) {
            AnimationUtils.mostrarToast(rootMenuStack, validacion.mensajeError(), true);
            return;
        }
        String nombre = validacion.nombre();
        BigDecimal precio = validacion.precio();
        final Set<Integer> seleccionados = obtenerIdsModificadoresSeleccionadosSet();
        if (btnCrearModificadorGlobal != null) {
            btnCrearModificadorGlobal.setDisable(true);
        }
        UiAsyncExecutor.ejecutarAsync(
            () -> {
                return personalizacionCoordinator.crearModificadorGlobal(nombre, precio);
            },
            res -> {
                if (btnCrearModificadorGlobal != null) {
                    btnCrearModificadorGlobal.setDisable(false);
                }
                MenuPersonalizacionCoordinator.ResultadoModificadores resultado =
                        (MenuPersonalizacionCoordinator.ResultadoModificadores) res;
                ProductoModificador creado = resultado.creado();
                List<ProductoModificador> globales = resultado.globales();
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
                return personalizacionCoordinator.eliminarModificadorGlobal(sel.getIdModificador());
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
        tablaUiConfigurer.renderizarCheckboxesModificadores(vboxCheckboxesModificadores,
                listaModificadoresGlobales, idsMarcados, checkboxesModificadores, this::actualizarBadgePersonalizacion);
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
        return CRUD_STATICS.formatearResumenOpcionesProducto(p);
    }

    private static String formatearTextoOpciones(int numVar, int numMods) {
        return com.foodcore.util.FormatoPersonalizacionUtil.construirResumenOpciones(numVar, numMods, "Estándar");
    }

    private void configurarSeccionCombos() {
        tablaUiConfigurer.configurarTablaCombo(cmbComboComponente, listaProductosSimples, tblComboComponentes,
                colComboCompNombre, colComboCompCantidad, listaComboComponentesEnEdicion);
        actualizarResumenCombo();
    }

    private void actualizarListaProductosSimples() {
        listaProductosSimples.setAll(crudPresenter.filtrarProductosSimples(listaProductos));
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
                () -> comboCoordinator.obtenerComboPorProducto(idProdTarget).orElse(null),
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
        MenuComboCoordinator.ResultadoComponenteComboUi resultado = comboCoordinator.agregarComponenteACombo(
                listaComboComponentesEnEdicion, seleccionado,
                txtComboCantidad != null ? txtComboCantidad.getText() : "1");
        if (!resultado.ok()) {
            AnimationUtils.mostrarToast(rootMenuStack, resultado.mensajeError(), true);
            return;
        }
        if (resultado.actualizadoExistente() && tblComboComponentes != null) {
            tblComboComponentes.refresh();
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
        MenuComboCoordinator.ValidacionCombo validacion = comboCoordinator.validarFormularioCombo(
                txtComboCodigo != null ? txtComboCodigo.getText() : "",
                txtComboNombre != null ? txtComboNombre.getText() : "",
                txtComboPrecio != null ? txtComboPrecio.getText() : "", listaComboComponentesEnEdicion);
        if (!validacion.valido()) {
            AnimationUtils.mostrarToast(rootMenuStack, validacion.mensajeError(), true);
            return;
        }
        String codigo = validacion.codigo();
        String nombre = validacion.nombre();
        BigDecimal precio = validacion.precio();
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
        return comboCoordinator.guardarCombo(idCombo, idProducto, codigo, nombre, precioCombo, activo, componentes);
    }

    private void actualizarResumenCombo() {
        if (lblComboResumen == null) {
            return;
        }
        lblComboResumen.setText(comboCoordinator.formatearResumenCombo(listaComboComponentesEnEdicion));
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