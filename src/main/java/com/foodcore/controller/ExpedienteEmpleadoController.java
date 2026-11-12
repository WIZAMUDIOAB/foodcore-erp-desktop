package com.foodcore.controller;

import com.foodcore.model.DocumentoEmpleado;
import com.foodcore.model.Usuario;
import com.foodcore.service.ExpedienteEmpleadoService;
import com.foodcore.service.impl.ExpedienteEmpleadoServiceImpl;
import com.foodcore.util.ExportFileChooserHelper;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Controlador JavaFX para gestión administrativa de expedientes y legajos PDF de colaboradores.
 */
public class ExpedienteEmpleadoController {

    private static final DateTimeFormatter FECHA_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

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

    private Usuario colaboradorActual;

    public ExpedienteEmpleadoController() {
        this(new ExpedienteEmpleadoServiceImpl());
    }

    public ExpedienteEmpleadoController(ExpedienteEmpleadoService expedienteService) {
        this.expedienteService = expedienteService;
    }

    @FXML
    public void initialize() {
        configurarCombos();
        configurarTablas();
        cargarExpedientes(null);
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
        try {
            List<Usuario> lista = expedienteService.listarExpedientes(true);
            todosColaboradores.setAll(lista);
            lblKpiTotalPersonal.setText(String.valueOf(lista.size()));

            List<DocumentoEmpleado> porVencer = expedienteService.listarDocumentosProximosAVencer(30);
            lblKpiDocsPorVencer.setText(String.valueOf(porVencer.size()));

            aplicarFiltroColaboradores();
            seleccionarColaborador(idPreferido);
        } catch (Exception e) {
            mostrarError("No se pudieron cargar los expedientes: " + e.getMessage());
        }
    }

    private void aplicarFiltroColaboradores() {
        String filtro = txtBuscarColaborador.getText() == null
                ? ""
                : txtBuscarColaborador.getText().trim().toLowerCase(Locale.ROOT);
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
            lblKpiDocsEmpleado.setText("0");
            lblEmpleadoSeleccionado.setText("Sin colaboradores disponibles");
            lblRolSeleccionado.setText("-");
            return;
        }
        Usuario objetivo = colaboradoresFiltrados.get(0);
        if (idPreferido != null) {
            objetivo = colaboradoresFiltrados.stream()
                    .filter(u -> u.getIdUsuario() == idPreferido)
                    .findFirst()
                    .orElse(objetivo);
        }
        tblColaboradores.getSelectionModel().select(objetivo);
        mostrarDetalleColaborador(objetivo);
    }

    private void mostrarDetalleColaborador(Usuario usuario) {
        this.colaboradorActual = usuario;
        String nombre = usuario.getNombreEmpleado() != null && !usuario.getNombreEmpleado().isBlank()
                ? usuario.getNombreEmpleado()
                : usuario.getUsername();
        lblEmpleadoSeleccionado.setText(nombre);
        lblRolSeleccionado.setText(Optional.ofNullable(usuario.getNombreRol()).orElse("PERSONAL"));

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
        txtSueldoBase.setText(String.format(Locale.US, "%.2f", usuario.getSueldoBase()));
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
        List<DocumentoEmpleado> docs = expedienteService.listarDocumentos(idUsuario);
        documentosActuales.setAll(docs);
        lblKpiDocsEmpleado.setText(String.valueOf(docs.size()));
    }

    @FXML
    public void handleGuardarDatosPersonales() {
        if (colaboradorActual == null) {
            mostrarError("Seleccione primero un colaborador.");
            return;
        }
        try {
            colaboradorActual.setTipoDocumento(cbTipoDocumento.getValue());
            colaboradorActual.setDni(txtDni.getText());
            colaboradorActual.setNombreEmpleado(txtNombreCompleto.getText());
            colaboradorActual.setFechaNacimiento(dpFechaNacimiento.getValue());
            colaboradorActual.setTelefono(txtTelefono.getText());
            colaboradorActual.setDireccion(txtDireccion.getText());
            colaboradorActual.setContactoEmergencia(txtContactoEmergencia.getText());
            colaboradorActual.setTelefonoEmergencia(txtTelefonoEmergencia.getText());

            expedienteService.guardarDatosPersonales(colaboradorActual);
            int id = colaboradorActual.getIdUsuario();
            cargarExpedientes(id);
            mostrarOk("Datos personales guardados correctamente.");
        } catch (Exception e) {
            mostrarError(e.getMessage());
        }
    }

    @FXML
    public void handleGuardarDatosLaborales() {
        if (colaboradorActual == null) {
            mostrarError("Seleccione primero un colaborador.");
            return;
        }
        try {
            double sueldo = Double.parseDouble(txtSueldoBase.getText().trim().replace(',', '.'));
            LocalTime entrada = parsearHoraOpcional(txtHoraEntrada.getText());
            LocalTime salida = parsearHoraOpcional(txtHoraSalida.getText());

            colaboradorActual.setCargo(txtCargo.getText());
            colaboradorActual.setTipoContrato(cbTipoContrato.getValue());
            colaboradorActual.setFechaIngreso(dpFechaIngreso.getValue());
            colaboradorActual.setSueldoBase(sueldo);
            colaboradorActual.setHorarioLaboral(txtHorarioLaboral.getText());
            colaboradorActual.setHoraEntradaProgramada(entrada);
            colaboradorActual.setHoraSalidaProgramada(salida);
            colaboradorActual.setFechaVencimientoContrato(dpVencContrato.getValue());
            colaboradorActual.setFechaVencimientoCarnetSanidad(dpVencCarnet.getValue());

            expedienteService.guardarDatosLaborales(colaboradorActual);
            int id = colaboradorActual.getIdUsuario();
            cargarExpedientes(id);
            mostrarOk("Datos laborales actualizados correctamente.");
        } catch (NumberFormatException e) {
            mostrarError("Ingrese un monto numérico válido para el Sueldo Base.");
        } catch (DateTimeParseException e) {
            mostrarError("Formato de hora inválido. Use HH:mm (ejemplo 09:00).");
        } catch (Exception e) {
            mostrarError(e.getMessage());
        }
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
        try {
            DocumentoEmpleado guardado = expedienteService.subirDocumentoPdf(
                    colaboradorActual.getIdUsuario(),
                    cbTipoDocPdf.getValue(),
                    seleccionado.get(),
                    dpDocEmision.getValue(),
                    dpDocVencimiento.getValue(),
                    txtDocObservaciones.getText());
            txtDocObservaciones.clear();
            cargarExpedientes(colaboradorActual.getIdUsuario());
            mostrarOk("PDF adjuntado correctamente: " + guardado.getNombreArchivo());
        } catch (Exception e) {
            mostrarError(e.getMessage());
        }
    }

    @FXML
    public void handleEliminarDocumento() {
        DocumentoEmpleado doc = tblDocumentos.getSelectionModel().getSelectedItem();
        if (doc == null) {
            mostrarError("Seleccione un documento de la tabla para eliminarlo.");
            return;
        }
        try {
            expedienteService.eliminarDocumento(doc.getIdDocumento());
            if (colaboradorActual != null) {
                cargarExpedientes(colaboradorActual.getIdUsuario());
            }
            mostrarOk("Documento eliminado del expediente.");
        } catch (Exception e) {
            mostrarError(e.getMessage());
        }
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
        lblMensajeEstado.setStyle("-fx-text-fill: #15803d; -fx-font-weight: bold;");
        lblMensajeEstado.setText("✅ " + mensaje);
    }

    private void mostrarError(String mensaje) {
        lblMensajeEstado.setStyle("-fx-text-fill: #b91c1c; -fx-font-weight: bold;");
        lblMensajeEstado.setText("⚠️ " + mensaje);
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
}
