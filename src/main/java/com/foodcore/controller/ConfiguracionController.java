package com.foodcore.controller;

import com.foodcore.App;
import com.foodcore.dao.ConfiguracionImpresoraDAO;
import com.foodcore.dao.impl.ConfiguracionImpresoraDAOImpl;
import com.foodcore.model.ConfiguracionImpresora;
import com.foodcore.service.AlertaService;
import com.foodcore.service.AlertaService.AlertaEmitida;
import com.foodcore.service.AlertaService.ConfiguracionAlertaEmail;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ConexionBD;
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Comparator;
import java.util.LinkedHashMap;
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

    private final ConfiguracionImpresoraDAO configImpresoraDAO = new ConfiguracionImpresoraDAOImpl();
    private final ObservableList<ConfiguracionImpresora> asignaciones = FXCollections.observableArrayList();

    private BooleanSupplier confirmacionRestauracionSupplier = this::solicitarConfirmacionRestauracionUi;
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
        configurarTablaImpresoras();
        poblarImpresorasDetectadas();
        cargarConfiguracion();
        cargarRuteoImpresorasAsync();
        cargarConfiguracionAlertasEmailUi();
        precargarUltimoBackupDisponible();
        inicializarToggleModoOscuroUi();
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
                try (Connection conn = ConexionBD.getConnection()) {
                    Map<Integer, String> categorias = configImpresoraDAO.obtenerMapaNombreCategorias(conn);
                    List<ConfiguracionImpresora> lista = configImpresoraDAO.listarTodas(conn);
                    Map<String, Object> res = new LinkedHashMap<>();
                    res.put("categorias", categorias);
                    res.put("lista", lista);
                    return res;
                }
            },
            res -> {
                @SuppressWarnings("unchecked")
                Map<Integer, String> categorias = (Map<Integer, String>) res.get("categorias");
                @SuppressWarnings("unchecked")
                List<ConfiguracionImpresora> lista = (List<ConfiguracionImpresora>) res.get("lista");

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
                try (Connection conn = ConexionBD.getConnection()) {
                    configImpresoraDAO.guardarOActualizar(conn, catSel.idCategoria(), impresoraFinal);
                }
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
                try (Connection conn = ConexionBD.getConnection()) {
                    configImpresoraDAO.eliminarPorCategoria(conn, sel.getIdCategoriaProducto());
                }
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

    @FXML
    public void handleEjecutarBackupAhora(ActionEvent event) {
        if (btnGenerarBackupAhora != null) {
            btnGenerarBackupAhora.setDisable(true);
        }
        actualizarEstadoRestauracion("Generando backup comprimido...", "badge-pill-warning");

        UiAsyncExecutor.ejecutarAsync(
            () -> ejecutorBackup.get(),
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
        if (!confirmacionRestauracionSupplier.getAsBoolean()) {
            actualizarEstadoRestauracion("Restauración cancelada por el usuario", "badge-pill-neutral");
            return;
        }

        if (btnRestaurarBackup != null) {
            btnRestaurarBackup.setDisable(true);
        }
        actualizarEstadoRestauracion("Restaurando base de datos...", "badge-pill-warning");

        UiAsyncExecutor.ejecutarAsync(
            () -> Boolean.TRUE.equals(ejecutorRestauracion.apply(archivoBackup)),
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
        if (lblEstadoRestauracion == null) {
            return;
        }
        lblEstadoRestauracion.setText(texto);
        lblEstadoRestauracion.getStyleClass().removeAll(
                "badge-pill-success", "badge-pill-danger", "badge-pill-warning", "badge-pill-neutral");
        lblEstadoRestauracion.getStyleClass().add(badgeClass);
    }

    private boolean solicitarConfirmacionRestauracionUi() {
        Alert confirm = DialogService.crearAlerta(
                Alert.AlertType.CONFIRMATION,
                "Confirmar Restauración de Base de Datos",
                "Se restaurará la base de datos desde el archivo seleccionado",
                "Esta acción detendrá temporalmente los servicios auxiliares y reemplazará los datos actuales. ¿Desea continuar?");
        if (rootConfigStack != null && rootConfigStack.getScene() != null) {
            confirm.initOwner(rootConfigStack.getScene().getWindow());
        }
        Optional<ButtonType> result = confirm.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    static boolean ejecutarScriptRestauracionSistema(Path backupFile) {
        if (backupFile == null || !Files.isRegularFile(backupFile)) {
            return false;
        }
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        ProcessBuilder pb;
        if (osName.contains("win")) {
            Path script = Path.of("scripts", "restore-auto.ps1").toAbsolutePath().normalize();
            if (!Files.isRegularFile(script)) {
                return false;
            }
            pb = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                    "-File", script.toString(),
                    "-BackupFile", backupFile.toAbsolutePath().normalize().toString(),
                    "-SkipServiceRestart"
            );
        } else {
            Path script = Path.of("scripts", "restore-auto.sh").toAbsolutePath().normalize();
            if (!Files.isRegularFile(script)) {
                return false;
            }
            pb = new ProcessBuilder("bash", script.toString(), backupFile.toAbsolutePath().normalize().toString());
        }
        pb.redirectErrorStream(true);
        Process proc = null;
        try {
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
        }
    }

    static Path ejecutarScriptBackupSistema() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        ProcessBuilder pb;
        if (osName.contains("win")) {
            Path script = Path.of("scripts", "backup-auto.ps1").toAbsolutePath().normalize();
            if (!Files.isRegularFile(script)) {
                return null;
            }
            pb = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                    "-File", script.toString()
            );
        } else {
            Path script = Path.of("scripts", "backup-auto.sh").toAbsolutePath().normalize();
            if (!Files.isRegularFile(script)) {
                return null;
            }
            pb = new ProcessBuilder("bash", script.toString());
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
            String[] lines = output.split("\\R");
            for (int i = lines.length - 1; i >= 0; i--) {
                String candidate = lines[i].trim();
                if (!candidate.isEmpty() && Files.isRegularFile(Path.of(candidate))) {
                    return Path.of(candidate);
                }
            }
            return null;
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
        String sql = "SELECT nombre_restaurante, direccion, telefono, ruc, nombre_impresora "
            + "FROM configuracion_restaurante WHERE id_config = 1";
        UiAsyncExecutor.ejecutarAsync(
            () -> {
                try (Connection conn = ConexionBD.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new String[] {
                            rs.getString("nombre_restaurante"),
                            rs.getString("direccion"),
                            rs.getString("telefono"),
                            rs.getString("ruc"),
                            rs.getString("nombre_impresora")
                        };
                    }
                    return null;
                }
            },
            datos -> {
                if (datos != null) {
                    txtNombreRestaurante.setText(datos[0]);
                    txtDireccion.setText(datos[1]);
                    txtTelefono.setText(datos[2]);
                    txtRuc.setText(datos[3] != null ? datos[3] : "");
                    txtImpresora.setText(datos[4]);
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
        final String sql = "INSERT INTO configuracion_restaurante (id_config, nombre_restaurante, direccion, telefono, ruc, nombre_impresora) "
            + "VALUES (1, ?, ?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE nombre_restaurante = VALUES(nombre_restaurante), direccion = VALUES(direccion), "
            + "telefono = VALUES(telefono), ruc = VALUES(ruc), nombre_impresora = VALUES(nombre_impresora)";

        if (btnGuardarConfig != null) {
            btnGuardarConfig.setDisable(true);
        }
        UiAsyncExecutor.ejecutarVoidAsync(
            () -> {
                try (Connection conn = ConexionBD.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, nombre);
                    ps.setString(2, direccion);
                    ps.setString(3, telefono);
                    ps.setString(4, ruc);
                    ps.setString(5, impresora);
                    ps.executeUpdate();
                }
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
