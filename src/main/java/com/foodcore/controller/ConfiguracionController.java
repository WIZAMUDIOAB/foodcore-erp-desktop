package com.foodcore.controller;

import com.foodcore.App;

import com.foodcore.model.ConfiguracionImpresora;
import com.foodcore.model.Usuario;
import com.foodcore.model.enums.Permiso;
import com.foodcore.security.AutorizacionService;
import com.foodcore.security.SesionManager;
import com.foodcore.security.SesionUsuario;
import com.foodcore.service.AlertaService;
import com.foodcore.service.AlertaService.AlertaEmitida;
import com.foodcore.service.AlertaService.ConfiguracionAlertaEmail;
import com.foodcore.service.AuthService;
import com.foodcore.service.ConfiguracionSistemaService;
import com.foodcore.service.DemoDataService;
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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import java.util.Comparator;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Controlador de Configuración del Restaurante, Múltiples Impresoras por Categoría (v5.2.0),
 * Alertas SMTP y Respaldo/Restauración Automatizada de BD (v5.3.0 Sub-Fase C).
 */
public class ConfiguracionController {

    private static final Logger LOGGER = Logger.getLogger(ConfiguracionController.class.getName());
    private static final int SCRIPT_TIMEOUT_SECONDS = 120;


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

    // Alertas SMTP (v5.3.0 Ítem 6)
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

    // Respaldo y Restauración (v5.3.0 Ítem 3)
    @FXML private TextField txtRutaBackupRestaurar;
    @FXML private Button btnSeleccionarBackup;
    @FXML private Button btnGenerarBackupAhora;
    @FXML private Button btnRestaurarBackup;
    @FXML private Label lblEstadoRestauracion;

    // Apariencia / Modo Oscuro (v5.4.0 D4)
    @FXML private CheckBox chkModoOscuro;
    @FXML private Label lblEstadoModoOscuro;

    // Datos de Demostración (v5.5.0 Sub-Fase E - E3)
    @FXML private Button btnCargarDatosDemo;
    @FXML private Button btnBorrarDatosDemo;
    @FXML private Label lblEstadoDemo;

    private final ConfiguracionSistemaService configuracionService = new ConfiguracionSistemaService();
    private final DemoDataService demoDataService = new DemoDataService();
    private final ObservableList<ConfiguracionImpresora> asignaciones = FXCollections.observableArrayList();

    private BooleanSupplier confirmacionRestauracionSupplier = this::solicitarConfirmacionRestauracionUi;
    private BooleanSupplier confirmacionCargarDemoSupplier = () -> solicitarConfirmacionUi(
            "Confirmar Carga de Datos Demo",
            "Se insertarán clientes, productos y ventas de prueba (es_demo = 1)",
            "Estos registros pueden eliminarse posteriormente sin afectar tus datos reales. ¿Deseas continuar?");
    private BooleanSupplier confirmacionBorrarDemoSupplier = () -> solicitarConfirmacionDestructivaUi(
            "Confirmar Borrado de Datos Demo",
            "Se eliminarán únicamente los registros marcados con es_demo = 1. Los datos reales permanecerán intactos. ¿Deseas continuar?");
    private Function<Path, Boolean> ejecutorRestauracion = ConfiguracionController::ejecutarScriptRestauracionSistema;
    private Supplier<Path> ejecutorBackup = ConfiguracionController::ejecutarScriptBackupSistema;

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

    /** Activa o desactiva {@code dark-theme.css} sobre la escena actual (Sub-Fase D - D4). */
    @FXML
    public void handleToggleModoOscuro(ActionEvent event) {
        boolean activo = chkModoOscuro != null && chkModoOscuro.isSelected();
        aplicarTemaOscuroUi(activo);
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
        colCatNombre.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getNombreCategoria() != null ? d.getValue().getNombreCategoria()
                        : "Categoría #" + d.getValue().getIdCategoriaProducto()));
        colImpWindows.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getNombreImpresoraWindows() != null ? d.getValue().getNombreImpresoraWindows() : ""));
        colImpEstado.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().isActiva() ? "ACTIVA" : "INACTIVA"));
        tblRuteoImpresoras.setItems(asignaciones);
    }

    private void poblarImpresorasDetectadas() {
        if (cmbNombreImpresoraWindows == null) {
            return;
        }
        Set<String> opciones = new LinkedHashSet<>();
        opciones.addAll(ImpresoraTermicaService.listarImpresorasWindows());
        opciones.add("Cocina");
        opciones.add("Barra");
        opciones.add("Parrilla");
        opciones.add("Caja");
        cmbNombreImpresoraWindows.getItems().setAll(opciones);
    }

    public void cargarRuteoImpresorasAsync() {
        UiAsyncExecutor.ejecutarAsync(
            () -> {
                return configuracionService.cargarRuteoImpresoras();
            },
            res -> {
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
            },
            ex -> LOGGER.log(Level.WARNING, "No se pudo cargar ruteo de impresoras por categoría", ex)
        );
    }

    @FXML
    public void handleAsignarImpresoraCategoria(ActionEvent event) {
        CategoriaOpcion catSel = cmbCategoriaImpresora != null ? cmbCategoriaImpresora.getValue() : null;
        String impresoraRaw = cmbNombreImpresoraWindows != null
                ? cmbNombreImpresoraWindows.getEditor().getText() : null;
        if ((impresoraRaw == null || impresoraRaw.isBlank()) && cmbNombreImpresoraWindows != null) {
            impresoraRaw = cmbNombreImpresoraWindows.getValue();
        }
        if (catSel == null || impresoraRaw == null || impresoraRaw.isBlank()) {
            AnimationUtils.mostrarToast(rootConfigStack,
                    "Seleccione una categoría e indique el nombre de la impresora Windows.", true);
            return;
        }
        final String impresoraFinal = impresoraRaw.trim();
        UiAsyncExecutor.ejecutarVoidAsync(
            () -> {
                configuracionService.asignarImpresoraCategoria(catSel.idCategoria(), impresoraFinal);
            },
            () -> {
                AnimationUtils.mostrarToast(rootConfigStack,
                        "Impresora asignada: " + catSel.nombre() + " → " + impresoraFinal, false);
                cargarRuteoImpresorasAsync();
            },
            ex -> AnimationUtils.mostrarToast(rootConfigStack,
                    "No se pudo guardar asignación de impresora: " + ex.getMessage(), true)
        );
    }

    @FXML
    public void handleEliminarImpresoraCategoria(ActionEvent event) {
        ConfiguracionImpresora sel = tblRuteoImpresoras != null
                ? tblRuteoImpresoras.getSelectionModel().getSelectedItem() : null;
        if (sel == null) {
            AnimationUtils.mostrarToast(rootConfigStack, "Seleccione una asignación en la tabla para quitar.", true);
            return;
        }
        UiAsyncExecutor.ejecutarVoidAsync(
            () -> {
                configuracionService.eliminarImpresoraCategoria(sel.getIdCategoriaProducto());
            },
            () -> {
                AnimationUtils.mostrarToast(rootConfigStack, "Asignación de impresora eliminada.", false);
                cargarRuteoImpresorasAsync();
            },
            ex -> AnimationUtils.mostrarToast(rootConfigStack,
                    "Error al quitar asignación: " + ex.getMessage(), true)
        );
    }

    // =========================================================================
    // Alertas de Fallo por Email SMTP (v5.3.0 Ítem 6)
    // =========================================================================

    private void cargarConfiguracionAlertasEmailUi() {
        ConfiguracionAlertaEmail cfg = AlertaService.getConfiguracionGlobal();
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
                    ? "Alertas activas → " + cfg.destinatario()
                    : "Alertas deshabilitadas");
        }
    }

    @FXML
    public void handleGuardarConfiguracionAlertas(ActionEvent event) {
        String emailDest = txtEmailAlertas != null ? txtEmailAlertas.getText().trim() : "";
        String host = txtSmtpHost != null ? txtSmtpHost.getText().trim() : "";
        String portRaw = txtSmtpPort != null ? txtSmtpPort.getText().trim() : "587";
        String user = txtSmtpUser != null ? txtSmtpUser.getText().trim() : "";
        String pass = txtSmtpPassword != null ? txtSmtpPassword.getText() : "";
        boolean habilitado = chkAlertasEmailHabilitadas == null || chkAlertasEmailHabilitadas.isSelected();
        boolean tls = chkSmtpTlsHabilitado == null || chkSmtpTlsHabilitado.isSelected();

        if (emailDest.isBlank() || !emailDest.contains("@")) {
            AnimationUtils.mostrarToast(rootConfigStack, "Ingrese un correo destinatario válido para alertas.", true);
            return;
        }
        int puerto;
        try {
            puerto = Integer.parseInt(portRaw);
            if (puerto <= 0 || puerto > 65535) {
                throw new NumberFormatException("Puerto fuera de rango");
            }
        } catch (NumberFormatException ex) {
            AnimationUtils.mostrarToast(rootConfigStack, "Puerto SMTP inválido (ej. 587 o 465).", true);
            return;
        }

        ConfiguracionAlertaEmail nuevaCfg = new ConfiguracionAlertaEmail(
                host.isBlank() ? "smtp.gmail.com" : host,
                puerto,
                user,
                pass,
                tls,
                emailDest,
                habilitado
        );
        AlertaService.actualizarConfiguracionGlobal(nuevaCfg, true);
        if (lblEstadoAlertasEmail != null) {
            lblEstadoAlertasEmail.setText("Guardado → " + emailDest);
            lblEstadoAlertasEmail.getStyleClass().removeAll("badge-pill-danger", "badge-pill-warning", "badge-pill-neutral");
            if (!lblEstadoAlertasEmail.getStyleClass().contains("badge-pill-success")) {
                lblEstadoAlertasEmail.getStyleClass().add("badge-pill-success");
            }
        }
        AnimationUtils.mostrarToast(rootConfigStack, "Configuración de alertas SMTP guardada para " + emailDest, false);
    }

    @FXML
    public void handleProbarAlertaEmail(ActionEvent event) {
        handleGuardarConfiguracionAlertas(event);
        if (btnProbarAlertaEmail != null) {
            btnProbarAlertaEmail.setDisable(true);
        }
        UiAsyncExecutor.ejecutarAsync(
            AlertaService::enviarAlertaPruebaGlobal,
            emitida -> {
                if (btnProbarAlertaEmail != null) {
                    btnProbarAlertaEmail.setDisable(false);
                }
                boolean ok = emitida != null && emitida.enviadoPorRed();
                if (lblEstadoAlertasEmail != null) {
                    lblEstadoAlertasEmail.setText(ok
                            ? "Prueba enviada a " + emitida.destinatario()
                            : "Prueba registrada (verifique credenciales SMTP)");
                }
                AnimationUtils.mostrarToast(
                        rootConfigStack,
                        ok ? "Email de prueba enviado a " + emitida.destinatario()
                                : "Alerta registrada; el servidor SMTP no confirmó envío de red.",
                        !ok
                );
            },
            ex -> {
                if (btnProbarAlertaEmail != null) {
                    btnProbarAlertaEmail.setDisable(false);
                }
                AnimationUtils.mostrarToast(rootConfigStack, "Error enviando prueba SMTP: " + ex.getMessage(), true);
            }
        );
    }

    // =========================================================================
    // Respaldo y Restauración Automatizada (v5.3.0 Ítem 3)
    // =========================================================================

    private void precargarUltimoBackupDisponible() {
        if (txtRutaBackupRestaurar == null || !txtRutaBackupRestaurar.getText().isBlank()) {
            return;
        }
        Path backupsDir = Path.of("backups");
        if (!Files.isDirectory(backupsDir)) {
            return;
        }
        try (Stream<Path> walk = Files.list(backupsDir)) {
            Optional<Path> reciente = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.endsWith(".sql.gz") || n.endsWith(".sql");
                    })
                    .max(Comparator.comparingLong(p -> p.toFile().lastModified()));
            reciente.ifPresent(p -> txtRutaBackupRestaurar.setText(p.toString()));
        } catch (IOException ignored) {
            // Si no hay directorio legible se deja el campo editable vacío
        }
    }

    @FXML
    public void handleSeleccionarArchivoBackup(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar Archivo de Backup FoodCore (.sql.gz / .sql)");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Backups MySQL comprimidos (*.sql.gz)", "*.sql.gz"),
                new FileChooser.ExtensionFilter("Volcados SQL (*.sql)", "*.sql")
        );
        Path backupsDir = Path.of("backups");
        if (Files.isDirectory(backupsDir)) {
            chooser.setInitialDirectory(backupsDir.toFile());
        }
        Window window = rootConfigStack != null && rootConfigStack.getScene() != null
                ? rootConfigStack.getScene().getWindow() : null;
        File seleccionado = chooser.showOpenDialog(window);
        if (seleccionado != null && txtRutaBackupRestaurar != null) {
            txtRutaBackupRestaurar.setText(seleccionado.getAbsolutePath());
        }
    }

    /**
     * Evalúa en modo fail-closed si el operador autenticado posee {@link Permiso#CONFIG_RED}
     * para ejecutar respaldos y restauraciones de base de datos.
     */
    public static boolean tienePermisoBackupRestauracion() {
        SesionUsuario sesion = SesionManager.getSesionActiva();
        Usuario actual = AuthService.getUsuarioSesionActual();
        if (actual != null) {
            if (sesion != null && sesion.getIdUsuario() == actual.getIdUsuario()
                    && actual.getNombreRol() != null
                    && actual.getNombreRol().equalsIgnoreCase(sesion.getCodigoRol())) {
                return sesion.tienePermiso(Permiso.CONFIG_RED);
            }
            if (actual.getNombreRol() == null || actual.getNombreRol().isBlank()) {
                return false;
            }
            return AutorizacionService.permisosPorDefectoParaRol(actual.getNombreRol())
                    .contains(Permiso.CONFIG_RED);
        }
        return sesion != null && sesion.tienePermiso(Permiso.CONFIG_RED);
    }

    @FXML
    public void handleEjecutarBackupAhora(ActionEvent event) {
        if (!tienePermisoBackupRestauracion()) {
            actualizarEstadoRestauracion("Acceso denegado (requiere CONFIG_RED)", "badge-pill-danger");
            AnimationUtils.mostrarToast(rootConfigStack,
                    "Acceso denegado: requiere permiso CONFIG_RED para generar backups.", true);
            return;
        }
        if (btnGenerarBackupAhora != null) {
            btnGenerarBackupAhora.setDisable(true);
        }
        actualizarEstadoRestauracion("Generando backup comprimido...", "badge-pill-warning");

        UiAsyncExecutor.ejecutarAsync(
            () -> {
                if (!tienePermisoBackupRestauracion()) {
                    throw new SecurityException("Acceso denegado: requiere permiso CONFIG_RED.");
                }
                return ejecutorBackup.get();
            },
            archivoGenerado -> {
                if (btnGenerarBackupAhora != null) {
                    btnGenerarBackupAhora.setDisable(false);
                }
                if (archivoGenerado != null) {
                    if (txtRutaBackupRestaurar != null) {
                        txtRutaBackupRestaurar.setText(archivoGenerado.toString());
                    }
                    actualizarEstadoRestauracion("Backup listo: " + archivoGenerado.getFileName(), "badge-pill-success");
                    AnimationUtils.mostrarToast(rootConfigStack, "Backup generado: " + archivoGenerado.getFileName(), false);
                } else {
                    actualizarEstadoRestauracion("Backup falló al ejecutarse", "badge-pill-danger");
                    AnimationUtils.mostrarToast(rootConfigStack, "No se pudo generar el backup automático.", true);
                }
            },
            ex -> {
                if (btnGenerarBackupAhora != null) {
                    btnGenerarBackupAhora.setDisable(false);
                }
                actualizarEstadoRestauracion("Error en backup", "badge-pill-danger");
                AnimationUtils.mostrarToast(rootConfigStack, "Error ejecutando backup: " + ex.getMessage(), true);
            }
        );
    }

    @FXML
    public void handleRestaurarDesdeBackup(ActionEvent event) {
        if (!tienePermisoBackupRestauracion()) {
            actualizarEstadoRestauracion("Acceso denegado (requiere CONFIG_RED)", "badge-pill-danger");
            AnimationUtils.mostrarToast(rootConfigStack,
                    "Acceso denegado: requiere permiso CONFIG_RED para restaurar la base de datos.", true);
            return;
        }
        String rutaTexto = txtRutaBackupRestaurar != null ? txtRutaBackupRestaurar.getText().trim() : "";
        if (rutaTexto.isBlank()) {
            AnimationUtils.mostrarToast(rootConfigStack, "Seleccione o indique la ruta de un backup válido.", true);
            return;
        }
        Path archivoBackup = Path.of(rutaTexto);
        if (!Files.isRegularFile(archivoBackup)) {
            actualizarEstadoRestauracion("Archivo no encontrado", "badge-pill-danger");
            AnimationUtils.mostrarToast(rootConfigStack, "El archivo de backup especificado no existe.", true);
            return;
        }
        Path fnBackup = archivoBackup.getFileName();
        String nombreLower = fnBackup != null
                ? fnBackup.toString().toLowerCase(Locale.ROOT) : "";
        if (!nombreLower.endsWith(".sql.gz") && !nombreLower.endsWith(".sql")) {
            actualizarEstadoRestauracion("Formato de backup no permitido", "badge-pill-danger");
            AnimationUtils.mostrarToast(rootConfigStack, "Solo se permiten archivos .sql.gz o .sql.", true);
            return;
        }
        if (!confirmacionRestauracionSupplier.getAsBoolean()) {
            actualizarEstadoRestauracion("Restauración cancelada por el usuario", "badge-pill-neutral");
            return;
        }

        if (btnRestaurarBackup != null) {
            btnRestaurarBackup.setDisable(true);
        }
        actualizarEstadoRestauracion("Restaurando base de datos...", "badge-pill-warning");

        UiAsyncExecutor.ejecutarAsync(
            () -> {
                if (!tienePermisoBackupRestauracion()) {
                    throw new SecurityException("Acceso denegado: requiere permiso CONFIG_RED.");
                }
                return Boolean.TRUE.equals(ejecutorRestauracion.apply(archivoBackup));
            },
            exito -> {
                if (btnRestaurarBackup != null) {
                    btnRestaurarBackup.setDisable(false);
                }
                if (Boolean.TRUE.equals(exito)) {
                    actualizarEstadoRestauracion("BD restaurada desde " + archivoBackup.getFileName(), "badge-pill-success");
                    AnimationUtils.mostrarToast(rootConfigStack,
                            "Restauración completada desde " + archivoBackup.getFileName(), false);
                } else {
                    actualizarEstadoRestauracion("Error al restaurar backup", "badge-pill-danger");
                    AnimationUtils.mostrarToast(rootConfigStack,
                            "No se pudo completar la restauración del backup.", true);
                }
            },
            ex -> {
                if (btnRestaurarBackup != null) {
                    btnRestaurarBackup.setDisable(false);
                }
                actualizarEstadoRestauracion("Error en restauración", "badge-pill-danger");
                AnimationUtils.mostrarToast(rootConfigStack,
                        "Error ejecutando restauración: " + ex.getMessage(), true);
            }
        );
    }

    private void actualizarEstadoRestauracion(String texto, String badgeClass) {
        actualizarBadgeEstado(lblEstadoRestauracion, texto, badgeClass);
    }

    private void actualizarBadgeEstado(Label label, String texto, String badgeClass) {
        if (label == null) {
            return;
        }
        label.setText(texto);
        label.getStyleClass().removeAll(
                "badge-pill-success", "badge-pill-danger", "badge-pill-warning", "badge-pill-neutral");
        label.getStyleClass().add(badgeClass);
    }

    private boolean solicitarConfirmacionRestauracionUi() {
        return solicitarConfirmacionDestructivaUi(
                "Confirmar Restauración de Base de Datos",
                "Se restaurará la base de datos desde el archivo seleccionado, deteniendo temporalmente los servicios y reemplazando los datos actuales. ¿Desea continuar?");
    }

    private boolean solicitarConfirmacionDestructivaUi(String titulo, String mensaje) {
        Alert confirm = DialogService.crearConfirmacionDestructiva(titulo, mensaje);
        if (rootConfigStack != null && rootConfigStack.getScene() != null) {
            confirm.initOwner(rootConfigStack.getScene().getWindow());
        }
        Optional<ButtonType> result = confirm.showAndWait();
        return result.isPresent() && result.get() == DialogService.BTN_SI_ANULAR;
    }

    private boolean solicitarConfirmacionUi(String titulo, String header, String contenido) {
        Alert confirm = DialogService.crearAlerta(Alert.AlertType.CONFIRMATION, titulo, header, contenido);
        if (rootConfigStack != null && rootConfigStack.getScene() != null) {
            confirm.initOwner(rootConfigStack.getScene().getWindow());
        }
        Optional<ButtonType> result = confirm.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private void refrescarEstadoDemoAsync() {
        UiAsyncExecutor.ejecutarAsync(
            demoDataService::contarDatosDemo,
            resumen -> actualizarLabelResumenDemo(resumen),
            ex -> LOGGER.log(Level.FINE, "Sin conteo demo disponible al iniciar", ex)
        );
    }

    private void actualizarLabelResumenDemo(ResumenDemo resumen) {
        if (resumen == null || resumen.total() == 0) {
            actualizarBadgeEstado(lblEstadoDemo, "Sin datos de demo cargados", "badge-pill-neutral");
        } else {
            actualizarBadgeEstado(
                    lblEstadoDemo,
                    String.format("Demo activos: %d clientes, %d productos, %d ventas",
                            resumen.clientes(), resumen.productos(), resumen.ventas()),
                    "badge-pill-success");
        }
    }

    @FXML
    public void handleCargarDatosDemo(ActionEvent event) {
        if (!confirmacionCargarDemoSupplier.getAsBoolean()) {
            return;
        }
        setBotonesDemoDeshabilitados(true);
        actualizarBadgeEstado(lblEstadoDemo, "Cargando script seed-demo.sql...", "badge-pill-warning");

        UiAsyncExecutor.ejecutarAsync(
            demoDataService::cargarDatosDemo,
            resumen -> {
                setBotonesDemoDeshabilitados(false);
                actualizarLabelResumenDemo(resumen);
                AnimationUtils.mostrarToast(rootConfigStack,
                        "Datos de demostración cargados correctamente.", false);
            },
            ex -> {
                setBotonesDemoDeshabilitados(false);
                actualizarBadgeEstado(lblEstadoDemo, "Error cargando datos demo", "badge-pill-danger");
                AnimationUtils.mostrarToast(rootConfigStack,
                        "Error cargando datos de demo: " + ex.getMessage(), true);
            }
        );
    }

    @FXML
    public void handleBorrarDatosDemo(ActionEvent event) {
        if (!confirmacionBorrarDemoSupplier.getAsBoolean()) {
            return;
        }
        setBotonesDemoDeshabilitados(true);
        actualizarBadgeEstado(lblEstadoDemo, "Eliminando registros es_demo = 1...", "badge-pill-warning");

        UiAsyncExecutor.ejecutarAsync(
            demoDataService::borrarDatosDemo,
            eliminados -> {
                setBotonesDemoDeshabilitados(false);
                actualizarBadgeEstado(lblEstadoDemo,
                        "Demo eliminados (" + eliminados + " filas)", "badge-pill-neutral");
                AnimationUtils.mostrarToast(rootConfigStack,
                        "Datos de demostración eliminados (" + eliminados + " registros).", false);
            },
            ex -> {
                setBotonesDemoDeshabilitados(false);
                actualizarBadgeEstado(lblEstadoDemo, "Error eliminando datos demo", "badge-pill-danger");
                AnimationUtils.mostrarToast(rootConfigStack,
                        "Error eliminando datos de demo: " + ex.getMessage(), true);
            }
        );
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
            this.confirmacionCargarDemoSupplier = supplier;
        }
    }

    public void setConfirmacionBorrarDemoSupplier(BooleanSupplier supplier) {
        if (supplier != null) {
            this.confirmacionBorrarDemoSupplier = supplier;
        }
    }

    public Label getLblEstadoDemo() {
        return lblEstadoDemo;
    }

    static boolean ejecutarScriptRestauracionSistema(Path backupFile) {
        if (!tienePermisoBackupRestauracion() || backupFile == null || !Files.isRegularFile(backupFile)) {
            return false;
        }
        Path fnBackup = backupFile.getFileName();
        String fileName = fnBackup != null
                ? fnBackup.toString().toLowerCase(Locale.ROOT) : "";
        boolean esGzip = fileName.endsWith(".sql.gz");
        boolean esSql = fileName.endsWith(".sql");
        if (!esGzip && !esSql) {
            return false;
        }
        Path stagingFile = esGzip
                ? Path.of("backups", ".restore_staging.sql.gz")
                : Path.of("backups", ".restore_staging.sql");
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        ProcessBuilder pb;
        if (osName.contains("win")) {
            Path script = Path.of("scripts", "restore-auto.ps1").toAbsolutePath().normalize();
            if (!Files.isRegularFile(script)) {
                return false;
            }
            if (esGzip) {
                pb = new ProcessBuilder(
                        "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                        "-File", "scripts\\restore-auto.ps1",
                        "-BackupFile", "backups\\.restore_staging.sql.gz",
                        "-SkipServiceRestart"
                );
            } else {
                pb = new ProcessBuilder(
                        "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                        "-File", "scripts\\restore-auto.ps1",
                        "-BackupFile", "backups\\.restore_staging.sql",
                        "-SkipServiceRestart"
                );
            }
        } else {
            Path script = Path.of("scripts", "restore-auto.sh").toAbsolutePath().normalize();
            if (!Files.isRegularFile(script)) {
                return false;
            }
            pb = esGzip
                    ? new ProcessBuilder("bash", "scripts/restore-auto.sh", "backups/.restore_staging.sql.gz")
                    : new ProcessBuilder("bash", "scripts/restore-auto.sh", "backups/.restore_staging.sql");
        }
        pb.redirectErrorStream(true);
        Process proc = null;
        try {
            Files.createDirectories(Path.of("backups"));
            Path backupReal = backupFile.toRealPath();
            Files.copy(backupReal, stagingFile, StandardCopyOption.REPLACE_EXISTING);
            proc = pb.start();
            try (var reader = proc.getInputStream()) {
                reader.readAllBytes();
            }
            boolean finished = proc.waitFor(SCRIPT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return finished && proc.exitValue() == 0;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOGGER.log(Level.WARNING, "Error ejecutando restore-auto: " + ex.getMessage(), ex);
            return false;
        } finally {
            if (proc != null && proc.isAlive()) {
                proc.destroyForcibly();
            }
            try {
                Files.deleteIfExists(stagingFile);
            } catch (IOException exDel) {
                LOGGER.log(Level.FINE, "No se pudo eliminar archivo temporal de staging de restauración", exDel);
            }
        }
    }

    static Path ejecutarScriptBackupSistema() {
        if (!tienePermisoBackupRestauracion()) {
            return null;
        }
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        ProcessBuilder pb;
        if (osName.contains("win")) {
            Path script = Path.of("scripts", "backup-auto.ps1").toAbsolutePath().normalize();
            if (!Files.isRegularFile(script)) {
                return null;
            }
            pb = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                    "-File", "scripts\\backup-auto.ps1"
            );
        } else {
            Path script = Path.of("scripts", "backup-auto.sh").toAbsolutePath().normalize();
            if (!Files.isRegularFile(script)) {
                return null;
            }
            pb = new ProcessBuilder("bash", "scripts/backup-auto.sh");
        }
        pb.redirectErrorStream(true);
        Process proc = null;
        try {
            proc = pb.start();
            String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = proc.waitFor(SCRIPT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished || proc.exitValue() != 0) {
                return null;
            }
            Path backupsDir = Path.of("backups").toAbsolutePath().normalize();
            if (!Files.isDirectory(backupsDir)) {
                return null;
            }
            String[] lines = output.split("\\R");
            String nombreReportado = null;
            for (int i = lines.length - 1; i >= 0; i--) {
                String candidate = lines[i].trim();
                if (!candidate.isEmpty()) {
                    int sep = Math.max(candidate.lastIndexOf('/'), candidate.lastIndexOf('\\'));
                    nombreReportado = sep >= 0 ? candidate.substring(sep + 1) : candidate;
                    break;
                }
            }
            try (Stream<Path> files = Files.list(backupsDir)) {
                List<Path> backups = files
                        .filter(Files::isRegularFile)
                        .filter(p -> {
                            Path fn = p.getFileName();
                            if (fn == null) {
                                return false;
                            }
                            String name = fn.toString();
                            return name.endsWith(".sql.gz") || name.endsWith(".sql");
                        })
                        .toList();
                if (nombreReportado != null) {
                    for (Path p : backups) {
                        Path fn = p.getFileName();
                        if (fn != null && fn.toString().equals(nombreReportado)) {
                            return p;
                        }
                    }
                }
                return backups.stream()
                        .max(Comparator.comparingLong(p -> p.toFile().lastModified()))
                        .orElse(null);
            }
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOGGER.log(Level.WARNING, "Error ejecutando backup-auto: " + ex.getMessage(), ex);
            return null;
        } finally {
            if (proc != null && proc.isAlive()) {
                proc.destroyForcibly();
            }
        }
    }

    public void setConfirmacionRestauracionSupplier(BooleanSupplier supplier) {
        this.confirmacionRestauracionSupplier = supplier != null ? supplier : this::solicitarConfirmacionRestauracionUi;
    }

    public void setEjecutorRestauracion(Function<Path, Boolean> ejecutor) {
        this.ejecutorRestauracion = ejecutor != null ? ejecutor : ConfiguracionController::ejecutarScriptRestauracionSistema;
    }

    public void setEjecutorBackup(Supplier<Path> ejecutor) {
        this.ejecutorBackup = ejecutor != null ? ejecutor : ConfiguracionController::ejecutarScriptBackupSistema;
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
        UiAsyncExecutor.ejecutarAsync(
            () -> {
                return configuracionService.cargarConfiguracionRestaurante();
            },
            datos -> {
                if (datos.isPresent()) {
                    var config = datos.get();
                    txtNombreRestaurante.setText(config.nombreRestaurante());
                    txtDireccion.setText(config.direccion());
                    txtTelefono.setText(config.telefono());
                    txtRuc.setText(config.ruc() != null ? config.ruc() : "");
                    txtImpresora.setText(config.nombreImpresora());
                }
            },
            e -> LOGGER.log(Level.FINE, "Usando configuración por defecto", e)
        );
    }

    @FXML
    public void handleGuardarConfiguracion(ActionEvent event) {
        final String nombre = txtNombreRestaurante.getText();
        final String direccion = txtDireccion.getText();
        final String telefono = txtTelefono.getText();
        final String ruc = txtRuc.getText();
        final String impresora = txtImpresora.getText();

        if (btnGuardarConfig != null) {
            btnGuardarConfig.setDisable(true);
        }
        UiAsyncExecutor.ejecutarVoidAsync(
            () -> {
                configuracionService.guardarConfiguracionRestaurante(
                        new ConfiguracionSistemaService.DatosRestauranteConfig(
                                nombre, direccion, telefono, ruc, impresora));
            },
            () -> {
                if (btnGuardarConfig != null) {
                    btnGuardarConfig.setDisable(false);
                }
                AnimationUtils.mostrarToast(rootConfigStack, "Configuración del restaurante e impresora guardada.", false);
            },
            e -> {
                if (btnGuardarConfig != null) {
                    btnGuardarConfig.setDisable(false);
                }
                AnimationUtils.mostrarToast(rootConfigStack, "Error al guardar configuración: " + (e != null ? e.getMessage() : "desconocido"), true);
            }
        );
    }
}
