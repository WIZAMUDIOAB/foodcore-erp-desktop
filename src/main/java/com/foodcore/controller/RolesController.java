package com.foodcore.controller;

import com.foodcore.dao.RolDAO;
import com.foodcore.dao.impl.RolDAOImpl;
import com.foodcore.model.Rol;
import com.foodcore.model.enums.Permiso;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ConexionBD;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador de Gestión de Roles Dinámicos y Matriz de Permisos (§2.1 SPEC-FOODCORE-v1.3-ENTERPRISE).
 */
public class RolesController {

    private static final Logger LOGGER = Logger.getLogger(RolesController.class.getName());

    @FXML private StackPane rootRolesStack;
    @FXML private Button btnNuevoRol;
    @FXML private Button btnActualizarRoles;
    @FXML private Button btnGuardarPermisos;
    @FXML private Label lblRolSeleccionadoTitulo;
    @FXML private Label lblRolDescripcion;

    @FXML private TableView<Rol> tblRoles;
    @FXML private TableColumn<Rol, String> colCodigoRol;
    @FXML private TableColumn<Rol, String> colNombreRol;
    @FXML private TableColumn<Rol, String> colEstadoRol;

    @FXML private VBox boxMatrizPermisos;

    private final RolDAO rolDAO = new RolDAOImpl();
    private final ObservableList<Rol> listaRoles = FXCollections.observableArrayList();
    private final Map<Permiso, CheckBox> mapCheckboxes = new HashMap<>();
    private Rol rolSeleccionadoActual;

    @FXML
    public void initialize() {
        AnimationUtils.attachTactileFeedback(btnNuevoRol);
        AnimationUtils.attachTactileFeedback(btnActualizarRoles);
        AnimationUtils.attachTactileFeedback(btnGuardarPermisos);

        colCodigoRol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCodigo()));
        colNombreRol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colEstadoRol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isEstado() ? "ACTIVO" : "INACTIVO"));

        tblRoles.setItems(listaRoles);
        tblRoles.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                seleccionarRol(newV);
            }
        });

        construirMatrizPermisosVisual();
        cargarRolesSincrono();
    }

    public static String getPermisoLabel(Permiso permiso) {
        if (permiso == null) return "";
        return switch (permiso) {
            case PEDIDO_CREAR -> "Crear Pedido y abrir mesa";
            case PEDIDO_ADICIONAR -> "Adicionar ítems a pedido";
            case PEDIDO_ADICIONAR_POST_ENTREGA -> "Adicionar ítems post-entrega";
            case VENTA_COBRAR -> "Cobrar Venta";
            case VENTA_DESCUENTO -> "Aplicar Descuentos";
            case VENTA_ANULAR -> "Anular Venta";
            case CAJA_APERTURA -> "Aperturar Caja";
            case CAJA_CIERRE -> "Cerrar Turno de Caja (Cierre Z)";
            case CAJA_MOVIMIENTO_MANUAL -> "Movimientos Manuales de Caja";
            case KDS_OPERAR -> "Operar Cocina (KDS)";
            case TICKET_REIMPRIMIR -> "Reimprimir Tickets Fiscales";
            case USUARIO_GESTIONAR -> "Gestionar Usuarios y Roles";
            case INSUMO_GESTIONAR -> "Gestionar Insumos y Recetas";
            case KARDEX_CONSULTAR -> "Consultar Auditoría Kárdex";
            case CONFIG_RED -> "Configuración de Red y Terminales";
        };
    }

    private void construirMatrizPermisosVisual() {
        boxMatrizPermisos.getChildren().clear();
        mapCheckboxes.clear();

        Map<String, List<Permiso>> categorias = new LinkedHashMap<>();
        categorias.put("Operaciones POS y Salón", List.of(
            Permiso.PEDIDO_CREAR, Permiso.PEDIDO_ADICIONAR, Permiso.PEDIDO_ADICIONAR_POST_ENTREGA,
            Permiso.VENTA_COBRAR, Permiso.VENTA_DESCUENTO, Permiso.VENTA_ANULAR, Permiso.TICKET_REIMPRIMIR
        ));
        categorias.put("Caja y Arqueo Fiscal", List.of(
            Permiso.CAJA_APERTURA, Permiso.CAJA_CIERRE, Permiso.CAJA_MOVIMIENTO_MANUAL
        ));
        categorias.put("Cocina (KDS)", List.of(
            Permiso.KDS_OPERAR
        ));
        categorias.put("Almacén, Compras y Kárdex", List.of(
            Permiso.INSUMO_GESTIONAR, Permiso.KARDEX_CONSULTAR
        ));
        categorias.put("Seguridad, Roles y Configuración Enterprise", List.of(
            Permiso.USUARIO_GESTIONAR, Permiso.CONFIG_RED
        ));

        for (Map.Entry<String, List<Permiso>> cat : categorias.entrySet()) {
            VBox catBox = new VBox(8);
            Label lblCat = new Label(cat.getKey().toUpperCase());
            lblCat.getStyleClass().add("sidebar-section-header");
            FlowPane flow = new FlowPane(12, 10);

            for (Permiso p : cat.getValue()) {
                CheckBox chk = new CheckBox(getPermisoLabel(p));
                chk.getStyleClass().add("text-body");
                chk.setWrapText(true);
                chk.setMaxWidth(340);
                chk.setTooltip(new javafx.scene.control.Tooltip(p.getDescripcion()));
                mapCheckboxes.put(p, chk);
                flow.getChildren().add(chk);
            }

            catBox.getChildren().addAll(lblCat, flow);
            boxMatrizPermisos.getChildren().add(catBox);
        }
    }

    public void cargarRolesSincrono() {
        List<Rol> resultado = new ArrayList<>();
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn != null) {
                resultado = rolDAO.listarTodos(conn);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error cargando roles de BD sincrónicamente: " + e.getMessage());
        }

        listaRoles.setAll(resultado);
        if (!listaRoles.isEmpty()) {
            tblRoles.getSelectionModel().selectFirst();
            seleccionarRolSincrono(listaRoles.get(0));
        }
    }

    public void seleccionarRolSincrono(Rol r) {
        if (r == null) return;
        this.rolSeleccionadoActual = r;
        lblRolSeleccionadoTitulo.setText("Permisos del Rol: " + r.getNombre() + " (" + r.getCodigo() + ")");
        lblRolDescripcion.setText(r.getDescripcion() != null ? r.getDescripcion() : "Sin descripción.");

        mapCheckboxes.values().forEach(chk -> chk.setSelected(false));

        Set<Permiso> permisos = EnumSet.noneOf(Permiso.class);
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn != null) {
                permisos = rolDAO.obtenerPermisosPorRol(conn, r.getCodigo());
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error cargando permisos del rol sincrónicamente: " + e.getMessage());
        }

        for (Map.Entry<Permiso, CheckBox> entry : mapCheckboxes.entrySet()) {
            entry.getValue().setSelected(permisos.contains(entry.getKey()));
        }
    }

    public void cargarRolesDesdeBD() {
        new Thread(() -> {
            List<Rol> resultado = new ArrayList<>();
            try (Connection conn = ConexionBD.getConnection()) {
                resultado = rolDAO.listarTodos(conn);
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Error cargando roles", e);
            }

            final List<Rol> roles = resultado;
            Platform.runLater(() -> {
                listaRoles.setAll(roles);
                if (!listaRoles.isEmpty()) {
                    tblRoles.getSelectionModel().selectFirst();
                }
            });
        }).start();
    }

    public void seleccionarRol(Rol r) {
        this.rolSeleccionadoActual = r;
        lblRolSeleccionadoTitulo.setText("Permisos del Rol: " + r.getNombre() + " (" + r.getCodigo() + ")");
        lblRolDescripcion.setText(r.getDescripcion() != null ? r.getDescripcion() : "Sin descripción.");

        // Desmarcar todos primero
        mapCheckboxes.values().forEach(chk -> chk.setSelected(false));

        new Thread(() -> {
            Set<Permiso> permisos = EnumSet.noneOf(Permiso.class);
            try (Connection conn = ConexionBD.getConnection()) {
                permisos = rolDAO.obtenerPermisosPorRol(conn, r.getCodigo());
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Error cargando permisos del rol", e);
            }

            final Set<Permiso> permsFinal = permisos;
            Platform.runLater(() -> {
                for (Map.Entry<Permiso, CheckBox> entry : mapCheckboxes.entrySet()) {
                    entry.getValue().setSelected(permsFinal.contains(entry.getKey()));
                }
            });
        }).start();
    }

    @FXML
    public void handleGuardarPermisos(ActionEvent event) {
        if (rolSeleccionadoActual == null) {
            AnimationUtils.mostrarToast(rootRolesStack, "Seleccione un rol antes de guardar.", true);
            return;
        }

        Set<Permiso> nuevosPermisos = EnumSet.noneOf(Permiso.class);
        for (Map.Entry<Permiso, CheckBox> entry : mapCheckboxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                nuevosPermisos.add(entry.getKey());
            }
        }

        try (Connection conn = ConexionBD.getConnection()) {
            rolDAO.actualizarPermisosRol(conn, rolSeleccionadoActual.getCodigo(), nuevosPermisos);
            AnimationUtils.mostrarToast(rootRolesStack, "Permisos del rol '" + rolSeleccionadoActual.getNombre() + "' guardados exitosamente en BD.", false);
        } catch (Exception e) {
            AnimationUtils.mostrarToast(rootRolesStack, "Error al guardar permisos: " + e.getMessage(), true);
        }
    }

    @FXML
    public void handleAbrirModalNuevoRol(ActionEvent event) {
        StackPane backdrop = new StackPane();
        backdrop.getStyleClass().add("modal-backdrop");
        VBox card = new VBox(12);
        card.getStyleClass().add("modal-dialog-surface");
        card.setMaxWidth(440);

        Label lblTitle = new Label("Crear Nuevo Rol Dinámico");
        lblTitle.getStyleClass().add("text-h2");

        TextField txtCod = new TextField(); txtCod.setPromptText("Código Rol (Ej. SUPERVISOR, DELIVERY_MGR)"); txtCod.getStyleClass().add("input-field");
        TextField txtNom = new TextField(); txtNom.setPromptText("Nombre Legible (Ej. Supervisor de Salón)"); txtNom.getStyleClass().add("input-field");
        TextField txtDesc = new TextField(); txtDesc.setPromptText("Descripción de Funciones"); txtDesc.getStyleClass().add("input-field");

        Button btnCancelar = new Button("Cancelar");
        btnCancelar.getStyleClass().add("btn-secondary");
        btnCancelar.setOnAction(ev -> rootRolesStack.getChildren().remove(backdrop));

        Button btnGuardar = new Button("Crear Rol");
        btnGuardar.getStyleClass().add("btn-primary");
        btnGuardar.setOnAction(ev -> {
            String cod = txtCod.getText() != null ? txtCod.getText().trim().toUpperCase() : "";
            String nom = txtNom.getText() != null ? txtNom.getText().trim() : "";
            String desc = txtDesc.getText() != null ? txtDesc.getText().trim() : "";

            if (cod.isBlank() || nom.isBlank()) {
                AnimationUtils.mostrarToast(rootRolesStack, "El código y el nombre del rol son obligatorios.", true);
                return;
            }

            try (Connection conn = ConexionBD.getConnection()) {
                rolDAO.crearRol(conn, cod, nom, desc);
                rootRolesStack.getChildren().remove(backdrop);
                AnimationUtils.mostrarToast(rootRolesStack, "Rol '" + nom + "' creado con éxito.", false);
                cargarRolesDesdeBD();
            } catch (Exception ex) {
                AnimationUtils.mostrarToast(rootRolesStack, "Error al crear rol: " + ex.getMessage(), true);
            }
        });

        HBox actions = new HBox(12, btnCancelar, btnGuardar);
        actions.setAlignment(Pos.CENTER_RIGHT);

        card.getChildren().addAll(lblTitle, txtCod, txtNom, txtDesc, actions);
        backdrop.getChildren().add(card);
        AnimationUtils.animateModalEnter(card);
        rootRolesStack.getChildren().add(backdrop);
    }

    public ObservableList<Rol> getListaRoles() {
        return listaRoles;
    }

    public Map<Permiso, CheckBox> getMapCheckboxes() {
        return mapCheckboxes;
    }
}
