package com.foodcore.controller;

import com.foodcore.config.AppContainer;
import com.foodcore.controller.dashboard.LifecycleAwareController;
import com.foodcore.model.DocumentoEmpleado;
import com.foodcore.model.Usuario;
import com.foodcore.service.ExpedienteEmpleadoService;
import com.foodcore.util.ExportFileChooserHelper;
import com.foodcore.util.UiAsyncExecutor;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador JavaFX para gestión administrativa de expedientes y legajos PDF de colaboradores.
 */
public class ExpedienteEmpleadoController implements LifecycleAwareController {

    private static final Logger LOGGER = Logger.getLogger(ExpedienteEmpleadoController.class.getName());
    private static final DateTimeFormatter FECHA_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    private record DatosExpedientes(
            List<Usuario> colaboradores,
            List<DocumentoEmpleado> porVencer) {
    }

    @FXML private StackPane rootExpedienteStack;
    @FXML private Label lblKpiTotalPersonal;
    @FXML private Label lblKpiDocsEmpleado;
    @FXML private Label lblKpiDocsPorVencer;

    @FXML private TextField txtBuscarColaborador;
    @FXML private TableView<Usuario> tblColaboradores;
    @FXML private TableColumn<Usuario, String> colColabNombre;
    @FXML private TableColumn<Usuario, String> colColabCargo;
    @FXML private TableColumn<Usuario, String> colColabDni;

    @FXML private Label lblEmpleadoSeleccionado;
    @FXML private Label lblRolSeleccionado;
    @FXML private Label lblMensajeEstado;
    @FXML private TabPane tabPaneExpediente;
    @FXML private Tab tabDatosPersonales;
    @FXML private Tab tabDatosLaborales;
    @FXML private Tab tabDocumentos;

    @FXML private ComboBox<String> cbTipoDocumento;
    @FXML private TextField txtDni;
    @FXML private TextField txtNombreCompleto;
    @FXML private DatePicker dpFechaNacimiento;
    @FXML private TextField txtTelefono;
    @FXML private TextField txtDireccion;
    @FXML private TextField txtContactoEmergencia;
    @FXML private TextField txtTelefonoEmergencia;
    @FXML private Button btnGuardarPersonales;

    @FXML private TextField txtCargo;
    @FXML private ComboBox<String> cbTipoContrato;
    @FXML private DatePicker dpFechaIngreso;
    @FXML private TextField txtSueldoBase;
    @FXML private TextField txtHorarioLaboral;
    @FXML private TextField txtHoraEntrada;
    @FXML private TextField txtHoraSalida;
    @FXML private DatePicker dpVencContrato;
    @FXML private DatePicker dpVencCarnet;
    @FXML private Button btnGuardarLaborales;

    @FXML private ComboBox<String> cbTipoDocPdf;
    @FXML private DatePicker dpDocEmision;
    @FXML private DatePicker dpDocVencimiento;
    @FXML private TextField txtDocObservaciones;
    @FXML private Button btnSubirPdf;
    @FXML private Button btnEliminarDoc;
    @FXML private TableView<DocumentoEmpleado> tblDocumentos;
    @FXML private TableColumn<DocumentoEmpleado, String> colDocTipo;
    @FXML private TableColumn<DocumentoEmpleado, String> colDocArchivo;
    @FXML private TableColumn<DocumentoEmpleado, String> colDocEmision;
    @FXML private TableColumn<DocumentoEmpleado, String> colDocVencimiento;
    @FXML private TableColumn<DocumentoEmpleado, String> colDocEstado;

    private final ExpedienteEmpleadoService expedienteService;
    private final ObservableList<Usuario> todosColaboradores = FXCollections.observableArrayList();
    private final ObservableList<Usuario> colaboradoresFiltrados = FXCollections.observableArrayList();
    private final ObservableList<DocumentoEmpleado> documentosActuales = FXCollections.observableArrayList();
    private final AtomicBoolean vistaActiva = new AtomicBoolean(true);
    private final AtomicLong cargaExpedientesSeq = new AtomicLong(0L);
    private final AtomicLong cargaDocsSeq = new AtomicLong(0L);
    private final AtomicBoolean guardarPersonalesEnCurso = new AtomicBoolean(false);
    private final AtomicBoolean guardarLaboralesEnCurso = new AtomicBoolean(false);
    private final AtomicBoolean subirPdfEnCurso = new AtomicBoolean(false);
    private final AtomicBoolean eliminarDocEnCurso = new AtomicBoolean(false);

    private Usuario colaboradorActual;

    public ExpedienteEmpleadoController() {
        this(AppContainer.getDefault().expedienteEmpleadoService());
    }

    public ExpedienteEmpleadoController(ExpedienteEmpleadoService expedienteService) {
        this.expedienteService = Objects.requireNonNull(
                expedienteService, "expedienteService no puede ser nulo");
    }

    @FXML
    public void initialize() {
        vistaActiva.set(true);
        configurarCombos();
        configurarTablas();
        cargarExpedientes(null);
    }

    @Override
    public void detenerRecursos() {
        vistaActiva.set(false);
    }

    private void configurarCombos() {
        cbTipoDocumento.setItems(FXCollections.observableArrayList("DNI", "CE", "PASAPORTE"));
        cbTipoDocumento.getSelectionModel().select("DNI");

        cbTipoContrato.setItems(FXCollections.observableArrayList(
                "INDETERMINADO", "PLAZO_FIJO", "PART_TIME", "LOCACION_SERVICIOS", "PRACTICANTE"));
        cbTipoContrato.getSelectionModel().select("PLAZO_FIJO");

        cbTipoDocPdf.setItems(FXCollections.observableArrayList(
                "CONTRATO_TRABAJO", "DNI_ESCANEADO", "CERTIFICADO_MANIPULACION",
                "CARNET_SANIDAD", "ANTECEDENTES", "OTRO"));
        cbTipoDocPdf.getSelectionModel().select("CONTRATO_TRABAJO");
    }

    private void configurarTablas() {
        colColabNombre.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getNombreEmpleado() != null && !c.getValue().getNombreEmpleado().isBlank()
                        ? c.getValue().getNombreEmpleado()
                        : c.getValue().getUsername()));
        colColabCargo.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getCargo() != null && !c.getValue().getCargo().isBlank()
                        ? c.getValue().getCargo()
                        : Optional.ofNullable(c.getValue().getNombreRol()).orElse("-")));
        colColabDni.setCellValueFactory(c -> new SimpleStringProperty(
                Optional.ofNullable(c.getValue().getDni()).orElse("-")));
        tblColaboradores.setItems(colaboradoresFiltrados);

        tblColaboradores.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                mostrarDetalleColaborador(newSel);
            }
        });

        txtBuscarColaborador.textProperty().addListener((obs, oldVal, newVal) -> aplicarFiltroColaboradores());

        colDocTipo.setCellValueFactory(c -> new SimpleStringProperty(
                Optional.ofNullable(c.getValue().getTipoDocumento()).orElse("-").replace('_', ' ')));
        colDocArchivo.setCellValueFactory(c -> new SimpleStringProperty(
                Optional.ofNullable(c.getValue().getNombreArchivo()).orElse("-")));
        colDocEmision.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getFechaEmision() != null ? c.getValue().getFechaEmision().format(FECHA_FMT) : "-"));
        colDocVencimiento.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getFechaVencimiento() != null
                        ? c.getValue().getFechaVencimiento().format(FECHA_FMT)
                        : "No vence"));
        colDocEstado.setCellValueFactory(c -> new SimpleStringProperty(
                calcularEtiquetaVigencia(c.getValue().getFechaVencimiento())));
        tblDocumentos.setItems(documentosActuales);
    }

    @FXML
    public void handleRefrescar() {
        Integer idSeleccionado = colaboradorActual != null ? colaboradorActual.getIdUsuario() : null;
        cargarExpedientes(idSeleccionado);
    }

    public void cargarExpedientes(Integer idPreferido) {
        long seq = cargaExpedientesSeq.incrementAndGet();
        UiAsyncExecutor.ejecutarAsync(
            () -> new DatosExpedientes(
                    expedienteService.listarExpedientes(true),
                    expedienteService.listarDocumentosProximosAVencer(30)),
            datos -> {
                if (!vistaActiva.get() || seq != cargaExpedientesSeq.get()) {
                    return;
                }
                List<Usuario> lista = datos != null && datos.colaboradores() != null
                        ? datos.colaboradores() : List.of();
                List<DocumentoEmpleado> porVencer = datos != null && datos.porVencer() != null
                        ? datos.porVencer() : List.of();
                todosColaboradores.setAll(lista);
                if (lblKpiTotalPersonal != null) {
                    lblKpiTotalPersonal.setText(String.valueOf(lista.size()));
                }
                if (lblKpiDocsPorVencer != null) {
                    lblKpiDocsPorVencer.setText(String.valueOf(porVencer.size()));
                }
                aplicarFiltroColaboradores();
                seleccionarColaborador(idPreferido);
            },
            e -> {
                LOGGER.log(Level.WARNING, "No se pudieron cargar los expedientes", e);
                if (vistaActiva.get()) {
                    mostrarError("No se pudieron cargar los expedientes: " + e.getMessage());
                }
            }
        );
    }

    private void aplicarFiltroColaboradores() {
        String filtro = txtBuscarColaborador != null && txtBuscarColaborador.getText() != null
                ? txtBuscarColaborador.getText().trim().toLowerCase(Locale.ROOT)
                : "";
        if (filtro.isBlank()) {
            colaboradoresFiltrados.setAll(todosColaboradores);
        } else {
            colaboradoresFiltrados.setAll(todosColaboradores.stream()
                    .filter(u -> contiene(u.getNombreEmpleado(), filtro)
                            || contiene(u.getUsername(), filtro)
                            || contiene(u.getDni(), filtro)
                            || contiene(u.getCargo(), filtro))
                    .toList());
        }
    }

    private void seleccionarColaborador(Integer idPreferido) {
        if (colaboradoresFiltrados.isEmpty()) {
            colaboradorActual = null;
            documentosActuales.clear();
            if (lblKpiDocsEmpleado != null) {
                lblKpiDocsEmpleado.setText("0");
            }
            if (lblEmpleadoSeleccionado != null) {
                lblEmpleadoSeleccionado.setText("Sin colaboradores disponibles");
            }
            if (lblRolSeleccionado != null) {
                lblRolSeleccionado.setText("-");
            }
            return;
        }
        Usuario objetivo = colaboradoresFiltrados.get(0);
        if (idPreferido != null) {
            objetivo = colaboradoresFiltrados.stream()
                    .filter(u -> u.getIdUsuario() == idPreferido)
                    .findFirst()
                    .orElse(objetivo);
        }
        if (tblColaboradores != null) {
            tblColaboradores.getSelectionModel().select(objetivo);
        }
        mostrarDetalleColaborador(objetivo);
    }

    private void mostrarDetalleColaborador(Usuario usuario) {
        this.colaboradorActual = usuario;
        String nombre = usuario.getNombreEmpleado() != null && !usuario.getNombreEmpleado().isBlank()
                ? usuario.getNombreEmpleado()
                : usuario.getUsername();
        if (lblEmpleadoSeleccionado != null) {
            lblEmpleadoSeleccionado.setText(nombre);
        }
        if (lblRolSeleccionado != null) {
            lblRolSeleccionado.setText(Optional.ofNullable(usuario.getNombreRol()).orElse("PERSONAL"));
        }

        cbTipoDocumento.setValue(
                usuario.getTipoDocumento() != null && !usuario.getTipoDocumento().isBlank()
                        ? usuario.getTipoDocumento()
                        : "DNI");
        txtDni.setText(nvl(usuario.getDni()));
        txtNombreCompleto.setText(nombre);
        dpFechaNacimiento.setValue(usuario.getFechaNacimiento());
        txtTelefono.setText(nvl(usuario.getTelefono()));
        txtDireccion.setText(nvl(usuario.getDireccion()));
        txtContactoEmergencia.setText(nvl(usuario.getContactoEmergencia()));
        txtTelefonoEmergencia.setText(nvl(usuario.getTelefonoEmergencia()));

        txtCargo.setText(nvl(usuario.getCargo()));
        cbTipoContrato.setValue(
                usuario.getTipoContrato() != null && !usuario.getTipoContrato().isBlank()
                        ? usuario.getTipoContrato()
                        : "PLAZO_FIJO");
        dpFechaIngreso.setValue(usuario.getFechaIngreso());
        BigDecimal sueldoMostrar = usuario.getSueldoBase() != null ? usuario.getSueldoBase() : BigDecimal.ZERO;
        txtSueldoBase.setText(String.format(Locale.US, "%.2f", sueldoMostrar));
        txtHorarioLaboral.setText(nvl(usuario.getHorarioLaboral()));
        txtHoraEntrada.setText(usuario.getHoraEntradaProgramada() != null
                ? usuario.getHoraEntradaProgramada().format(HORA_FMT)
                : "");
        txtHoraSalida.setText(usuario.getHoraSalidaProgramada() != null
                ? usuario.getHoraSalidaProgramada().format(HORA_FMT)
                : "");
        dpVencContrato.setValue(usuario.getFechaVencimientoContrato());
        dpVencCarnet.setValue(usuario.getFechaVencimientoCarnetSanidad());

        cargarDocumentosColaborador(usuario.getIdUsuario());
    }

    private void cargarDocumentosColaborador(int idUsuario) {
        long seq = cargaDocsSeq.incrementAndGet();
        UiAsyncExecutor.ejecutarAsync(
            () -> expedienteService.listarDocumentos(idUsuario),
            docs -> {
                if (!vistaActiva.get() || seq != cargaDocsSeq.get()) {
                    return;
                }
                List<DocumentoEmpleado> seguros = docs != null ? docs : List.of();
                documentosActuales.setAll(seguros);
                if (lblKpiDocsEmpleado != null) {
                    lblKpiDocsEmpleado.setText(String.valueOf(seguros.size()));
                }
            },
            e -> {
                LOGGER.log(Level.WARNING, "Error cargando documentos del colaborador", e);
                if (vistaActiva.get()) {
                    mostrarError("No se pudieron cargar los documentos: " + e.getMessage());
                }
            }
        );
    }

    @FXML
    public void handleGuardarDatosPersonales() {
        if (colaboradorActual == null) {
            mostrarError("Seleccione primero un colaborador.");
            return;
        }
        if (!guardarPersonalesEnCurso.compareAndSet(false, true)) {
            return;
        }
        Usuario snapshot = copiarUsuario(colaboradorActual);
        snapshot.setTipoDocumento(cbTipoDocumento != null ? cbTipoDocumento.getValue() : null);
        snapshot.setDni(txtDni != null ? txtDni.getText() : null);
        snapshot.setNombreEmpleado(txtNombreCompleto != null ? txtNombreCompleto.getText() : null);
        snapshot.setFechaNacimiento(dpFechaNacimiento != null ? dpFechaNacimiento.getValue() : null);
        snapshot.setTelefono(txtTelefono != null ? txtTelefono.getText() : null);
        snapshot.setDireccion(txtDireccion != null ? txtDireccion.getText() : null);
        snapshot.setContactoEmergencia(txtContactoEmergencia != null ? txtContactoEmergencia.getText() : null);
        snapshot.setTelefonoEmergencia(txtTelefonoEmergencia != null ? txtTelefonoEmergencia.getText() : null);

        if (btnGuardarPersonales != null) {
            btnGuardarPersonales.setDisable(true);
        }

        UiAsyncExecutor.ejecutarVoidAsync(
            () -> expedienteService.guardarDatosPersonales(snapshot),
            () -> {
                guardarPersonalesEnCurso.set(false);
                if (btnGuardarPersonales != null) {
                    btnGuardarPersonales.setDisable(false);
                }
                if (!vistaActiva.get()) {
                    return;
                }
                cargarExpedientes(snapshot.getIdUsuario());
                mostrarOk("Datos personales guardados correctamente.");
            },
            e -> {
                guardarPersonalesEnCurso.set(false);
                if (btnGuardarPersonales != null) {
                    btnGuardarPersonales.setDisable(false);
                }
                LOGGER.log(Level.WARNING, "Error guardando datos personales", e);
                if (vistaActiva.get()) {
                    mostrarError(e.getMessage());
                }
            }
        );
    }

    @FXML
    public void handleGuardarDatosLaborales() {
        if (colaboradorActual == null) {
            mostrarError("Seleccione primero un colaborador.");
            return;
        }
        BigDecimal sueldo;
        LocalTime entrada;
        LocalTime salida;
        try {
            String rawSueldo = txtSueldoBase != null && txtSueldoBase.getText() != null
                    ? txtSueldoBase.getText().trim() : "";
            sueldo = new BigDecimal(rawSueldo.replace(',', '.')).setScale(2, RoundingMode.HALF_UP);
            entrada = parsearHoraOpcional(txtHoraEntrada != null ? txtHoraEntrada.getText() : null);
            salida = parsearHoraOpcional(txtHoraSalida != null ? txtHoraSalida.getText() : null);
        } catch (NumberFormatException e) {
            mostrarError("Ingrese un monto numérico válido para el Sueldo Base.");
            return;
        } catch (DateTimeParseException e) {
            mostrarError("Formato de hora inválido. Use HH:mm (ejemplo 09:00).");
            return;
        }
        if (!guardarLaboralesEnCurso.compareAndSet(false, true)) {
            return;
        }

        Usuario snapshot = copiarUsuario(colaboradorActual);
        snapshot.setCargo(txtCargo != null ? txtCargo.getText() : null);
        snapshot.setTipoContrato(cbTipoContrato != null ? cbTipoContrato.getValue() : null);
        snapshot.setFechaIngreso(dpFechaIngreso != null ? dpFechaIngreso.getValue() : null);
        snapshot.setSueldoBase(sueldo);
        snapshot.setHorarioLaboral(txtHorarioLaboral != null ? txtHorarioLaboral.getText() : null);
        snapshot.setHoraEntradaProgramada(entrada);
        snapshot.setHoraSalidaProgramada(salida);
        snapshot.setFechaVencimientoContrato(dpVencContrato != null ? dpVencContrato.getValue() : null);
        snapshot.setFechaVencimientoCarnetSanidad(dpVencCarnet != null ? dpVencCarnet.getValue() : null);

        if (btnGuardarLaborales != null) {
            btnGuardarLaborales.setDisable(true);
        }

        UiAsyncExecutor.ejecutarVoidAsync(
            () -> expedienteService.guardarDatosLaborales(snapshot),
            () -> {
                guardarLaboralesEnCurso.set(false);
                if (btnGuardarLaborales != null) {
                    btnGuardarLaborales.setDisable(false);
                }
                if (!vistaActiva.get()) {
                    return;
                }
                cargarExpedientes(snapshot.getIdUsuario());
                mostrarOk("Datos laborales actualizados correctamente.");
            },
            e -> {
                guardarLaboralesEnCurso.set(false);
                if (btnGuardarLaborales != null) {
                    btnGuardarLaborales.setDisable(false);
                }
                LOGGER.log(Level.WARNING, "Error guardando datos laborales", e);
                if (vistaActiva.get()) {
                    mostrarError(e.getMessage());
                }
            }
        );
    }

    @FXML
    public void handleSubirPdf() {
        if (colaboradorActual == null) {
            mostrarError("Seleccione un colaborador antes de subir un PDF.");
            return;
        }
        Optional<File> seleccionado = ExportFileChooserHelper.elegirPdfApertura(
                btnSubirPdf, "Seleccionar documento PDF del colaborador");
        if (seleccionado.isEmpty()) {
            return;
        }
        if (!subirPdfEnCurso.compareAndSet(false, true)) {
            return;
        }
        int idUsuario = colaboradorActual.getIdUsuario();
        String tipoDoc = cbTipoDocPdf != null ? cbTipoDocPdf.getValue() : null;
        File archivoOrigen = seleccionado.get();
        LocalDate emision = dpDocEmision != null ? dpDocEmision.getValue() : null;
        LocalDate vencimiento = dpDocVencimiento != null ? dpDocVencimiento.getValue() : null;
        String observaciones = txtDocObservaciones != null ? txtDocObservaciones.getText() : null;

        if (btnSubirPdf != null) {
            btnSubirPdf.setDisable(true);
        }

        UiAsyncExecutor.ejecutarAsync(
            () -> expedienteService.subirDocumentoPdf(
                    idUsuario, tipoDoc, archivoOrigen, emision, vencimiento, observaciones),
            guardado -> {
                subirPdfEnCurso.set(false);
                if (btnSubirPdf != null) {
                    btnSubirPdf.setDisable(false);
                }
                if (!vistaActiva.get()) {
                    return;
                }
                if (txtDocObservaciones != null) {
                    txtDocObservaciones.clear();
                }
                cargarExpedientes(idUsuario);
                mostrarOk("PDF adjuntado correctamente: " + guardado.getNombreArchivo());
            },
            e -> {
                subirPdfEnCurso.set(false);
                if (btnSubirPdf != null) {
                    btnSubirPdf.setDisable(false);
                }
                LOGGER.log(Level.WARNING, "Error subiendo PDF al expediente", e);
                if (vistaActiva.get()) {
                    mostrarError(e.getMessage());
                }
            }
        );
    }

    @FXML
    public void handleEliminarDocumento() {
        DocumentoEmpleado doc = tblDocumentos != null ? tblDocumentos.getSelectionModel().getSelectedItem() : null;
        if (doc == null) {
            mostrarError("Seleccione un documento de la tabla para eliminarlo.");
            return;
        }
        if (!eliminarDocEnCurso.compareAndSet(false, true)) {
            return;
        }
        long idDocumento = doc.getIdDocumento();
        Integer idColab = colaboradorActual != null ? colaboradorActual.getIdUsuario() : null;
        if (btnEliminarDoc != null) {
            btnEliminarDoc.setDisable(true);
        }

        UiAsyncExecutor.ejecutarVoidAsync(
            () -> expedienteService.eliminarDocumento(idDocumento),
            () -> {
                eliminarDocEnCurso.set(false);
                if (btnEliminarDoc != null) {
                    btnEliminarDoc.setDisable(false);
                }
                if (!vistaActiva.get()) {
                    return;
                }
                if (idColab != null) {
                    cargarExpedientes(idColab);
                }
                mostrarOk("Documento eliminado del expediente.");
            },
            e -> {
                eliminarDocEnCurso.set(false);
                if (btnEliminarDoc != null) {
                    btnEliminarDoc.setDisable(false);
                }
                LOGGER.log(Level.WARNING, "Error eliminando documento del expediente", e);
                if (vistaActiva.get()) {
                    mostrarError(e.getMessage());
                }
            }
        );
    }

    private static Usuario copiarUsuario(Usuario src) {
        Usuario copy = new Usuario();
        copy.setIdUsuario(src.getIdUsuario());
        copy.setIdEmpleado(src.getIdEmpleado());
        copy.setIdRol(src.getIdRol());
        copy.setUsername(src.getUsername());
        copy.setNombreRol(src.getNombreRol());
        copy.setActivo(src.isActivo());
        copy.setNombreEmpleado(src.getNombreEmpleado());
        copy.setTipoDocumento(src.getTipoDocumento());
        copy.setDni(src.getDni());
        copy.setFechaNacimiento(src.getFechaNacimiento());
        copy.setTelefono(src.getTelefono());
        copy.setDireccion(src.getDireccion());
        copy.setContactoEmergencia(src.getContactoEmergencia());
        copy.setTelefonoEmergencia(src.getTelefonoEmergencia());
        copy.setCargo(src.getCargo());
        copy.setTipoContrato(src.getTipoContrato());
        copy.setFechaIngreso(src.getFechaIngreso());
        copy.setSueldoBase(src.getSueldoBase());
        copy.setHorarioLaboral(src.getHorarioLaboral());
        copy.setHoraEntradaProgramada(src.getHoraEntradaProgramada());
        copy.setHoraSalidaProgramada(src.getHoraSalidaProgramada());
        copy.setFechaVencimientoContrato(src.getFechaVencimientoContrato());
        copy.setFechaVencimientoCarnetSanidad(src.getFechaVencimientoCarnetSanidad());
        return copy;
    }

    private String calcularEtiquetaVigencia(LocalDate vencimiento) {
        if (vencimiento == null) {
            return "SIN VENCIMIENTO";
        }
        long dias = ChronoUnit.DAYS.between(LocalDate.now(ZONA_LIMA), vencimiento);
        if (dias < 0) {
            return "VENCIDO (" + Math.abs(dias) + "d)";
        }
        if (dias == 0) {
            return "VENCE HOY";
        }
        if (dias <= 30) {
            return "POR VENCER (" + dias + "d)";
        }
        return "VIGENTE (" + dias + "d)";
    }

    private LocalTime parsearHoraOpcional(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return LocalTime.parse(texto.trim(), HORA_FMT);
    }

    private boolean contiene(String valor, String filtro) {
        return valor != null && valor.toLowerCase(Locale.ROOT).contains(filtro);
    }

    private String nvl(String valor) {
        return valor == null ? "" : valor;
    }

    private void mostrarOk(String mensaje) {
        if (lblMensajeEstado != null) {
            lblMensajeEstado.getStyleClass().removeAll("fc-status-msg-ok", "fc-status-msg-error");
            lblMensajeEstado.getStyleClass().add("fc-status-msg-ok");
            lblMensajeEstado.setText("✅ " + mensaje);
        }
    }

    private void mostrarError(String mensaje) {
        if (lblMensajeEstado != null) {
            lblMensajeEstado.getStyleClass().removeAll("fc-status-msg-ok", "fc-status-msg-error");
            lblMensajeEstado.getStyleClass().add("fc-status-msg-error");
            lblMensajeEstado.setText("⚠️ " + mensaje);
        }
    }

    public TabPane getTabPaneExpediente() {
        return tabPaneExpediente;
    }

    public TableView<Usuario> getTblColaboradores() {
        return tblColaboradores;
    }

    public TableView<DocumentoEmpleado> getTblDocumentos() {
        return tblDocumentos;
    }

    public Label getLblMensajeEstado() {
        return lblMensajeEstado;
    }

    public Button getBtnGuardarPersonales() {
        return btnGuardarPersonales;
    }

    public Button getBtnGuardarLaborales() {
        return btnGuardarLaborales;
    }

    public Button getBtnSubirPdf() {
        return btnSubirPdf;
    }

    public Button getBtnEliminarDoc() {
        return btnEliminarDoc;
    }
}
