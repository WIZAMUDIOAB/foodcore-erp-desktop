package com.foodcore.controller;

import com.foodcore.dao.AuditoriaDAO;
import com.foodcore.dao.impl.AuditoriaDAOImpl;
import com.foodcore.model.RegistroAuditoria;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.UiAsyncExecutor;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;

import java.sql.Connection;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador de Bitácora de Auditoría conforme a SPEC-FOODCORE-v1.3-ENTERPRISE (§5.2).
 */
public class AuditoriaController {

    private static final Logger LOGGER = Logger.getLogger(AuditoriaController.class.getName());
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @FXML private StackPane rootAuditoriaStack;
    @FXML private Button btnActualizarAuditoria;
    @FXML private ComboBox<String> cmbFiltroMozo;
    private final List<RegistroAuditoria> todosEventos = new ArrayList<>();

    @FXML private TableView<RegistroAuditoria> tblAuditoria;
    @FXML private TableColumn<RegistroAuditoria, String> colIdAuditoria;
    @FXML private TableColumn<RegistroAuditoria, String> colFecha;
    @FXML private TableColumn<RegistroAuditoria, String> colUsuario;
    @FXML private TableColumn<RegistroAuditoria, String> colAccion;
    @FXML private TableColumn<RegistroAuditoria, String> colEntidad;
    @FXML private TableColumn<RegistroAuditoria, String> colDatosAnteriores;
    @FXML private TableColumn<RegistroAuditoria, String> colDatosNuevos;

    private final AuditoriaDAO auditoriaDAO = new AuditoriaDAOImpl();
    private final ObservableList<RegistroAuditoria> listaAuditoria = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        AnimationUtils.attachTactileFeedback(btnActualizarAuditoria);

        colIdAuditoria.setCellValueFactory(c -> new SimpleStringProperty("#" + c.getValue().getIdAuditoria()));
        colFecha.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getFecha() != null ? c.getValue().getFecha().format(FMT) : "-"
        ));
        colUsuario.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombreUsuario()));
        colAccion.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getAccion()));
        colEntidad.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEntidadAfectada()));
        colDatosAnteriores.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getDatosAnteriores() != null ? c.getValue().getDatosAnteriores() : "-"
        ));
        colDatosNuevos.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getDatosNuevos() != null ? c.getValue().getDatosNuevos() : "-"
        ));

        // Badges para acciones críticas (anulaciones, descuentos, diferencias)
        colAccion.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Label badge = new Label(item);
                if (item.contains("ANULAR") || item.contains("ELIMINAR")) {
                    badge.getStyleClass().add("badge-pill-danger");
                } else if (item.contains("DESCUENTO") || item.contains("MODIFICAR")) {
                    badge.getStyleClass().add("badge-pill-warning");
                } else {
                    badge.getStyleClass().add("badge-pill-accent");
                }
                setGraphic(badge);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
        });

        tblAuditoria.setItems(listaAuditoria);
        if (cmbFiltroMozo != null) {
            cmbFiltroMozo.valueProperty().addListener((obs, oldVal, newVal) -> aplicarFiltroMozo(newVal));
        }
        cargarEventosAuditoria();
    }

    @FXML
    public void cargarEventosAuditoria() {
        UiAsyncExecutor.ejecutarAsync(
            () -> {
                List<RegistroAuditoria> items = new ArrayList<>();
                try (Connection conn = ConexionBD.getConnection()) {
                    items = auditoriaDAO.listarUltimosEventos(conn, 100);
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "Error cargando auditoria", e);
                }
                return items;
            },
            resultadoFinal -> {
                todosEventos.clear();
                todosEventos.addAll(resultadoFinal);
                actualizarComboMozos();
                aplicarFiltroMozo(cmbFiltroMozo != null ? cmbFiltroMozo.getValue() : null);
            },
            e -> LOGGER.log(Level.FINE, "Error cargando auditoria", e)
        );
    }

    private void actualizarComboMozos() {
        if (cmbFiltroMozo == null) return;
        String seleccionado = cmbFiltroMozo.getValue();
        java.util.Set<String> usuarios = new java.util.TreeSet<>();
        for (RegistroAuditoria reg : todosEventos) {
            if (reg.getNombreUsuario() != null && !reg.getNombreUsuario().isBlank()) {
                usuarios.add(reg.getNombreUsuario());
            }
        }
        ObservableList<String> items = FXCollections.observableArrayList();
        items.add("Todos los Mozos / Operadores");
        items.addAll(usuarios);
        cmbFiltroMozo.setItems(items);
        if (seleccionado != null && items.contains(seleccionado)) {
            cmbFiltroMozo.setValue(seleccionado);
        } else {
            cmbFiltroMozo.getSelectionModel().selectFirst();
        }
    }

    public void aplicarFiltroMozo(String mozoSeleccionado) {
        if (todosEventos.isEmpty() && !listaAuditoria.isEmpty()) {
            todosEventos.addAll(listaAuditoria);
        }
        if (mozoSeleccionado == null || mozoSeleccionado.isBlank() || mozoSeleccionado.startsWith("Todos")) {
            listaAuditoria.setAll(todosEventos);
        } else {
            List<RegistroAuditoria> filtrados = todosEventos.stream()
                .filter(r -> mozoSeleccionado.equalsIgnoreCase(r.getNombreUsuario()))
                .toList();
            listaAuditoria.setAll(filtrados);
        }
    }

    public ObservableList<RegistroAuditoria> getListaAuditoria() {
        return listaAuditoria;
    }
}
