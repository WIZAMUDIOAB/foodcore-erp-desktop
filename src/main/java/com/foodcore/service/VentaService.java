package com.foodcore.service;

import com.foodcore.dao.*;
import com.foodcore.dao.impl.ComboDAOImpl;
import com.foodcore.dao.impl.VentaDAOImpl;
import com.foodcore.util.FormatoPersonalizacionUtil;
import com.foodcore.exception.CajaCerradaException;
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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final Map<String, Venta> REGISTRO_IDEMPOTENCIA = new ConcurrentHashMap<>();

    public record PagoSplit(String medioPago, BigDecimal monto) {
    }

    public record SubcuentaItems(List<DetalleVenta> detalles, String medioPago) {
    }

    private final TransactionManager tx;
    private final VentaDAO ventaDAO;
    private final CajaDAO cajaDAO;
    private final InsumoDAO insumoDAO;
    private final PedidoDAO pedidoDAO;
    private final ComboDAO comboDAO;
    private final AutorizacionService autorizacionService = new AutorizacionService();

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
     * con su respectivo medio de pago y comprobante interno.
     */
    public List<Venta> dividirCuentaPorItems(List<SubcuentaItems> subcuentas, int idUsuario)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException {
        if (subcuentas == null || subcuentas.isEmpty()) {
            throw new NegocioException("Debe especificar al menos una subcuenta con ítems.");
        }
        List<Venta> ventasGeneradas = new ArrayList<>();
        int correlativo = 1;
        for (SubcuentaItems sub : subcuentas) {
            if (sub.detalles() == null || sub.detalles().isEmpty()) {
                continue;
            }
            BigDecimal suma = BigDecimal.ZERO;
            for (DetalleVenta d : sub.detalles()) {
                BigDecimal subLinea = d.getPrecioUnitario().multiply(BigDecimal.valueOf(d.getCantidad()));
                d.setSubtotal(subLinea);
                suma = suma.add(subLinea);
            }
            DesgloseFiscal desglose = DesgloseFiscal.desdeTotalConImpuestos(suma);
            String medio = sub.medioPago() != null ? sub.medioPago() : "EFECTIVO";
            Venta v = new Venta(0, null, 1, idUsuario, 1,
                desglose.getSubtotalGravado(), desglose.getIgv(), desglose.getTotal(),
                medio, desglose.getTotal(), BigDecimal.ZERO);
            v.setIdempotencyKey(UUID.randomUUID().toString());
            ComprobantePago comp = new ComprobantePago(0, 0, "TICKET", "SP" + correlativo,
                String.valueOf(System.currentTimeMillis() % 1000000), desglose.getTotal());
            ventasGeneradas.add(registrarVenta(v, sub.detalles(), comp));
            correlativo++;
        }
        return ventasGeneradas;
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

        tx.executeVoidChecked(conn -> {
            int idCaja;
            BigDecimal totalVenta;
            String metodoPago;
            String estadoActual;

            String sqlVenta = "SELECT id_caja, total, metodo_pago, estado FROM venta WHERE id_venta = ? FOR UPDATE";
            try (PreparedStatement ps = conn.prepareStatement(sqlVenta)) {
                ps.setInt(1, idVenta);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new NegocioException("No se encontró la venta #" + idVenta + " para anular.");
                    }
                    idCaja = rs.getInt("id_caja");
                    totalVenta = rs.getBigDecimal("total");
                    metodoPago = rs.getString("metodo_pago");
                    estadoActual = rs.getString("estado");
                }
            }

            if ("ANULADA".equalsIgnoreCase(estadoActual)) {
                throw new NegocioException("La venta #" + idVenta + " ya fue anulada previamente.");
            }

            try (PreparedStatement psUpd = conn.prepareStatement("UPDATE venta SET estado = 'ANULADA' WHERE id_venta = ?")) {
                psUpd.setInt(1, idVenta);
                psUpd.executeUpdate();
            }

            int idUsuario = (int) sesion.getIdUsuario();
            MovimientoCaja movAnulacion = new MovimientoCaja(
                0, idCaja, idUsuario, "ANULACION",
                metodoPago != null ? metodoPago : "EFECTIVO",
                totalVenta.negate(),
                "Anulación Venta #" + idVenta + ": " + glosaMotivo
            );
            cajaDAO.registrarMovimiento(conn, movAnulacion);

            // Leer ítems vendidos y sus recetas (o componentes de combo) para revertir stock o registrar merma (§3.2, v4.8.0)
            Map<Long, BigDecimal> insumosAfectados = new TreeMap<>();
            String sqlDet = "SELECT id_producto, id_combo, cantidad FROM detalle_venta WHERE id_venta = ?";
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

            for (Map.Entry<Long, BigDecimal> entry : insumosAfectados.entrySet()) {
                long idIns = entry.getKey();
                BigDecimal cantInsumo = entry.getValue();
                if (!platoYaPreparado) {
                    // Revertir al inventario porque el plato no llegó a cocinarse
                    insumoDAO.registrarMovimientoKardexAtomico(
                        conn, idIns, "ENTRADA", cantInsumo, sesion.getIdUsuario(),
                        "Reversión por Anulación Venta #" + idVenta + " (No preparado)"
                    );
                } else {
                    // El plato ya fue cocinado: el descuento de venta ya ocurrió; registramos asiento de MERMA
                    Insumo ins = insumoDAO.obtenerPorId(conn, idIns);
                    BigDecimal saldoActual = ins != null ? ins.getStockActualDisponible() : BigDecimal.ZERO;
                    BigDecimal costo = ins != null && ins.getCostoUnitario() != null ? ins.getCostoUnitario() : BigDecimal.ZERO;
                    String obsMerma = "Merma por Anulación Venta #" + idVenta + " (Plato ya cocinado: " + glosaMotivo + ")";
                    insumoDAO.registrarMovimientoInventario(conn, idIns, "MERMA", cantInsumo, obsMerma, idVenta);
                    KardexMovimiento kmMerma = new KardexMovimiento(
                        0L, idIns, "MERMA", cantInsumo.negate(), saldoActual, saldoActual,
                        costo, "ANULACION", (long) idVenta, sesion.getIdUsuario(), obsMerma, LocalDateTime.now()
                    );
                    insumoDAO.insertarKardex(conn, kmMerma);
                }
            }
        });
    }

    private Venta registrarVentaConPagos(Venta venta,
                                         List<DetalleVenta> detalles,
                                         ComprobantePago comprobante,
                                         List<PagoSplit> pagosDivididos)
            throws NegocioException, SQLException, StockInsuficienteException, InsumoNoEncontradoException {
        if (venta == null) {
            throw new NegocioException("Los datos de la venta no pueden ser nulos.");
        }
        if (detalles == null || detalles.isEmpty()) {
            throw new NegocioException("No se puede registrar una venta sin productos en el detalle.");
        }

        // 1. Verificar idempotencia (SPEC-FOODCORE-v1.0 §8.1, §9.2)
        String idemKey = venta.getIdempotencyKey();
        if (idemKey != null && REGISTRO_IDEMPOTENCIA.containsKey(idemKey)) {
            LOGGER.info("Idempotencia activada: devolviendo venta previamente procesada para clave " + idemKey);
            return REGISTRO_IDEMPOTENCIA.get(idemKey);
        }

        // 2. Recalcular total desde las líneas de detalle con redondeo HALF_EVEN y descuento si aplica (§3.2)
        BigDecimal sumaLineas = BigDecimal.ZERO;
        for (DetalleVenta det : detalles) {
            if (det.getCantidad() <= 0) {
                throw new NegocioException("La cantidad de cada ítem de venta debe ser mayor a cero.");
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
        BigDecimal totalNeto = sumaLineas.subtract(descuentoAplicado);

        DesgloseFiscal desglose = DesgloseFiscal.desdeTotalConImpuestos(totalNeto);
        venta.setSubtotal(desglose.getSubtotalGravado());
        venta.setIgv(desglose.getIgv());
        venta.setDescuento(descuentoAplicado);
        venta.setTotal(desglose.getTotal());

        if (pagosDivididos != null && !pagosDivididos.isEmpty()) {
            BigDecimal sumaPagos = BigDecimal.ZERO;
            for (PagoSplit p : pagosDivididos) {
                sumaPagos = sumaPagos.add(p.monto());
            }
            if (sumaPagos.setScale(2, RoundingMode.HALF_EVEN).compareTo(desglose.getTotal()) != 0) {
                throw new NegocioException("La suma de los pagos divididos (S/ " + sumaPagos
                    + ") no coincide con el total de la venta (S/ " + desglose.getTotal() + ").");
            }
        }

        // 3. Ejecutar unidad de trabajo atómica ACID vía TransactionManager (§7.1, §9.1, R-09, R-10, R-11)
        return tx.executeChecked(conn -> {
            Optional<Caja> cajaAbierta = cajaDAO.obtenerCajaAbierta(conn);
            if (cajaAbierta.isEmpty()) {
                throw new CajaCerradaException();
            }
            int idCaja = cajaAbierta.get().getIdCaja();
            venta.setIdCaja(idCaja);

            // 3.1 Consolidar requerimientos de insumos de todas las líneas (explotando combos) ordenados por id_insumo (R-09, v4.8.0)
            TreeMap<Long, BigDecimal> requerimientosPorInsumo = new TreeMap<>();
            for (DetalleVenta detalle : detalles) {
                completarTrazabilidadComboDetalle(conn, detalle);
                acumularInsumosParaLinea(
                        conn, requerimientosPorInsumo, detalle.getIdCombo(), detalle.getIdProducto(), detalle.getCantidad()
                );
            }

            // 3.2 Adquirir bloqueo pesimista SELECT ... FOR UPDATE en orden ascendente de id_insumo (R-09)
            List<Long> idsOrdenados = new ArrayList<>(requerimientosPorInsumo.keySet());
            Map<Long, BigDecimal> saldosAnteriores = insumoDAO.lockearPorIds(conn, idsOrdenados);

            // 3.3 Insertar cabecera y detalle de venta
            int idVenta = ventaDAO.insertarVenta(conn, venta);
            venta.setIdVenta(idVenta);

            for (DetalleVenta detalle : detalles) {
                detalle.setIdVenta(idVenta);
                ventaDAO.insertarDetalleVenta(conn, detalle);
            }

            // 3.4 Descontar stock en orden ascendente y registrar kardex_movimiento con saldo_anterior/posterior (R-02.3, R-09, R-11)
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
                    LocalDateTime.now()
                );
                insumoDAO.insertarKardex(conn, km);
            }

            // 3.5 Registrar movimiento(s) en libro de caja (tipo VENTA) (§3.1)
            int idUsrMov = venta.getIdUsuario() > 0 ? venta.getIdUsuario() : 1;
            if (pagosDivididos != null && !pagosDivididos.isEmpty()) {
                int idxPago = 1;
                for (PagoSplit p : pagosDivididos) {
                    String medio = p.medioPago() != null ? p.medioPago() : "EFECTIVO";
                    MovimientoCaja movSplit = new MovimientoCaja(
                        0, idCaja, idUsrMov,
                        "VENTA", medio, p.monto().setScale(2, RoundingMode.HALF_EVEN),
                        "Cobro Split (" + idxPago + "/" + pagosDivididos.size() + ") Venta #" + idVenta + " [" + medio + "]"
                    );
                    cajaDAO.registrarMovimiento(conn, movSplit);
                    idxPago++;
                }
            } else {
                String medioPago = venta.getMetodoPago() != null ? venta.getMetodoPago() : "EFECTIVO";
                MovimientoCaja movVenta = new MovimientoCaja(
                    0, idCaja, idUsrMov,
                    "VENTA", medioPago, desglose.getTotal(),
                    "Cobro Venta #" + idVenta + " [" + medioPago + "]"
                );
                cajaDAO.registrarMovimiento(conn, movVenta);
            }

            // 3.6 Emitir Comprobante de Pago
            if (comprobante != null) {
                comprobante.setIdVenta(idVenta);
                comprobante.setTotal(desglose.getTotal());
                ventaDAO.insertarComprobante(conn, comprobante);
            }

            // 3.7 Si la venta proviene de un pedido de salón, marcar pedido como COBRADO en la misma transacción
            if (venta.getIdPedido() != null && venta.getIdPedido() > 0) {
                pedidoDAO.actualizarEstadoPedido(conn, venta.getIdPedido(), "COBRADO");
            }

            if (idemKey != null) {
                REGISTRO_IDEMPOTENCIA.put(idemKey, venta);
            }
            LOGGER.info("Venta #" + idVenta + " registrada exitosamente (Total: S/ " + desglose.getTotal() + ").");
            return venta;
        });
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
}
