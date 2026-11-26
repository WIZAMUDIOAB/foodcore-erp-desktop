package com.foodcore.service.api;

import com.foodcore.dao.MesaDAO;
import com.foodcore.dao.PedidoDAO;
import com.foodcore.dao.ProductoDAO;
import com.foodcore.dao.VentaDAO;
import com.foodcore.exception.CajaCerradaException;
import com.foodcore.exception.ConflictoEstadoException;
import com.foodcore.exception.InsumoNoEncontradoException;
import com.foodcore.exception.NegocioException;
import com.foodcore.exception.StockInsuficienteException;
import com.foodcore.model.Caja;
import com.foodcore.model.ComprobantePago;
import com.foodcore.model.DesgloseFiscal;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Pedido;
import com.foodcore.model.Producto;
import com.foodcore.model.Venta;
import com.foodcore.service.CajaService;
import com.foodcore.service.PedidoService;
import com.foodcore.service.VentaService;
import com.foodcore.util.ConexionBD;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementación por defecto de {@link ApiOperacionesPort} apoyada en DAOs y servicios transaccionales.
 */
public class DefaultApiOperacionesService implements ApiOperacionesPort {

    private static final Logger LOGGER = Logger.getLogger(DefaultApiOperacionesService.class.getName());
    private static final Set<String> ESTADOS_PEDIDO_TERMINALES = Set.of(
            "COBRADO", "PAGADO", "CERRADO", "CANCELADO", "ANULADO"
    );

    private final ProductoDAO productoDAO;
    private final MesaDAO mesaDAO;
    private final PedidoDAO pedidoDAO;
    private final VentaDAO ventaDAO;
    private final PedidoService pedidoService;
    private final CajaService cajaService;
    private final VentaService ventaService;
    private final ConnectionProvider connectionProvider;

    @FunctionalInterface
    interface ConnectionProvider {
        Connection getConnection() throws SQLException;
    }

    public DefaultApiOperacionesService(ProductoDAO productoDAO,
                                        MesaDAO mesaDAO,
                                        PedidoDAO pedidoDAO,
                                        VentaDAO ventaDAO,
                                        PedidoService pedidoService,
                                        CajaService cajaService,
                                        VentaService ventaService) {
        this(productoDAO, mesaDAO, pedidoDAO, ventaDAO, pedidoService, cajaService, ventaService,
                ConexionBD::getConnection);
    }

    DefaultApiOperacionesService(ProductoDAO productoDAO,
                                 MesaDAO mesaDAO,
                                 PedidoDAO pedidoDAO,
                                 VentaDAO ventaDAO,
                                 PedidoService pedidoService,
                                 CajaService cajaService,
                                 VentaService ventaService,
                                 ConnectionProvider connectionProvider) {
        this.productoDAO = Objects.requireNonNull(productoDAO);
        this.mesaDAO = Objects.requireNonNull(mesaDAO);
        this.pedidoDAO = Objects.requireNonNull(pedidoDAO);
        this.ventaDAO = Objects.requireNonNull(ventaDAO);
        this.pedidoService = Objects.requireNonNull(pedidoService);
        this.cajaService = Objects.requireNonNull(cajaService);
        this.ventaService = Objects.requireNonNull(ventaService);
        this.connectionProvider = Objects.requireNonNull(connectionProvider);
    }

    @Override
    public List<Producto> listarCatalogoProductos(boolean soloDisponibles) {
        List<Producto> productos = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection()) {
            if (conn != null) {
                productos = soloDisponibles
                        ? productoDAO.listarProductosDisponibles(conn)
                        : productoDAO.listarTodos(conn);
                if (!soloDisponibles && productos.isEmpty()) {
                    productos = productoDAO.listarProductosDisponibles(conn);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Error consultando catálogo de productos en BD", e);
        }
        return productos;
    }

    @Override
    public void actualizarDisponibilidadProducto(int idProducto, boolean disponible) throws ApiOperacionException {
        try (Connection conn = connectionProvider.getConnection()) {
            if (conn == null) {
                throw new ApiOperacionException(503, "Servicio de base de datos no disponible.");
            }
            productoDAO.cambiarDisponibilidad(conn, idProducto, disponible);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error SQL actualizando disponibilidad de producto #" + idProducto, e);
            throw new ApiOperacionException(503, "No se pudo actualizar la disponibilidad en base de datos.", e);
        }
    }

    @Override
    public Optional<String> obtenerImagenUrlProducto(int idProducto) {
        try (Connection conn = connectionProvider.getConnection()) {
            if (conn != null) {
                return productoDAO.obtenerImagenUrlPorId(conn, idProducto);
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "No se pudo consultar ruta de imagen de producto #" + idProducto, e);
        }
        return Optional.empty();
    }

    @Override
    public EstadoMesasSnapshot obtenerEstadoMesas() {
        Map<String, String> estadosPorMesa = new LinkedHashMap<>();
        Map<String, BigDecimal> totalesPorMesa = new LinkedHashMap<>();
        try (Connection conn = connectionProvider.getConnection()) {
            if (conn != null) {
                Map<String, String> dbEstados = mesaDAO.obtenerEstadoTodasMesas(conn);
                estadosPorMesa.putAll(dbEstados);

                Map<String, BigDecimal> activosPorMesa = pedidoDAO.obtenerTotalesPedidosActivosPorMesa(conn);
                for (Map.Entry<String, BigDecimal> entry : activosPorMesa.entrySet()) {
                    estadosPorMesa.put(entry.getKey(), "OCUPADA");
                    totalesPorMesa.put(entry.getKey(), entry.getValue());
                }
            }
        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.FINE, "No se pudo consultar tabla mesa en GET /api/mesas", e);
        }
        return new EstadoMesasSnapshot(estadosPorMesa, totalesPorMesa);
    }

    @Override
    public ResultadoCreacionPedido crearPedidoOIdempotente(int idUsuario, String numeroMesa, String uuidLocal,
            List<ItemSolicitudPedido> items) throws ApiOperacionException {
        List<DetallePedido> detalles = new ArrayList<>();
        BigDecimal totalCalculado = BigDecimal.ZERO;

        try (Connection conn = connectionProvider.getConnection()) {
            if (conn == null) {
                throw new ApiOperacionException(503, "Base de datos no disponible para registrar pedidos.");
            }
            Optional<Pedido> optExistente = pedidoDAO.buscarPorIdempotencyKey(conn, uuidLocal);
            if (optExistente.isPresent()) {
                Pedido existente = optExistente.get();
                List<DetallePedido> detExistentes = pedidoDAO.listarDetallesPorPedido(conn, existente.getIdPedido());
                return new ResultadoCreacionPedido(existente, detExistentes, true);
            }

            Map<Integer, Producto> catalogoPorId = new HashMap<>();
            for (Producto p : productoDAO.listarTodos(conn)) {
                catalogoPorId.put(p.getIdProducto(), p);
            }

            for (ItemSolicitudPedido item : items) {
                Producto prodAutoritativo = catalogoPorId.get(item.idProducto());
                if (prodAutoritativo == null) {
                    throw new ApiOperacionException(422, "Producto no encontrado en catálogo: #" + item.idProducto());
                }
                if (!prodAutoritativo.isDisponible()) {
                    throw new ApiOperacionException(422,
                            "Producto no disponible actualmente: " + prodAutoritativo.getNombre());
                }
                BigDecimal precioAutoritativo = prodAutoritativo.getPrecioVenta();
                if (precioAutoritativo == null || precioAutoritativo.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new ApiOperacionException(422,
                            "El producto #" + item.idProducto() + " no tiene un precio válido configurado.");
                }
                BigDecimal precioUnit = precioAutoritativo.setScale(2, RoundingMode.HALF_UP);
                BigDecimal subtotal = precioUnit.multiply(BigDecimal.valueOf(item.cantidad()))
                        .setScale(2, RoundingMode.HALF_UP);
                DetallePedido det = new DetallePedido(0, 0, item.idProducto(), prodAutoritativo.getNombre(),
                        item.cantidad(), precioUnit, subtotal, item.notas() != null ? item.notas() : "");
                detalles.add(det);
                totalCalculado = totalCalculado.add(subtotal);
            }
        } catch (SQLException exSql) {
            LOGGER.log(Level.WARNING, "Error SQL validando catálogo/idempotencia en POST /api/pedidos", exSql);
            throw new ApiOperacionException(503, "Servicio de base de datos no disponible.", exSql);
        }

        Pedido nuevoPedido = construirNuevoPedido(idUsuario, numeroMesa, uuidLocal, totalCalculado);
        Pedido guardado;
        try {
            guardado = pedidoService.registrarPedidoYEnviarComanda(nuevoPedido, detalles);
        } catch (ConflictoEstadoException exConf) {
            throw new ApiOperacionException(409,
                    exConf.getMessage() != null ? exConf.getMessage() : "Conflicto de estado del pedido.", exConf);
        } catch (NegocioException exNeg) {
            throw new ApiOperacionException(422,
                    exNeg.getMessage() != null ? exNeg.getMessage() : "Validación de negocio fallida.", exNeg);
        } catch (SQLException exDb) {
            LOGGER.log(Level.WARNING, "Error persistiendo pedido en POST /api/pedidos", exDb);
            throw new ApiOperacionException(503, "No se pudo persistir el pedido en base de datos.", exDb);
        }

        try (Connection conn = connectionProvider.getConnection()) {
            if (conn != null) {
                mesaDAO.actualizarEstadoMesaPorNumero(conn, numeroMesa, "OCUPADA", guardado.getIdPedido());
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Aviso al sincronizar estado de mesa en POST /api/pedidos", e);
        }
        return new ResultadoCreacionPedido(guardado, detalles, false);
    }

    @Override
    public PedidosActivosSnapshot listarPedidosActivosConDetalles() throws ApiOperacionException {
        try (Connection conn = connectionProvider.getConnection()) {
            if (conn == null) {
                throw new ApiOperacionException(503, "Base de datos no disponible.");
            }
            List<Pedido> pedidos = pedidoDAO.listarPedidosActivos(conn);
            Map<Integer, List<DetallePedido>> detalles = pedidoDAO.listarDetallesPedidosActivosAgrupados(conn);
            return new PedidosActivosSnapshot(pedidos, detalles);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error consultando GET /api/pedidos en BD", e);
            throw new ApiOperacionException(503, "No se pudieron consultar los pedidos en base de datos.", e);
        }
    }

    @Override
    public Optional<PedidoConDetalles> buscarPedidoPorUuid(String uuid) throws ApiOperacionException {
        try (Connection conn = connectionProvider.getConnection()) {
            if (conn == null) {
                throw new ApiOperacionException(503, "Base de datos no disponible.");
            }
            Optional<Pedido> opt = pedidoDAO.buscarPorIdempotencyKey(conn, uuid);
            if (opt.isEmpty()) {
                return Optional.empty();
            }
            Pedido pedido = opt.get();
            List<DetallePedido> detalles = pedidoDAO.listarDetallesPorPedido(conn, pedido.getIdPedido());
            return Optional.of(new PedidoConDetalles(pedido, detalles));
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error consultando GET /api/pedidos/{uuid} en BD", e);
            throw new ApiOperacionException(503, "No se pudo consultar el pedido en base de datos.", e);
        }
    }

    @Override
    public void actualizarEstadoPedido(int idPedido, String nuevoEstado) throws ApiOperacionException {
        try (Connection conn = connectionProvider.getConnection()) {
            if (conn == null) {
                throw new ApiOperacionException(503, "Base de datos no disponible.");
            }
            Optional<Pedido> actualOpt = pedidoDAO.obtenerPorId(conn, idPedido);
            if (actualOpt.isEmpty()) {
                throw new ApiOperacionException(404, "Pedido no encontrado: #" + idPedido);
            }
            String estadoActual = actualOpt.get().getEstado() != null
                    ? actualOpt.get().getEstado().trim().toUpperCase(Locale.ROOT)
                    : "";
            if (ESTADOS_PEDIDO_TERMINALES.contains(estadoActual) && !estadoActual.equals(nuevoEstado)) {
                throw new ApiOperacionException(409,
                        "No se puede cambiar el estado de un pedido en estado terminal (" + estadoActual + ").");
            }
            pedidoDAO.actualizarEstadoPedido(conn, idPedido, nuevoEstado);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error SQL actualizando estado de pedido #" + idPedido, e);
            throw new ApiOperacionException(503, "No se pudo actualizar el estado del pedido en base de datos.", e);
        }
    }

    @Override
    public EstadoCajaSnapshot consultarEstadoCaja() {
        Optional<Caja> optCaja = Optional.empty();
        try {
            optCaja = cajaService.obtenerCajaActiva();
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Aviso consultando caja activa en BD", e);
        }
        if (optCaja.isEmpty()) {
            return new EstadoCajaSnapshot(false, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        Caja caja = optCaja.get();
        BigDecimal ventas = BigDecimal.ZERO;
        BigDecimal gastos = BigDecimal.ZERO;
        try {
            ventas = cajaService.calcularVentasTurno(caja.getIdCaja());
            gastos = cajaService.calcularGastosTurno(caja.getIdCaja());
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "Aviso consultando totales del turno en BD", ex);
        }
        BigDecimal saldo = caja.getMontoApertura().add(ventas).subtract(gastos);
        return new EstadoCajaSnapshot(true, caja, ventas, gastos, saldo);
    }

    @Override
    public int abrirTurnoCaja(int idUsuario, String nombreCaja, BigDecimal montoInicial)
            throws ApiOperacionException {
        try {
            return cajaService.abrirTurnoCaja(idUsuario, nombreCaja, montoInicial);
        } catch (NegocioException exNeg) {
            String msg = exNeg.getMessage() != null ? exNeg.getMessage() : "No se pudo abrir la caja.";
            int status = msg.toLowerCase(Locale.ROOT).contains("ya existe una caja abierta") ? 409 : 422;
            throw new ApiOperacionException(status, msg, exNeg);
        } catch (SQLException exSql) {
            LOGGER.log(Level.WARNING, "Error SQL abriendo caja", exSql);
            throw new ApiOperacionException(503, "No se pudo abrir la caja en base de datos.", exSql);
        }
    }

    @Override
    public ResultadoCierreCaja cerrarTurnoCaja(int idUsuario, int idCajaSolicitado, BigDecimal contado)
            throws ApiOperacionException {
        try {
            Optional<Caja> activaOpt = cajaService.obtenerCajaActiva();
            if (activaOpt.isEmpty()) {
                throw new ApiOperacionException(409, "No hay ningún turno de caja abierto para cerrar.");
            }
            Caja activa = activaOpt.get();
            int idCaja = idCajaSolicitado <= 0 ? activa.getIdCaja() : idCajaSolicitado;
            if (activa.getIdCaja() != idCaja) {
                throw new ApiOperacionException(409,
                        "La caja #" + idCaja + " no corresponde al turno abierto actual.");
            }
            BigDecimal ventasTurno = cajaService.calcularVentasTurno(idCaja);
            BigDecimal gastosTurno = cajaService.calcularGastosTurno(idCaja);
            BigDecimal esperado = activa.getMontoApertura().add(ventasTurno).subtract(gastosTurno)
                    .setScale(2, RoundingMode.HALF_UP);
            cajaService.cerrarTurnoCaja(idCaja, idUsuario, contado);
            BigDecimal diferencia = contado.subtract(esperado).setScale(2, RoundingMode.HALF_UP);
            return new ResultadoCierreCaja(idCaja, esperado, contado, diferencia);
        } catch (CajaCerradaException exClosed) {
            throw new ApiOperacionException(409,
                    exClosed.getMessage() != null ? exClosed.getMessage() : "La caja ya está cerrada.", exClosed);
        } catch (NegocioException exNeg) {
            throw new ApiOperacionException(422,
                    exNeg.getMessage() != null ? exNeg.getMessage() : "Validación fallida al cerrar caja.", exNeg);
        } catch (SQLException exSql) {
            LOGGER.log(Level.WARNING, "Error SQL cerrando caja", exSql);
            throw new ApiOperacionException(503, "No se pudo cerrar la caja en base de datos.", exSql);
        }
    }

    @Override
    public ResultadoCobroCaja cobrarEnCaja(SolicitudCobroCaja solicitud) throws ApiOperacionException {
        List<DetalleVenta> detallesVenta = new ArrayList<>();
        BigDecimal totalAutoritativo = BigDecimal.ZERO;
        Integer idPedidoPersistido = null;
        String numeroMesa = solicitud.numeroMesa() != null ? solicitud.numeroMesa() : "";
        int siguienteCorrelativo;

        try (Connection conn = connectionProvider.getConnection()) {
            if (conn == null) {
                throw new ApiOperacionException(503, "Base de datos no disponible para procesar cobro.");
            }
            if (solicitud.idPedido() > 0) {
                Optional<Pedido> pedOpt = pedidoDAO.obtenerPorId(conn, solicitud.idPedido());
                if (pedOpt.isEmpty()) {
                    throw new ApiOperacionException(404,
                            "Pedido no encontrado para cobrar: #" + solicitud.idPedido());
                }
                Pedido pedido = pedOpt.get();
                String estPed = pedido.getEstado() != null ? pedido.getEstado().trim().toUpperCase(Locale.ROOT) : "";
                if (ESTADOS_PEDIDO_TERMINALES.contains(estPed)) {
                    throw new ApiOperacionException(409,
                            "El pedido #" + solicitud.idPedido() + " ya se encuentra en estado " + estPed + ".");
                }
                if (numeroMesa.isBlank() && pedido.getNumeroMesa() != null) {
                    numeroMesa = pedido.getNumeroMesa();
                }
                List<DetallePedido> detsPed = pedidoDAO.listarDetallesPorPedido(conn, solicitud.idPedido());
                if (detsPed.isEmpty()) {
                    throw new ApiOperacionException(422,
                            "El pedido #" + solicitud.idPedido() + " no tiene ítems registrados.");
                }
                totalAutoritativo = agregarDetallesDesdePedido(detsPed, detallesVenta);
                idPedidoPersistido = solicitud.idPedido();
            } else {
                totalAutoritativo = agregarDetallesDirectos(conn, solicitud.itemsDirectos(), detallesVenta);
            }
            siguienteCorrelativo = Math.max(1, ventaDAO.obtenerSiguienteCorrelativoComprobante(conn));
        } catch (SQLException exSql) {
            LOGGER.log(Level.WARNING, "Error SQL preparando cobro en POST /api/caja/cobrar", exSql);
            throw new ApiOperacionException(503, "Servicio de base de datos no disponible.", exSql);
        }

        BigDecimal bTotal = totalAutoritativo.setScale(2, RoundingMode.HALF_UP);
        BigDecimal bMontoRecibido = solicitud.montoRecibidoOpt() != null ? solicitud.montoRecibidoOpt() : bTotal;
        if (solicitud.montoRecibidoOpt() != null
                && "EFECTIVO".equals(solicitud.medioPago())
                && bMontoRecibido.compareTo(bTotal) < 0) {
            throw new ApiOperacionException(422,
                    "El monto recibido (S/ " + bMontoRecibido + ") es menor al total a cobrar (S/ " + bTotal + ").");
        }
        BigDecimal bVuelto = bMontoRecibido.subtract(bTotal).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        String serie = "FACTURA".equals(solicitud.tipoComprobante()) ? "F001"
                : ("BOLETA".equals(solicitud.tipoComprobante()) ? "B001" : "T001");
        String correlativo = String.format("%06d", siguienteCorrelativo);
        String nroComprobante = serie + "-" + correlativo;

        Venta ventaPersistida = persistirVenta(solicitud, idPedidoPersistido, detallesVenta, bTotal,
                bMontoRecibido, bVuelto, serie, correlativo);

        boolean mesaLiberada = false;
        if (!numeroMesa.isBlank()) {
            try (Connection conn = connectionProvider.getConnection()) {
                if (conn != null) {
                    mesaLiberada = mesaDAO.actualizarEstadoMesaPorNumero(conn, numeroMesa, "LIBRE", null);
                }
            } catch (SQLException exMesa) {
                LOGGER.log(Level.FINE, "Aviso liberando mesa tras cobro #" + ventaPersistida.getIdVenta(), exMesa);
            }
        }
        return new ResultadoCobroCaja(ventaPersistida, idPedidoPersistido, numeroMesa,
                solicitud.tipoComprobante(), nroComprobante, solicitud.medioPago(),
                bMontoRecibido, bVuelto, mesaLiberada);
    }

    private static Pedido construirNuevoPedido(int idUsuario, String numeroMesa, String uuidLocal,
            BigDecimal totalCalculado) {
        Pedido nuevoPedido = new Pedido();
        nuevoPedido.setIdUsuario(idUsuario);
        nuevoPedido.setIdUsuarioCreador(idUsuario);
        nuevoPedido.setNumeroMesa(numeroMesa);
        try {
            int idMesaNum = Integer.parseInt(numeroMesa.replaceAll("[^0-9]", ""));
            if (idMesaNum > 0) {
                nuevoPedido.setIdMesa(idMesaNum);
            }
        } catch (NumberFormatException e) {
            LOGGER.log(Level.FINEST, "Número de mesa alfanumérico sin id numérico", e);
        }
        nuevoPedido.setTotal(totalCalculado.setScale(2, RoundingMode.HALF_UP));
        nuevoPedido.setIdempotencyKey(uuidLocal);
        return nuevoPedido;
    }

    private static BigDecimal agregarDetallesDesdePedido(List<DetallePedido> detsPed,
            List<DetalleVenta> detallesVenta) {
        BigDecimal total = BigDecimal.ZERO;
        for (DetallePedido dp : detsPed) {
            BigDecimal precioU = dp.getPrecioUnitario().setScale(2, RoundingMode.HALF_UP);
            BigDecimal sub = precioU.multiply(BigDecimal.valueOf(dp.getCantidad())).setScale(2, RoundingMode.HALF_UP);
            DetalleVenta dv = new DetalleVenta(0, 0, dp.getIdProducto(), dp.getNombreProducto(),
                    dp.getCantidad(), precioU, sub);
            dv.setIdVariante(dp.getIdVariante());
            dv.setNombreVariante(dp.getNombreVariante());
            dv.setModificadoresTexto(dp.getModificadoresTexto());
            dv.setIdCombo(dp.getIdCombo());
            dv.setComboComponentesTexto(dp.getComboComponentesTexto());
            detallesVenta.add(dv);
            total = total.add(sub);
        }
        return total;
    }

    private BigDecimal agregarDetallesDirectos(Connection conn, List<ItemSolicitudPedido> itemsDirectos,
            List<DetalleVenta> detallesVenta) throws SQLException, ApiOperacionException {
        Map<Integer, Producto> catalogoPorId = new HashMap<>();
        for (Producto p : productoDAO.listarTodos(conn)) {
            catalogoPorId.put(p.getIdProducto(), p);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (ItemSolicitudPedido item : itemsDirectos) {
            Producto prod = catalogoPorId.get(item.idProducto());
            if (prod == null || !prod.isDisponible() || prod.getPrecioVenta() == null) {
                throw new ApiOperacionException(422, "Producto no válido o agotado: #" + item.idProducto());
            }
            BigDecimal precioU = prod.getPrecioVenta().setScale(2, RoundingMode.HALF_UP);
            BigDecimal sub = precioU.multiply(BigDecimal.valueOf(item.cantidad())).setScale(2, RoundingMode.HALF_UP);
            detallesVenta.add(new DetalleVenta(0, 0, item.idProducto(), prod.getNombre(), item.cantidad(), precioU, sub));
            total = total.add(sub);
        }
        return total;
    }

    private Venta persistirVenta(SolicitudCobroCaja solicitud, Integer idPedidoPersistido,
            List<DetalleVenta> detallesVenta, BigDecimal bTotal, BigDecimal bMontoRecibido, BigDecimal bVuelto,
            String serie, String correlativo) throws ApiOperacionException {
        try {
            DesgloseFiscal desglose = DesgloseFiscal.desdeTotalConImpuestos(bTotal);
            Venta venta = new Venta(0, idPedidoPersistido, 0, solicitud.idUsuario(), null,
                    desglose.getSubtotalGravado(), desglose.getIgv(), desglose.getTotal(),
                    solicitud.medioPago(), bMontoRecibido, bVuelto);
            venta.setAplicarFidelizacion(false);
            venta.setIdempotencyKey(solicitud.idempotencyKey());
            ComprobantePago comprobante = new ComprobantePago(0, 0, solicitud.tipoComprobante(),
                    serie, correlativo, desglose.getTotal());
            return ventaService.registrarVenta(venta, detallesVenta, comprobante);
        } catch (CajaCerradaException exClosed) {
            throw new ApiOperacionException(409,
                    exClosed.getMessage() != null ? exClosed.getMessage()
                            : "Debe abrir un turno de caja antes de cobrar.",
                    exClosed);
        } catch (StockInsuficienteException | InsumoNoEncontradoException | NegocioException exNeg) {
            throw new ApiOperacionException(422,
                    exNeg.getMessage() != null ? exNeg.getMessage()
                            : "No se pudo completar el cobro por validación de negocio.",
                    exNeg);
        } catch (SQLException exSql) {
            LOGGER.log(Level.WARNING, "Error SQL persistiendo venta en POST /api/caja/cobrar", exSql);
            throw new ApiOperacionException(503, "No se pudo persistir la venta en base de datos.", exSql);
        }
    }
}
