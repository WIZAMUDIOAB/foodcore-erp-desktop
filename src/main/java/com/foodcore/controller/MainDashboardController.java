package com.foodcore.controller;

import com.foodcore.App;
import com.foodcore.model.Usuario;
import com.foodcore.offline.SyncWorker;
import com.foodcore.service.AuthService;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ToastService;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
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
    private static final Map<String, String> RUTAS_BREADCRUMBS = Map.ofEntries(
            Map.entry("/com/foodcore/view/DashboardView.fxml", "Inicio > Operaciones > Dashboard Gerencial"),
            Map.entry("/com/foodcore/view/SalonMesasView.fxml", "Inicio > Salón > Mesas"),
            Map.entry("/com/foodcore/view/ReservasView.fxml", "Inicio > Operaciones > Libro de Reservas"),
            Map.entry("/com/foodcore/view/DeliveryView.fxml", "Inicio > Operaciones > Delivery y Envíos"),
            Map.entry("/com/foodcore/view/VentaPOSView.fxml", "Inicio > Operaciones > Punto de Venta (POS)"),
            Map.entry("/com/foodcore/view/KitchenDisplayView.fxml", "Inicio > Operaciones > Cocina (KDS)"),
            Map.entry("/com/foodcore/view/CajaView.fxml", "Inicio > Operaciones > Caja y Turnos"),
            Map.entry("/com/foodcore/view/HistorialVentasView.fxml", "Inicio > Operaciones > Historial y Devoluciones"),
            Map.entry("/com/foodcore/view/PromocionesView.fxml", "Inicio > Comercial > Promociones y Descuentos"),
            Map.entry("/com/foodcore/view/KioscoView.fxml", "Inicio > Comercial > Kiosco Autoservicio"),
            Map.entry("/com/foodcore/view/MenuQrModalView.fxml", "Inicio > Comercial > Menú Digital QR"),
            Map.entry("/com/foodcore/view/EstadoResultadosView.fxml", "Inicio > Finanzas > Estado de Resultados (P&L)"),
            Map.entry("/com/foodcore/view/ReportesContablesView.fxml", "Inicio > Finanzas > Reportes Contables"),
            Map.entry("/com/foodcore/view/ProveedoresView.fxml", "Inicio > Finanzas > Proveedores y CxP"),
            Map.entry("/com/foodcore/view/ClientesView.fxml", "Inicio > Finanzas > Clientes y CxC"),
            Map.entry("/com/foodcore/view/HistorialPreciosView.fxml", "Inicio > Finanzas > Historial de Precios"),
            Map.entry("/com/foodcore/view/ReportesAvanzadosView.fxml", "Inicio > Finanzas > Reportes Avanzados"),
            Map.entry("/com/foodcore/view/AsistenciaView.fxml", "Inicio > Recursos Humanos > Control de Asistencia"),
            Map.entry("/com/foodcore/view/RelojAsistenciaView.fxml", "Inicio > Recursos Humanos > Reloj Marcador"),
            Map.entry("/com/foodcore/view/HorariosView.fxml", "Inicio > Recursos Humanos > Turnos y Horarios"),
            Map.entry("/com/foodcore/view/MiHorarioView.fxml", "Inicio > Recursos Humanos > Mi Portal y Horario"),
            Map.entry("/com/foodcore/view/SolicitudesView.fxml", "Inicio > Recursos Humanos > Solicitudes y Ausencias"),
            Map.entry("/com/foodcore/view/ExpedienteEmpleadoView.fxml", "Inicio > Recursos Humanos > Expedientes y Legajos"),
            Map.entry("/com/foodcore/view/PlanillaView.fxml", "Inicio > Recursos Humanos > Pagos y Planilla"),
            Map.entry("/com/foodcore/view/InventarioView.fxml", "Inicio > Backoffice > Inventario de Insumos"),
            Map.entry("/com/foodcore/view/InventarioFisicoView.fxml", "Inicio > Backoffice > Toma de Inventario Físico"),
            Map.entry("/com/foodcore/view/MenuView.fxml", "Inicio > Backoffice > Menú y Platillos"),
            Map.entry("/com/foodcore/view/RecetasView.fxml", "Inicio > Backoffice > Recetas y Escandallo"),
            Map.entry("/com/foodcore/view/MermasView.fxml", "Inicio > Backoffice > Registro de Mermas"),
            Map.entry("/com/foodcore/view/AnalisisMermasView.fxml", "Inicio > Backoffice > Análisis de Mermas"),
            Map.entry("/com/foodcore/view/KardexView.fxml", "Inicio > Backoffice > Auditoría Kardex"),
            Map.entry("/com/foodcore/view/AuditoriaView.fxml", "Inicio > Backoffice > Bitácora de Auditoría"),
            Map.entry("/com/foodcore/view/UsuariosView.fxml", "Inicio > Sistema > Gestión de Usuarios"),
            Map.entry("/com/foodcore/view/RolesView.fxml", "Inicio > Sistema > Roles y Permisos"),
            Map.entry("/com/foodcore/view/ConfiguracionView.fxml", "Inicio > Sistema > Configuración del Sistema"),
            Map.entry("/com/foodcore/view/EstadoSistemaView.fxml", "Inicio > Sistema > Estado del Sistema"),
            Map.entry("/com/foodcore/view/MantenimientoBDView.fxml", "Inicio > Sistema > Mantenimiento BD")
    );

    @FXML private StackPane rootDashboardStack;
    @FXML private BorderPane mainBorderPane;
    @FXML private VBox sidebarBox;
    @FXML private VBox boxBrandHeader;
    @FXML private Button btnToggleTheme;
    @FXML private FontIcon iconToggleTheme;
    @FXML private Button btnToggleSidebar;
    @FXML private Label lblBuscarSidebar;
    @FXML private javafx.scene.control.TextField txtBuscarSidebar;
    @FXML private HBox breadcrumbBar;
    @FXML private Label lblBreadcrumbs;
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
    @FXML private Button btnNavHistorialVentas;

    @FXML private Button btnNavEstadoResultados;
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
    @FXML private Button btnNavInventarioFisico;
    @FXML private Button btnNavMenu;
    @FXML private Button btnNavRecetas;
    @FXML private Button btnNavMermas;
    @FXML private Button btnNavAnalisisMermas;
    @FXML private Button btnNavKardex;
    @FXML private Button btnNavAuditoria;
    @FXML private Button btnNavUsuarios;
    @FXML private Button btnNavRoles;
    @FXML private Button btnNavConfiguracion;
    @FXML private Button btnNavEstadoSistema;

    @FXML private Button btnNavReportes;
    @FXML private Button btnNavMantenimiento;

    @FXML private Button btnCambiarPasswordMenu;
    @FXML private Button btnCerrarSesion;

    private Parent dialogoPasswordActivo;
    private boolean sidebarColapsado = false;
    private Object controladorActivo;
    private Usuario usuarioAccesoActual;
    private String rutaBreadcrumbsActual = "Inicio > Operaciones > Dashboard Gerencial";

    @FXML
    public void initialize() {
        ToastService.registrarHostPane(rootDashboardStack);
        Button[] buttons = {
            btnToggleTheme, btnToggleSidebar, btnNavDashboard, btnNavSalon, btnNavReservas, btnNavDelivery, btnNavPOS, btnNavKDS, btnNavCaja, btnNavHistorialVentas,
            btnNavEstadoResultados, btnNavReportesContables, btnNavProveedores, btnNavClientes, btnNavHistorialPrecios, btnNavReportesAvanzados,
            btnNavAsistencia, btnNavReloj, btnNavHorarios, btnNavMiHorario, btnNavSolicitudes, btnNavExpedientes, btnNavPlanilla,
            btnNavInventario, btnNavInventarioFisico, btnNavMenu, btnNavRecetas, btnNavMermas, btnNavAnalisisMermas, btnNavKardex, btnNavAuditoria,
            btnNavUsuarios, btnNavRoles, btnNavConfiguracion, btnNavEstadoSistema, btnNavReportes, btnNavMantenimiento,
            btnCambiarPasswordMenu, btnCerrarSesion
        };
        for (Button btn : buttons) {
            AnimationUtils.attachTactileFeedback(btn);
        }
        actualizarIconoTema(App.isDarkModeActivo());

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
        this.usuarioAccesoActual = user;
        mostrarTodosLosNodos();

        if (user == null || user.getNombreRol() == null) {
            ocultarNodo(btnNavEstadoSistema);
            ocultarNodo(btnNavAuditoria);
            cargarVista("/com/foodcore/view/DashboardView.fxml", btnNavDashboard);
            return;
        }

        String rol = user.getNombreRol().toUpperCase(Locale.ROOT);
        if (!rol.contains("ADMIN")) {
            ocultarNodo(btnNavEstadoSistema);
            ocultarNodo(btnNavAuditoria);
        }

        if (rol.contains("MOZO") || rol.contains("MESERO")) {
            ocultarNodo(btnNavDashboard);
            ocultarNodo(btnNavKDS);
            ocultarNodo(btnNavCaja);
            ocultarNodo(btnNavHistorialVentas);
            ocultarSeccionBackofficeCompleta();
            cargarVista("/com/foodcore/view/SalonMesasView.fxml", btnNavSalon);
        } else if (rol.contains("COCIN")) {
            ocultarNodo(btnNavDashboard);
            ocultarNodo(btnNavSalon);
            ocultarNodo(btnNavDelivery);
            ocultarNodo(btnNavPOS);
            ocultarNodo(btnNavCaja);
            ocultarNodo(btnNavHistorialVentas);
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
        ocultarNodo(btnNavEstadoResultados);
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
        ocultarNodo(btnNavInventarioFisico);
        ocultarNodo(btnNavMenu);
        ocultarNodo(btnNavRecetas);
        ocultarNodo(btnNavMermas);
        ocultarNodo(btnNavAnalisisMermas);
        ocultarNodo(btnNavKardex);
        ocultarNodo(btnNavAuditoria);
        ocultarNodo(btnNavUsuarios);
        ocultarNodo(btnNavRoles);
        ocultarNodo(btnNavConfiguracion);
        ocultarNodo(btnNavEstadoSistema);
        ocultarNodo(btnNavReportes);
        ocultarNodo(btnNavMantenimiento);
    }

    private void mostrarTodosLosNodos() {
        Node[] nodes = {
            lblSecOperaciones, lblSecFinanzas, lblSecRrhh, lblSecBackoffice, lblSecSistema,
            btnNavDashboard, btnNavSalon, btnNavReservas, btnNavDelivery, btnNavPOS, btnNavKDS, btnNavCaja, btnNavHistorialVentas,
            btnNavEstadoResultados, btnNavReportesContables, btnNavProveedores, btnNavClientes, btnNavHistorialPrecios, btnNavReportesAvanzados,
            btnNavAsistencia, btnNavReloj, btnNavHorarios, btnNavMiHorario, btnNavSolicitudes, btnNavExpedientes, btnNavPlanilla,
            btnNavInventario, btnNavInventarioFisico, btnNavMenu, btnNavRecetas, btnNavMermas, btnNavAnalisisMermas,
            btnNavKardex, btnNavAuditoria, btnNavUsuarios, btnNavRoles, btnNavConfiguracion, btnNavEstadoSistema
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
        if (lblBuscarSidebar != null) {
            lblBuscarSidebar.setVisible(!sidebarColapsado);
            lblBuscarSidebar.setManaged(!sidebarColapsado);
        }
        if (txtBuscarSidebar != null) {
            txtBuscarSidebar.setVisible(!sidebarColapsado);
            txtBuscarSidebar.setManaged(!sidebarColapsado);
        }
        if (btnToggleSidebar != null) {
            btnToggleSidebar.setText(sidebarColapsado ? "»" : "«");
        }
    }

    /**
     * Alterna entre Modo Claro y Modo Oscuro desde el botón superior izquierdo del sidebar (F-UX).
     */
    @FXML
    public void handleToggleTheme(ActionEvent event) {
        boolean nuevoModoOscuro = !App.isDarkModeActivo();
        App.setDarkModeActivo(nuevoModoOscuro);
        Scene scene = rootDashboardStack != null ? rootDashboardStack.getScene() : null;
        if (scene != null) {
            App.aplicarModoOscuro(scene, nuevoModoOscuro);
        }
        actualizarIconoTema(nuevoModoOscuro);
    }

    private void actualizarIconoTema(boolean darkMode) {
        if (iconToggleTheme != null) {
            iconToggleTheme.setIconLiteral(darkMode ? "fth-sun" : "fth-moon");
        } else if (btnToggleTheme != null && btnToggleTheme.getGraphic() instanceof FontIcon fi) {
            fi.setIconLiteral(darkMode ? "fth-sun" : "fth-moon");
        }
    }

    public Button getBtnToggleTheme() {
        return btnToggleTheme;
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
    public void handleNavHistorialVentas(ActionEvent event) {
        cargarVista("/com/foodcore/view/HistorialVentasView.fxml", btnNavHistorialVentas);
    }

    @FXML
    public void handleNavInventario(ActionEvent event) {
        cargarVista("/com/foodcore/view/InventarioView.fxml", btnNavInventario);
    }

    @FXML
    public void handleNavInventarioFisico(ActionEvent event) {
        cargarVista("/com/foodcore/view/InventarioFisicoView.fxml", btnNavInventarioFisico);
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
        Usuario act = AuthService.getUsuarioSesionActual() != null
                ? AuthService.getUsuarioSesionActual()
                : usuarioAccesoActual;
        if (!AuditoriaController.esUsuarioAdmin(act)) {
            AnimationUtils.mostrarToast(rootDashboardStack,
                    "Acceso restringido: Bitácora de Auditoría es exclusiva para ADMIN.", true);
            return;
        }
        cargarVista("/com/foodcore/view/AuditoriaView.fxml", btnNavAuditoria);
        if (controladorActivo instanceof AuditoriaController audCtrl) {
            audCtrl.validarAcceso(act);
        }
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
    public void handleNavEstadoResultados(ActionEvent event) {
        cargarVista("/com/foodcore/view/EstadoResultadosView.fxml", btnNavEstadoResultados);
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
        } else if (controladorActivo instanceof EstadoSistemaController esc) {
            esc.stopTimeline();
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
                if (contentArea.getScene() != null) {
                    App.aplicarModoOscuro(contentArea.getScene(), App.isDarkModeActivo());
                }
            } else if (mainBorderPane != null) {
                mainBorderPane.setCenter(view);
                if (mainBorderPane.getScene() != null) {
                    App.aplicarModoOscuro(mainBorderPane.getScene(), App.isDarkModeActivo());
                }
            }

            AnimationUtils.enterWithFadeSlide(view, 6.0, 0);
            actualizarEstilosNav(navBtnActivo);
            actualizarBreadcrumbs(resolverRutaBreadcrumbs(fxmlPath, navBtnActivo));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error cargando vista " + fxmlPath, e);
        }
    }

    /**
     * Actualiza la barra superior de migas de pan con la ruta jerárquica activa (Sub-Fase F - F8).
     */
    public void actualizarBreadcrumbs(String ruta) {
        String rutaFinal = (ruta == null || ruta.isBlank())
                ? "Inicio > Operaciones > Dashboard Gerencial"
                : ruta.trim();
        this.rutaBreadcrumbsActual = rutaFinal;
        if (lblBreadcrumbs != null) {
            lblBreadcrumbs.setText(rutaFinal);
        }
        if (breadcrumbBar != null) {
            breadcrumbBar.setVisible(true);
            breadcrumbBar.setManaged(true);
        }
    }

    private static String resolverRutaBreadcrumbs(String fxmlPath, Button navBtnActivo) {
        if (fxmlPath != null) {
            String mapped = RUTAS_BREADCRUMBS.get(fxmlPath);
            if (mapped != null) {
                return mapped;
            }
        }
        if (navBtnActivo != null && navBtnActivo.getText() != null && !navBtnActivo.getText().isBlank()) {
            return "Inicio > " + navBtnActivo.getText().trim();
        }
        return "Inicio > Operaciones > Dashboard Gerencial";
    }

    public HBox getBreadcrumbBar() {
        return breadcrumbBar;
    }

    public Label getLblBreadcrumbs() {
        return lblBreadcrumbs;
    }

    public String getRutaBreadcrumbsActual() {
        return rutaBreadcrumbsActual;
    }

    private void actualizarEstilosNav(Button activo) {
        Button[] buttons = {
            btnNavDashboard, btnNavSalon, btnNavReservas, btnNavDelivery, btnNavPOS, btnNavKDS, btnNavCaja, btnNavHistorialVentas,
            btnNavPromociones, btnNavKiosco, btnNavMenuQr,
            btnNavEstadoResultados, btnNavReportesContables, btnNavProveedores, btnNavClientes, btnNavHistorialPrecios, btnNavReportesAvanzados,
            btnNavAsistencia, btnNavReloj, btnNavHorarios, btnNavMiHorario, btnNavSolicitudes, btnNavExpedientes, btnNavPlanilla,
            btnNavInventario, btnNavInventarioFisico, btnNavMenu, btnNavRecetas, btnNavMermas, btnNavAnalisisMermas,
            btnNavKardex, btnNavAuditoria, btnNavUsuarios, btnNavRoles, btnNavConfiguracion, btnNavEstadoSistema, btnNavReportes, btnNavMantenimiento
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

    public Button getBtnNavEstadoResultados() {
        return btnNavEstadoResultados;
    }

    public Button getBtnNavMiHorario() {
        return btnNavMiHorario;
    }

    public Button getBtnNavEstadoSistema() {
        return btnNavEstadoSistema;
    }

    @FXML
    public void handleNavEstadoSistema(ActionEvent event) {
        Usuario act = AuthService.getUsuarioSesionActual();
        if (act != null && (act.getNombreRol() == null
                || !act.getNombreRol().toUpperCase(Locale.ROOT).contains("ADMIN"))) {
            AnimationUtils.mostrarToast(rootDashboardStack,
                    "Acceso restringido: Estado del Sistema es exclusivo para ADMIN.", true);
            return;
        }
        cargarVista("/com/foodcore/view/EstadoSistemaView.fxml", btnNavEstadoSistema);
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
