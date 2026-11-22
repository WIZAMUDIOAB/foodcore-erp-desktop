package com.foodcore.controller;

import com.foodcore.App;
import com.foodcore.model.Usuario;
import com.foodcore.controller.dashboard.DashboardNavigationCatalog;
import com.foodcore.controller.dashboard.DashboardRbacPolicy;
import com.foodcore.controller.dashboard.DashboardSyncIndicatorPresenter;
import com.foodcore.controller.dashboard.DashboardViewLifecycleHelper;
import com.foodcore.controller.dashboard.DefaultDashboardRbacPolicy;
import com.foodcore.controller.dashboard.NavDestination;
import com.foodcore.controller.dashboard.NavSection;
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
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.util.EnumMap;
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
    @FXML private Label lblSecComercial;
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
    private final DashboardRbacPolicy rbacPolicy;
    private final DashboardNavigationCatalog navigationCatalog;
    private final DashboardSyncIndicatorPresenter syncIndicatorPresenter;
    private final DashboardViewLifecycleHelper lifecycleHelper;
    private final EnumMap<NavSection, Node> nodosSeccion = new EnumMap<>(NavSection.class);
    private final EnumMap<NavDestination, Button> botonesDestino = new EnumMap<>(NavDestination.class);
    private boolean sidebarColapsado = false;
    private Object controladorActivo;
    private Usuario usuarioAccesoActual;
    private SyncWorker.SyncStatusListener syncStatusListener;
    private String rutaBreadcrumbsActual = "Inicio > Operaciones > Dashboard Gerencial";

    public MainDashboardController() {
        this(new DefaultDashboardRbacPolicy(), new DashboardNavigationCatalog(),
                new DashboardSyncIndicatorPresenter(), new DashboardViewLifecycleHelper());
    }

    public MainDashboardController(DashboardRbacPolicy rbacPolicy, DashboardNavigationCatalog navigationCatalog,
            DashboardSyncIndicatorPresenter syncIndicatorPresenter, DashboardViewLifecycleHelper lifecycleHelper) {
        this.rbacPolicy = rbacPolicy != null ? rbacPolicy : new DefaultDashboardRbacPolicy();
        this.navigationCatalog = navigationCatalog != null ? navigationCatalog : new DashboardNavigationCatalog();
        this.syncIndicatorPresenter = syncIndicatorPresenter != null
                ? syncIndicatorPresenter : new DashboardSyncIndicatorPresenter();
        this.lifecycleHelper = lifecycleHelper != null ? lifecycleHelper : new DashboardViewLifecycleHelper();
    }

    private void inicializarMapasNavegacion() {
        nodosSeccion.put(NavSection.OPERACIONES, lblSecOperaciones);
        nodosSeccion.put(NavSection.COMERCIAL, lblSecComercial);
        nodosSeccion.put(NavSection.FINANZAS, lblSecFinanzas);
        nodosSeccion.put(NavSection.RRHH, lblSecRrhh);
        nodosSeccion.put(NavSection.BACKOFFICE, lblSecBackoffice);
        nodosSeccion.put(NavSection.SISTEMA, lblSecSistema);
        botonesDestino.put(NavDestination.DASHBOARD, btnNavDashboard);
        botonesDestino.put(NavDestination.REPORTES, btnNavReportes);
        botonesDestino.put(NavDestination.SALON, btnNavSalon);
        botonesDestino.put(NavDestination.RESERVAS, btnNavReservas);
        botonesDestino.put(NavDestination.DELIVERY, btnNavDelivery);
        botonesDestino.put(NavDestination.POS, btnNavPOS);
        botonesDestino.put(NavDestination.KDS, btnNavKDS);
        botonesDestino.put(NavDestination.CAJA, btnNavCaja);
        botonesDestino.put(NavDestination.HISTORIAL_VENTAS, btnNavHistorialVentas);
        botonesDestino.put(NavDestination.PROMOCIONES, btnNavPromociones);
        botonesDestino.put(NavDestination.KIOSCO, btnNavKiosco);
        botonesDestino.put(NavDestination.MENU_QR, btnNavMenuQr);
        botonesDestino.put(NavDestination.ESTADO_RESULTADOS, btnNavEstadoResultados);
        botonesDestino.put(NavDestination.REPORTES_CONTABLES, btnNavReportesContables);
        botonesDestino.put(NavDestination.PROVEEDORES, btnNavProveedores);
        botonesDestino.put(NavDestination.CLIENTES, btnNavClientes);
        botonesDestino.put(NavDestination.HISTORIAL_PRECIOS, btnNavHistorialPrecios);
        botonesDestino.put(NavDestination.REPORTES_AVANZADOS, btnNavReportesAvanzados);
        botonesDestino.put(NavDestination.ASISTENCIA, btnNavAsistencia);
        botonesDestino.put(NavDestination.RELOJ, btnNavReloj);
        botonesDestino.put(NavDestination.HORARIOS, btnNavHorarios);
        botonesDestino.put(NavDestination.MI_HORARIO, btnNavMiHorario);
        botonesDestino.put(NavDestination.SOLICITUDES, btnNavSolicitudes);
        botonesDestino.put(NavDestination.EXPEDIENTES, btnNavExpedientes);
        botonesDestino.put(NavDestination.PLANILLA, btnNavPlanilla);
        botonesDestino.put(NavDestination.INVENTARIO, btnNavInventario);
        botonesDestino.put(NavDestination.INVENTARIO_FISICO, btnNavInventarioFisico);
        botonesDestino.put(NavDestination.MENU, btnNavMenu);
        botonesDestino.put(NavDestination.RECETAS, btnNavRecetas);
        botonesDestino.put(NavDestination.MERMAS, btnNavMermas);
        botonesDestino.put(NavDestination.ANALISIS_MERMAS, btnNavAnalisisMermas);
        botonesDestino.put(NavDestination.KARDEX, btnNavKardex);
        botonesDestino.put(NavDestination.AUDITORIA, btnNavAuditoria);
        botonesDestino.put(NavDestination.USUARIOS, btnNavUsuarios);
        botonesDestino.put(NavDestination.ROLES, btnNavRoles);
        botonesDestino.put(NavDestination.CONFIGURACION, btnNavConfiguracion);
        botonesDestino.put(NavDestination.ESTADO_SISTEMA, btnNavEstadoSistema);
        botonesDestino.put(NavDestination.MANTENIMIENTO, btnNavMantenimiento);
    }

    @FXML
    public void initialize() {
        inicializarMapasNavegacion();
        ToastService.registrarHostPane(rootDashboardStack);
        Button[] buttons = {
            btnToggleTheme, btnToggleSidebar, btnNavDashboard, btnNavSalon, btnNavReservas, btnNavDelivery,
            btnNavPOS, btnNavKDS, btnNavCaja, btnNavHistorialVentas,
            btnNavPromociones, btnNavKiosco, btnNavMenuQr,
            btnNavEstadoResultados, btnNavReportesContables, btnNavProveedores, btnNavClientes,
            btnNavHistorialPrecios, btnNavReportesAvanzados,
            btnNavAsistencia, btnNavReloj, btnNavHorarios, btnNavMiHorario, btnNavSolicitudes,
            btnNavExpedientes, btnNavPlanilla,
            btnNavInventario, btnNavInventarioFisico, btnNavMenu, btnNavRecetas, btnNavMermas,
            btnNavAnalisisMermas, btnNavKardex, btnNavAuditoria,
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
            if (lblUsuarioLogueado != null) {
                lblUsuarioLogueado.setText(act.getUsername() + " · " + act.getNombreRol());
            }
            aplicarControlAcceso(act);
        } else {
            if (lblUsuarioLogueado != null) {
                lblUsuarioLogueado.setText("Sin sesión activa");
            }
            aplicarControlAcceso(null);
        }
    }

    private void configurarSyncWorkerListener() {
        desregistrarSyncWorkerListener();
        SyncWorker syncWorker = SyncWorker.getInstancia();
        syncStatusListener = (online, pendientes, alerta) -> Platform.runLater(() -> {
            syncIndicatorPresenter.aplicarEstado(dotNetworkStatus, lblNetworkStatus, online, pendientes);
        });
        syncWorker.addListener(syncStatusListener);
    }

    private void desregistrarSyncWorkerListener() {
        if (syncStatusListener != null) {
            SyncWorker.getInstancia().removeListener(syncStatusListener);
            syncStatusListener = null;
        }
    }

    /**
     * Aplica las reglas RBAC fail-closed del Sidebar según SPEC-FOODCORE-v1.2, v1.3 (§5) y v5.7.1:
     * oculta todos los elementos por defecto y habilita explícitamente solo los autorizados para el rol/permisos.
     */
    public void aplicarControlAcceso(Usuario user) {
        this.usuarioAccesoActual = user;
        if (botonesDestino.isEmpty() || botonesDestino.get(NavDestination.DASHBOARD) == null) {
            inicializarMapasNavegacion();
        }
        ocultarTodosLosNodos();

        if (user == null || user.getNombreRol() == null || user.getNombreRol().isBlank()) {
            detenerControladorActivo();
            if (contentArea != null) {
                contentArea.getChildren().clear();
            }
            return;
        }

        DashboardRbacPolicy.PoliticaAccesoDashboard politica = rbacPolicy.evaluarAcceso(user);
        if (politica == null || !politica.autorizado()) {
            detenerControladorActivo();
            if (contentArea != null) {
                contentArea.getChildren().clear();
            }
            return;
        }
        mostrarPolitica(politica);
        NavDestination inicial = politica.destinoInicial();
        Button botonInicial = inicial != null ? botonesDestino.get(inicial) : null;
        if (inicial != null) {
            cargarVista(inicial.getFxmlPath(), botonInicial);
        }
    }

    private void mostrarPolitica(DashboardRbacPolicy.PoliticaAccesoDashboard politica) {
        for (NavSection seccion : politica.seccionesVisibles()) {
            mostrarNodos(nodosSeccion.get(seccion));
        }
        for (NavDestination destino : politica.destinosVisibles()) {
            mostrarNodos(botonesDestino.get(destino));
        }
    }

    private void ocultarTodosLosNodos() {
        nodosSeccion.values().forEach(this::ocultarNodo);
        botonesDestino.values().forEach(this::ocultarNodo);
    }

    private void mostrarNodos(Node... nodes) {
        if (nodes == null) {
            return;
        }
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
        if (!rbacPolicy.puedeAccederAuditoria(act)) {
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
        lifecycleHelper.detenerControladorActivo(controladorActivo);
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

    private String resolverRutaBreadcrumbs(String fxmlPath, Button navBtnActivo) {
        return navigationCatalog.resolverRutaBreadcrumbs(fxmlPath,
                navBtnActivo != null ? navBtnActivo.getText() : null);
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
        desregistrarSyncWorkerListener();
        detenerControladorActivo();
        this.usuarioAccesoActual = null;
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

    public Button getBtnNavPromociones() {
        return btnNavPromociones;
    }

    public Button getBtnNavKiosco() {
        return btnNavKiosco;
    }

    public Button getBtnNavMenuQr() {
        return btnNavMenuQr;
    }

    public Button getBtnNavReservas() {
        return btnNavReservas;
    }

    public Button getBtnNavAuditoria() {
        return btnNavAuditoria;
    }

    public Button getBtnNavConfiguracion() {
        return btnNavConfiguracion;
    }

    public Button getBtnNavCaja() {
        return btnNavCaja;
    }

    public Button getBtnNavPOS() {
        return btnNavPOS;
    }

    public Button getBtnNavKDS() {
        return btnNavKDS;
    }

    public Button getBtnNavSalon() {
        return btnNavSalon;
    }

    public SyncWorker.SyncStatusListener getSyncStatusListener() {
        return syncStatusListener;
    }

    @FXML
    public void handleNavEstadoSistema(ActionEvent event) {
        Usuario act = AuthService.getUsuarioSesionActual() != null
                ? AuthService.getUsuarioSesionActual()
                : usuarioAccesoActual;
        if (!rbacPolicy.puedeAccederEstadoSistema(act)) {
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
