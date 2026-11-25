package com.foodcore.controller;

import com.foodcore.config.AppContainer;
import com.foodcore.controller.dashboard.LifecycleAwareController;
import com.foodcore.model.ReservaMesa;
import com.foodcore.service.ReservasService;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ToastService;
import com.foodcore.util.UiAsyncExecutor;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ReservasController implements LifecycleAwareController {

    private static final Logger LOGGER = Logger.getLogger(ReservasController.class.getName());

    @FXML private StackPane rootReservasStack;
    @FXML private Label lblKpiTotalReservas;
    @FXML private Label lblKpiPersonas;
    @FXML private Label lblKpiMesasComprometidas;
    @FXML private Label lblKpiConfirmadas;

    @FXML private DatePicker dpFecha;
    @FXML private ComboBox<String> cmbFiltroEstado;
    @FXML private Label lblTotalRegistros;

    @FXML private Button btnNuevaReserva;
    @FXML private Button btnWhatsApp;
    @FXML private Button btnSentar;

    @FXML private TableView<ReservaMesa> tblReservas;
    @FXML private TableColumn<ReservaMesa, String> colHora;
    @FXML private TableColumn<ReservaMesa, String> colMesa;
    @FXML private TableColumn<ReservaMesa, String> colCliente;
    @FXML private TableColumn<ReservaMesa, String> colTelefono;
    @FXML private TableColumn<ReservaMesa, String> colPersonas;
    @FXML private TableColumn<ReservaMesa, String> colEstado;
    @FXML private TableColumn<ReservaMesa, String> colNotas;

    @FXML private VBox modalReserva;
    @FXML private TextField txtCliente;
    @FXML private TextField txtTelefono;
    @FXML private TextField txtPersonas;
    @FXML private DatePicker dpModalFecha;
    @FXML private TextField txtModalHora;
    @FXML private ComboBox<String> cmbModalMesa;
    @FXML private ComboBox<String> cmbModalEstado;
    @FXML private TextField txtNotas;

    private final ReservasService reservasService;
    private final ObservableList<ReservaMesa> listaReservas = FXCollections.observableArrayList();
    private final AtomicBoolean vistaActiva = new AtomicBoolean(true);
    private final AtomicLong cargaSeq = new AtomicLong(0L);
    private final AtomicBoolean guardarEnCurso = new AtomicBoolean(false);
    private final AtomicBoolean sentarEnCurso = new AtomicBoolean(false);

    public ReservasController() {
        this(AppContainer.getDefault().resolve(ReservasService.class));
    }

    public ReservasController(ReservasService reservasService) {
        this.reservasService = Objects.requireNonNull(reservasService, "reservasService no puede ser nulo");
    }

    @FXML
    public void initialize() {
        vistaActiva.set(true);
        AnimationUtils.attachTactileFeedback(btnNuevaReserva);
        AnimationUtils.attachTactileFeedback(btnWhatsApp);
        AnimationUtils.attachTactileFeedback(btnSentar);

        dpFecha.setValue(LocalDate.now());
        cmbFiltroEstado.setItems(FXCollections.observableArrayList(
                "TODOS", "CONFIRMADA", "PENDIENTE", "SENTADA", "CANCELADA"));
        cmbFiltroEstado.setValue("TODOS");

        // Opciones del modal
        List<String> mesas = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            mesas.add(String.format("Mesa %02d", i));
        }
        cmbModalMesa.setItems(FXCollections.observableArrayList(mesas));
        cmbModalMesa.setValue("Mesa 01");

        cmbModalEstado.setItems(FXCollections.observableArrayList("CONFIRMADA", "PENDIENTE", "SENTADA", "CANCELADA"));
        cmbModalEstado.setValue("CONFIRMADA");

        dpModalFecha.setValue(LocalDate.now());

        configurarTabla();
        cargarDatos();
    }

    @Override
    public void detenerRecursos() {
        vistaActiva.set(false);
    }

    private void configurarTabla() {
        colHora.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getHoraTexto()));
        colMesa.setCellValueFactory(c -> new SimpleStringProperty("Mesa " + c.getValue().getNumeroMesa()));
        colCliente.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getClienteNombre()));
        colTelefono.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getClienteTelefono()));
        colPersonas.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPersonas() + " pax"));

        colEstado.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEstado()));
        colEstado.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String estado, boolean empty) {
                super.updateItem(estado, empty);
                if (empty || estado == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    setText(estado);
                    if ("CONFIRMADA".equalsIgnoreCase(estado)) {
                        setStyle("-fx-background-color: #ECFDF5; -fx-text-fill: #059669; -fx-alignment: CENTER; -fx-font-weight: bold; -fx-background-radius: 12;");
                    } else if ("PENDIENTE".equalsIgnoreCase(estado)) {
                        setStyle("-fx-background-color: #FFFBEB; -fx-text-fill: #D97706; -fx-alignment: CENTER; -fx-font-weight: bold; -fx-background-radius: 12;");
                    } else if ("SENTADA".equalsIgnoreCase(estado)) {
                        setStyle("-fx-background-color: #F7F5F1; -fx-text-fill: #EA580C; -fx-alignment: CENTER; -fx-font-weight: bold; -fx-background-radius: 12;");
                    } else {
                        setStyle("-fx-background-color: #FEF2F2; -fx-text-fill: #DC2626; -fx-alignment: CENTER; -fx-background-radius: 12;");
                    }
                }
            }
        });

        colNotas.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNotas() != null ? c.getValue().getNotas() : "-"));

        tblReservas.setItems(listaReservas);
    }

    @FXML
    public void handleFiltrar() {
        cargarDatos();
    }

    public void cargarDatos() {
        LocalDate fecha = dpFecha != null && dpFecha.getValue() != null ? dpFecha.getValue() : LocalDate.now();
        long seq = cargaSeq.incrementAndGet();
        UiAsyncExecutor.ejecutarAsync(
            () -> reservasService.listarPorFecha(fecha),
            todas -> {
                if (!vistaActiva.get() || seq != cargaSeq.get()) {
                    return;
                }
                aplicarReservasEnUi(todas);
            },
            ex -> {
                if (!vistaActiva.get()) {
                    return;
                }
                LOGGER.log(Level.WARNING, "Error al cargar reservas", ex);
                mostrarToastSeguro("Error al cargar reservas: " + ex.getMessage(), true);
            }
        );
    }

    private void aplicarReservasEnUi(List<ReservaMesa> todas) {
        List<ReservaMesa> seguras = todas != null ? todas : List.of();
        int total = seguras.size();
        int personas = seguras.stream().mapToInt(ReservaMesa::getPersonas).sum();
        long mesasUnicas = seguras.stream().map(ReservaMesa::getIdMesa).distinct().count();
        long confirmadas = seguras.stream().filter(r -> "CONFIRMADA".equalsIgnoreCase(r.getEstado())).count();
        long pendientes = seguras.stream().filter(r -> "PENDIENTE".equalsIgnoreCase(r.getEstado())).count();

        if (lblKpiTotalReservas != null) {
            lblKpiTotalReservas.setText(String.valueOf(total));
        }
        if (lblKpiPersonas != null) {
            lblKpiPersonas.setText(String.valueOf(personas));
        }
        if (lblKpiMesasComprometidas != null) {
            lblKpiMesasComprometidas.setText(String.valueOf(mesasUnicas));
        }
        if (lblKpiConfirmadas != null) {
            lblKpiConfirmadas.setText(confirmadas + " / " + pendientes);
        }

        String filtroEst = cmbFiltroEstado != null ? cmbFiltroEstado.getValue() : null;
        List<ReservaMesa> filtradas = seguras;
        if (filtroEst != null && !"TODOS".equalsIgnoreCase(filtroEst)) {
            filtradas = seguras.stream()
                    .filter(r -> filtroEst.equalsIgnoreCase(r.getEstado()))
                    .collect(Collectors.toList());
        }

        listaReservas.setAll(filtradas);
        if (lblTotalRegistros != null) {
            lblTotalRegistros.setText(filtradas.size() + " reservas encontradas");
        }
    }

    @FXML
    public void handleAbrirModal() {
        modalReserva.setVisible(true);
        AnimationUtils.animateModalEnter(modalReserva);
    }

    @FXML
    public void handleCerrarModal() {
        modalReserva.setVisible(false);
    }

    @FXML
    public void handleGuardarReserva() {
        String cli = txtCliente != null ? txtCliente.getText() : null;
        String tel = txtTelefono != null ? txtTelefono.getText() : null;
        if (cli == null || cli.trim().isEmpty()) {
            mostrarToastSeguro("Ingrese el nombre del cliente.", true);
            return;
        }
        if (tel == null || tel.trim().isEmpty()) {
            mostrarToastSeguro("Ingrese el teléfono o celular para WhatsApp.", true);
            return;
        }

        int pax;
        try {
            String rawPax = txtPersonas != null && txtPersonas.getText() != null
                    ? txtPersonas.getText().trim() : "";
            pax = rawPax.isEmpty() ? 2 : Integer.parseInt(rawPax);
            if (pax <= 0) {
                mostrarToastSeguro("Ingrese una cantidad de personas mayor a 0.", true);
                return;
            }
        } catch (NumberFormatException ex) {
            LOGGER.log(Level.FINE, "Cantidad de personas inválida en reserva", ex);
            mostrarToastSeguro("Ingrese una cantidad numérica válida de personas.", true);
            return;
        }

        LocalTime hora;
        try {
            String rawHora = txtModalHora != null && txtModalHora.getText() != null
                    ? txtModalHora.getText().trim() : "";
            hora = LocalTime.parse(rawHora, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (DateTimeParseException ex) {
            LOGGER.log(Level.FINE, "Formato de hora inválido en reserva", ex);
            mostrarToastSeguro("Formato de hora inválido. Use HH:mm (ejemplo 20:00).", true);
            return;
        }

        LocalDate fecha = dpModalFecha != null && dpModalFecha.getValue() != null
                ? dpModalFecha.getValue() : LocalDate.now();
        String mesaSel = cmbModalMesa != null && cmbModalMesa.getValue() != null
                ? cmbModalMesa.getValue() : "Mesa 01";
        long idMesa = Long.parseLong(mesaSel.replaceAll("[^0-9]", ""));

        ReservaMesa r = new ReservaMesa();
        r.setIdMesa(idMesa);
        r.setNumeroMesa(String.format("%02d", idMesa));
        r.setClienteNombre(cli.trim());
        r.setClienteTelefono(tel.trim());
        r.setFecha(fecha);
        r.setHora(hora);
        r.setPersonas(pax);
        r.setEstado(cmbModalEstado != null && cmbModalEstado.getValue() != null
                ? cmbModalEstado.getValue() : "CONFIRMADA");
        r.setNotas(txtNotas != null ? txtNotas.getText() : null);

        if (!guardarEnCurso.compareAndSet(false, true)) {
            return;
        }
        if (modalReserva != null) {
            modalReserva.setDisable(true);
        }

        UiAsyncExecutor.ejecutarAsync(
            () -> reservasService.registrarReserva(r),
            idCreado -> {
                guardarEnCurso.set(false);
                if (modalReserva != null) {
                    modalReserva.setDisable(false);
                }
                if (!vistaActiva.get()) {
                    return;
                }
                if (modalReserva != null) {
                    modalReserva.setVisible(false);
                }
                if (txtCliente != null) {
                    txtCliente.clear();
                }
                if (txtTelefono != null) {
                    txtTelefono.clear();
                }
                if (txtNotas != null) {
                    txtNotas.clear();
                }
                mostrarToastSeguro(
                        "✓ Reserva creada para " + r.getClienteNombre() + " (Mesa " + r.getNumeroMesa() + ")",
                        false);
                cargarDatos();
            },
            ex -> {
                guardarEnCurso.set(false);
                if (modalReserva != null) {
                    modalReserva.setDisable(false);
                }
                LOGGER.log(Level.WARNING, "Error al guardar reserva", ex);
                mostrarToastSeguro("Error: " + ex.getMessage(), true);
            }
        );
    }

    @FXML
    public void handleEnviarWhatsApp() {
        ReservaMesa sel = tblReservas != null ? tblReservas.getSelectionModel().getSelectedItem() : null;
        if (sel == null) {
            mostrarToastSeguro("Seleccione una reserva de la tabla para enviar WhatsApp.", true);
            return;
        }

        String url = sel.generarUrlWhatsApp();
        try {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(url);
            clipboard.setContent(content);

            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
            mostrarToastSeguro("✓ Enlace WhatsApp copiado al portapapeles y lanzado al navegador.", false);
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "No se pudo abrir el navegador de escritorio directamente", ex);
            mostrarToastSeguro("Enlace de confirmación copiado: " + url, false);
        }
    }

    @FXML
    public void handleSentarReserva() {
        ReservaMesa sel = tblReservas != null ? tblReservas.getSelectionModel().getSelectedItem() : null;
        if (sel == null) {
            mostrarToastSeguro("Seleccione la reserva a sentar.", true);
            return;
        }
        if (!sentarEnCurso.compareAndSet(false, true)) {
            return;
        }
        int idReserva = sel.getIdReserva();
        String numeroMesa = sel.getNumeroMesa();
        if (btnSentar != null) {
            btnSentar.setDisable(true);
        }

        UiAsyncExecutor.ejecutarAsync(
            () -> reservasService.actualizarEstado(idReserva, "SENTADA"),
            actualizado -> {
                sentarEnCurso.set(false);
                if (btnSentar != null) {
                    btnSentar.setDisable(false);
                }
                if (!vistaActiva.get()) {
                    return;
                }
                if (!Boolean.TRUE.equals(actualizado)) {
                    mostrarToastSeguro("No se pudo actualizar la reserva seleccionada.", true);
                    return;
                }
                mostrarToastSeguro("✓ Comensales de Mesa " + numeroMesa + " sentados en salón.", false);
                cargarDatos();
            },
            ex -> {
                sentarEnCurso.set(false);
                if (btnSentar != null) {
                    btnSentar.setDisable(false);
                }
                LOGGER.log(Level.WARNING, "Error al sentar reserva", ex);
                mostrarToastSeguro("Error al actualizar estado: " + ex.getMessage(), true);
            }
        );
    }

    private void mostrarToastSeguro(String mensaje, boolean esError) {
        if (rootReservasStack != null && rootReservasStack.getScene() != null) {
            AnimationUtils.mostrarToast(rootReservasStack, mensaje, esError);
        } else {
            if (lblTotalRegistros != null) {
                lblTotalRegistros.setText(mensaje);
            }
            ToastService.mostrarToast(
                    null,
                    mensaje,
                    esError ? ToastService.TipoToast.ERROR : ToastService.TipoToast.EXITO);
        }
    }

    public TableView<ReservaMesa> getTblReservas() {
        return tblReservas;
    }

    public Button getBtnSentar() {
        return btnSentar;
    }

    public Label getLblKpiTotalReservas() {
        return lblKpiTotalReservas;
    }

    public Label getLblTotalRegistros() {
        return lblTotalRegistros;
    }

    public ObservableList<ReservaMesa> getListaReservas() {
        return listaReservas;
    }
}
