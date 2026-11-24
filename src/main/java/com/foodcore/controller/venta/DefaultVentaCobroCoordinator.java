package com.foodcore.controller.venta;

import com.foodcore.exception.CajaCerradaException;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Caja;
import com.foodcore.model.ComprobantePago;
import com.foodcore.model.DesgloseFiscal;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Pedido;
import com.foodcore.model.Usuario;
import com.foodcore.model.Venta;
import com.foodcore.model.dto.PagoSplit;
import com.foodcore.model.enums.Permiso;
import com.foodcore.security.AutorizacionService;
import com.foodcore.security.SesionManager;
import com.foodcore.security.SesionUsuario;
import com.foodcore.service.PedidoService;
import com.foodcore.service.VentaService;
import com.foodcore.service.impl.CajaServiceImpl;
import com.foodcore.util.ImpresoraTermicaService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DefaultVentaCobroCoordinator implements VentaCobroCoordinator {
    private static final Logger LOGGER = Logger.getLogger(DefaultVentaCobroCoordinator.class.getName());

    @FunctionalInterface
    public interface CajaActivaResolver {
        Optional<Caja> obtenerCajaActiva() throws SQLException;
    }

    private final VentaService ventaService;
    private final AutorizacionService autorizacionService;
    private final Supplier<ImpresoraTermicaService> impresoraSupplier;
    private final CajaActivaResolver cajaActivaResolver;

    public DefaultVentaCobroCoordinator(VentaService ventaService, AutorizacionService autorizacionService) {
        this(ventaService, autorizacionService, ImpresoraTermicaService::new,
                () -> new CajaServiceImpl().obtenerCajaActiva());
    }

    public DefaultVentaCobroCoordinator(VentaService ventaService, AutorizacionService autorizacionService,
            Supplier<ImpresoraTermicaService> impresoraSupplier) {
        this(ventaService, autorizacionService, impresoraSupplier, () -> new CajaServiceImpl().obtenerCajaActiva());
    }

    public DefaultVentaCobroCoordinator(VentaService ventaService, AutorizacionService autorizacionService,
            Supplier<ImpresoraTermicaService> impresoraSupplier, CajaActivaResolver cajaActivaResolver) {
        this.ventaService = ventaService;
        this.autorizacionService = autorizacionService;
        this.impresoraSupplier = impresoraSupplier;
        this.cajaActivaResolver = Objects.requireNonNull(cajaActivaResolver);
    }

    @Override
    public List<PagoSplit> construirPagosPorModalidad(String modalidad, BigDecimal totalNeto,
            List<DetalleVenta> items) {
        if (totalNeto == null || totalNeto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El total neto a dividir debe ser mayor a cero.");
        }
        BigDecimal total = totalNeto.setScale(2, RoundingMode.HALF_UP);
        if (MODALIDAD_SPLIT_4.equals(modalidad)) {
            BigDecimal cuarta = total.divide(new BigDecimal("4"), 2, RoundingMode.HALF_UP);
            BigDecimal acum = cuarta.multiply(new BigDecimal("3"));
            BigDecimal ultimo = total.subtract(acum);
            if (ultimo.compareTo(BigDecimal.ZERO) <= 0) {
                cuarta = total.divide(new BigDecimal("4"), 2, RoundingMode.DOWN);
                ultimo = total.subtract(cuarta.multiply(new BigDecimal("3")));
            }
            List<PagoSplit> pagos4 = new ArrayList<>();
            String[] metodos = {"EFECTIVO", "TARJETA", "YAPE_PLIN", "EFECTIVO"};
            BigDecimal[] montos = {cuarta, cuarta, cuarta, ultimo};
            for (int i = 0; i < montos.length; i++) {
                if (montos[i].compareTo(BigDecimal.ZERO) > 0) {
                    pagos4.add(new PagoSplit(metodos[i], montos[i]));
                }
            }
            return pagos4;
        }
        if (MODALIDAD_SPLIT_ITEMS.equals(modalidad) && items != null && !items.isEmpty()) {
            BigDecimal brutoA = BigDecimal.ZERO;
            BigDecimal brutoB = BigDecimal.ZERO;
            if (items.size() >= 2) {
                for (int i = 0; i < items.size(); i++) {
                    BigDecimal sub = items.get(i) != null && items.get(i).getSubtotal() != null
                            ? items.get(i).getSubtotal() : BigDecimal.ZERO;
                    if (i % 2 == 0) {
                        brutoA = brutoA.add(sub);
                    } else {
                        brutoB = brutoB.add(sub);
                    }
                }
            } else {
                DetalleVenta unico = items.get(0);
                int qty = unico != null ? unico.getCantidad() : 1;
                if (qty >= 2 && unico.getPrecioUnitario() != null) {
                    int qtyA = (qty + 1) / 2;
                    int qtyB = qty - qtyA;
                    brutoA = unico.getPrecioUnitario().multiply(BigDecimal.valueOf(qtyA));
                    brutoB = unico.getPrecioUnitario().multiply(BigDecimal.valueOf(qtyB));
                }
            }
            BigDecimal sumaBruta = brutoA.add(brutoB);
            if (sumaBruta.compareTo(BigDecimal.ZERO) > 0
                    && brutoA.compareTo(BigDecimal.ZERO) > 0 && brutoB.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal montoA = total.multiply(brutoA).divide(sumaBruta, 2, RoundingMode.HALF_UP);
                BigDecimal montoB = total.subtract(montoA);
                if (montoA.compareTo(BigDecimal.ZERO) > 0 && montoB.compareTo(BigDecimal.ZERO) > 0) {
                    return List.of(new PagoSplit("EFECTIVO", montoA),
                            new PagoSplit("TARJETA", montoB));
                }
            }
        }
        BigDecimal mitad = total.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
        BigDecimal resto = total.subtract(mitad);
        return List.of(new PagoSplit("EFECTIVO", mitad),
                new PagoSplit("TARJETA", resto));
    }

    @Override
    public Venta construirVentaParaCobro(Integer pedidoActivoId, int idUsuario, Integer idCliente,
            BigDecimal basePreCanje, BigDecimal totalNeto, String metodoPago, BigDecimal descuentoActivo,
            BigDecimal descuentoPuntosActivo, int puntosARedimir) throws NegocioException {
        return crearVentaConCaja(pedidoActivoId, 1, idUsuario, idCliente, basePreCanje,
                totalNeto, metodoPago, descuentoActivo, descuentoPuntosActivo, puntosARedimir);
    }

    @Override
    public Venta registrarVentaDividida(Integer pedidoActivoId, int idUsuario, Integer idCliente,
            BigDecimal basePreCanje, BigDecimal totalNeto, BigDecimal descuentoActivo,
            BigDecimal descuentoPuntosActivo, int puntosARedimir, List<DetalleVenta> items,
            List<PagoSplit> pagos) throws Exception {
        int idCajaActiva = resolverIdCajaActiva();
        Venta venta = crearVentaConCaja(pedidoActivoId, idCajaActiva, idUsuario, idCliente, basePreCanje,
                totalNeto, "MIXTO", descuentoActivo, descuentoPuntosActivo, puntosARedimir);
        String corrSplit = generarCorrelativo();
        ComprobantePago comprobante = new ComprobantePago(0, 0, "TICKET", "SPL", corrSplit, totalNeto);
        return ventaService.registrarVentaDividida(venta, new ArrayList<>(items), comprobante, pagos);
    }

    @Override
    public void procesarCobroDirecto(Integer pedidoActivoId, Integer idCliente, BigDecimal basePreCanje,
            BigDecimal totalNeto, String metodo, BigDecimal descuentoActivo, BigDecimal descuentoPuntosActivo,
            int puntosARedimir, List<DetalleVenta> items) throws Exception {
        Usuario usr = SesionManager.getUsuarioActual();
        int idUsuario = (usr != null && usr.getIdUsuario() > 0) ? usr.getIdUsuario() : 1;
        int idCajaActiva = resolverIdCajaActiva();
        Venta venta = crearVentaConCaja(pedidoActivoId, idCajaActiva, idUsuario, idCliente, basePreCanje, totalNeto,
                metodo, descuentoActivo, descuentoPuntosActivo, puntosARedimir);
        String corrTicket = generarCorrelativo();
        ComprobantePago comprobante = new ComprobantePago(0, 0, "TICKET", "TCK", corrTicket, totalNeto);
        ventaService.procesarCobroVenta(venta, new ArrayList<>(items), comprobante);
    }

    @Override
    public ResultadoEnvioComanda enviarComandaACocina(PedidoService pedidoServiceActual, Pedido pedido,
            List<DetallePedido> detallesCocina) throws Exception {
        Pedido pedidoFinal = pedido;
        if (pedido.getIdPedido() <= 0) {
            pedidoFinal = pedidoServiceActual.registrarPedidoYEnviarComanda(pedido, detallesCocina);
        }
        boolean impresionOk = true;
        try {
            impresoraSupplier.get().imprimirComandaPorCategorias(pedidoFinal, detallesCocina);
        } catch (Exception exImp) {
            impresionOk = false;
            LOGGER.log(Level.WARNING, "Comanda persistida pero falló la impresión térmica", exImp);
        }
        return new ResultadoEnvioComanda(pedidoFinal, impresionOk);
    }

    @Override
    public ResultadoItemPedido agregarItemAPedidoExistente(PedidoService pedidoServiceActual, int idPedido,
            DetallePedido nuevoItem) throws NegocioException, SQLException {
        Pedido actualizado = pedidoServiceActual.agregarItemsAPedido(idPedido, List.of(nuevoItem));
        BigDecimal totalPedido = pedidoServiceActual.calcularTotal(idPedido);
        actualizado.setTotal(totalPedido);
        List<DetallePedido> detallesCompletos = actualizado.getDetalles() != null
                ? actualizado.getDetalles()
                : pedidoServiceActual.listarDetallesPorPedido(idPedido);
        return new ResultadoItemPedido(totalPedido, detallesCompletos);
    }

    @Override
    public void imprimirPreCuenta(Pedido pedido, List<DetallePedido> detalles) throws Exception {
        impresoraSupplier.get().imprimirPreCuenta(pedido, detalles, null);
    }

    @Override
    public ValidacionAnulacionSupervisor validarAnulacionSupervisor(String idRaw, String motivo) {
        if (idRaw == null || idRaw.trim().isEmpty()) {
            return new ValidacionAnulacionSupervisor(false, 0, null, "Dato requerido",
                    "Debe indicar el ID de la venta a anular.");
        }
        final int idVenta;
        try {
            idVenta = Integer.parseInt(idRaw.trim());
        } catch (NumberFormatException ex) {
            return new ValidacionAnulacionSupervisor(false, 0, null, "Dato inválido",
                    "El ID de la venta debe ser numérico.");
        }
        String motivoFinal = motivo == null || motivo.trim().isEmpty()
                ? "Anulación autorizada en caja (Modo Supervisor)" : motivo.trim();
        return new ValidacionAnulacionSupervisor(true, idVenta, motivoFinal, null, null);
    }

    @Override
    public Usuario anularVentaConSupervisor(int idVenta, boolean yaPreparado, SesionUsuario sesion,
            String pinSupervisor, String motivo) throws Exception {
        return ventaService.anularVentaConSupervisor(idVenta, yaPreparado, sesion, pinSupervisor, motivo);
    }

    @Override
    public void anularVentaDirecta(int idVenta, SesionUsuario sesion, String motivo) throws Exception {
        ventaService.anularVenta(idVenta, false, sesion, motivo);
    }

    @Override
    public boolean tienePermiso(SesionUsuario sesion, Permiso permiso) {
        return autorizacionService.tienePermiso(sesion, permiso);
    }

    @Override
    public SesionUsuario crearSesionActualPOS(Usuario usr) {
        String codigoRol = usr != null && usr.getNombreRol() != null ? usr.getNombreRol() : "CAJERO";
        long idUsr = usr != null ? usr.getIdUsuario() : 2L;
        String nombreUsr = usr != null && usr.getUsername() != null ? usr.getUsername() : "usuario";
        return autorizacionService.crearSesion(idUsr, nombreUsr, codigoRol, "POS-01");
    }

    private Venta crearVentaConCaja(Integer pedidoActivoId, int idCajaActiva, int idUsuario, Integer idCliente,
            BigDecimal basePreCanje, BigDecimal totalNeto, String metodoPago, BigDecimal descuentoActivo,
            BigDecimal descuentoPuntosActivo, int puntosARedimir) throws NegocioException {
        DesgloseFiscal desglose = DesgloseFiscal.desdeTotalConImpuestos(basePreCanje);
        Venta venta = new Venta(0, pedidoActivoId, idCajaActiva, idUsuario, idCliente,
                desglose.getSubtotalGravado(), desglose.getIgv(), totalNeto, metodoPago, totalNeto, BigDecimal.ZERO);
        venta.setDescuento(descuentoActivo);
        venta.setDescuentoPuntos(descuentoPuntosActivo);
        venta.setPuntosACanjear(puntosARedimir);
        venta.setIdempotencyKey(UUID.randomUUID().toString());
        return venta;
    }

    private int resolverIdCajaActiva() throws SQLException, NegocioException {
        return cajaActivaResolver.obtenerCajaActiva()
                .map(Caja::getIdCaja)
                .orElseThrow(() -> new CajaCerradaException(
                        "No hay una caja abierta para registrar la operación de cobro."));
    }

    private static String generarCorrelativo() {
        return String.format("%06d", Math.floorMod(UUID.randomUUID().hashCode(), 1_000_000));
    }
}
