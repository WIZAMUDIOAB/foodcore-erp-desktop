package com.foodcore.controller.dashboard;

public enum NavDestination {
    DASHBOARD("/com/foodcore/view/DashboardView.fxml", "Inicio > Operaciones > Dashboard Gerencial"),
    SALON("/com/foodcore/view/SalonMesasView.fxml", "Inicio > Salón > Mesas"),
    RESERVAS("/com/foodcore/view/ReservasView.fxml", "Inicio > Operaciones > Libro de Reservas"),
    DELIVERY("/com/foodcore/view/DeliveryView.fxml", "Inicio > Operaciones > Delivery y Envíos"),
    POS("/com/foodcore/view/VentaPOSView.fxml", "Inicio > Operaciones > Punto de Venta (POS)"),
    KDS("/com/foodcore/view/KitchenDisplayView.fxml", "Inicio > Operaciones > Cocina (KDS)"),
    CAJA("/com/foodcore/view/CajaView.fxml", "Inicio > Operaciones > Caja y Turnos"),
    HISTORIAL_VENTAS("/com/foodcore/view/HistorialVentasView.fxml", "Inicio > Operaciones > Historial y Devoluciones"),
    PROMOCIONES("/com/foodcore/view/PromocionesView.fxml", "Inicio > Comercial > Promociones y Descuentos"),
    KIOSCO("/com/foodcore/view/KioscoView.fxml", "Inicio > Comercial > Kiosco Autoservicio"),
    MENU_QR("/com/foodcore/view/MenuQrModalView.fxml", "Inicio > Comercial > Menú Digital QR"),
    ESTADO_RESULTADOS("/com/foodcore/view/EstadoResultadosView.fxml", "Inicio > Finanzas > Estado de Resultados (P&L)"),
    REPORTES_CONTABLES("/com/foodcore/view/ReportesContablesView.fxml", "Inicio > Finanzas > Reportes Contables"),
    PROVEEDORES("/com/foodcore/view/ProveedoresView.fxml", "Inicio > Finanzas > Proveedores y CxP"),
    CLIENTES("/com/foodcore/view/ClientesView.fxml", "Inicio > Finanzas > Clientes y CxC"),
    HISTORIAL_PRECIOS("/com/foodcore/view/HistorialPreciosView.fxml", "Inicio > Finanzas > Historial de Precios"),
    REPORTES_AVANZADOS("/com/foodcore/view/ReportesAvanzadosView.fxml", "Inicio > Finanzas > Reportes Avanzados"),
    REPORTES("/com/foodcore/view/DashboardView.fxml", "Inicio > Operaciones > Dashboard Gerencial"),
    ASISTENCIA("/com/foodcore/view/AsistenciaView.fxml", "Inicio > Recursos Humanos > Control de Asistencia"),
    RELOJ("/com/foodcore/view/RelojAsistenciaView.fxml", "Inicio > Recursos Humanos > Reloj Marcador"),
    HORARIOS("/com/foodcore/view/HorariosView.fxml", "Inicio > Recursos Humanos > Turnos y Horarios"),
    MI_HORARIO("/com/foodcore/view/MiHorarioView.fxml", "Inicio > Recursos Humanos > Mi Portal y Horario"),
    SOLICITUDES("/com/foodcore/view/SolicitudesView.fxml", "Inicio > Recursos Humanos > Solicitudes y Ausencias"),
    EXPEDIENTES("/com/foodcore/view/ExpedienteEmpleadoView.fxml", "Inicio > Recursos Humanos > Expedientes y Legajos"),
    PLANILLA("/com/foodcore/view/PlanillaView.fxml", "Inicio > Recursos Humanos > Pagos y Planilla"),
    INVENTARIO("/com/foodcore/view/InventarioView.fxml", "Inicio > Backoffice > Inventario de Insumos"),
    INVENTARIO_FISICO("/com/foodcore/view/InventarioFisicoView.fxml", "Inicio > Backoffice > Toma de Inventario Físico"),
    MENU("/com/foodcore/view/MenuView.fxml", "Inicio > Backoffice > Menú y Platillos"),
    RECETAS("/com/foodcore/view/RecetasView.fxml", "Inicio > Backoffice > Recetas y Escandallo"),
    MERMAS("/com/foodcore/view/MermasView.fxml", "Inicio > Backoffice > Registro de Mermas"),
    ANALISIS_MERMAS("/com/foodcore/view/AnalisisMermasView.fxml", "Inicio > Backoffice > Análisis de Mermas"),
    KARDEX("/com/foodcore/view/KardexView.fxml", "Inicio > Backoffice > Auditoría Kardex"),
    AUDITORIA("/com/foodcore/view/AuditoriaView.fxml", "Inicio > Backoffice > Bitácora de Auditoría"),
    USUARIOS("/com/foodcore/view/UsuariosView.fxml", "Inicio > Sistema > Gestión de Usuarios"),
    ROLES("/com/foodcore/view/RolesView.fxml", "Inicio > Sistema > Roles y Permisos"),
    CONFIGURACION("/com/foodcore/view/ConfiguracionView.fxml", "Inicio > Sistema > Configuración del Sistema"),
    ESTADO_SISTEMA("/com/foodcore/view/EstadoSistemaView.fxml", "Inicio > Sistema > Estado del Sistema"),
    MANTENIMIENTO("/com/foodcore/view/MantenimientoBDView.fxml", "Inicio > Sistema > Mantenimiento BD");

    private final String fxmlPath;
    private final String breadcrumb;

    NavDestination(String fxmlPath, String breadcrumb) {
        this.fxmlPath = fxmlPath;
        this.breadcrumb = breadcrumb;
    }

    public String getFxmlPath() {
        return fxmlPath;
    }

    public String getBreadcrumb() {
        return breadcrumb;
    }
}