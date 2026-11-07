package com.foodcore.controller;

import com.foodcore.model.ReservaMesa;
import com.foodcore.service.ReservasService;
import com.foodcore.util.AnimationUtils;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
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
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ReservasController {

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

    private final ReservasService reservasService = new ReservasService();
    private final ObservableList<ReservaMesa> listaReservas = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        AnimationUtils.attachTactileFeedback(btnNuevaReserva);
        AnimationUtils.attachTactileFeedback(btnWhatsApp);
        AnimationUtils.attachTactileFeedback(btnSentar);

        dpFecha.setValue(LocalDate.now());
        cmbFiltroEstado.setItems(FXCollections.observableArrayList("TODOS", "CONFIRMADA", "PENDIENTE", "SENTADA", "CANCELADA"));
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
                        setStyle("-fx-background-color: #E8F6EE; -fx-text-fill: #1F9D55; -fx-alignment: CENTER; -fx-font-weight: bold; -fx-background-radius: 12;");
                    } else if ("PENDIENTE".equalsIgnoreCase(estado)) {
                        setStyle("-fx-background-color: #FEF3C7; -fx-text-fill: #D97706; -fx-alignment: CENTER; -fx-font-weight: bold; -fx-background-radius: 12;");
                    } else if ("SENTADA".equalsIgnoreCase(estado)) {
                        setStyle("-fx-background-color: #EDE9FE; -fx-text-fill: #6D28D9; -fx-alignment: CENTER; -fx-font-weight: bold; -fx-background-radius: 12;");
                    } else {
                        setStyle("-fx-background-color: #FEE2E2; -fx-text-fill: #DC2626; -fx-alignment: CENTER; -fx-background-radius: 12;");
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
        LocalDate f = dpFecha.getValue() != null ? dpFecha.getValue() : LocalDate.now();
        try {
            List<ReservaMesa> todas = reservasService.listarPorFecha(f);

            // KPIs
            int total = todas.size();
            int personas = todas.stream().mapToInt(ReservaMesa::getPersonas).sum();
            long mesasUnicas = todas.stream().map(ReservaMesa::getIdMesa).distinct().count();
            long confirmadas = todas.stream().filter(r -> "CONFIRMADA".equalsIgnoreCase(r.getEstado())).count();
            long pendientes = todas.stream().filter(r -> "PENDIENTE".equalsIgnoreCase(r.getEstado())).count();

            lblKpiTotalReservas.setText(String.valueOf(total));
            lblKpiPersonas.setText(String.valueOf(personas));
            lblKpiMesasComprometidas.setText(String.valueOf(mesasUnicas));
            lblKpiConfirmadas.setText(confirmadas + " / " + pendientes);

            // Filtro por Estado
            String filtroEst = cmbFiltroEstado.getValue();
            List<ReservaMesa> filtradas = todas;
            if (filtroEst != null && !"TODOS".equalsIgnoreCase(filtroEst)) {
                filtradas = todas.stream()
                        .filter(r -> filtroEst.equalsIgnoreCase(r.getEstado()))
                        .collect(Collectors.toList());
            }

            listaReservas.setAll(filtradas);
            lblTotalRegistros.setText(filtradas.size() + " reservas encontradas");
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error al cargar reservas", ex);
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
        String cli = txtCliente.getText();
        String tel = txtTelefono.getText();
        if (cli == null || cli.trim().isEmpty()) {
            AnimationUtils.mostrarToast(rootReservasStack, "Ingrese el nombre del cliente.", true);
            return;
        }
        if (tel == null || tel.trim().isEmpty()) {
            AnimationUtils.mostrarToast(rootReservasStack, "Ingrese el teléfono o celular para WhatsApp.", true);
            return;
        }

        try {
            int pax = 2;
            try {
                pax = Integer.parseInt(txtPersonas.getText().trim());
            } catch (Exception ignored) {
            }

            LocalDate fecha = dpModalFecha.getValue() != null ? dpModalFecha.getValue() : LocalDate.now();
            LocalTime hora = LocalTime.parse(txtModalHora.getText().trim(), DateTimeFormatter.ofPattern("HH:mm"));

            String mesaSel = cmbModalMesa.getValue();
            long idMesa = Long.parseLong(mesaSel.replaceAll("[^0-9]", ""));

            ReservaMesa r = new ReservaMesa();
            r.setIdMesa(idMesa);
            r.setNumeroMesa(String.format("%02d", idMesa));
            r.setClienteNombre(cli.trim());
            r.setClienteTelefono(tel.trim());
            r.setFecha(fecha);
            r.setHora(hora);
            r.setPersonas(pax);
            r.setEstado(cmbModalEstado.getValue() != null ? cmbModalEstado.getValue() : "CONFIRMADA");
            r.setNotas(txtNotas.getText());

            reservasService.registrarReserva(r);
            modalReserva.setVisible(false);

            // Limpiar inputs
            txtCliente.clear();
            txtTelefono.clear();
            txtNotas.clear();

            AnimationUtils.mostrarToast(rootReservasStack, "✓ Reserva creada para " + cli + " (Mesa " + r.getNumeroMesa() + ")", false);
            cargarDatos();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error al guardar reserva", ex);
            AnimationUtils.mostrarToast(rootReservasStack, "Error: " + ex.getMessage(), true);
        }
    }

    @FXML
    public void handleEnviarWhatsApp() {
        ReservaMesa sel = tblReservas.getSelectionModel().getSelectedItem();
        if (sel == null) {
            AnimationUtils.mostrarToast(rootReservasStack, "Seleccione una reserva de la tabla para enviar WhatsApp.", true);
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
            AnimationUtils.mostrarToast(rootReservasStack, "✓ Enlace WhatsApp copiado al portapapeles y lanzado al navegador.", false);
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "No se pudo abrir el navegador de escritorio directamente", ex);
            AnimationUtils.mostrarToast(rootReservasStack, "Enlace de confirmación copiado: " + url, false);
        }
    }

    @FXML
    public void handleSentarReserva() {
        ReservaMesa sel = tblReservas.getSelectionModel().getSelectedItem();
        if (sel == null) {
            AnimationUtils.mostrarToast(rootReservasStack, "Seleccione la reserva a sentar.", true);
            return;
        }

        try {
            reservasService.actualizarEstado(sel.getIdReserva(), "SENTADA");
            AnimationUtils.mostrarToast(rootReservasStack, "✓ Comensales de Mesa " + sel.getNumeroMesa() + " sentados en salón.", false);
            cargarDatos();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error al sentar reserva", ex);
            AnimationUtils.mostrarToast(rootReservasStack, "Error al actualizar estado: " + ex.getMessage(), true);
        }
    }
}
