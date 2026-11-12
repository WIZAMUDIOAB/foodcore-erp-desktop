package com.foodcore.controller;

import com.foodcore.App;
import com.foodcore.model.Usuario;
import com.foodcore.offline.SyncWorker;
import com.foodcore.service.AuthService;
import com.foodcore.util.AnimationUtils;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador principal de {@code MainLayout.fxml} / {@code MainDashboardView.fxml} según SPEC-FOODCORE-v1.2 (§5):
 * Sidebar lateral colapsable y control de acceso basado en roles (RBAC):
 * <ul>
 *   <li>{@code ADMIN}: Acceso total (Dashboard Gerencial como vista inicial, POS, KDS, Salón, Caja y Backoffice).</li>
 *   <li>{@code CAJERO}: POS, Salón, Caja, Mi Portal y Solicitudes (sin acceso a Inventario/Recetas/Backoffice).</li>
 *   <li>{@code MOZO}: Salón, POS, Mi Portal y Solicitudes (solo toma de pedidos).</li>
 *   <li>{@code COCINERO}: KDS, Mi Portal y Solicitudes.</li>
 * </ul>
 */
public class MainDashboardController {

    private static final Logger LOGGER = Logger.getLogger(MainDashboardController.class.getName());

    @FXML private StackPane rootDashboardStack;
    @FXML private BorderPane mainBorderPane;
    @FXML private VBox sidebarBox;
    @FXML private VBox boxBrandHeader;
    @FXML private Button btnToggleSidebar;
    @FXML private javafx.scene.control.TextField txtBuscarSidebar;
    @FXML private StackPane contentArea;
    @FXML private Label lblUsuarioLogueado;
    @FXML private Region dotNetworkStatus;
    @FXML private Label lblNetworkStatus;

    @FXML private Label lblSecOperaciones;
    @FXML private Label lblSecFinanzas;
    @FXML private Label lblSecRrhh;
    @FXML private Label lblSecBackoffice;
    @FXML private Label lblSecSistema;

    @FXML private Button btnNavDashboard;
    @FXML private Button btnNavPromociones;
    @FXML private Button btnNavKiosco;
    @FXML private Button btnNavMenuQr;
    @FXML private Button btnNavSalon;
    @FXML private Button btnNavReservas;
    @FXML private Button btnNavDelivery;
    @FXML private Button btnNavPOS;
    @FXML private Button btnNavKDS;
    @FXML private Button btnNavCaja;

    @FXML private Button btnNavReportesContables;
    @FXML private Button btnNavProveedores;
    @FXML private Button btnNavClientes;
    @FXML private Button btnNavHistorialPrecios;
    @FXML private Button btnNavReportesAvanzados;

    @FXML private Button btnNavAsistencia;
    @FXML private Button btnNavReloj;
    @FXML private Button btnNavHorarios;
    @FXML private Button btnNavMiHorario;
    @FXML private Button btnNavSolicitudes;
    @FXML private Button btnNavExpedientes;
    @FXML private Button btnNavPlanilla;

    @FXML private Button btnNavInventario;
    @FXML private Button btnNavMenu;
    @FXML private Button btnNavRecetas;
    @FXML private Button btnNavMermas;
    @FXML private Button btnNavAnalisisMermas;
    @FXML private Button btnNavKardex;
    @FXML private Button btnNavAuditoria;
    @FXML private Button btnNavUsuarios;
    @FXML private Button btnNavRoles;
    @FXML private Button btnNavConfiguracion;

    @FXML private Button btnNavReportes;
    @FXML private Button btnNavMantenimiento;

    @FXML private Button btnCambiarPasswordMenu;
    @FXML private Button btnCerrarSesion;

    private Parent dialogoPasswordActivo;
    private boolean sidebarColapsado = false;
    private Object controladorActivo;

    @FXML
    public void initialize() {
        Button[] buttons = {
            btnToggleSidebar, btnNavDashboard, btnNavSalon, btnNavReservas, btnNavDelivery, btnNavPOS, btnNavKDS, btnNavCaja,
            btnNavReportesContables, btnNavProveedores, btnNavClientes, btnNavHistorialPrecios, btnNavReportesAvanzados,
            btnNavAsistencia, btnNavReloj, btnNavHorarios, btnNavMiHorario, btnNavSolicitudes, btnNavExpedientes, btnNavPlanilla,
            btnNavInventario, btnNavMenu, btnNavRecetas, btnNavMermas, btnNavAnalisisMermas, btnNavKardex, btnNavAuditoria,
            btnNavUsuarios, btnNavRoles, btnNavConfiguracion, btnNavReportes, btnNavMantenimiento,
            btnCambiarPasswordMenu, btnCerrarSesion
        };
        for (Button btn : buttons) {
            AnimationUtils.attachTactileFeedback(btn);
        }

        configurarSyncWorkerListener();

        Usuario act = AuthService.getUsuarioSesionActual();
        if (act != null) {
            lblUsuarioLogueado.setText(act.getUsername() + " · " + act.getNombreRol());
            aplicarControlAcceso(act);
        } else {
            cargarVista("/com/foodcore/view/DashboardView.fxml", btnNavDashboard);
        }
    }

    private void configurarSyncWorkerListener() {
        SyncWorker syncWorker = SyncWorker.getInstancia();
        syncWorker.addListener((online, pendientes, alerta) -> Platform.runLater(() -> {
            if (lblNetworkStatus == null || dotNetworkStatus == null) {
                return;
            }
            if (online) {
                dotNetworkStatus.getStyleClass().removeAll("status-dot-danger", "status-dot-warning");
                if (!dotNetworkStatus.getStyleClass().contains("status-dot-online")) {
                    dotNetworkStatus.getStyleClass().add("status-dot-online");
                }
                lblNetworkStatus.getStyleClass().removeAll("badge-pill-danger", "badge-pill-warning");
                if (!lblNetworkStatus.getStyleClass().contains("badge-pill-success")) {
                    lblNetworkStatus.getStyleClass().add("badge-pill-success");
                }
                lblNetworkStatus.setText(pendientes > 0 ? "ONLINE (" + pendientes + ")" : "ONLINE");
            } else {
                dotNetworkStatus.getStyleClass().removeAll("status-dot-online", "status-dot-warning");
                if (!dotNetworkStatus.getStyleClass().contains("status-dot-danger")) {
                    dotNetworkStatus.getStyleClass().add("status-dot-danger");
                }
                lblNetworkStatus.getStyleClass().removeAll("badge-pill-success", "badge-pill-warning");
                if (!lblNetworkStatus.getStyleClass().contains("badge-pill-danger")) {
                    lblNetworkStatus.getStyleClass().add("badge-pill-danger");
                }
                lblNetworkStatus.setText(pendientes > 0 ? "OFFLINE (" + pendientes + " pend)" : "OFFLINE");
            }
        }));
    }

    /**
     * Aplica las reglas RBAC del Sidebar según SPEC-FOODCORE-v1.2, v1.3 (§5) y v4.4.0.
     */
    public void aplicarControlAcceso(Usuario user) {
        mostrarTodosLosNodos();

        if (user == null || user.getNombreRol() == null) {
            cargarVista("/com/foodcore/view/DashboardView.fxml", btnNavDashboard);
            return;
        }

        String rol = user.getNombreRol().toUpperCase();

        if (rol.contains("MOZO") || rol.contains("MESERO")) {
            ocultarNodo(btnNavDashboard);
            ocultarNodo(btnNavKDS);
            ocultarNodo(btnNavCaja);
            ocultarSeccionBackofficeCompleta();
            cargarVista("/com/foodcore/view/SalonMesasView.fxml", btnNavSalon);
        } else if (rol.contains("COCIN")) {
            ocultarNodo(btnNavDashboard);
            ocultarNodo(btnNavSalon);
            ocultarNodo(btnNavDelivery);
            ocultarNodo(btnNavPOS);
            ocultarNodo(btnNavCaja);
            ocultarSeccionBackofficeCompleta();
            cargarVista("/com/foodcore/view/KitchenDisplayView.fxml", btnNavKDS);
        } else if (rol.contains("CAJERO")) {
            ocultarNodo(btnNavDashboard);
            ocultarNodo(btnNavKDS);
            ocultarSeccionBackofficeCompleta();
            cargarVista("/com/foodcore/view/VentaPOSView.fxml", btnNavPOS);
        } else {
            cargarVista("/com/foodcore/view/DashboardView.fxml", btnNavDashboard);
        }
    }

    private void ocultarSeccionBackofficeCompleta() {
        ocultarNodo(lblSecFinanzas);
        ocultarNodo(lblSecBackoffice);
        ocultarNodo(lblSecSistema);
        ocultarNodo(btnNavReportesContables);
        ocultarNodo(btnNavProveedores);
        ocultarNodo(btnNavClientes);
        ocultarNodo(btnNavHistorialPrecios);
        ocultarNodo(btnNavReportesAvanzados);
        ocultarNodo(btnNavAsistencia);
        ocultarNodo(btnNavHorarios);
        ocultarNodo(btnNavExpedientes);
        ocultarNodo(btnNavPlanilla);
        ocultarNodo(btnNavInventario);
        ocultarNodo(btnNavMenu);
        ocultarNodo(btnNavRecetas);
        ocultarNodo(btnNavMermas);
        ocultarNodo(btnNavAnalisisMermas);
        ocultarNodo(btnNavKardex);
        ocultarNodo(btnNavAuditoria);
        ocultarNodo(btnNavUsuarios);
        ocultarNodo(btnNavRoles);
        ocultarNodo(btnNavConfiguracion);
        ocultarNodo(btnNavReportes);
        ocultarNodo(btnNavMantenimiento);
    }

    private void mostrarTodosLosNodos() {
        Node[] nodes = {
            lblSecOperaciones, lblSecFinanzas, lblSecRrhh, lblSecBackoffice, lblSecSistema,
            btnNavDashboard, btnNavSalon, btnNavReservas, btnNavDelivery, btnNavPOS, btnNavKDS, btnNavCaja,
            btnNavReportesContables, btnNavProveedores, btnNavClientes, btnNavHistorialPrecios, btnNavReportesAvanzados,
            btnNavAsistencia, btnNavReloj, btnNavHorarios, btnNavMiHorario, btnNavSolicitudes, btnNavExpedientes, btnNavPlanilla,
            btnNavInventario, btnNavMenu, btnNavRecetas, btnNavMermas, btnNavAnalisisMermas,
            btnNavKardex, btnNavAuditoria, btnNavUsuarios, btnNavRoles, btnNavConfiguracion
        };
        for (Node n : nodes) {
            if (n != null) {
                n.setVisible(true);
                n.setManaged(true);
            }
        }
    }

    private void ocultarNodo(Node n) {
        if (n != null) {
            n.setVisible(false);
            n.setManaged(false);
        }
    }

    /**
     * Colapsa o expande el Sidebar lateral (§5).
     */
    @FXML
    public void handleToggleSidebar(ActionEvent event) {
        sidebarColapsado = !sidebarColapsado;
        if (sidebarBox != null) {
            sidebarBox.setPrefWidth(sidebarColapsado ? 84.0 : 260.0);
        }
        if (boxBrandHeader != null) {
            boxBrandHeader.setVisible(!sidebarColapsado);
            boxBrandHeader.setManaged(!sidebarColapsado);
        }
        if (txtBuscarSidebar != null) {
            txtBuscarSidebar.setVisible(!sidebarColapsado);
            txtBuscarSidebar.setManaged(!sidebarColapsado);
        }
        if (btnToggleSidebar != null) {
            btnToggleSidebar.setText(sidebarColapsado ? "»" : "«");
        }
    }

    @FXML
    public void handleNavDashboard(ActionEvent event) {
        cargarVista("/com/foodcore/view/DashboardView.fxml", btnNavDashboard);
    }

    @FXML
    public void handleNavSalon(ActionEvent event) {
        cargarVista("/com/foodcore/view/SalonMesasView.fxml", btnNavSalon);
    }

    @FXML
    public void handleNavDelivery(ActionEvent event) {
        cargarVista("/com/foodcore/view/DeliveryView.fxml", btnNavDelivery);
    }

    @FXML
    public void handleNavPOS(ActionEvent event) {
        cargarVista("/com/foodcore/view/VentaPOSView.fxml", btnNavPOS);
    }

    @FXML
    public void handleNavKDS(ActionEvent event) {
        cargarVista("/com/foodcore/view/KitchenDisplayView.fxml", btnNavKDS);
    }

    @FXML
    public void handleNavCaja(ActionEvent event) {
        cargarVista("/com/foodcore/view/CajaView.fxml", btnNavCaja);
    }

    @FXML
    public void handleNavInventario(ActionEvent event) {
        cargarVista("/com/foodcore/view/InventarioView.fxml", btnNavInventario);
    }

    @FXML
    public void handleNavMenu(ActionEvent event) {
        cargarVista("/com/foodcore/view/MenuView.fxml", btnNavMenu);
    }

    @FXML
    public void handleNavRecetas(ActionEvent event) {
        cargarVista("/com/foodcore/view/RecetasView.fxml", btnNavRecetas);
    }

    @FXML
    public void handleNavMermas(ActionEvent event) {
        cargarVista("/com/foodcore/view/MermasView.fxml", btnNavMermas);
    }

    @FXML
    public void handleNavKardex(ActionEvent event) {
        cargarVista("/com/foodcore/view/KardexView.fxml", btnNavKardex);
    }

    @FXML
    public void handleNavAuditoria(ActionEvent event) {
        cargarVista("/com/foodcore/view/AuditoriaView.fxml", btnNavAuditoria);
    }

    @FXML
    public void handleNavUsuarios(ActionEvent event) {
        cargarVista("/com/foodcore/view/UsuariosView.fxml", btnNavUsuarios);
    }

    @FXML
    public void handleNavRoles(ActionEvent event) {
        cargarVista("/com/foodcore/view/RolesView.fxml", btnNavRoles);
    }

    @FXML
    public void handleNavConfiguracion(ActionEvent event) {
        cargarVista("/com/foodcore/view/ConfiguracionView.fxml", btnNavConfiguracion);
    }

    @FXML
    public void handleNavReportesContables(ActionEvent event) {
        cargarVista("/com/foodcore/view/ReportesContablesView.fxml", btnNavReportesContables);
    }

    @FXML
    public void handleNavProveedores(ActionEvent event) {
        cargarVista("/com/foodcore/view/ProveedoresView.fxml", btnNavProveedores);
    }

    @FXML
    public void handleNavClientes(ActionEvent event) {
        cargarVista("/com/foodcore/view/ClientesView.fxml", btnNavClientes);
    }

    @FXML
    public void handleNavHistorialPrecios(ActionEvent event) {
        cargarVista("/com/foodcore/view/HistorialPreciosView.fxml", btnNavHistorialPrecios);
    }

    @FXML
    public void handleNavReportesAvanzados(ActionEvent event) {
        cargarVista("/com/foodcore/view/ReportesAvanzadosView.fxml", btnNavReportesAvanzados);
    }

    @FXML
    public void handleNavAsistencia(ActionEvent event) {
        cargarVista("/com/foodcore/view/AsistenciaView.fxml", btnNavAsistencia);
    }

    @FXML
    public void handleNavReloj(ActionEvent event) {
        cargarVista("/com/foodcore/view/RelojAsistenciaView.fxml", btnNavReloj);
    }

    @FXML
    public void handleNavHorarios(ActionEvent event) {
        cargarVista("/com/foodcore/view/HorariosView.fxml", btnNavHorarios);
    }

    @FXML
    public void handleNavMiHorario(ActionEvent event) {
        cargarVista("/com/foodcore/view/MiHorarioView.fxml", btnNavMiHorario);
    }

    @FXML
    public void handleNavSolicitudes(ActionEvent event) {
        cargarVista("/com/foodcore/view/SolicitudesView.fxml", btnNavSolicitudes);
    }

    @FXML
    public void handleNavExpedientes(ActionEvent event) {
        cargarVista("/com/foodcore/view/ExpedienteEmpleadoView.fxml", btnNavExpedientes);
    }

    @FXML
    public void handleNavPlanilla(ActionEvent event) {
        cargarVista("/com/foodcore/view/PlanillaView.fxml", btnNavPlanilla);
    }

    @FXML
    public void handleNavReportes(ActionEvent event) {
        cargarVista("/com/foodcore/view/DashboardView.fxml", btnNavDashboard);
    }

    @FXML
    public void handleNavMantenimiento(ActionEvent event) {
        cargarVista("/com/foodcore/view/MantenimientoBDView.fxml", btnNavMantenimiento);
    }

    @FXML
    public void handleAbrirCambioPassword(ActionEvent event) {
        Usuario act = AuthService.getUsuarioSesionActual();
        if (act == null || rootDashboardStack == null) {
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/PasswordChangeDialog.fxml"));
            Parent dialogRoot = loader.load();
            PasswordChangeDialogController ctrl = loader.getController();
            this.dialogoPasswordActivo = dialogRoot;
            if (mainBorderPane != null) {
                mainBorderPane.setDisable(true);
            }
            ctrl.initContext(
                act,
                () -> {
                    cerrarModalPassword();
                    AnimationUtils.mostrarToast(rootDashboardStack, "Contraseña actualizada correctamente.", false);
                },
                this::cerrarModalPassword
            );
            rootDashboardStack.getChildren().add(dialogRoot);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "No se pudo abrir PasswordChangeDialog desde el dashboard", e);
        }
    }

    private void cerrarModalPassword() {
        if (dialogoPasswordActivo != null && rootDashboardStack != null) {
            rootDashboardStack.getChildren().remove(dialogoPasswordActivo);
        }
        dialogoPasswordActivo = null;
        if (mainBorderPane != null) {
            mainBorderPane.setDisable(false);
        }
    }

    public Object getControladorActivo() {
        return controladorActivo;
    }

    private void detenerControladorActivo() {
        if (controladorActivo instanceof RelojAsistenciaController rac) {
            rac.stopTimeline();
        } else if (controladorActivo instanceof VentaController vc) {
            vc.stopTimeline();
        } else if (controladorActivo instanceof KitchenDisplayController kdc) {
            kdc.stopTimers();
        }
        controladorActivo = null;
    }

    private void cargarVista(String fxmlPath, Button navBtnActivo) {
        try {
            detenerControladorActivo();

            if (contentArea != null) {
                contentArea.getChildren().clear();
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent view = loader.load();
            controladorActivo = loader.getController();

            if (contentArea != null) {
                contentArea.getChildren().setAll(view);
            } else if (mainBorderPane != null) {
                mainBorderPane.setCenter(view);
            }

            AnimationUtils.enterWithFadeSlide(view, 6.0, 0);
            actualizarEstilosNav(navBtnActivo);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error cargando vista " + fxmlPath, e);
        }
    }

    private void actualizarEstilosNav(Button activo) {
        Button[] buttons = {
            btnNavDashboard, btnNavSalon, btnNavReservas, btnNavDelivery, btnNavPOS, btnNavKDS, btnNavCaja,
            btnNavPromociones, btnNavKiosco, btnNavMenuQr,
            btnNavReportesContables, btnNavProveedores, btnNavClientes, btnNavHistorialPrecios, btnNavReportesAvanzados,
            btnNavAsistencia, btnNavReloj, btnNavHorarios, btnNavMiHorario, btnNavSolicitudes, btnNavExpedientes, btnNavPlanilla,
            btnNavInventario, btnNavMenu, btnNavRecetas, btnNavMermas, btnNavAnalisisMermas,
            btnNavKardex, btnNavAuditoria, btnNavUsuarios, btnNavRoles, btnNavConfiguracion, btnNavReportes, btnNavMantenimiento
        };
        for (Button btn : buttons) {
            if (btn != null) {
                btn.getStyleClass().remove("sidebar-nav-button-active");
            }
        }
        if (activo != null && !activo.getStyleClass().contains("sidebar-nav-button-active")) {
            activo.getStyleClass().add("sidebar-nav-button-active");
        }
    }

    @FXML
    public void handleCerrarSesion(ActionEvent event) {
        detenerControladorActivo();
        AuthService.logout();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/LoginView.fxml"));
            Parent loginRoot = loader.load();
            Scene currentScene = rootDashboardStack != null ? rootDashboardStack.getScene() : null;
            if (currentScene != null && currentScene.getWindow() instanceof Stage stage) {
                Scene loginScene = new Scene(loginRoot, 1080, 700);
                App.applyStylesheets(loginScene);
                stage.setScene(loginScene);
                stage.setTitle("FoodCore ERP v1.2 - Iniciar Sesión");
                stage.centerOnScreen();
            } else if (currentScene != null) {
                App.applyStylesheets(currentScene);
                currentScene.setRoot(loginRoot);
            } else {
                App.setRoot("LoginView");
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error retornando a LoginView tras cerrar sesión", e);
        }
    }

    public Button getBtnNavInventario() {
        return btnNavInventario;
    }

    public Button getBtnNavRecetas() {
        return btnNavRecetas;
    }

    public Button getBtnNavUsuarios() {
        return btnNavUsuarios;
    }

    public Button getBtnNavDashboard() {
        return btnNavDashboard;
    }

    public Button getBtnNavSolicitudes() {
        return btnNavSolicitudes;
    }

    public Button getBtnNavExpedientes() {
        return btnNavExpedientes;
    }

    public Button getBtnNavPlanilla() {
        return btnNavPlanilla;
    }

    public Button getBtnNavMiHorario() {
        return btnNavMiHorario;
    }

    public boolean isSidebarColapsado() {
        return sidebarColapsado;
    }

    @FXML
    public void handleNavReservas(ActionEvent event) {
        cargarVista("/com/foodcore/view/ReservasView.fxml", btnNavReservas);
    }

    @FXML
    public void handleNavAnalisisMermas(ActionEvent event) {
        cargarVista("/com/foodcore/view/AnalisisMermasView.fxml", btnNavAnalisisMermas);
    }

    @FXML
    public void handleNavPromociones(ActionEvent event) {
        cargarVista("/com/foodcore/view/PromocionesView.fxml", btnNavPromociones);
    }

    @FXML
    public void handleNavKiosco(ActionEvent event) {
        cargarVista("/com/foodcore/view/KioscoView.fxml", btnNavKiosco);
    }

    @FXML
    public void handleNavMenuQr(ActionEvent event) {
        cargarVista("/com/foodcore/view/MenuQrModalView.fxml", btnNavMenuQr);
    }
}
