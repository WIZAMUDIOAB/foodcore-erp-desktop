package com.foodcore.controller;

import com.foodcore.dao.AuditoriaDAO;
import com.foodcore.dao.impl.AuditoriaDAOImpl;
import com.foodcore.model.RegistroAuditoria;
import com.foodcore.model.Usuario;
import com.foodcore.service.AuthService;
import com.foodcore.service.ExportacionContableService;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.DialogService;
import com.foodcore.util.EmptyStateHelper;
import com.foodcore.util.ExportFileChooserHelper.FormatoExportacion;
import com.foodcore.util.ReporteExportUIHelper;
import com.foodcore.util.ToastService;
import com.foodcore.util.UiAsyncExecutor;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.File;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador de Bitácora de Auditoría conforme a SPEC-FOODCORE-v1.3-ENTERPRISE (§5.2)
 * y Sub-Fase E (v5.5.0 E1): filtros multi-criterio, paginación de 50 registros,
 * EmptyStateHelper, exportación PDF/Excel/CSV y validación de rol exclusivo ADMIN.
 */
public class AuditoriaController {

    public static final int TAMANO_PAGINA = 50;
    private static final int LIMITE_EXPORTACION = 10_000;
    private static final String TODOS_USUARIOS = "Todos los Usuarios";
    private static final String TODAS_ACCIONES = "Todas las Acciones";
    private static final List<String> ACCIONES_BASE = List.of(
            "LOGIN", "VENTA", "ANULACION", "CREACION_COMANDA",
            "DEVOLUCION_PARCIAL", "DESCUENTO", "MODIFICAR_PIN", "CIERRE_CONTABLE"
    );

    private static final Logger LOGGER = Logger.getLogger(AuditoriaController.class.getName());
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @FXML private StackPane rootAuditoriaStack;
    @FXML private VBox boxContenedorPrincipal;
    @FXML private VBox boxContenidoAuditoria;
    @FXML private VBox boxAccesoRestringido;

    @FXML private ComboBox<String> cmbFiltroMozo;
    @FXML private ComboBox<String> cmbFiltroAccion;
    @FXML private DatePicker dpDesde;
    @FXML private DatePicker dpHasta;
    @FXML private Button btnFiltrarAuditoria;
    @FXML private Button btnLimpiarFiltros;

    @FXML private Button btnExportarPdf;
    @FXML private Button btnExportarExcel;
    @FXML private Button btnExportarCsv;
    @FXML private Label lblEstadoExportacion;

    @FXML private TableView<RegistroAuditoria> tblAuditoria;
    @FXML private VBox emptyState;
    @FXML private TableColumn<RegistroAuditoria, String> colIdAuditoria;
    @FXML private TableColumn<RegistroAuditoria, String> colFecha;
    @FXML private TableColumn<RegistroAuditoria, String> colUsuario;
    @FXML private TableColumn<RegistroAuditoria, String> colAccion;
    @FXML private TableColumn<RegistroAuditoria, String> colEntidad;
    @FXML private TableColumn<RegistroAuditoria, String> colDatosAnteriores;
    @FXML private TableColumn<RegistroAuditoria, String> colDatosNuevos;

    @FXML private HBox boxPaginacion;
    @FXML private Label lblPaginacionInfo;
    @FXML private Button btnPaginaAnterior;
    @FXML private Button btnPaginaSiguiente;

    private final AuditoriaDAO auditoriaDAO = new AuditoriaDAOImpl();
    private final ExportacionContableService exportacionService = new ExportacionContableService();
    private final List<RegistroAuditoria> todosEventos = new ArrayList<>();
    private final ObservableList<RegistroAuditoria> listaAuditoria = FXCollections.observableArrayList();

    private boolean accesoPermitido = true;
    private int paginaActual = 1;
    private int totalRegistros = 0;

    private record CargaAuditoriaResult(
            List<RegistroAuditoria> paginaItems,
            int total,
            List<String> usuarios,
            List<String> acciones) {
    }

    @FXML
    public void initialize() {
        AnimationUtils.attachTactileFeedback(btnFiltrarAuditoria);
        AnimationUtils.attachTactileFeedback(btnLimpiarFiltros);
        AnimationUtils.attachTactileFeedback(btnExportarPdf);
        AnimationUtils.attachTactileFeedback(btnExportarExcel);
        AnimationUtils.attachTactileFeedback(btnExportarCsv);
        AnimationUtils.attachTactileFeedback(btnPaginaAnterior);
        AnimationUtils.attachTactileFeedback(btnPaginaSiguiente);

        configurarTabla();
        inicializarCombosFiltro();
        validarAcceso(AuthService.getUsuarioSesionActual());
    }

    private void configurarTabla() {
        colIdAuditoria.setCellValueFactory(c -> new SimpleStringProperty("#" + c.getValue().getIdAuditoria()));
        colFecha.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getFecha() != null ? c.getValue().getFecha().format(FMT) : "-"
        ));
        colUsuario.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getNombreUsuario() != null ? c.getValue().getNombreUsuario() : "-"
        ));
        colAccion.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getAccion() != null ? c.getValue().getAccion() : "-"
        ));
        colEntidad.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getEntidadAfectada() != null ? c.getValue().getEntidadAfectada() : "-"
        ));
        colDatosAnteriores.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getDatosAnteriores() != null ? c.getValue().getDatosAnteriores() : "-"
        ));
        colDatosNuevos.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getDatosNuevos() != null ? c.getValue().getDatosNuevos() : "-"
        ));

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
                String up = item.toUpperCase(Locale.ROOT);
                if (up.contains("ANUL") || up.contains("ELIMIN") || up.contains("FALLIDO") || up.contains("BLOQUEADO")) {
                    badge.getStyleClass().add("badge-pill-danger");
                } else if (up.contains("DESCUENTO") || up.contains("MODIFICAR") || up.contains("DEVOL")) {
                    badge.getStyleClass().add("badge-pill-warning");
                } else {
                    badge.getStyleClass().add("badge-pill-accent");
                }
                setGraphic(badge);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
        });

        tblAuditoria.setItems(listaAuditoria);
        tblAuditoria.setPlaceholder(EmptyStateHelper.crearEmptyState(
                "No hay registros de auditoría",
                "No se encontraron eventos para los filtros seleccionados."
        ));
        EmptyStateHelper.vincularATabla(tblAuditoria, emptyState);
    }

    private void inicializarCombosFiltro() {
        if (cmbFiltroMozo != null) {
            cmbFiltroMozo.setItems(FXCollections.observableArrayList(TODOS_USUARIOS));
            cmbFiltroMozo.getSelectionModel().selectFirst();
        }
        if (cmbFiltroAccion != null) {
            ObservableList<String> acciones = FXCollections.observableArrayList(TODAS_ACCIONES);
            acciones.addAll(ACCIONES_BASE);
            cmbFiltroAccion.setItems(acciones);
            cmbFiltroAccion.getSelectionModel().selectFirst();
        }
    }

    /**
     * Valida que el usuario actual posea rol ADMIN antes de exponer la bitácora.
     */
    public boolean validarAcceso(Usuario usuario) {
        this.accesoPermitido = esUsuarioAdmin(usuario);
        if (boxContenidoAuditoria != null) {
            boxContenidoAuditoria.setVisible(accesoPermitido);
            boxContenidoAuditoria.setManaged(accesoPermitido);
        }
        if (boxAccesoRestringido != null) {
            boxAccesoRestringido.setVisible(!accesoPermitido);
            boxAccesoRestringido.setManaged(!accesoPermitido);
        }
        if (!accesoPermitido) {
            todosEventos.clear();
            listaAuditoria.clear();
            totalRegistros = 0;
            paginaActual = 1;
            actualizarControlesPaginacion();
            return false;
        }
        cargarEventosAuditoria();
        return true;
    }

    public static boolean esUsuarioAdmin(Usuario usuario) {
        return usuario != null
                && usuario.getNombreRol() != null
                && usuario.getNombreRol().toUpperCase(Locale.ROOT).contains("ADMIN");
    }

    @FXML
    public void cargarEventosAuditoria() {
        if (!accesoPermitido) {
            return;
        }
        final String usuario = cmbFiltroMozo != null ? cmbFiltroMozo.getValue() : null;
        final String accion = cmbFiltroAccion != null ? cmbFiltroAccion.getValue() : null;
        final LocalDate desde = dpDesde != null ? dpDesde.getValue() : null;
        final LocalDate hasta = dpHasta != null ? dpHasta.getValue() : null;
        final int offset = Math.max(0, (paginaActual - 1) * TAMANO_PAGINA);

        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            ToastService.mostrarAdvertencia(
                    rootAuditoriaStack,
                    "Rango de fechas inválido: la fecha 'Desde' no puede ser posterior a 'Hasta'.");
            return;
        }

        UiAsyncExecutor.ejecutarAsync(
            () -> {
                try (Connection conn = ConexionBD.getConnection()) {
                    if (conn == null) {
                        return new CargaAuditoriaResult(List.of(), 0, List.of(), List.of());
                    }
                    int total = auditoriaDAO.contarEventosFiltrados(conn, usuario, accion, desde, hasta);
                    int maxPagina = Math.max(1, (int) Math.ceil((double) total / TAMANO_PAGINA));
                    int offsetEfectivo = offset >= total ? Math.max(0, (maxPagina - 1) * TAMANO_PAGINA) : offset;
                    List<RegistroAuditoria> items = auditoriaDAO.listarEventosFiltrados(
                            conn, usuario, accion, desde, hasta, offsetEfectivo, TAMANO_PAGINA);
                    List<String> usuarios = auditoriaDAO.listarUsuariosDistinct(conn);
                    List<String> acciones = auditoriaDAO.listarAccionesDistinct(conn);
                    return new CargaAuditoriaResult(items, total, usuarios, acciones);
                }
            },
            res -> {
                todosEventos.clear();
                todosEventos.addAll(res.paginaItems());
                totalRegistros = res.total();
                int totalPaginas = getTotalPaginas();
                if (paginaActual > totalPaginas) {
                    paginaActual = totalPaginas;
                }
                actualizarCombosDesdeBd(res.usuarios(), res.acciones());
                listaAuditoria.setAll(res.paginaItems());
                actualizarControlesPaginacion();
            },
            e -> LOGGER.log(Level.FINE, "Error cargando bitácora de auditoría", e)
        );
    }

    @FXML
    public void handleFiltrarAuditoria(ActionEvent event) {
        if (!accesoPermitido) {
            return;
        }
        paginaActual = 1;
        cargarEventosAuditoria();
    }

    @FXML
    public void handleLimpiarFiltros(ActionEvent event) {
        if (!accesoPermitido) {
            return;
        }
        if (cmbFiltroMozo != null && !cmbFiltroMozo.getItems().isEmpty()) {
            cmbFiltroMozo.getSelectionModel().selectFirst();
        }
        if (cmbFiltroAccion != null && !cmbFiltroAccion.getItems().isEmpty()) {
            cmbFiltroAccion.getSelectionModel().selectFirst();
        }
        if (dpDesde != null) {
            dpDesde.setValue(null);
        }
        if (dpHasta != null) {
            dpHasta.setValue(null);
        }
        paginaActual = 1;
        cargarEventosAuditoria();
    }

    @FXML
    public void handlePaginaAnterior(ActionEvent event) {
        if (!accesoPermitido || paginaActual <= 1) {
            return;
        }
        paginaActual--;
        cargarEventosAuditoria();
    }

    @FXML
    public void handlePaginaSiguiente(ActionEvent event) {
        if (!accesoPermitido || paginaActual >= getTotalPaginas()) {
            return;
        }
        paginaActual++;
        cargarEventosAuditoria();
    }

    @FXML
    public void handleExportarPdf(ActionEvent event) {
        iniciarExportacion(FormatoExportacion.PDF, "Exportar Bitácora a PDF", "auditoria_foodcore.pdf");
    }

    @FXML
    public void handleExportarExcel(ActionEvent event) {
        iniciarExportacion(FormatoExportacion.EXCEL, "Exportar Bitácora a Excel", "auditoria_foodcore.xlsx");
    }

    @FXML
    public void handleExportarCsv(ActionEvent event) {
        iniciarExportacion(FormatoExportacion.CSV, "Exportar Bitácora a CSV", "auditoria_foodcore.csv");
    }

    private void iniciarExportacion(FormatoExportacion formato, String tituloDialogo, String nombreSugerido) {
        if (!accesoPermitido) {
            AnimationUtils.mostrarToast(rootAuditoriaStack, "Solo rol ADMIN puede exportar auditoría.", true);
            return;
        }
        List<RegistroAuditoria> registros = obtenerRegistrosCompletosParaExportar();
        if (registros.isEmpty()) {
            AnimationUtils.mostrarToast(rootAuditoriaStack, "No hay registros de auditoría para exportar.", true);
            return;
        }
        ReporteExportUIHelper.exportarTablaConSelector(
                rootAuditoriaStack,
                lblEstadoExportacion,
                tituloDialogo,
                nombreSugerido,
                "Bitácora de Auditoría FoodCore",
                "AUDITORIA",
                formato,
                ENCABEZADOS_EXPORTACION,
                mapearFilasExportacion(registros),
                exportacionService
        );
    }

    /**
     * Exporta una lista concreta de eventos de auditoría a PDF, Excel o CSV en disco.
     */
    public void exportarListaAArchivo(FormatoExportacion formato, File destino, List<RegistroAuditoria> registros)
            throws Exception {
        if (!accesoPermitido) {
            throw new SecurityException("Acceso denegado: solo el rol ADMIN puede exportar auditoría.");
        }
        if (formato == null || destino == null) {
            throw new IllegalArgumentException("Formato y archivo de destino son obligatorios.");
        }
        List<List<String>> filas = mapearFilasExportacion(registros != null ? registros : List.of());
        if (formato == FormatoExportacion.PDF) {
            exportacionService.exportarReporteGenericoPdf(
                    "Bitácora de Auditoría FoodCore", ENCABEZADOS_EXPORTACION, filas, destino);
        } else if (formato == FormatoExportacion.EXCEL) {
            exportacionService.exportarReporteGenericoExcel(
                    "AUDITORIA", ENCABEZADOS_EXPORTACION, filas, destino);
        } else if (formato == FormatoExportacion.CSV) {
            exportacionService.exportarReporteGenericoCsv(
                    ENCABEZADOS_EXPORTACION, filas, destino);
        } else {
            throw new IllegalArgumentException("Formato de exportación no soportado: " + formato);
        }
    }

    private List<RegistroAuditoria> obtenerRegistrosCompletosParaExportar() {
        String usuario = cmbFiltroMozo != null ? cmbFiltroMozo.getValue() : null;
        String accion = cmbFiltroAccion != null ? cmbFiltroAccion.getValue() : null;
        LocalDate desde = dpDesde != null ? dpDesde.getValue() : null;
        LocalDate hasta = dpHasta != null ? dpHasta.getValue() : null;
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn != null) {
                List<RegistroAuditoria> desdeBd = auditoriaDAO.listarEventosFiltrados(
                        conn, usuario, accion, desde, hasta, 0, LIMITE_EXPORTACION);
                if (!desdeBd.isEmpty()) {
                    return desdeBd;
                }
            }
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "Fallback a eventos en memoria para exportación", ex);
        }
        return List.copyOf(listaAuditoria);
    }

    private static final List<String> ENCABEZADOS_EXPORTACION = List.of(
            "Audit ID", "Fecha y Hora", "Usuario", "Acción", "Entidad", "Datos Anteriores", "Datos Nuevos"
    );

    public static List<List<String>> mapearFilasExportacion(List<RegistroAuditoria> registros) {
        List<List<String>> filas = new ArrayList<>();
        for (RegistroAuditoria r : registros) {
            filas.add(List.of(
                    "#" + r.getIdAuditoria(),
                    r.getFecha() != null ? r.getFecha().format(FMT) : "-",
                    r.getNombreUsuario() != null ? r.getNombreUsuario() : "-",
                    r.getAccion() != null ? r.getAccion() : "-",
                    r.getEntidadAfectada() != null ? r.getEntidadAfectada() : "-",
                    r.getDatosAnteriores() != null ? r.getDatosAnteriores() : "-",
                    r.getDatosNuevos() != null ? r.getDatosNuevos() : "-"
            ));
        }
        return filas;
    }

    private void actualizarCombosDesdeBd(List<String> usuariosBd, List<String> accionesBd) {
        if (cmbFiltroMozo != null) {
            String seleccionActual = cmbFiltroMozo.getValue();
            Set<String> opciones = new LinkedHashSet<>();
            opciones.add(TODOS_USUARIOS);
            opciones.addAll(usuariosBd);
            cmbFiltroMozo.setItems(FXCollections.observableArrayList(opciones));
            if (seleccionActual != null && opciones.contains(seleccionActual)) {
                cmbFiltroMozo.setValue(seleccionActual);
            } else {
                cmbFiltroMozo.getSelectionModel().selectFirst();
            }
        }
        if (cmbFiltroAccion != null) {
            String accionActual = cmbFiltroAccion.getValue();
            Set<String> opcionesAcc = new LinkedHashSet<>();
            opcionesAcc.add(TODAS_ACCIONES);
            opcionesAcc.addAll(ACCIONES_BASE);
            opcionesAcc.addAll(accionesBd);
            cmbFiltroAccion.setItems(FXCollections.observableArrayList(opcionesAcc));
            if (accionActual != null && opcionesAcc.contains(accionActual)) {
                cmbFiltroAccion.setValue(accionActual);
            } else {
                cmbFiltroAccion.getSelectionModel().selectFirst();
            }
        }
    }

    private void actualizarControlesPaginacion() {
        int totalPaginas = getTotalPaginas();
        if (lblPaginacionInfo != null) {
            lblPaginacionInfo.setText(String.format(
                    "Página %d de %d · %d registros (%d por página)",
                    paginaActual, totalPaginas, totalRegistros, TAMANO_PAGINA));
        }
        if (btnPaginaAnterior != null) {
            btnPaginaAnterior.setDisable(paginaActual <= 1);
        }
        if (btnPaginaSiguiente != null) {
            btnPaginaSiguiente.setDisable(paginaActual >= totalPaginas);
        }
    }

    /**
     * Filtro rápido en memoria por usuario/mozo (mantiene compatibilidad con pruebas E2E Sub-Fase C.1).
     */
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
        totalRegistros = listaAuditoria.size();
        paginaActual = 1;
        actualizarControlesPaginacion();
    }

    public ObservableList<RegistroAuditoria> getListaAuditoria() {
        return listaAuditoria;
    }

    public boolean isAccesoPermitido() {
        return accesoPermitido;
    }

    public int getPaginaActual() {
        return paginaActual;
    }

    public int getTotalRegistros() {
        return totalRegistros;
    }

    public int getTotalPaginas() {
        return Math.max(1, (int) Math.ceil((double) totalRegistros / TAMANO_PAGINA));
    }

    public ComboBox<String> getCmbFiltroMozo() {
        return cmbFiltroMozo;
    }

    public ComboBox<String> getCmbFiltroAccion() {
        return cmbFiltroAccion;
    }

    public DatePicker getDpDesde() {
        return dpDesde;
    }

    public DatePicker getDpHasta() {
        return dpHasta;
    }
}
