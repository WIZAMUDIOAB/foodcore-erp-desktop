package com.foodcore.config;

import com.foodcore.dao.AuditoriaDAO;
import com.foodcore.dao.CajaDAO;
import com.foodcore.dao.CategoriaDAO;
import com.foodcore.dao.CategoriaDAOImpl;
import com.foodcore.dao.CierreContableDAO;
import com.foodcore.dao.ClienteDAO;
import com.foodcore.dao.ComboDAO;
import com.foodcore.dao.CuentaPorCobrarDAO;
import com.foodcore.dao.CuentaPorPagarDAO;
import com.foodcore.dao.DocumentoEmpleadoDAO;
import com.foodcore.dao.ExpedienteUsuarioDAO;
import com.foodcore.dao.FidelizacionDAO;
import com.foodcore.dao.FinanzasBiDAO;
import com.foodcore.dao.HistorialPrecioDAO;
import com.foodcore.dao.InsumoDAO;
import com.foodcore.dao.MesaDAO;
import com.foodcore.dao.MovimientoPagoDAO;
import com.foodcore.dao.PedidoDAO;
import com.foodcore.dao.ProductoDAO;
import com.foodcore.dao.ProductoDAOImpl;
import com.foodcore.dao.ProductoPersonalizacionDAO;
import com.foodcore.dao.PromocionDAO;
import com.foodcore.dao.ProveedorDAO;
import com.foodcore.dao.ReservaDAO;
import com.foodcore.dao.SolicitudAusenciaDAO;
import com.foodcore.dao.TurnoProgramadoDAO;
import com.foodcore.dao.UsuarioDAO;
import com.foodcore.dao.VacacionesDAO;
import com.foodcore.dao.VentaDAO;
import com.foodcore.dao.impl.AsistenciaDAOImpl;
import com.foodcore.dao.impl.AuditoriaDAOImpl;
import com.foodcore.dao.impl.CajaDAOImpl;
import com.foodcore.dao.impl.CierreContableDAOImpl;
import com.foodcore.dao.impl.ClienteDAOImpl;
import com.foodcore.dao.impl.ComboDAOImpl;
import com.foodcore.dao.impl.CompraDAOImpl;
import com.foodcore.dao.impl.CuentaPorCobrarDAOImpl;
import com.foodcore.dao.impl.CuentaPorPagarDAOImpl;
import com.foodcore.dao.impl.DocumentoEmpleadoDAOImpl;
import com.foodcore.dao.impl.ExpedienteUsuarioDAOImpl;
import com.foodcore.dao.impl.FidelizacionDAOImpl;
import com.foodcore.dao.impl.FinanzasBiDAOImpl;
import com.foodcore.dao.impl.HistorialPrecioDAOImpl;
import com.foodcore.dao.impl.InsumoDAOImpl;
import com.foodcore.dao.impl.MesaDAOImpl;
import com.foodcore.dao.impl.MovimientoPagoDAOImpl;
import com.foodcore.dao.impl.PedidoDAOImpl;
import com.foodcore.dao.impl.ProductoPersonalizacionDAOImpl;
import com.foodcore.dao.impl.PromocionDAOImpl;
import com.foodcore.dao.impl.ProveedorDAOImpl;
import com.foodcore.dao.impl.ReservaDAOImpl;
import com.foodcore.dao.impl.SolicitudAusenciaDAOImpl;
import com.foodcore.dao.impl.TurnoProgramadoDAOImpl;
import com.foodcore.dao.impl.UsuarioDAOImpl;
import com.foodcore.dao.impl.VacacionesDAOImpl;
import com.foodcore.dao.impl.VentaDAOImpl;
import com.foodcore.security.AutorizacionService;
import com.foodcore.security.EncriptadorPassword;
import com.foodcore.security.TotpService;
import com.foodcore.service.ActaEntregaTurnoService;
import com.foodcore.service.AsistenciaService;
import com.foodcore.service.AuditoriaConsultaService;
import com.foodcore.service.AusenciasVacacionesService;
import com.foodcore.service.AuthService;
import com.foodcore.service.CajaService;
import com.foodcore.service.CierreContableService;
import com.foodcore.service.ClienteGestionService;
import com.foodcore.service.ComboService;
import com.foodcore.service.CuentasPorCobrarService;
import com.foodcore.service.CuentasPorPagarService;
import com.foodcore.service.ExpedienteEmpleadoService;
import com.foodcore.service.ExportacionContableService;
import com.foodcore.service.FidelizacionService;
import com.foodcore.service.FinanzasBiService;
import com.foodcore.service.FinanzasService;
import com.foodcore.service.HistorialPreciosService;
import com.foodcore.service.HorariosService;
import com.foodcore.service.ImportacionMasivaService;
import com.foodcore.service.MenuGestionService;
import com.foodcore.service.MozoOrdenService;
import com.foodcore.service.OnboardingService;
import com.foodcore.service.PedidoService;
import com.foodcore.service.PersonalizacionProductoService;
import com.foodcore.service.PlanillaService;
import com.foodcore.service.PromocionService;
import com.foodcore.service.ProveedorGestionService;
import com.foodcore.service.ReporteService;
import com.foodcore.service.ReservasService;
import com.foodcore.service.SalonService;
import com.foodcore.service.SolicitudAusenciaConsultaService;
import com.foodcore.service.SupervisorAutorizacionService;
import com.foodcore.service.VentaPosConsultaService;
import com.foodcore.service.VentaService;
import com.foodcore.service.impl.AusenciasVacacionesServiceImpl;
import com.foodcore.service.impl.AuthServiceImpl;
import com.foodcore.service.impl.CajaServiceImpl;
import com.foodcore.service.impl.ComboServiceImpl;
import com.foodcore.service.impl.ExpedienteEmpleadoServiceImpl;
import com.foodcore.service.impl.PedidoServiceImpl;
import com.foodcore.service.impl.PersonalizacionProductoServiceImpl;
import com.foodcore.service.impl.PlanillaServiceImpl;
import com.foodcore.service.impl.VentaServiceImpl;
import com.foodcore.tx.TransactionManager;
import com.foodcore.tx.impl.JdbcTransactionManager;
import com.foodcore.util.ImpresoraTermicaService;
import javafx.fxml.FXMLLoader;
import javafx.util.Callback;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Composition Root central de FoodCore ERP (F-ARCH-006):
 * cablea infraestructura transaccional, DAOs y servicios por interfaz/contrato y expone un
 * {@link Callback} {@link #controllerFactory()} para {@link FXMLLoader}.
 */
public final class AppContainer {

    private static final class DefaultHolder {
        private static final AppContainer INSTANCE = new AppContainer();
    }

    private final Function<Class<?>, Supplier<?>> resolver;

    public AppContainer() {
        this(Map.of());
    }

    private AppContainer(Map<Class<?>, Object> overrides) {
        Map<Class<?>, Object> registry = new HashMap<>();
        registrarGrafoBase(registry);
        if (overrides != null && !overrides.isEmpty()) {
            registry.putAll(overrides);
            reconstruirServiciosDependientesSiAplica(registry, overrides);
        }
        Map<Class<?>, Supplier<?>> providers = new HashMap<>();
        for (Map.Entry<Class<?>, Object> entry : registry.entrySet()) {
            Object instance = entry.getValue();
            providers.put(entry.getKey(), () -> instance);
        }
        Map<Class<?>, Supplier<?>> frozen = Map.copyOf(providers);
        this.resolver = frozen::get;
    }

    public static AppContainer getDefault() {
        return DefaultHolder.INSTANCE;
    }

    public static AppContainer withOverrides(Map<Class<?>, Object> overrides) {
        Objects.requireNonNull(overrides, "overrides no puede ser nulo");
        return new AppContainer(overrides);
    }

    private static void registrarGrafoBase(Map<Class<?>, Object> reg) {
        TransactionManager tx = new JdbcTransactionManager();
        reg.put(TransactionManager.class, tx);

        // DAOs por contrato de interfaz
        UsuarioDAO usuarioDAO = new UsuarioDAOImpl();
        CajaDAO cajaDAO = new CajaDAOImpl();
        InsumoDAO insumoDAO = new InsumoDAOImpl();
        PedidoDAO pedidoDAO = new PedidoDAOImpl();
        VentaDAO ventaDAO = new VentaDAOImpl();
        ComboDAO comboDAO = new ComboDAOImpl();
        ClienteDAO clienteDAO = new ClienteDAOImpl();
        CuentaPorCobrarDAO cuentaPorCobrarDAO = new CuentaPorCobrarDAOImpl();
        CuentaPorPagarDAO cuentaPorPagarDAO = new CuentaPorPagarDAOImpl();
        ProveedorDAO proveedorDAO = new ProveedorDAOImpl();
        HistorialPrecioDAO historialPrecioDAO = new HistorialPrecioDAOImpl();
        ProductoDAO productoDAO = new ProductoDAOImpl();
        CategoriaDAO categoriaDAO = new CategoriaDAOImpl();
        ProductoPersonalizacionDAO personalizacionDAO = new ProductoPersonalizacionDAOImpl();
        AuditoriaDAO auditoriaDAO = new AuditoriaDAOImpl();
        CierreContableDAO cierreContableDAO = new CierreContableDAOImpl();
        FidelizacionDAO fidelizacionDAO = new FidelizacionDAOImpl();
        PromocionDAO promocionDAO = new PromocionDAOImpl();
        FinanzasBiDAO finanzasBiDAO = new FinanzasBiDAOImpl();
        MesaDAO mesaDAO = new MesaDAOImpl();
        ReservaDAO reservaDAO = new ReservaDAOImpl();
        ExpedienteUsuarioDAO expedienteUsuarioDAO = new ExpedienteUsuarioDAOImpl();
        DocumentoEmpleadoDAO documentoEmpleadoDAO = new DocumentoEmpleadoDAOImpl();
        MovimientoPagoDAO movimientoPagoDAO = new MovimientoPagoDAOImpl();
        SolicitudAusenciaDAO solicitudAusenciaDAO = new SolicitudAusenciaDAOImpl();
        VacacionesDAO vacacionesDAO = new VacacionesDAOImpl();
        TurnoProgramadoDAO turnoProgramadoDAO = new TurnoProgramadoDAOImpl();

        reg.put(UsuarioDAO.class, usuarioDAO);
        reg.put(CajaDAO.class, cajaDAO);
        reg.put(InsumoDAO.class, insumoDAO);
        reg.put(PedidoDAO.class, pedidoDAO);
        reg.put(VentaDAO.class, ventaDAO);
        reg.put(ComboDAO.class, comboDAO);
        reg.put(ClienteDAO.class, clienteDAO);
        reg.put(CuentaPorCobrarDAO.class, cuentaPorCobrarDAO);
        reg.put(CuentaPorPagarDAO.class, cuentaPorPagarDAO);
        reg.put(ProveedorDAO.class, proveedorDAO);
        reg.put(HistorialPrecioDAO.class, historialPrecioDAO);
        reg.put(ProductoDAO.class, productoDAO);
        reg.put(CategoriaDAO.class, categoriaDAO);
        reg.put(ProductoPersonalizacionDAO.class, personalizacionDAO);
        reg.put(AuditoriaDAO.class, auditoriaDAO);
        reg.put(CierreContableDAO.class, cierreContableDAO);
        reg.put(FidelizacionDAO.class, fidelizacionDAO);
        reg.put(PromocionDAO.class, promocionDAO);
        reg.put(FinanzasBiDAO.class, finanzasBiDAO);
        reg.put(MesaDAO.class, mesaDAO);
        reg.put(ReservaDAO.class, reservaDAO);
        reg.put(ExpedienteUsuarioDAO.class, expedienteUsuarioDAO);
        reg.put(DocumentoEmpleadoDAO.class, documentoEmpleadoDAO);
        reg.put(MovimientoPagoDAO.class, movimientoPagoDAO);
        reg.put(SolicitudAusenciaDAO.class, solicitudAusenciaDAO);
        reg.put(VacacionesDAO.class, vacacionesDAO);
        reg.put(TurnoProgramadoDAO.class, turnoProgramadoDAO);

        // Seguridad e infraestructura sin estado BD en construcción
        EncriptadorPassword encriptador = new EncriptadorPassword();
        AutorizacionService autorizacionService = new AutorizacionService();
        TotpService totpService = new TotpService();
        reg.put(EncriptadorPassword.class, encriptador);
        reg.put(AutorizacionService.class, autorizacionService);
        reg.put(TotpService.class, totpService);
        reg.put(ImpresoraTermicaService.class, new ImpresoraTermicaService());

        // Servicios compartidos
        AuditoriaConsultaService auditoriaConsultaService = new AuditoriaConsultaService(auditoriaDAO);
        CierreContableService cierreContableService = new CierreContableService(tx, cierreContableDAO, auditoriaDAO);
        ActaEntregaTurnoService actaEntregaTurnoService = new ActaEntregaTurnoService();
        SupervisorAutorizacionService supervisorService =
                new SupervisorAutorizacionService(usuarioDAO, encriptador);
        FidelizacionService fidelizacionService = new FidelizacionService(fidelizacionDAO, clienteDAO, tx);

        AuthService authService = new AuthServiceImpl(usuarioDAO, encriptador);
        CajaService cajaService = new CajaServiceImpl(
                tx, cajaDAO, insumoDAO, cierreContableService, actaEntregaTurnoService);
        PedidoService pedidoService = new PedidoServiceImpl(tx, pedidoDAO, autorizacionService);
        VentaService ventaService = new VentaServiceImpl(
                tx, ventaDAO, cajaDAO, insumoDAO, pedidoDAO, comboDAO,
                clienteDAO, cuentaPorCobrarDAO, auditoriaDAO,
                autorizacionService, fidelizacionService, cierreContableService, supervisorService);
        FinanzasService finanzasService = new FinanzasService(
                tx, proveedorDAO, cuentaPorPagarDAO, clienteDAO, cuentaPorCobrarDAO,
                historialPrecioDAO, insumoDAO, productoDAO, cajaDAO, ventaDAO, cierreContableService);

        PersonalizacionProductoService personalizacionService =
                new PersonalizacionProductoServiceImpl(tx, productoDAO, personalizacionDAO, comboDAO);
        ComboService comboService = new ComboServiceImpl(tx, comboDAO, productoDAO, categoriaDAO);
        PlanillaService planillaService =
                new PlanillaServiceImpl(expedienteUsuarioDAO, movimientoPagoDAO, cierreContableService);
        ExpedienteEmpleadoService expedienteService = new ExpedienteEmpleadoServiceImpl(
                expedienteUsuarioDAO, documentoEmpleadoDAO, Paths.get("uploads", "expedientes"));
        AusenciasVacacionesService ausenciasService =
                new AusenciasVacacionesServiceImpl(solicitudAusenciaDAO, vacacionesDAO, expedienteUsuarioDAO);

        reg.put(AuditoriaConsultaService.class, auditoriaConsultaService);
        reg.put(CierreContableService.class, cierreContableService);
        reg.put(ActaEntregaTurnoService.class, actaEntregaTurnoService);
        reg.put(SupervisorAutorizacionService.class, supervisorService);
        reg.put(FidelizacionService.class, fidelizacionService);
        reg.put(AuthService.class, authService);
        reg.put(CajaService.class, cajaService);
        reg.put(PedidoService.class, pedidoService);
        reg.put(VentaService.class, ventaService);
        reg.put(FinanzasService.class, finanzasService);
        reg.put(PersonalizacionProductoService.class, personalizacionService);
        reg.put(ComboService.class, comboService);
        reg.put(PlanillaService.class, planillaService);
        reg.put(ExpedienteEmpleadoService.class, expedienteService);
        reg.put(AusenciasVacacionesService.class, ausenciasService);

        // Servicios de soporte a controladores
        reg.put(PromocionService.class, new PromocionService(promocionDAO));
        reg.put(VentaPosConsultaService.class, new VentaPosConsultaService());
        reg.put(MozoOrdenService.class, new MozoOrdenService());
        reg.put(OnboardingService.class, new OnboardingService());
        reg.put(ClienteGestionService.class, new ClienteGestionService(clienteDAO));
        reg.put(ProveedorGestionService.class, new ProveedorGestionService());
        reg.put(CuentasPorCobrarService.class, new CuentasPorCobrarService(cuentaPorCobrarDAO, cajaDAO));
        reg.put(CuentasPorPagarService.class, new CuentasPorPagarService());
        reg.put(HistorialPreciosService.class, new HistorialPreciosService());
        reg.put(FinanzasBiService.class, new FinanzasBiService(finanzasBiDAO));
        reg.put(ExportacionContableService.class,
                new ExportacionContableService(ventaDAO, new CompraDAOImpl(), cajaDAO));
        reg.put(ReporteService.class, new ReporteService());
        reg.put(SalonService.class, new SalonService(mesaDAO));
        reg.put(ReservasService.class, new ReservasService(reservaDAO));
        reg.put(MenuGestionService.class, new MenuGestionService());
        reg.put(ImportacionMasivaService.class,
                new ImportacionMasivaService(productoDAO, categoriaDAO, insumoDAO, tx));
        reg.put(HorariosService.class, new HorariosService(turnoProgramadoDAO, usuarioDAO));
        reg.put(AsistenciaService.class,
                new AsistenciaService(new AsistenciaDAOImpl(), usuarioDAO, turnoProgramadoDAO));
        reg.put(SolicitudAusenciaConsultaService.class, new SolicitudAusenciaConsultaService());
    }

    private static void reconstruirServiciosDependientesSiAplica(
            Map<Class<?>, Object> reg, Map<Class<?>, Object> overrides) {
        if (overrides.containsKey(TransactionManager.class) && !overrides.containsKey(FinanzasService.class)) {
            TransactionManager tx = (TransactionManager) reg.get(TransactionManager.class);
            reg.put(FinanzasService.class, new FinanzasService(
                    tx,
                    (ProveedorDAO) reg.get(ProveedorDAO.class),
                    (CuentaPorPagarDAO) reg.get(CuentaPorPagarDAO.class),
                    (ClienteDAO) reg.get(ClienteDAO.class),
                    (CuentaPorCobrarDAO) reg.get(CuentaPorCobrarDAO.class),
                    (HistorialPrecioDAO) reg.get(HistorialPrecioDAO.class),
                    (InsumoDAO) reg.get(InsumoDAO.class),
                    (ProductoDAO) reg.get(ProductoDAO.class),
                    (CajaDAO) reg.get(CajaDAO.class),
                    (VentaDAO) reg.get(VentaDAO.class),
                    (CierreContableService) reg.get(CierreContableService.class)));
        }
    }

    public boolean canResolve(Class<?> type) {
        return type == AppContainer.class || resolver.apply(type) != null;
    }

    public <T> T resolve(Class<T> type) {
        Objects.requireNonNull(type, "type no puede ser nulo");
        if (type == AppContainer.class) {
            return type.cast(this);
        }
        Supplier<?> supplier = resolver.apply(type);
        if (supplier == null) {
            throw new IllegalArgumentException("Tipo no registrado en AppContainer: " + type.getName());
        }
        return type.cast(supplier.get());
    }

    public Callback<Class<?>, Object> controllerFactory() {
        return this::instantiateController;
    }

    public FXMLLoader createLoader(URL location) {
        FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(location, "location no puede ser nula"));
        loader.setControllerFactory(controllerFactory());
        return loader;
    }

    private Object instantiateController(Class<?> controllerClass) {
        Objects.requireNonNull(controllerClass, "controllerClass no puede ser nulo");
        Constructor<?>[] constructors = controllerClass.getConstructors();
        Arrays.sort(constructors, Comparator.<Constructor<?>>comparingInt(Constructor::getParameterCount).reversed());
        for (Constructor<?> ctor : constructors) {
            Class<?>[] params = ctor.getParameterTypes();
            if (todosResolvibles(params)) {
                Object[] args = new Object[params.length];
                for (int i = 0; i < params.length; i++) {
                    args[i] = resolve(params[i]);
                }
                return invocarConstructor(controllerClass, ctor, args);
            }
        }
        try {
            Constructor<?> noArg = controllerClass.getDeclaredConstructor();
            return invocarConstructor(controllerClass, noArg);
        } catch (NoSuchMethodException ex) {
            throw new IllegalStateException(
                    "No se encontró constructor resoluble para controlador " + controllerClass.getName(), ex);
        }
    }

    private boolean todosResolvibles(Class<?>[] params) {
        for (Class<?> param : params) {
            if (!canResolve(param)) {
                return false;
            }
        }
        return true;
    }

    private static Object invocarConstructor(Class<?> controllerClass, Constructor<?> ctor, Object... args) {
        try {
            return ctor.newInstance(args);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalStateException(
                    "No se pudo instanciar controlador " + controllerClass.getName(), ex);
        }
    }

    public TransactionManager transactionManager() {
        return resolve(TransactionManager.class);
    }

    public AuthService authService() {
        return resolve(AuthService.class);
    }

    public VentaService ventaService() {
        return resolve(VentaService.class);
    }

    public CajaService cajaService() {
        return resolve(CajaService.class);
    }

    public PedidoService pedidoService() {
        return resolve(PedidoService.class);
    }

    public FinanzasService finanzasService() {
        return resolve(FinanzasService.class);
    }

    public FidelizacionService fidelizacionService() {
        return resolve(FidelizacionService.class);
    }

    public AuditoriaConsultaService auditoriaConsultaService() {
        return resolve(AuditoriaConsultaService.class);
    }

    public PersonalizacionProductoService personalizacionProductoService() {
        return resolve(PersonalizacionProductoService.class);
    }

    public ComboService comboService() {
        return resolve(ComboService.class);
    }

    public PlanillaService planillaService() {
        return resolve(PlanillaService.class);
    }

    public ExpedienteEmpleadoService expedienteEmpleadoService() {
        return resolve(ExpedienteEmpleadoService.class);
    }

    public AusenciasVacacionesService ausenciasVacacionesService() {
        return resolve(AusenciasVacacionesService.class);
    }
}
