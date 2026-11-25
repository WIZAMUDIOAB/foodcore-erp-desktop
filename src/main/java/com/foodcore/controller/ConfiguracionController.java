package com.foodcore.controller;

import com.foodcore.App;
import com.foodcore.controller.config.ConfigAlertasSmtpCoordinator;
import com.foodcore.controller.config.ConfigBackupCoordinator;
import com.foodcore.controller.config.ConfigDemoDataCoordinator;
import com.foodcore.controller.config.ConfigImpresorasCoordinator;
import com.foodcore.controller.config.DefaultConfigAlertasSmtpCoordinator;
import com.foodcore.controller.config.DefaultConfigBackupCoordinator;
import com.foodcore.controller.config.DefaultConfigDemoDataCoordinator;
import com.foodcore.controller.config.DefaultConfigImpresorasCoordinator;
import com.foodcore.model.ConfiguracionImpresora;
import com.foodcore.service.AlertaService.ConfiguracionAlertaEmail;
import com.foodcore.service.ConfiguracionSistemaService.DatosRestauranteConfig;
import com.foodcore.service.DemoDataService.ResumenDemo;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.DialogService;
import com.foodcore.util.ImpresoraTermicaService;
import com.foodcore.util.UiAsyncExecutor;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Controlador de configuración del restaurante. */
public class ConfiguracionController {
    private static final Logger LOGGER = Logger.getLogger(ConfiguracionController.class.getName());

    public record CategoriaOpcion(int idCategoria, String nombre) {
        @Override
        public String toString() {
            return nombre;
        }
    }

    @FXML private StackPane rootConfigStack;
    @FXML private TextField txtNombreRestaurante;
    @FXML private TextField txtDireccion;
    @FXML private TextField txtTelefono;
    @FXML private TextField txtRuc;
    @FXML private TextField txtImpresora;
    @FXML private Button btnGuardarConfig;
    @FXML private ComboBox<CategoriaOpcion> cmbCategoriaImpresora;
    @FXML private ComboBox<String> cmbNombreImpresoraWindows;
    @FXML private Button btnAsignarImpresora;
    @FXML private Button btnQuitarImpresora;
    @FXML private TableView<ConfiguracionImpresora> tblRuteoImpresoras;
    @FXML private TableColumn<ConfiguracionImpresora, String> colCatNombre;
    @FXML private TableColumn<ConfiguracionImpresora, String> colImpWindows;
    @FXML private TableColumn<ConfiguracionImpresora, String> colImpEstado;
    @FXML private TextField txtEmailAlertas;
    @FXML private CheckBox chkAlertasEmailHabilitadas;
    @FXML private CheckBox chkSmtpTlsHabilitado;
    @FXML private TextField txtSmtpHost;
    @FXML private TextField txtSmtpPort;
    @FXML private TextField txtSmtpUser;
    @FXML private PasswordField txtSmtpPassword;
    @FXML private Button btnGuardarAlertasEmail;
    @FXML private Button btnProbarAlertaEmail;
    @FXML private Label lblEstadoAlertasEmail;
    @FXML private TextField txtRutaBackupRestaurar;
    @FXML private Button btnSeleccionarBackup;
    @FXML private Button btnGenerarBackupAhora;
    @FXML private Button btnRestaurarBackup;
    @FXML private Label lblEstadoRestauracion;
    @FXML private CheckBox chkModoOscuro;
    @FXML private Label lblEstadoModoOscuro;
    @FXML private Button btnCargarDatosDemo;
    @FXML private Button btnBorrarDatosDemo;
    @FXML private Label lblEstadoDemo;

    private final ConfigBackupCoordinator backupCoordinator;
    private final ConfigAlertasSmtpCoordinator alertasCoordinator;
    private final ConfigImpresorasCoordinator impresorasCoordinator;
    private final ConfigDemoDataCoordinator demoCoordinator;
    private final ObservableList<ConfiguracionImpresora> asignaciones = FXCollections.observableArrayList();
    private BooleanSupplier confirmacionRestauracionSupplier = this::solicitarConfirmacionRestauracionUi;
    private BooleanSupplier confirmacionCargarDemoSupplier = () -> solicitarConfirmacionUi(
            "Confirmar Carga de Datos Demo",
            "Se insertarán clientes, productos y ventas de prueba (es_demo = 1)",
            "Estos registros pueden eliminarse posteriormente sin afectar tus datos reales. ¿Deseas continuar?");
    private BooleanSupplier confirmacionBorrarDemoSupplier = () -> solicitarConfirmacionDestructivaUi(
            "Confirmar Borrado de Datos Demo",
            "Se eliminarán únicamente los registros marcados con es_demo = 1. Los datos reales permanecerán intactos. ¿Deseas continuar?");
    private Function<Path, Boolean> ejecutorRestauracion = backupCoordinatorFunction();
    private Supplier<Path> ejecutorBackup = backupCoordinatorSupplier();

    public ConfiguracionController() {
        this(new DefaultConfigBackupCoordinator(), new DefaultConfigAlertasSmtpCoordinator(),
                new DefaultConfigImpresorasCoordinator(), new DefaultConfigDemoDataCoordinator());
    }

    public ConfiguracionController(ConfigBackupCoordinator backupCoordinator,
                                   ConfigAlertasSmtpCoordinator alertasCoordinator,
                                   ConfigImpresorasCoordinator impresorasCoordinator,
                                   ConfigDemoDataCoordinator demoCoordinator) {
        this.backupCoordinator = Objects.requireNonNull(backupCoordinator);
        this.alertasCoordinator = Objects.requireNonNull(alertasCoordinator);
        this.impresorasCoordinator = Objects.requireNonNull(impresorasCoordinator);
        this.demoCoordinator = Objects.requireNonNull(demoCoordinator);
        this.ejecutorRestauracion = this.backupCoordinator::ejecutarScriptRestauracionSistema;
        this.ejecutorBackup = this.backupCoordinator::ejecutarScriptBackupSistema;
    }

    private static Function<Path, Boolean> backupCoordinatorFunction() {
        return path -> Boolean.valueOf(new DefaultConfigBackupCoordinator().ejecutarScriptRestauracionSistema(path));
    }

    private static Supplier<Path> backupCoordinatorSupplier() {
        return () -> new DefaultConfigBackupCoordinator().ejecutarScriptBackupSistema();
    }

    @FXML
    public void initialize() {
        AnimationUtils.attachTactileFeedback(btnGuardarConfig);
        AnimationUtils.attachTactileFeedback(btnAsignarImpresora);
        AnimationUtils.attachTactileFeedback(btnQuitarImpresora);
        AnimationUtils.attachTactileFeedback(btnGuardarAlertasEmail);
        AnimationUtils.attachTactileFeedback(btnProbarAlertaEmail);
        AnimationUtils.attachTactileFeedback(btnSeleccionarBackup);
        AnimationUtils.attachTactileFeedback(btnGenerarBackupAhora);
        AnimationUtils.attachTactileFeedback(btnRestaurarBackup);
        AnimationUtils.attachTactileFeedback(btnCargarDatosDemo);
        AnimationUtils.attachTactileFeedback(btnBorrarDatosDemo);
        configurarTablaImpresoras();
        poblarImpresorasDetectadas();
        cargarConfiguracion();
        cargarRuteoImpresorasAsync();
        cargarConfiguracionAlertasEmailUi();
        precargarUltimoBackupDisponible();
        inicializarToggleModoOscuroUi();
        refrescarEstadoDemoAsync();
    }

    private void inicializarToggleModoOscuroUi() {
        boolean activo = App.isDarkModeActivo();
        if (chkModoOscuro != null) {
            chkModoOscuro.setSelected(activo);
        }
        actualizarEstadoModoOscuroUi(activo);
    }

    @FXML
    public void handleToggleModoOscuro(ActionEvent event) {
        aplicarTemaOscuroUi(chkModoOscuro != null && chkModoOscuro.isSelected());
    }

    public void aplicarTemaOscuroUi(boolean activo) {
        App.setDarkModeActivo(activo);
        Scene scene = rootConfigStack != null ? rootConfigStack.getScene() : null;
        if (scene != null) {
            App.aplicarModoOscuro(scene, activo);
        } else if (App.getPrimaryStage() != null && App.getPrimaryStage().getScene() != null) {
            App.aplicarModoOscuro(App.getPrimaryStage().getScene(), activo);
        }
        actualizarEstadoModoOscuroUi(activo);
    }

    private void actualizarEstadoModoOscuroUi(boolean activo) {
        if (lblEstadoModoOscuro != null) {
            lblEstadoModoOscuro.setText(activo ? "Modo Oscuro activo" : "Tema Claro activo");
        }
    }

    public CheckBox getChkModoOscuro() {
        return chkModoOscuro;
    }

    public Label getLblEstadoModoOscuro() {
        return lblEstadoModoOscuro;
    }

    private void configurarTablaImpresoras() {
        if (tblRuteoImpresoras == null) {
            return;
        }
        colCatNombre.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getNombreCategoria() != null
                ? d.getValue().getNombreCategoria() : "Categoría #" + d.getValue().getIdCategoriaProducto()));
        colImpWindows.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getNombreImpresoraWindows() != null ? d.getValue().getNombreImpresoraWindows() : ""));
        colImpEstado.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().isActiva() ? "ACTIVA" : "INACTIVA"));
        tblRuteoImpresoras.setItems(asignaciones);
    }

    private void poblarImpresorasDetectadas() {
        if (cmbNombreImpresoraWindows == null) {
            return;
        }
        Set<String> opciones = new LinkedHashSet<>(impresorasCoordinator.obtenerImpresorasDisponibles());
        opciones.addAll(ImpresoraTermicaService.listarImpresorasWindows());
        opciones.add("Cocina");
        opciones.add("Barra");
        opciones.add("Parrilla");
        opciones.add("Caja");
        cmbNombreImpresoraWindows.getItems().setAll(opciones);
    }

    public void cargarRuteoImpresorasAsync() {
        UiAsyncExecutor.ejecutarAsync(impresorasCoordinator::cargarRuteoImpresoras, res -> {
            Map<Integer, String> categorias = res.categorias();
            List<ConfiguracionImpresora> lista = res.asignaciones();
            if (cmbCategoriaImpresora != null && categorias != null) {
                cmbCategoriaImpresora.getItems().clear();
                for (Map.Entry<Integer, String> entry : categorias.entrySet()) {
                    cmbCategoriaImpresora.getItems().add(new CategoriaOpcion(entry.getKey(), entry.getValue()));
                }
                if (!cmbCategoriaImpresora.getItems().isEmpty() && cmbCategoriaImpresora.getValue() == null) {
                    cmbCategoriaImpresora.getSelectionModel().selectFirst();
                }
            }
            asignaciones.setAll(lista != null ? lista : List.of());
        }, ex -> LOGGER.log(Level.WARNING, "No se pudo cargar ruteo de impresoras por categoría", ex));
    }

    @FXML
    public void handleAsignarImpresoraCategoria(ActionEvent event) {
        CategoriaOpcion catSel = cmbCategoriaImpresora != null ? cmbCategoriaImpresora.getValue() : null;
        String raw = cmbNombreImpresoraWindows != null ? cmbNombreImpresoraWindows.getEditor().getText() : null;
        if ((raw == null || raw.isBlank()) && cmbNombreImpresoraWindows != null) {
            raw = cmbNombreImpresoraWindows.getValue();
        }
        if (catSel == null || raw == null || raw.isBlank()) {
            AnimationUtils.mostrarToast(rootConfigStack, "Seleccione una categoría e indique el nombre de la impresora Windows.", true);
            return;
        }
        String impresora = raw.trim();
        UiAsyncExecutor.ejecutarVoidAsync(() -> impresorasCoordinator.asignarImpresoraCategoria(catSel.idCategoria(), impresora),
                () -> {
                    AnimationUtils.mostrarToast(rootConfigStack, "Impresora asignada: " + catSel.nombre() + " → " + impresora, false);
                    cargarRuteoImpresorasAsync();
                },
                ex -> AnimationUtils.mostrarToast(rootConfigStack, "No se pudo guardar asignación de impresora: " + ex.getMessage(), true));
    }

    @FXML
    public void handleEliminarImpresoraCategoria(ActionEvent event) {
        ConfiguracionImpresora sel = tblRuteoImpresoras != null ? tblRuteoImpresoras.getSelectionModel().getSelectedItem() : null;
        if (sel == null) {
            AnimationUtils.mostrarToast(rootConfigStack, "Seleccione una asignación en la tabla para quitar.", true);
            return;
        }
        UiAsyncExecutor.ejecutarVoidAsync(() -> impresorasCoordinator.eliminarImpresoraCategoria(sel.getIdCategoriaProducto()),
                () -> {
                    AnimationUtils.mostrarToast(rootConfigStack, "Asignación de impresora eliminada.", false);
                    cargarRuteoImpresorasAsync();
                },
                ex -> AnimationUtils.mostrarToast(rootConfigStack, "Error al quitar asignación: " + ex.getMessage(), true));
    }

    private void cargarConfiguracionAlertasEmailUi() {
        ConfiguracionAlertaEmail cfg = alertasCoordinator.obtenerConfiguracionActual();
        if (cfg == null) {
            return;
        }
        if (txtEmailAlertas != null) {
            txtEmailAlertas.setText(cfg.destinatario());
        }
        if (chkAlertasEmailHabilitadas != null) {
            chkAlertasEmailHabilitadas.setSelected(cfg.habilitado());
        }
        if (chkSmtpTlsHabilitado != null) {
            chkSmtpTlsHabilitado.setSelected(cfg.tlsEnabled());
        }
        if (txtSmtpHost != null) {
            txtSmtpHost.setText(cfg.smtpHost());
        }
        if (txtSmtpPort != null) {
            txtSmtpPort.setText(String.valueOf(cfg.smtpPort()));
        }
        if (txtSmtpUser != null) {
            txtSmtpUser.setText(cfg.smtpUser());
        }
        if (txtSmtpPassword != null) {
            txtSmtpPassword.setText(cfg.smtpPassword());
        }
        if (lblEstadoAlertasEmail != null) {
            lblEstadoAlertasEmail.setText(cfg.habilitado()
                    ? "Alertas activas → " + cfg.destinatario() : "Alertas deshabilitadas");
        }
    }

    @FXML
    public void handleGuardarConfiguracionAlertas(ActionEvent event) {
        String email = txtEmailAlertas != null ? txtEmailAlertas.getText().trim() : "";
        String host = txtSmtpHost != null ? txtSmtpHost.getText().trim() : "";
        String port = txtSmtpPort != null ? txtSmtpPort.getText().trim() : "587";
        String user = txtSmtpUser != null ? txtSmtpUser.getText().trim() : "";
        String pass = txtSmtpPassword != null ? txtSmtpPassword.getText() : "";
        boolean enabled = chkAlertasEmailHabilitadas == null || chkAlertasEmailHabilitadas.isSelected();
        boolean tls = chkSmtpTlsHabilitado == null || chkSmtpTlsHabilitado.isSelected();
        ConfigAlertasSmtpCoordinator.ValidacionSmtp validation = alertasCoordinator.validarYConstruirConfiguracion(email, host, port, user, pass, enabled, tls);
        if (!validation.valido()) {
            AnimationUtils.mostrarToast(rootConfigStack, validation.mensajeError(), true);
            return;
        }
        ConfiguracionAlertaEmail cfg = validation.config();
        alertasCoordinator.guardarConfiguracion(cfg, true);
        if (lblEstadoAlertasEmail != null) {
            lblEstadoAlertasEmail.setText("Guardado → " + email);
            lblEstadoAlertasEmail.getStyleClass().removeAll("badge-pill-danger", "badge-pill-warning", "badge-pill-neutral");
            if (!lblEstadoAlertasEmail.getStyleClass().contains("badge-pill-success")) {
                lblEstadoAlertasEmail.getStyleClass().add("badge-pill-success");
            }
        }
        AnimationUtils.mostrarToast(rootConfigStack, "Configuración de alertas SMTP guardada para " + email, false);
    }

    @FXML
    public void handleProbarAlertaEmail(ActionEvent event) {
        handleGuardarConfiguracionAlertas(event);
        if (btnProbarAlertaEmail != null) {
            btnProbarAlertaEmail.setDisable(true);
        }
        UiAsyncExecutor.ejecutarAsync(alertasCoordinator::enviarAlertaPrueba, emitida -> {
            if (btnProbarAlertaEmail != null) {
                btnProbarAlertaEmail.setDisable(false);
            }
            boolean ok = emitida != null && emitida.enviadoPorRed();
            if (lblEstadoAlertasEmail != null) {
                lblEstadoAlertasEmail.setText(ok ? "Prueba enviada a " + emitida.destinatario()
                        : "Prueba registrada (verifique credenciales SMTP)");
            }
            AnimationUtils.mostrarToast(rootConfigStack, ok ? "Email de prueba enviado a " + emitida.destinatario()
                    : "Alerta registrada; el servidor SMTP no confirmó envío de red.", !ok);
        }, ex -> {
            if (btnProbarAlertaEmail != null) {
                btnProbarAlertaEmail.setDisable(false);
            }
            AnimationUtils.mostrarToast(rootConfigStack, "Error enviando prueba SMTP: " + ex.getMessage(), true);
        });
    }

    private void precargarUltimoBackupDisponible() {
        if (txtRutaBackupRestaurar == null || !txtRutaBackupRestaurar.getText().isBlank()) {
            return;
        }
        backupCoordinator.buscarUltimoBackupDisponible(Path.of("backups"))
                .ifPresent(path -> txtRutaBackupRestaurar.setText(path.toString()));
    }

    @FXML
    public void handleSeleccionarArchivoBackup(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar Archivo de Backup FoodCore (.sql.gz / .sql)");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Backups MySQL comprimidos (*.sql.gz)", "*.sql.gz"),
                new FileChooser.ExtensionFilter("Volcados SQL (*.sql)", "*.sql"));
        Path dir = Path.of("backups");
        if (Files.isDirectory(dir)) {
            chooser.setInitialDirectory(dir.toFile());
        }
        Window window = rootConfigStack != null && rootConfigStack.getScene() != null ? rootConfigStack.getScene().getWindow() : null;
        File selected = chooser.showOpenDialog(window);
        if (selected != null && txtRutaBackupRestaurar != null) {
            txtRutaBackupRestaurar.setText(selected.getAbsolutePath());
        }
    }

    public static boolean tienePermisoBackupRestauracion() {
        return new DefaultConfigBackupCoordinator().tienePermisoBackupRestauracion();
    }

    @FXML
    public void handleEjecutarBackupAhora(ActionEvent event) {
        if (!tienePermisoBackupRestauracion()) {
            actualizarEstadoRestauracion("Acceso denegado (requiere CONFIG_RED)", "badge-pill-danger");
            AnimationUtils.mostrarToast(rootConfigStack, "Acceso denegado: requiere permiso CONFIG_RED para generar backups.", true);
            return;
        }
        if (btnGenerarBackupAhora != null) {
            btnGenerarBackupAhora.setDisable(true);
        }
        actualizarEstadoRestauracion("Generando backup comprimido...", "badge-pill-warning");
        UiAsyncExecutor.ejecutarAsync(() -> {
            if (!tienePermisoBackupRestauracion()) {
                throw new SecurityException("Acceso denegado: requiere permiso CONFIG_RED.");
            }
            return ejecutorBackup.get();
        }, archivo -> {
            if (btnGenerarBackupAhora != null) {
                btnGenerarBackupAhora.setDisable(false);
            }
            if (archivo != null) {
                if (txtRutaBackupRestaurar != null) {
                    txtRutaBackupRestaurar.setText(archivo.toString());
                }
                actualizarEstadoRestauracion("Backup listo: " + archivo.getFileName(), "badge-pill-success");
                AnimationUtils.mostrarToast(rootConfigStack, "Backup generado: " + archivo.getFileName(), false);
            } else {
                actualizarEstadoRestauracion("Backup falló al ejecutarse", "badge-pill-danger");
                AnimationUtils.mostrarToast(rootConfigStack, "No se pudo generar el backup automático.", true);
            }
        }, ex -> {
            if (btnGenerarBackupAhora != null) {
                btnGenerarBackupAhora.setDisable(false);
            }
            actualizarEstadoRestauracion("Error en backup", "badge-pill-danger");
            AnimationUtils.mostrarToast(rootConfigStack, "Error ejecutando backup: " + ex.getMessage(), true);
        });
    }

    @FXML
    public void handleRestaurarDesdeBackup(ActionEvent event) {
        if (!tienePermisoBackupRestauracion()) {
            actualizarEstadoRestauracion("Acceso denegado (requiere CONFIG_RED)", "badge-pill-danger");
            AnimationUtils.mostrarToast(rootConfigStack, "Acceso denegado: requiere permiso CONFIG_RED para restaurar la base de datos.", true);
            return;
        }
        String raw = txtRutaBackupRestaurar != null ? txtRutaBackupRestaurar.getText().trim() : "";
        if (raw.isBlank()) {
            AnimationUtils.mostrarToast(rootConfigStack, "Seleccione o indique la ruta de un backup válido.", true);
            return;
        }
        ConfigBackupCoordinator.ValidacionBackup validation = backupCoordinator.validarArchivoBackup(raw);
        if (!validation.valido()) {
            actualizarEstadoRestauracion(validation.badgeTexto(), validation.badgeClass());
            AnimationUtils.mostrarToast(rootConfigStack, validation.mensajeToast(), true);
            return;
        }
        Path file = validation.archivo();
        if (!confirmacionRestauracionSupplier.getAsBoolean()) {
            actualizarEstadoRestauracion("Restauración cancelada por el usuario", "badge-pill-neutral");
            return;
        }
        if (btnRestaurarBackup != null) {
            btnRestaurarBackup.setDisable(true);
        }
        actualizarEstadoRestauracion("Restaurando base de datos...", "badge-pill-warning");
        UiAsyncExecutor.ejecutarAsync(() -> {
            if (!tienePermisoBackupRestauracion()) {
                throw new SecurityException("Acceso denegado: requiere permiso CONFIG_RED.");
            }
            return Boolean.TRUE.equals(ejecutorRestauracion.apply(file));
        }, ok -> {
            if (btnRestaurarBackup != null) {
                btnRestaurarBackup.setDisable(false);
            }
            if (Boolean.TRUE.equals(ok)) {
                actualizarEstadoRestauracion("BD restaurada desde " + file.getFileName(), "badge-pill-success");
                AnimationUtils.mostrarToast(rootConfigStack, "Restauración completada desde " + file.getFileName(), false);
            } else {
                actualizarEstadoRestauracion("Error al restaurar backup", "badge-pill-danger");
                AnimationUtils.mostrarToast(rootConfigStack, "No se pudo completar la restauración del backup.", true);
            }
        }, ex -> {
            if (btnRestaurarBackup != null) {
                btnRestaurarBackup.setDisable(false);
            }
            actualizarEstadoRestauracion("Error en restauración", "badge-pill-danger");
            AnimationUtils.mostrarToast(rootConfigStack, "Error ejecutando restauración: " + ex.getMessage(), true);
        });
    }

    private void actualizarEstadoRestauracion(String texto, String badgeClass) {
        actualizarBadgeEstado(lblEstadoRestauracion, texto, badgeClass);
    }

    private void actualizarBadgeEstado(Label label, String texto, String badgeClass) {
        if (label == null) {
            return;
        }
        label.setText(texto);
        label.getStyleClass().removeAll("badge-pill-success", "badge-pill-danger", "badge-pill-warning", "badge-pill-neutral");
        label.getStyleClass().add(badgeClass);
    }

    private boolean solicitarConfirmacionRestauracionUi() {
        return solicitarConfirmacionDestructivaUi("Confirmar Restauración de Base de Datos",
                "Se restaurará la base de datos desde el archivo seleccionado, deteniendo temporalmente los servicios y reemplazando los datos actuales. ¿Desea continuar?");
    }

    private boolean solicitarConfirmacionDestructivaUi(String title, String message) {
        Alert confirm = DialogService.crearConfirmacionDestructiva(title, message);
        if (rootConfigStack != null && rootConfigStack.getScene() != null) {
            confirm.initOwner(rootConfigStack.getScene().getWindow());
        }
        Optional<ButtonType> result = confirm.showAndWait();
        return result.isPresent() && result.get() == DialogService.BTN_SI_ANULAR;
    }

    private boolean solicitarConfirmacionUi(String title, String header, String content) {
        Alert confirm = DialogService.crearAlerta(Alert.AlertType.CONFIRMATION, title, header, content);
        if (rootConfigStack != null && rootConfigStack.getScene() != null) {
            confirm.initOwner(rootConfigStack.getScene().getWindow());
        }
        Optional<ButtonType> result = confirm.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private void refrescarEstadoDemoAsync() {
        UiAsyncExecutor.ejecutarAsync(demoCoordinator::contarDatosDemo, this::actualizarLabelResumenDemo,
                ex -> LOGGER.log(Level.FINE, "Sin conteo demo disponible al iniciar", ex));
    }

    private void actualizarLabelResumenDemo(ResumenDemo resumen) {
        ConfigDemoDataCoordinator.EstadoBadgeUi estado = demoCoordinator.formatearResumenDemo(resumen);
        actualizarBadgeEstado(lblEstadoDemo, estado.texto(), estado.badgeClass());
    }

    @FXML
    public void handleCargarDatosDemo(ActionEvent event) {
        if (!confirmacionCargarDemoSupplier.getAsBoolean()) {
            return;
        }
        setBotonesDemoDeshabilitados(true);
        actualizarBadgeEstado(lblEstadoDemo, "Cargando script seed-demo.sql...", "badge-pill-warning");
        UiAsyncExecutor.ejecutarAsync(demoCoordinator::cargarDatosDemo, resumen -> {
            setBotonesDemoDeshabilitados(false);
            actualizarLabelResumenDemo(resumen);
            AnimationUtils.mostrarToast(rootConfigStack, "Datos de demostración cargados correctamente.", false);
        }, ex -> {
            setBotonesDemoDeshabilitados(false);
            actualizarBadgeEstado(lblEstadoDemo, "Error cargando datos demo", "badge-pill-danger");
            AnimationUtils.mostrarToast(rootConfigStack, "Error cargando datos de demo: " + ex.getMessage(), true);
        });
    }

    @FXML
    public void handleBorrarDatosDemo(ActionEvent event) {
        if (!confirmacionBorrarDemoSupplier.getAsBoolean()) {
            return;
        }
        setBotonesDemoDeshabilitados(true);
        actualizarBadgeEstado(lblEstadoDemo, "Eliminando registros es_demo = 1...", "badge-pill-warning");
        UiAsyncExecutor.ejecutarAsync(demoCoordinator::borrarDatosDemo, deleted -> {
            setBotonesDemoDeshabilitados(false);
            actualizarBadgeEstado(lblEstadoDemo, "Demo eliminados (" + deleted + " filas)", "badge-pill-neutral");
            AnimationUtils.mostrarToast(rootConfigStack, "Datos de demostración eliminados (" + deleted + " registros).", false);
        }, ex -> {
            setBotonesDemoDeshabilitados(false);
            actualizarBadgeEstado(lblEstadoDemo, "Error eliminando datos demo", "badge-pill-danger");
            AnimationUtils.mostrarToast(rootConfigStack, "Error eliminando datos de demo: " + ex.getMessage(), true);
        });
    }

    private void setBotonesDemoDeshabilitados(boolean disabled) {
        if (btnCargarDatosDemo != null) {
            btnCargarDatosDemo.setDisable(disabled);
        }
        if (btnBorrarDatosDemo != null) {
            btnBorrarDatosDemo.setDisable(disabled);
        }
    }

    public void setConfirmacionCargarDemoSupplier(BooleanSupplier supplier) {
        if (supplier != null) {
            confirmacionCargarDemoSupplier = supplier;
        }
    }

    public void setConfirmacionBorrarDemoSupplier(BooleanSupplier supplier) {
        if (supplier != null) {
            confirmacionBorrarDemoSupplier = supplier;
        }
    }

    public Label getLblEstadoDemo() {
        return lblEstadoDemo;
    }

    static boolean ejecutarScriptRestauracionSistema(Path backupFile) {
        return new DefaultConfigBackupCoordinator().ejecutarScriptRestauracionSistema(backupFile);
    }

    static Path ejecutarScriptBackupSistema() {
        return new DefaultConfigBackupCoordinator().ejecutarScriptBackupSistema();
    }

    public void setConfirmacionRestauracionSupplier(BooleanSupplier supplier) {
        confirmacionRestauracionSupplier = supplier != null ? supplier : this::solicitarConfirmacionRestauracionUi;
    }

    public void setEjecutorRestauracion(Function<Path, Boolean> executor) {
        ejecutorRestauracion = executor != null ? executor : this.backupCoordinator::ejecutarScriptRestauracionSistema;
    }

    public void setEjecutorBackup(Supplier<Path> executor) {
        ejecutorBackup = executor != null ? executor : this.backupCoordinator::ejecutarScriptBackupSistema;
    }

    public TextField getTxtRutaBackupRestaurar() {
        return txtRutaBackupRestaurar;
    }

    public Label getLblEstadoRestauracion() {
        return lblEstadoRestauracion;
    }

    public TextField getTxtEmailAlertas() {
        return txtEmailAlertas;
    }

    public TextField getTxtSmtpHost() {
        return txtSmtpHost;
    }

    public TextField getTxtSmtpPort() {
        return txtSmtpPort;
    }

    public Label getLblEstadoAlertasEmail() {
        return lblEstadoAlertasEmail;
    }

    public ObservableList<ConfiguracionImpresora> getAsignaciones() {
        return asignaciones;
    }

    public ComboBox<CategoriaOpcion> getCmbCategoriaImpresora() {
        return cmbCategoriaImpresora;
    }

    public ComboBox<String> getCmbNombreImpresoraWindows() {
        return cmbNombreImpresoraWindows;
    }

    public void cargarConfiguracion() {
        UiAsyncExecutor.ejecutarAsync(impresorasCoordinator::cargarConfiguracionRestaurante, datos -> {
            if (datos.isPresent()) {
                DatosRestauranteConfig config = datos.get();
                txtNombreRestaurante.setText(config.nombreRestaurante());
                txtDireccion.setText(config.direccion());
                txtTelefono.setText(config.telefono());
                txtRuc.setText(config.ruc() != null ? config.ruc() : "");
                txtImpresora.setText(config.nombreImpresora());
            }
        }, ex -> LOGGER.log(Level.FINE, "Usando configuración por defecto", ex));
    }

    @FXML
    public void handleGuardarConfiguracion(ActionEvent event) {
        String nombre = txtNombreRestaurante.getText();
        String direccion = txtDireccion.getText();
        String telefono = txtTelefono.getText();
        String ruc = txtRuc.getText();
        String impresora = txtImpresora.getText();
        if (btnGuardarConfig != null) {
            btnGuardarConfig.setDisable(true);
        }
        UiAsyncExecutor.ejecutarVoidAsync(
                () -> impresorasCoordinator.guardarConfiguracionRestaurante(new DatosRestauranteConfig(nombre, direccion, telefono, ruc, impresora)),
                () -> {
                    if (btnGuardarConfig != null) {
                        btnGuardarConfig.setDisable(false);
                    }
                    AnimationUtils.mostrarToast(rootConfigStack, "Configuración del restaurante e impresora guardada.", false);
                },
                ex -> {
                    if (btnGuardarConfig != null) {
                        btnGuardarConfig.setDisable(false);
                    }
                    AnimationUtils.mostrarToast(rootConfigStack, "Error al guardar configuración: " + (ex != null ? ex.getMessage() : "desconocido"), true);
                });
    }
}
