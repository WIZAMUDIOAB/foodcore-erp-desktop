package com.foodcore.service;

import com.foodcore.dao.*;
import com.foodcore.dao.impl.AuditoriaDAOImpl;
import com.foodcore.dao.impl.ClienteDAOImpl;
import com.foodcore.dao.impl.ComboDAOImpl;
import com.foodcore.dao.impl.CuentaPorCobrarDAOImpl;
import com.foodcore.dao.impl.VentaDAOImpl;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.FormatoPersonalizacionUtil;
import com.foodcore.exception.CajaCerradaException;
import com.foodcore.exception.CodigoError;
import com.foodcore.exception.InsumoNoEncontradoException;
import com.foodcore.exception.NegocioException;
import com.foodcore.exception.StockInsuficienteException;
import com.foodcore.model.*;
import com.foodcore.model.enums.Permiso;
import com.foodcore.security.AutorizacionService;
import com.foodcore.security.SesionUsuario;
import com.foodcore.tx.TransactionManager;
import com.foodcore.tx.impl.JdbcTransactionManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.logging.Logger;

/**
 * Servicio transaccional de Ventas con control estricto ACID mediante {@link TransactionManager},
 * bloqueo pesimista {@code SELECT ... FOR UPDATE} en orden ascendente de {@code id_insumo} (R-09),
 * propagación directa de {@link StockInsuficienteException} sin envolver en {@link SQLException} (R-11),
 * trazabilidad en {@code kardex_movimiento}, división de cuentas (§3.1), descuentos RBAC (§3.2)
 * y anulaciones auditadas (§3.2).
 */
public class VentaService {

    private static final Logger LOGGER = Logger.getLogger(VentaService.class.getName());
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    public record PagoSplit(String medioPago, BigDecimal monto) {
    }

    public record SubcuentaItems(List<DetalleVenta> detalles, String medioPago) {
    }

    /** Resultado de una devolución parcial de ítems en venta existente (v5.2.0). */
    public record ResultadoDevolucionParcial(
            int idVenta,
            List<Integer> idsDetalleDevueltos,
            BigDecimal montoReembolso,
            BigDecimal nuevoTotalVenta,
            String nuevoEstadoVenta,
            int cantidadInsumosRevertidos
    ) {
        public ResultadoDevolucionParcial {
            idsDetalleDevueltos = idsDetalleDevueltos != null ? List.copyOf(idsDetalleDevueltos) : List.of();
        }
    }

    private final TransactionManager tx;
    private final VentaDAO ventaDAO;
    private final CajaDAO cajaDAO;
    private final InsumoDAO insumoDAO;
    private final PedidoDAO pedidoDAO;
    private final ComboDAO comboDAO;
    private final ClienteDAO clienteDAO = new ClienteDAOImpl();
    private final CuentaPorCobrarDAO cuentaPorCobrarDAO = new CuentaPorCobrarDAOImpl();
    private final AutorizacionService autorizacionService = new AutorizacionService();
    private final FidelizacionService fidelizacionService = new FidelizacionService();
    private final CierreContableService cierreContableService = new CierreContableService();
    private final SupervisorAutorizacionService supervisorAutorizacionService = new SupervisorAutorizacionService();
    private final AuditoriaDAOImpl auditoriaDAO = new AuditoriaDAOImpl();

    public VentaService() {
        this(new JdbcTransactionManager(), new VentaDAOImpl(), new CajaDAOImpl(), new InsumoDAOImpl(), new PedidoDAOImpl(), new ComboDAOImpl());
    }

    public VentaService(TransactionManager tx,
                        VentaDAO ventaDAO,
                        CajaDAO cajaDAO,
                        InsumoDAO insumoDAO,
                        PedidoDAO pedidoDAO) {
        this(tx, ventaDAO, cajaDAO, insumoDAO, pedidoDAO, new ComboDAOImpl());
    }

    public VentaService(TransactionManager tx,
                        VentaDAO ventaDAO,
                        CajaDAO cajaDAO,
                        InsumoDAO insumoDAO,
                        PedidoDAO pedidoDAO,
                        ComboDAO comboDAO) {
        this.tx = tx;
        this.ventaDAO = ventaDAO;
        this.cajaDAO = cajaDAO;
        this.insumoDAO = insumoDAO;
        this.pedidoDAO = pedidoDAO;
        this.comboDAO = comboDAO != null ? comboDAO : new ComboDAOImpl();
    }

    public Venta procesarCobroVenta(Venta venta, List<DetalleVenta> detalles, ComprobantePago comprobante)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException {
        return registrarVenta(venta, detalles, comprobante);
    }

    /**
     * Valida el permiso {@link Permiso#VENTA_DESCUENTO} (exclusivo de Admin/Supervisor §3.2)
     * y calcula el monto de descuento porcentual (ej. 10%, 20%) o de monto fijo sobre un total bruto.
     */
    public BigDecimal calcularDescuentoAutorizado(BigDecimal totalBruto,
                                                  BigDecimal valorDescuento,
                                                  boolean esPorcentaje,
                                                  SesionUsuario sesion) throws NegocioException {
        autorizacionService.exigir(sesion, Permiso.VENTA_DESCUENTO);
        if (totalBruto == null || totalBruto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegocioException("El total de la venta debe ser mayor a cero para aplicar descuento.");
        }
        if (valorDescuento == null || valorDescuento.compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegocioException("El valor del descuento debe ser mayor a cero.");
        }
        BigDecimal montoDescuento;
        if (esPorcentaje) {
            if (valorDescuento.compareTo(new BigDecimal("100")) >= 0) {
                throw new NegocioException("El porcentaje de descuento debe ser menor al 100%.");
            }
            montoDescuento = totalBruto.multiply(valorDescuento)
                .divide(new BigDecimal("100"), DesgloseFiscal.ESCALA_MONETARIA, RoundingMode.HALF_EVEN);
        } else {
            if (valorDescuento.compareTo(totalBruto) >= 0) {
                throw new NegocioException("El descuento fijo no puede igualar ni superar el total de la venta.");
            }
            montoDescuento = valorDescuento.setScale(DesgloseFiscal.ESCALA_MONETARIA, RoundingMode.HALF_EVEN);
        }
        return montoDescuento;
    }

    public Venta registrarVenta(Venta venta, List<DetalleVenta> detalles, ComprobantePago comprobante)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException {
        return registrarVentaConPagos(venta, detalles, comprobante, null);
    }

    /**
     * Registra atómicamente una venta POS a {@code CREDITO_CORPORATIVO} junto con su
     * {@link CuentaPorCobrar} en la MISMA transacción JDBC, validando línea de crédito bajo
     * bloqueo pesimista {@code FOR UPDATE}. Si falla la CxC, la venta completa se revierte.
     */
    public Venta registrarVentaConCreditoCorporativo(Venta venta,
                                                     List<DetalleVenta> detalles,
                                                     ComprobantePago comprobante,
                                                     String docCliente,
                                                     String nombreCliente)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException {
        if (venta == null) {
            throw new NegocioException("Los datos de la venta a crédito no pueden ser nulos.");
        }
        validarYPrepararMontosVenta(venta, detalles, null);

        return tx.executeChecked(conn -> {
            Cliente clienteCredito = resolverClienteParaCredito(conn, venta.getIdCliente(), docCliente);
            if (clienteCredito == null) {
                throw new NegocioException(
                        "Para ventas a CREDITO_CORPORATIVO debe seleccionar o registrar un cliente válido.");
            }
            venta.setIdCliente(clienteCredito.getIdCliente());

            // Bloqueo pesimista de cliente para validar límite de crédito sin condiciones de carrera
            BigDecimal limiteCredito = BigDecimal.ZERO;
            int diasCredito = 30;
            String sqlLockCli = "SELECT limite_credito, dias_credito, activo FROM cliente WHERE id_cliente = ? FOR UPDATE";
            try (PreparedStatement psLock = conn.prepareStatement(sqlLockCli)) {
                psLock.setInt(1, clienteCredito.getIdCliente());
                try (ResultSet rs = psLock.executeQuery()) {
                    if (!rs.next() || !rs.getBoolean("activo")) {
                        throw new NegocioException("El cliente corporativo no existe o se encuentra inactivo.");
                    }
                    BigDecimal lim = rs.getBigDecimal("limite_credito");
                    if (lim != null) {
                        limiteCredito = lim;
                    }
                    int dias = rs.getInt("dias_credito");
                    if (dias > 0) {
                        diasCredito = dias;
                    }
                }
            }

            if (limiteCredito.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal deudaPendiente = BigDecimal.ZERO;
                String sqlDeuda = "SELECT COALESCE(SUM(monto_total - monto_cobrado), 0) AS deuda "
                        + "FROM cuenta_por_cobrar WHERE id_cliente = ? AND estado <> 'COBRADA'";
                try (PreparedStatement psDeuda = conn.prepareStatement(sqlDeuda)) {
                    psDeuda.setInt(1, clienteCredito.getIdCliente());
                    try (ResultSet rsDeuda = psDeuda.executeQuery()) {
                        if (rsDeuda.next() && rsDeuda.getBigDecimal("deuda") != null) {
                            deudaPendiente = rsDeuda.getBigDecimal("deuda");
                        }
                    }
                }
                if (deudaPendiente.add(venta.getTotal()).compareTo(limiteCredito) > 0) {
                    throw new NegocioException(String.format(Locale.US,
                            "Límite de crédito corporativo excedido. Límite: S/ %.2f | Deuda actual: S/ %.2f | Venta: S/ %.2f",
                            limiteCredito, deudaPendiente, venta.getTotal()));
                }
            }

            Venta ventaRegistrada = registrarVentaEnConexion(conn, venta, detalles, comprobante, null);

            String nroComprobante = comprobante != null && comprobante.getSerie() != null && comprobante.getNumero() != null
                    ? comprobante.getSerie() + "-" + comprobante.getNumero()
                    : "VTA-" + ventaRegistrada.getIdVenta();
            LocalDate hoyLima = LocalDate.now(ZONA_LIMA);
            CuentaPorCobrar cxc = new CuentaPorCobrar();
            cxc.setIdCliente(clienteCredito.getIdCliente());
            cxc.setIdVenta(ventaRegistrada.getIdVenta());
            cxc.setNumeroComprobante(nroComprobante);
            cxc.setFechaEmision(hoyLima);
            cxc.setFechaVencimiento(hoyLima.plusDays(diasCredito));
            cxc.setMontoTotal(ventaRegistrada.getTotal().doubleValue());
            cxc.setMontoCobrado(0.0);
            cxc.setEstado("PENDIENTE");
            String etiquetaNombre = (nombreCliente != null && !nombreCliente.isBlank())
                    ? nombreCliente.trim() : clienteCredito.getRazonSocial();
            cxc.setObservaciones("Venta POS a credito corporativo. Cliente: " + etiquetaNombre);
            cuentaPorCobrarDAO.insertar(conn, cxc);

            return ventaRegistrada;
        });
    }

    private Cliente resolverClienteParaCredito(Connection conn, Integer idCliente, String docCliente) throws SQLException {
        if (idCliente != null && idCliente > 0) {
            Optional<Cliente> optById = clienteDAO.buscarPorId(conn, idCliente);
            if (optById.isPresent()) {
                return optById.get();
            }
        }
        if (docCliente != null && !docCliente.isBlank()) {
            Optional<Cliente> optByDoc = clienteDAO.buscarPorDocumento(conn, docCliente.trim());
            if (optByDoc.isPresent()) {
                return optByDoc.get();
            }
            return clienteDAO.buscarPorDniOTelefono(conn, docCliente.trim()).orElse(null);
        }
        return null;
    }

    /**
     * División de cuenta en múltiples pagos / partes iguales (§3.1): genera la venta descontando stock
     * y registra múltiples asientos en {@code movimiento_caja} según los medios de pago indicados.
     */
    public Venta registrarVentaDividida(Venta venta,
                                        List<DetalleVenta> detalles,
                                        ComprobantePago comprobante,
                                        List<PagoSplit> pagosDivididos)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException {
        if (pagosDivididos == null || pagosDivididos.size() < 2) {
            throw new NegocioException("Para dividir la cuenta debe indicar al menos 2 pagos.");
        }
        return registrarVentaConPagos(venta, detalles, comprobante, pagosDivididos);
    }

    /**
     * División de cuenta por ítems (§3.1): genera una venta independiente por cada subcuenta
     * dentro de una ÚNICA transacción ACID (todo o nada) con correlativos sin colisiones.
     */
    public List<Venta> dividirCuentaPorItems(List<SubcuentaItems> subcuentas, int idUsuario)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException {
        if (subcuentas == null || subcuentas.isEmpty()) {
            throw new NegocioException("Debe especificar al menos una subcuenta con ítems.");
        }

        List<SubcuentaItems> subcuentasValidas = new ArrayList<>();
        for (SubcuentaItems sub : subcuentas) {
            if (sub != null && sub.detalles() != null && !sub.detalles().isEmpty()) {
                subcuentasValidas.add(sub);
            }
        }
        if (subcuentasValidas.isEmpty()) {
            throw new NegocioException("Debe especificar al menos una subcuenta con ítems válidos.");
        }

        return tx.executeChecked(conn -> {
            List<Venta> ventasGeneradas = new ArrayList<>();
            int correlativo = 1;
            for (SubcuentaItems sub : subcuentasValidas) {
                BigDecimal suma = BigDecimal.ZERO;
                for (DetalleVenta d : sub.detalles()) {
                    if (d == null || d.getCantidad() <= 0 || d.getPrecioUnitario() == null
                            || d.getPrecioUnitario().compareTo(BigDecimal.ZERO) <= 0) {
                        throw new NegocioException("Cada ítem en la división debe tener cantidad y precio unitario mayores a cero.");
                    }
                    BigDecimal subLinea = d.getPrecioUnitario()
                            .multiply(BigDecimal.valueOf(d.getCantidad()))
                            .setScale(DesgloseFiscal.ESCALA_MONETARIA, DesgloseFiscal.MODO_REDONDEO);
                    d.setSubtotal(subLinea);
                    suma = suma.add(subLinea);
                }
                DesgloseFiscal desglose = DesgloseFiscal.desdeTotalConImpuestos(suma);
                String medio = sub.medioPago() != null && !sub.medioPago().isBlank() ? sub.medioPago() : "EFECTIVO";
                Venta v = new Venta(0, null, 1, idUsuario, 1,
                        desglose.getSubtotalGravado(), desglose.getIgv(), desglose.getTotal(),
                        medio, desglose.getTotal(), BigDecimal.ZERO);
                v.setAplicarFidelizacion(false);
                v.setIdempotencyKey(UUID.randomUUID().toString());
                String sufijoUnico = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
                ComprobantePago comp = new ComprobantePago(0, 0, "TICKET", "SP" + correlativo,
                        String.format(Locale.ROOT, "%04d-%s", correlativo, sufijoUnico), desglose.getTotal());
                validarYPrepararMontosVenta(v, sub.detalles(), null);
                ventasGeneradas.add(registrarVentaEnConexion(conn, v, sub.detalles(), comp, null));
                correlativo++;
            }
            return ventasGeneradas;
        });
    }

    /**
     * Anulación auditada de una venta (§3.2):
     * <ul>
     *   <li>Exige permiso {@link Permiso#VENTA_ANULAR}.</li>
     *   <li>Actualiza estado de venta a {@code ANULADA}.</li>
     *   <li>Genera {@code movimiento_caja} de tipo {@code ANULACION}.</li>
     *   <li>Si {@code platoYaPreparado == false}, revierte el inventario ({@code ENTRADA});
     *       si {@code platoYaPreparado == true}, registra un movimiento {@code MERMA} en el Kardex.</li>
     * </ul>
     */
    public void anularVenta(int idVenta, boolean platoYaPreparado, SesionUsuario sesion, String motivo)
            throws NegocioException, SQLException {
        autorizacionService.exigir(sesion, Permiso.VENTA_ANULAR);
        if (idVenta <= 0) {
            throw new NegocioException("ID de venta inválido para anulación.");
        }
        String glosaMotivo = (motivo != null && !motivo.isBlank()) ? motivo.trim() : "Anulación operativa POS";
        ejecutarAnulacionTx(idVenta, platoYaPreparado, sesion.getIdUsuario(), glosaMotivo, null, sesion.getNombreCompleto());
    }

    /**
     * Anulación en Modo Supervisor (v5.2.0): permite a un cajero sin {@link Permiso#VENTA_ANULAR}
     * anular una venta previa autenticación PIN de un {@code SUPERVISOR} o {@code ADMIN},
     * registrando trazabilidad explícita de ambos usuarios en {@code auditoria}.
     */
    public Usuario anularVentaConSupervisor(int idVenta,
                                            boolean platoYaPreparado,
                                            SesionUsuario sesionCajero,
                                            String pinSupervisor,
                                            String motivo) throws NegocioException, SQLException {
        if (sesionCajero == null || sesionCajero.haExpirado(LocalDateTime.now(ZONA_LIMA))) {
            throw new NegocioException(CodigoError.ACCESO_DENEGADO,
                    "Debe iniciar sesión vigente para solicitar una anulación supervisada.");
        }
        if (idVenta <= 0) {
            throw new NegocioException("ID de venta inválido para anulación.");
        }
        Usuario supervisor = supervisorAutorizacionService.autenticarSupervisorPorPin(pinSupervisor);
        String glosaMotivo = (motivo != null && !motivo.isBlank()) ? motivo.trim() : "Anulación supervisada POS";
        ejecutarAnulacionTx(idVenta, platoYaPreparado, sesionCajero.getIdUsuario(), glosaMotivo, supervisor, sesionCajero.getNombreCompleto());
        return supervisor;
    }

    private void ejecutarAnulacionTx(int idVenta,
                                     boolean platoYaPreparado,
                                     long idUsuarioSolicitante,
                                     String glosaMotivo,
                                     Usuario supervisorAutorizante,
                                     String usernameSolicitante) throws NegocioException, SQLException {
        tx.executeVoidChecked(conn -> {
            int idCaja;
            Integer idCliente;
            BigDecimal totalVenta;
            String metodoPago;
            String estadoActual;
            LocalDateTime fechaVenta = null;

            String sqlVenta = "SELECT id_caja, id_cliente, total, metodo_pago, estado, fecha_hora FROM venta WHERE id_venta = ? FOR UPDATE";
            try (PreparedStatement ps = conn.prepareStatement(sqlVenta)) {
                ps.setInt(1, idVenta);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new NegocioException("No se encontró la venta #" + idVenta + " para anular.");
                    }
                    idCaja = rs.getInt("id_caja");
                    int rawCli = rs.getInt("id_cliente");
                    idCliente = rs.wasNull() || rawCli <= 0 ? null : rawCli;
                    totalVenta = rs.getBigDecimal("total");
                    metodoPago = rs.getString("metodo_pago");
                    estadoActual = rs.getString("estado");
                    Timestamp ts = rs.getTimestamp("fecha_hora");
                    if (ts != null) {
                        fechaVenta = ts.toLocalDateTime();
                    }
                }
            }

            if ("ANULADA".equalsIgnoreCase(estadoActual)) {
                throw new NegocioException("La venta #" + idVenta + " ya fue anulada previamente.");
            }
            cierreContableService.validarPeriodoAbierto(conn, fechaVenta);

            try (PreparedStatement psUpd = conn.prepareStatement("UPDATE venta SET estado = 'ANULADA' WHERE id_venta = ?")) {
                psUpd.setInt(1, idVenta);
                psUpd.executeUpdate();
            }

            int idUsuarioMov = (int) idUsuarioSolicitante;
            String sufijoSupervisor = supervisorAutorizante != null
                    ? " [Autorizado por " + supervisorAutorizante.getUsername() + "]" : "";
            MovimientoCaja movAnulacion = new MovimientoCaja(
                0, idCaja, idUsuarioMov, "ANULACION",
                normalizarMedioPagoMovimientoCaja(metodoPago),
                totalVenta.negate(),
                "Anulación Venta #" + idVenta + ": " + glosaMotivo + sufijoSupervisor
            );
            cajaDAO.registrarMovimiento(conn, movAnulacion);

            if (idCliente != null) {
                fidelizacionService.revertirPuntosPorAnulacionVenta(conn, idCliente, idVenta, glosaMotivo);
            }

            if (supervisorAutorizante != null) {
                auditoriaDAO.registrarEventoSeguro(
                        conn,
                        supervisorAutorizante.getIdUsuario(),
                        "ANULACION_VENTA_SUPERVISOR",
                        "venta#" + idVenta,
                        estadoActual + " (S/ " + totalVenta + ")",
                        "ANULADA | Solicitante=" + (usernameSolicitante != null ? usernameSolicitante : "cajero#" + idUsuarioSolicitante)
                                + " | Autorizante=" + supervisorAutorizante.getUsername()
                                + " (" + supervisorAutorizante.getNombreRol() + ") | Motivo=" + glosaMotivo
                );
            }

            // Leer ítems vendidos no devueltos y sus recetas (o componentes de combo) para revertir stock o registrar merma (§3.2, v4.8.0)
            Map<Long, BigDecimal> insumosAfectados = new TreeMap<>();
            String sqlDet = "SELECT id_producto, id_combo, cantidad FROM detalle_venta WHERE id_venta = ? AND COALESCE(estado, 'COMPLETADO') <> 'DEVUELTO'";
            try (PreparedStatement psDet = conn.prepareStatement(sqlDet)) {
                psDet.setInt(1, idVenta);
                try (ResultSet rsDet = psDet.executeQuery()) {
                    while (rsDet.next()) {
                        int idProd = rsDet.getInt("id_producto");
                        int rawCombo = rsDet.getInt("id_combo");
                        Integer idCombo = rsDet.wasNull() || rawCombo <= 0 ? null : rawCombo;
                        int cant = rsDet.getInt("cantidad");
                        acumularInsumosParaLinea(conn, insumosAfectados, idCombo, idProd, cant);
                    }
                }
            }

            Map<Long, BigDecimal> saldosBloqueadosMerma = Collections.emptyMap();
            if (platoYaPreparado && !insumosAfectados.isEmpty()) {
                saldosBloqueadosMerma = insumoDAO.lockearPorIds(conn, new ArrayList<>(insumosAfectados.keySet()));
            }

            for (Map.Entry<Long, BigDecimal> entry : insumosAfectados.entrySet()) {
                long idIns = entry.getKey();
                BigDecimal cantInsumo = entry.getValue();
                if (!platoYaPreparado) {
                    insumoDAO.registrarMovimientoKardexAtomico(
                        conn, idIns, "ENTRADA", cantInsumo, idUsuarioSolicitante,
                        "Reversión por Anulación Venta #" + idVenta + " (No preparado)"
                    );
                } else {
                    Insumo ins = insumoDAO.obtenerPorId(conn, idIns);
                    BigDecimal saldoActual = saldosBloqueadosMerma.getOrDefault(
                            idIns, ins != null ? ins.getStockActualDisponible() : BigDecimal.ZERO);
                    BigDecimal costo = ins != null && ins.getCostoUnitario() != null ? ins.getCostoUnitario() : BigDecimal.ZERO;
                    String obsMerma = "Merma por Anulación Venta #" + idVenta + " (Plato ya cocinado: " + glosaMotivo + ")";
                    insumoDAO.registrarMovimientoInventario(conn, idIns, "MERMA", cantInsumo, obsMerma, idVenta);
                    KardexMovimiento kmMerma = new KardexMovimiento(
                        0L, idIns, "MERMA", cantInsumo.negate(), saldoActual, saldoActual,
                        costo, "ANULACION", (long) idVenta, idUsuarioSolicitante, obsMerma, LocalDateTime.now(ZONA_LIMA)
                    );
                    insumoDAO.insertarKardex(conn, kmMerma);
                }
            }
        });
    }

    /** Lista ventas del historial filtrables por rango de fechas y texto (v5.2.0). */
    public List<Venta> listarVentasHistorial(LocalDate desde, LocalDate hasta, String textoBusqueda) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return ventaDAO.listarVentasConFiltro(conn, desde, hasta, textoBusqueda);
        }
    }

    /** Lista los ítems de detalle de una venta con su estado de devolución (v5.2.0). */
    public List<DetalleVenta> listarDetallesVenta(int idVenta) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return ventaDAO.listarDetallesPorVenta(conn, idVenta);
        }
    }

    /**
     * Ejecuta la devolución parcial atómica de ítems específicos de una venta (v5.2.0):
     * marca {@code detalle_venta.estado = 'DEVUELTO'}, recalcula cabecera, registra egreso de caja,
     * devuelve insumos al inventario con movimiento Kárdex {@code DEVOLUCION} y audita la operación.
     */
    public ResultadoDevolucionParcial devolverItemsParcial(int idVenta,
                                                           List<Integer> idsDetalleADevolver,
                                                           SesionUsuario sesion,
                                                           String motivo) throws NegocioException, SQLException {
        autorizacionService.exigir(sesion, Permiso.VENTA_ANULAR);
        if (sesion.haExpirado(LocalDateTime.now(ZONA_LIMA))) {
            throw new NegocioException(CodigoError.ACCESO_DENEGADO,
                    "Debe iniciar sesión vigente para procesar devoluciones parciales.");
        }
        if (idVenta <= 0 || idsDetalleADevolver == null || idsDetalleADevolver.isEmpty()) {
            throw new NegocioException(CodigoError.VALIDACION_ENTRADA,
                    "Debe seleccionar una venta y al menos un ítem válido para devolver.");
        }
        Set<Integer> idsUnicos = new LinkedHashSet<>();
        for (Integer idDet : idsDetalleADevolver) {
            if (idDet != null && idDet > 0) {
                idsUnicos.add(idDet);
            }
        }
        if (idsUnicos.isEmpty()) {
            throw new NegocioException(CodigoError.VALIDACION_ENTRADA,
                    "Debe seleccionar al menos un ítem válido para devolver.");
        }
        String glosaMotivo = (motivo != null && !motivo.isBlank()) ? motivo.trim() : "Devolución parcial en POS";

        return tx.executeChecked(conn -> {
            int idCajaVenta;
            Integer idCliente;
            BigDecimal totalActual;
            String metodoPago;
            String estadoVenta;
            LocalDateTime fechaVenta = null;

            String sqlVenta = "SELECT id_caja, id_cliente, total, metodo_pago, estado, fecha_hora FROM venta WHERE id_venta = ? FOR UPDATE";
            try (PreparedStatement ps = conn.prepareStatement(sqlVenta)) {
                ps.setInt(1, idVenta);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new NegocioException(CodigoError.RECURSO_NO_ENCONTRADO,
                                "No se encontró la venta #" + idVenta + ".");
                    }
                    idCajaVenta = rs.getInt("id_caja");
                    int rawCli = rs.getInt("id_cliente");
                    idCliente = rs.wasNull() || rawCli <= 0 ? null : rawCli;
                    totalActual = rs.getBigDecimal("total") != null ? rs.getBigDecimal("total") : BigDecimal.ZERO;
                    metodoPago = rs.getString("metodo_pago");
                    estadoVenta = rs.getString("estado");
                    Timestamp ts = rs.getTimestamp("fecha_hora");
                    if (ts != null) {
                        fechaVenta = ts.toLocalDateTime();
                    }
                }
            }

            if ("ANULADA".equalsIgnoreCase(estadoVenta)) {
                throw new NegocioException("La venta #" + idVenta + " está ANULADA; no admite devoluciones parciales.");
            }
            cierreContableService.validarPeriodoAbierto(conn, fechaVenta);

            List<DetalleVenta> todosDetalles = ventaDAO.listarDetallesPorVenta(conn, idVenta);
            Map<Integer, DetalleVenta> mapaDetalles = new LinkedHashMap<>();
            for (DetalleVenta d : todosDetalles) {
                mapaDetalles.put(d.getIdDetalleVenta(), d);
            }

            BigDecimal subtotalDevuelto = BigDecimal.ZERO;
            BigDecimal subtotalRestante = BigDecimal.ZERO;
            List<DetalleVenta> aDevolver = new ArrayList<>();

            for (Integer idReq : idsUnicos) {
                DetalleVenta det = mapaDetalles.get(idReq);
                if (det == null) {
                    throw new NegocioException(CodigoError.RECURSO_NO_ENCONTRADO,
                            "El ítem #" + idReq + " no pertenece a la venta #" + idVenta + ".");
                }
                if (det.isDevuelto()) {
                    throw new NegocioException("El ítem '" + det.getNombreProducto() + "' ya fue devuelto previamente.");
                }
                aDevolver.add(det);
                BigDecimal sub = det.getSubtotal() != null ? det.getSubtotal() : BigDecimal.ZERO;
                subtotalDevuelto = subtotalDevuelto.add(sub);
            }

            for (DetalleVenta det : todosDetalles) {
                if (!det.isDevuelto() && !idsUnicos.contains(det.getIdDetalleVenta())) {
                    BigDecimal sub = det.getSubtotal() != null ? det.getSubtotal() : BigDecimal.ZERO;
                    subtotalRestante = subtotalRestante.add(sub);
                }
            }

            // Prorrateo proporcional si la venta tuvo descuento comercial o canje de puntos (evita sobre-reembolso)
            BigDecimal brutoActivo = subtotalDevuelto.add(subtotalRestante);
            BigDecimal montoReembolso;
            if (subtotalRestante.compareTo(BigDecimal.ZERO) <= 0) {
                montoReembolso = DesgloseFiscal.normalizarMoneda(totalActual);
            } else if (brutoActivo.compareTo(BigDecimal.ZERO) > 0 && totalActual.compareTo(brutoActivo) < 0) {
                montoReembolso = DesgloseFiscal.normalizarMoneda(
                        totalActual.multiply(subtotalDevuelto)
                                .divide(brutoActivo, DesgloseFiscal.ESCALA_MONETARIA, DesgloseFiscal.MODO_REDONDEO)
                                .min(totalActual)
                );
            } else {
                montoReembolso = DesgloseFiscal.normalizarMoneda(subtotalDevuelto.min(totalActual));
            }
            BigDecimal nuevoTotal = DesgloseFiscal.normalizarMoneda(totalActual.subtract(montoReembolso).max(BigDecimal.ZERO));
            DesgloseFiscal desgloseRestante = nuevoTotal.compareTo(BigDecimal.ZERO) > 0
                    ? DesgloseFiscal.desdeTotalConImpuestos(nuevoTotal)
                    : null;
            BigDecimal nuevoSubtotal = desgloseRestante != null ? desgloseRestante.getSubtotalGravado() : BigDecimal.ZERO;
            BigDecimal nuevoIgv = desgloseRestante != null ? desgloseRestante.getIgv() : BigDecimal.ZERO;
            String nuevoEstado = (subtotalRestante.compareTo(BigDecimal.ZERO) <= 0 || nuevoTotal.compareTo(BigDecimal.ZERO) <= 0)
                    ? "ANULADA" : "COMPLETADA";

            Map<Long, BigDecimal> insumosARevertir = new TreeMap<>();
            for (DetalleVenta det : aDevolver) {
                int actualizados = ventaDAO.marcarDetalleDevuelto(conn, det.getIdDetalleVenta(), glosaMotivo);
                if (actualizados == 0) {
                    throw new NegocioException("El ítem #" + det.getIdDetalleVenta() + " ya se encontraba devuelto.");
                }
                acumularInsumosParaLinea(conn, insumosARevertir, det.getIdCombo(), det.getIdProducto(), det.getCantidad());
            }

            ventaDAO.actualizarTotalesVentaPorDevolucion(conn, idVenta, nuevoSubtotal, nuevoIgv, nuevoTotal, nuevoEstado);

            Optional<Caja> cajaActiva = cajaDAO.obtenerCajaAbierta(conn);
            int idCajaEgreso = cajaActiva.map(Caja::getIdCaja).orElse(idCajaVenta);
            MovimientoCaja movEgreso = new MovimientoCaja(
                    0,
                    idCajaEgreso,
                    (int) sesion.getIdUsuario(),
                    "EGRESO",
                    normalizarMedioPagoMovimientoCaja(metodoPago),
                    montoReembolso,
                    "Devolución parcial Venta #" + idVenta + " (" + aDevolver.size() + " ítem(s)): " + glosaMotivo
            );
            cajaDAO.registrarMovimiento(conn, movEgreso);

            if (idCliente != null) {
                fidelizacionService.revertirPuntosPorDevolucionParcial(
                        conn, idCliente, idVenta, montoReembolso, "ANULADA".equalsIgnoreCase(nuevoEstado), glosaMotivo
                );
            }

            if (!insumosARevertir.isEmpty()) {
                List<Long> idsOrdenados = new ArrayList<>(insumosARevertir.keySet());
                Map<Long, BigDecimal> saldosAnteriores = insumoDAO.lockearPorIds(conn, idsOrdenados);
                for (Long idIns : idsOrdenados) {
                    BigDecimal cantRevertir = DesgloseFiscal.normalizarStock(insumosARevertir.get(idIns));
                    BigDecimal saldoAnterior = saldosAnteriores.getOrDefault(idIns, BigDecimal.ZERO);
                    BigDecimal saldoPosterior = DesgloseFiscal.normalizarStock(saldoAnterior.add(cantRevertir));
                    insumoDAO.actualizarStock(conn, idIns, saldoPosterior);

                    Insumo ins = insumoDAO.obtenerPorId(conn, idIns);
                    BigDecimal costoUnit = (ins != null && ins.getCostoUnitario() != null)
                            ? ins.getCostoUnitario() : BigDecimal.ZERO;
                    String obsKardex = "DEVOLUCION parcial Venta #" + idVenta + ": " + glosaMotivo;
                    insumoDAO.registrarMovimientoInventario(conn, idIns, "ENTRADA", cantRevertir, obsKardex, idVenta);
                    KardexMovimiento km = new KardexMovimiento(
                            0L,
                            idIns,
                            "DEVOLUCION",
                            cantRevertir,
                            saldoAnterior,
                            saldoPosterior,
                            costoUnit,
                            "DEVOLUCION",
                            (long) idVenta,
                            sesion.getIdUsuario(),
                            obsKardex,
                            LocalDateTime.now(ZONA_LIMA)
                    );
                    insumoDAO.insertarKardex(conn, km);
                }
            }

            auditoriaDAO.registrarEventoSeguro(
                    conn,
                    (int) sesion.getIdUsuario(),
                    "DEVOLUCION_PARCIAL_VENTA",
                    "venta#" + idVenta,
                    "Total=S/ " + totalActual,
                    "Reembolso=S/ " + montoReembolso + " | NuevoTotal=S/ " + nuevoTotal
                            + " | Items=" + idsUnicos + " | Motivo=" + glosaMotivo
            );

            return new ResultadoDevolucionParcial(
                    idVenta,
                    List.copyOf(idsUnicos),
                    montoReembolso,
                    nuevoTotal,
                    nuevoEstado,
                    insumosARevertir.size()
            );
        });
    }

    private Venta registrarVentaConPagos(Venta venta,
                                         List<DetalleVenta> detalles,
                                         ComprobantePago comprobante,
                                         List<PagoSplit> pagosDivididos)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException {
        validarYPrepararMontosVenta(venta, detalles, pagosDivididos);
        return tx.executeChecked(conn -> registrarVentaEnConexion(conn, venta, detalles, comprobante, pagosDivididos));
    }

    private void validarYPrepararMontosVenta(Venta venta,
                                             List<DetalleVenta> detalles,
                                             List<PagoSplit> pagosDivididos) throws NegocioException {
        if (venta == null) {
            throw new NegocioException("Los datos de la venta no pueden ser nulos.");
        }
        if (detalles == null || detalles.isEmpty()) {
            throw new NegocioException("No se puede registrar una venta sin productos en el detalle.");
        }

        // Recalcular total desde las líneas de detalle validando cantidad > 0 y precioUnitario > 0
        BigDecimal sumaLineas = BigDecimal.ZERO;
        for (DetalleVenta det : detalles) {
            if (det == null || det.getCantidad() <= 0) {
                throw new NegocioException("La cantidad de cada ítem de venta debe ser mayor a cero.");
            }
            if (det.getPrecioUnitario() == null || det.getPrecioUnitario().compareTo(BigDecimal.ZERO) <= 0) {
                throw new NegocioException("El precio unitario de cada ítem de venta debe ser mayor a cero.");
            }
            BigDecimal subtotalLinea = det.getPrecioUnitario()
                .multiply(BigDecimal.valueOf(det.getCantidad()))
                .setScale(DesgloseFiscal.ESCALA_MONETARIA, DesgloseFiscal.MODO_REDONDEO);
            det.setSubtotal(subtotalLinea);
            sumaLineas = sumaLineas.add(subtotalLinea);
        }

        BigDecimal descuentoAplicado = venta.getDescuento() != null
            ? venta.getDescuento().setScale(DesgloseFiscal.ESCALA_MONETARIA, DesgloseFiscal.MODO_REDONDEO)
            : BigDecimal.ZERO;
        if (descuentoAplicado.compareTo(BigDecimal.ZERO) < 0 || descuentoAplicado.compareTo(sumaLineas) >= 0) {
            throw new NegocioException("El descuento aplicado es inválido respecto al total de la venta.");
        }
        BigDecimal totalTrasDescuentoComercial = sumaLineas.subtract(descuentoAplicado);
        DesgloseFiscal desglose = DesgloseFiscal.desdeTotalConImpuestos(totalTrasDescuentoComercial);

        // Derivar descuentoPuntos ESTRICTAMENTE desde puntosACanjear + ConfiguracionFidelizacion (nunca confiar en caller)
        int puntosSolicitados = venta.getPuntosACanjear();
        BigDecimal descuentoPuntosCaller = venta.getDescuentoPuntos() != null
            ? venta.getDescuentoPuntos().setScale(DesgloseFiscal.ESCALA_MONETARIA, DesgloseFiscal.MODO_REDONDEO)
            : BigDecimal.ZERO;
        if (puntosSolicitados < 0) {
            throw new NegocioException("Los puntos a canjear no pueden ser negativos.");
        }
        if (puntosSolicitados == 0 && descuentoPuntosCaller.compareTo(BigDecimal.ZERO) > 0) {
            throw new NegocioException(
                    "Descuento por puntos rechazado: no se puede aplicar descuento de fidelización con 0 puntos a canjear.");
        }
        BigDecimal descuentoPuntos;
        if (puntosSolicitados > 0) {
            if (venta.getIdCliente() == null || venta.getIdCliente() <= 0 || !venta.isAplicarFidelizacion()) {
                throw new NegocioException(
                        "El canje de puntos requiere un cliente válido con fidelización habilitada.");
            }
            descuentoPuntos = fidelizacionService.calcularDescuentoExactoParaCanjeVenta(
                    puntosSolicitados, totalTrasDescuentoComercial);
        } else {
            descuentoPuntos = BigDecimal.ZERO.setScale(DesgloseFiscal.ESCALA_MONETARIA, DesgloseFiscal.MODO_REDONDEO);
        }

        BigDecimal totalFinal = totalTrasDescuentoComercial.subtract(descuentoPuntos)
            .setScale(DesgloseFiscal.ESCALA_MONETARIA, DesgloseFiscal.MODO_REDONDEO);
        if (totalFinal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegocioException("El total final de la venta debe ser estrictamente mayor a cero.");
        }

        venta.setSubtotal(desglose.getSubtotalGravado());
        venta.setIgv(desglose.getIgv());
        venta.setDescuento(descuentoAplicado);
        venta.setDescuentoPuntos(descuentoPuntos);
        venta.setTotal(totalFinal);

        if (pagosDivididos != null && !pagosDivididos.isEmpty()) {
            BigDecimal sumaPagos = BigDecimal.ZERO;
            for (PagoSplit p : pagosDivididos) {
                if (p == null || p.monto() == null || p.monto().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new NegocioException("Cada pago en cuenta dividida debe tener un monto estrictamente positivo.");
                }
                sumaPagos = sumaPagos.add(p.monto());
            }
            if (sumaPagos.setScale(2, RoundingMode.HALF_EVEN).compareTo(totalFinal) != 0) {
                throw new NegocioException("La suma de los pagos divididos (S/ " + sumaPagos
                    + ") no coincide con el total de la venta (S/ " + totalFinal + ").");
            }
        }
    }

    private Venta registrarVentaEnConexion(Connection conn,
                                           Venta venta,
                                           List<DetalleVenta> detalles,
                                           ComprobantePago comprobante,
                                           List<PagoSplit> pagosDivididos)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException {
        // 1. Verificar idempotencia contra BD (nunca cachear ventas revertidas en un mapa estático en memoria)
        String idemKey = venta.getIdempotencyKey();
        if (idemKey != null && !idemKey.isBlank()) {
            Optional<Venta> existente = ventaDAO.buscarPorIdempotencyKey(conn, idemKey.trim());
            if (existente.isPresent()) {
                LOGGER.info("Idempotencia BD activada: devolviendo venta existente #" + existente.get().getIdVenta());
                return existente.get();
            }
        }

        cierreContableService.validarPeriodoAbierto(conn, venta.getFechaHora());

        Optional<Caja> cajaAbierta = cajaDAO.obtenerCajaAbierta(conn);
        if (cajaAbierta.isEmpty()) {
            throw new CajaCerradaException();
        }
        int idCaja = cajaAbierta.get().getIdCaja();
        venta.setIdCaja(idCaja);

        BigDecimal totalFinal = venta.getTotal();
        BigDecimal descuentoPuntos = venta.getDescuentoPuntos() != null ? venta.getDescuentoPuntos() : BigDecimal.ZERO;

        // 2. Consolidar requerimientos de insumos de todas las líneas (explotando combos) ordenados por id_insumo (R-09, v4.8.0)
        TreeMap<Long, BigDecimal> requerimientosPorInsumo = new TreeMap<>();
        for (DetalleVenta detalle : detalles) {
            completarTrazabilidadComboDetalle(conn, detalle);
            acumularInsumosParaLinea(
                    conn, requerimientosPorInsumo, detalle.getIdCombo(), detalle.getIdProducto(), detalle.getCantidad()
            );
        }

        // 3. Adquirir bloqueo pesimista SELECT ... FOR UPDATE en orden ascendente de id_insumo (R-09)
        List<Long> idsOrdenados = new ArrayList<>(requerimientosPorInsumo.keySet());
        Map<Long, BigDecimal> saldosAnteriores = insumoDAO.lockearPorIds(conn, idsOrdenados);

        // 4. Insertar cabecera y detalle de venta
        int idVenta = ventaDAO.insertarVenta(conn, venta);
        venta.setIdVenta(idVenta);

        for (DetalleVenta detalle : detalles) {
            detalle.setIdVenta(idVenta);
            ventaDAO.insertarDetalleVenta(conn, detalle);
        }

        // 5. Descontar stock en orden ascendente y registrar kardex_movimiento con saldo_anterior/posterior (R-02.3, R-09, R-11)
        for (Long idInsumo : idsOrdenados) {
            if (!saldosAnteriores.containsKey(idInsumo)) {
                throw new InsumoNoEncontradoException(idInsumo);
            }
            BigDecimal cantidadADescontar = requerimientosPorInsumo.get(idInsumo)
                .setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO);
            BigDecimal saldoAnterior = saldosAnteriores.get(idInsumo);

            // Lanza directamente StockInsuficienteException si stock_actual_disponible < cantidadADescontar
            insumoDAO.descontarStock(conn, idInsumo, cantidadADescontar);

            BigDecimal saldoPosterior = saldoAnterior.subtract(cantidadADescontar)
                .setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO);
            Insumo ins = insumoDAO.obtenerPorId(conn, idInsumo);
            BigDecimal costoUnit = (ins != null && ins.getCostoUnitario() != null)
                ? ins.getCostoUnitario() : BigDecimal.ZERO;

            String glosa = "Venta #" + idVenta;
            insumoDAO.registrarMovimientoInventario(conn, idInsumo, "SALIDA", cantidadADescontar, glosa, idVenta);

            KardexMovimiento km = new KardexMovimiento(
                0L,
                idInsumo,
                "SALIDA",
                cantidadADescontar.negate(), // Cantidad firmada negativa para SALIDA (§8.2)
                saldoAnterior,
                saldoPosterior,
                costoUnit,
                "VENTA",
                (long) idVenta,
                venta.getIdUsuario() > 0 ? venta.getIdUsuario() : 1L,
                glosa,
                LocalDateTime.now(ZONA_LIMA)
            );
            insumoDAO.insertarKardex(conn, km);
        }

        // 6. Registrar movimiento(s) en libro de caja (tipo VENTA) (§3.1)
        int idUsrMov = venta.getIdUsuario() > 0 ? venta.getIdUsuario() : 1;
        if (pagosDivididos != null && !pagosDivididos.isEmpty()) {
            int idxPago = 1;
            for (PagoSplit p : pagosDivididos) {
                String medio = normalizarMedioPagoMovimientoCaja(p.medioPago());
                MovimientoCaja movSplit = new MovimientoCaja(
                    0, idCaja, idUsrMov,
                    "VENTA", medio, p.monto().setScale(2, RoundingMode.HALF_EVEN),
                    "Cobro Split (" + idxPago + "/" + pagosDivididos.size() + ") Venta #" + idVenta + " [" + medio + "]"
                );
                cajaDAO.registrarMovimiento(conn, movSplit);
                idxPago++;
            }
        } else {
            String medioPago = normalizarMedioPagoMovimientoCaja(venta.getMetodoPago());
            MovimientoCaja movVenta = new MovimientoCaja(
                0, idCaja, idUsrMov,
                "VENTA", medioPago, totalFinal,
                "Cobro Venta #" + idVenta + " [" + medioPago + "]"
            );
            cajaDAO.registrarMovimiento(conn, movVenta);
        }

        // 7. Emitir Comprobante de Pago
        if (comprobante != null) {
            comprobante.setIdVenta(idVenta);
            comprobante.setTotal(totalFinal);
            ventaDAO.insertarComprobante(conn, comprobante);
        }

        // 8. Si la venta proviene de un pedido de salón, marcar pedido como COBRADO en la misma transacción
        if (venta.getIdPedido() != null && venta.getIdPedido() > 0) {
            pedidoDAO.actualizarEstadoPedido(conn, venta.getIdPedido(), "COBRADO");
        }

        // 9. Fidelización CRM (v5.0.0): redimir puntos usados y acumular puntos ganados en historial_puntos ACID
        if (venta.getIdCliente() != null && venta.getIdCliente() > 0 && venta.isAplicarFidelizacion()) {
            if (venta.getPuntosACanjear() > 0 && descuentoPuntos.compareTo(BigDecimal.ZERO) > 0) {
                fidelizacionService.canjearPuntosConfiguradosEnVenta(
                        conn, venta.getIdCliente(), idVenta, venta.getPuntosACanjear(), descuentoPuntos
                );
            }
            if (totalFinal.compareTo(BigDecimal.ZERO) > 0) {
                fidelizacionService.acumularPuntosPorVenta(conn, venta.getIdCliente(), idVenta, totalFinal);
            }
        }

        LOGGER.info("Venta #" + idVenta + " registrada exitosamente (Total: S/ " + totalFinal + ").");
        return venta;
    }

    private void completarTrazabilidadComboDetalle(java.sql.Connection conn, DetalleVenta detalle) throws SQLException {
        if (detalle == null || detalle.getIdProducto() <= 0) {
            return;
        }
        Optional<Combo> optCombo = (detalle.getIdCombo() != null && detalle.getIdCombo() > 0)
                ? comboDAO.obtenerPorId(conn, detalle.getIdCombo())
                : comboDAO.obtenerPorIdProducto(conn, detalle.getIdProducto());
        if (optCombo.isEmpty()) {
            return;
        }
        Combo combo = optCombo.get();
        if (detalle.getIdCombo() == null || detalle.getIdCombo() <= 0) {
            detalle.setIdCombo(combo.getIdCombo());
        }
        if (detalle.getComboComponentesTexto() == null || detalle.getComboComponentesTexto().isBlank()) {
            String desglose = FormatoPersonalizacionUtil.formatearDesgloseComboMultilinea(combo.getDetalles());
            if (!desglose.isBlank()) {
                detalle.setComboComponentesTexto(desglose);
            }
        }
    }

    private void acumularInsumosParaLinea(java.sql.Connection conn,
                                          Map<Long, BigDecimal> destino,
                                          Integer idCombo,
                                          int idProducto,
                                          int cantidadVendida) throws SQLException, NegocioException {
        List<ComboDetalle> componentesCombo = comboDAO.obtenerComponentesParaVenta(conn, idCombo, idProducto);
        if (!componentesCombo.isEmpty()) {
            for (ComboDetalle comp : componentesCombo) {
                int cantComp = Math.max(1, cantidadVendida) * Math.max(1, comp.getCantidad());
                acumularRecetaProducto(conn, destino, comp.getIdProducto(), cantComp);
            }
            return;
        }
        acumularRecetaProducto(conn, destino, idProducto, Math.max(1, cantidadVendida));
    }

    private void acumularRecetaProducto(java.sql.Connection conn,
                                        Map<Long, BigDecimal> destino,
                                        int idProducto,
                                        int cantidad) throws SQLException, NegocioException {
        List<Receta> receta = insumoDAO.obtenerRecetaPorProducto(conn, idProducto);
        for (Receta rec : receta) {
            BigDecimal cantRequerida = DesgloseFiscal.normalizarStock(
                    rec.getCantidadNecesaria().multiply(BigDecimal.valueOf(cantidad))
            );
            destino.merge(rec.getIdInsumo(), cantRequerida, BigDecimal::add);
        }
    }

    /**
     * Normaliza el medio de pago para {@code movimiento_caja.medio_pago} ({@code VARCHAR(15)} en V1):
     * mapea {@code CREDITO_CORPORATIVO} a {@code CREDITO} y acota longitudes mayores a 15 caracteres.
     */
    public static String normalizarMedioPagoMovimientoCaja(String medioPago) {
        if (medioPago == null || medioPago.isBlank()) {
            return "EFECTIVO";
        }
        String limpio = medioPago.trim().toUpperCase(Locale.ROOT);
        if ("CREDITO_CORPORATIVO".equals(limpio)) {
            return "CREDITO";
        }
        return limpio.length() > 15 ? limpio.substring(0, 15) : limpio;
    }
}
