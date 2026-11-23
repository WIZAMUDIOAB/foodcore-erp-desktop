package com.foodcore.service.finanzas;

import com.foodcore.dao.*;
import com.foodcore.exception.NegocioException;
import com.foodcore.exception.PeriodoCerradoException;
import com.foodcore.model.*;
import com.foodcore.service.CierreContableService;
import com.foodcore.tx.TransactionManager;
import com.foodcore.tx.impl.JdbcTransactionManager;
import java.math.*;
import java.sql.*;
import java.time.*;
import java.util.Optional;

public final class DefaultCuentaCorrienteUseCase implements CuentaCorrienteUseCase {
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final BigDecimal TOLERANCIA_REDONDEO = new BigDecimal("0.001");
    private static final String SQL_LOCK_CLIENTE = "SELECT id_cliente FROM cliente WHERE id_cliente = ? FOR UPDATE";
    private final TransactionManager tx;
    private final CuentaPorPagarDAO cuentaPorPagarDAO;
    private final CuentaPorCobrarDAO cuentaPorCobrarDAO;
    private final ClienteDAO clienteDAO;
    private final CajaDAO cajaDAO;
    private final VentaDAO ventaDAO;
    private final CierreContableService cierreContableService;
    private static final class AbortarSinCommitException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
    public DefaultCuentaCorrienteUseCase(TransactionManager tx, CuentaPorPagarDAO cuentaPorPagarDAO, CuentaPorCobrarDAO cuentaPorCobrarDAO, ClienteDAO clienteDAO, CajaDAO cajaDAO, VentaDAO ventaDAO, CierreContableService cierreContableService) {
        this.tx = tx == null ? new JdbcTransactionManager() : tx;
        this.cuentaPorPagarDAO = cuentaPorPagarDAO; this.cuentaPorCobrarDAO = cuentaPorCobrarDAO;
        this.clienteDAO = clienteDAO; this.cajaDAO = cajaDAO; this.ventaDAO = ventaDAO;
        this.cierreContableService = cierreContableService == null ? new CierreContableService() : cierreContableService;
    }
    private static BigDecimal validarMontoPositivo(double monto, String descripcion) {
        if (Double.isNaN(monto) || Double.isInfinite(monto) || monto <= 0.0) throw new IllegalArgumentException("El " + descripcion + " debe ser un número finito mayor a 0");
        return BigDecimal.valueOf(monto).setScale(2, RoundingMode.HALF_UP);
    }
    private void verificarPeriodoAbierto(Connection conn, LocalDate fecha) throws SQLException {
        try { cierreContableService.validarPeriodoAbierto(conn, fecha != null ? fecha : LocalDate.now(ZONA_LIMA)); }
        catch (PeriodoCerradoException ex) { throw new IllegalStateException(ex.getMessage(), ex); }
    }
    @Override public boolean registrarPagoProveedor(int idCuentaPagar, double monto, String metodoPago, Integer idCaja, int idUsuario, String nota) throws SQLException {
        BigDecimal mb = validarMontoPositivo(monto, "monto a abonar");
        try { return tx.executeChecked(conn -> {
            verificarPeriodoAbierto(conn, LocalDate.now(ZONA_LIMA));
            CuentaPorPagar cxp = cuentaPorPagarDAO.buscarPorIdParaActualizar(conn, idCuentaPagar).orElseThrow(() -> new IllegalArgumentException("Cuenta por pagar no encontrada #" + idCuentaPagar));
            BigDecimal saldo = BigDecimal.valueOf(cxp.getMontoTotal()).setScale(2, RoundingMode.HALF_UP).subtract(BigDecimal.valueOf(cxp.getMontoPagado()).setScale(2, RoundingMode.HALF_UP)).setScale(2, RoundingMode.HALF_UP);
            if (mb.compareTo(saldo.add(TOLERANCIA_REDONDEO)) > 0) throw new IllegalArgumentException("El monto (S/ " + mb + ") excede el saldo pendiente (S/ " + saldo + ")");
            PagoProveedor pago = new PagoProveedor(); pago.setIdCuentaPagar(idCuentaPagar); pago.setIdCaja(idCaja); pago.setIdUsuario(idUsuario); pago.setMonto(mb.doubleValue()); pago.setMetodoPago(metodoPago); pago.setNota(nota);
            if (!cuentaPorPagarDAO.registrarPago(conn, pago)) throw new AbortarSinCommitException();
            if (idCaja != null && "EFECTIVO".equalsIgnoreCase(metodoPago)) { Optional<Caja> caja = cajaDAO.obtenerCajaAbierta(conn); if (caja.isPresent()) { MovimientoCaja m = new MovimientoCaja(); m.setIdCaja(caja.get().getIdCaja()); m.setIdUsuario(idUsuario); m.setTipoMovimiento("EGRESO"); m.setMedioPago("EFECTIVO"); m.setMonto(mb); m.setConcepto("PAGO PROVEEDOR: " + cxp.getRazonSocialProveedor() + " (Doc: " + cxp.getNumeroDocumento() + ")"); cajaDAO.registrarMovimiento(conn, m); } }
            return true;
        }); } catch (AbortarSinCommitException ex) { return false; } catch (NegocioException ex) { throw new IllegalStateException(ex.getMessage(), ex); }
    }
    @Override public boolean registrarCobroCliente(int idCuentaCobrar, double monto, String metodoPago, Integer idCaja, int idUsuario, String nota) throws SQLException {
        BigDecimal mb = validarMontoPositivo(monto, "monto a cobrar");
        try { return tx.executeChecked(conn -> {
            verificarPeriodoAbierto(conn, LocalDate.now(ZONA_LIMA));
            CuentaPorCobrar cxc = cuentaPorCobrarDAO.buscarPorIdParaActualizar(conn, idCuentaCobrar).orElseThrow(() -> new IllegalArgumentException("Cuenta por cobrar no encontrada #" + idCuentaCobrar));
            BigDecimal saldo = BigDecimal.valueOf(cxc.getMontoTotal()).setScale(2, RoundingMode.HALF_UP).subtract(BigDecimal.valueOf(cxc.getMontoCobrado()).setScale(2, RoundingMode.HALF_UP)).setScale(2, RoundingMode.HALF_UP);
            if (mb.compareTo(saldo.add(TOLERANCIA_REDONDEO)) > 0) throw new IllegalArgumentException("El monto (S/ " + mb + ") excede el saldo adeudado (S/ " + saldo + ")");
            CobroCliente cobro = new CobroCliente(); cobro.setIdCuentaCobrar(idCuentaCobrar); cobro.setIdCaja(idCaja); cobro.setIdUsuario(idUsuario); cobro.setMonto(mb.doubleValue()); cobro.setMetodoPago(metodoPago); cobro.setNota(nota);
            if (!cuentaPorCobrarDAO.registrarCobro(conn, cobro)) throw new AbortarSinCommitException();
            if (idCaja != null && "EFECTIVO".equalsIgnoreCase(metodoPago)) { Optional<Caja> caja = cajaDAO.obtenerCajaAbierta(conn); if (caja.isPresent()) { MovimientoCaja m = new MovimientoCaja(); m.setIdCaja(caja.get().getIdCaja()); m.setIdUsuario(idUsuario); m.setTipoMovimiento("INGRESO"); m.setMedioPago("EFECTIVO"); m.setMonto(mb); m.setConcepto("COBRO CLIENTE: " + cxc.getRazonSocialCliente() + " (Comp: " + cxc.getNumeroComprobante() + ")"); cajaDAO.registrarMovimiento(conn, m); } }
            return true;
        }); } catch (AbortarSinCommitException ex) { return false; } catch (NegocioException ex) { throw new IllegalStateException(ex.getMessage(), ex); }
    }
    @Override public int registrarVentaCreditoCorporativo(Venta venta, int idCliente, int diasCredito) throws SQLException {
        if (venta == null || venta.getTotal() == null || venta.getTotal().compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("El total de la venta a crédito debe ser mayor a 0");
        if (idCliente <= 0) throw new IllegalArgumentException("Debe seleccionar un cliente corporativo válido");
        BigDecimal total = venta.getTotal().setScale(2, RoundingMode.HALF_UP);
        try { return tx.executeChecked(conn -> {
            LocalDate fecha = venta.getFechaHora() != null ? venta.getFechaHora().toLocalDate() : LocalDate.now(ZONA_LIMA); verificarPeriodoAbierto(conn, fecha);
            try (PreparedStatement ps = conn.prepareStatement(SQL_LOCK_CLIENTE)) { ps.setInt(1, idCliente); try (ResultSet rs = ps.executeQuery()) { if (!rs.next()) throw new IllegalArgumentException("Cliente corporativo no encontrado #" + idCliente); } }
            Cliente cliente = clienteDAO.buscarPorId(conn, idCliente).orElseThrow(() -> new IllegalArgumentException("Cliente corporativo no encontrado #" + idCliente));
            BigDecimal limite = BigDecimal.valueOf(cliente.getLimiteCredito()).setScale(2, RoundingMode.HALF_UP), deuda = BigDecimal.valueOf(cliente.getSaldoDeudor()).setScale(2, RoundingMode.HALF_UP);
            if (limite.compareTo(BigDecimal.ZERO) > 0 && deuda.add(total).compareTo(limite) > 0) throw new IllegalStateException("Límite de crédito superado (Límite: S/ " + limite + ", Deuda actual: S/ " + deuda + ", Total venta: S/ " + total + ")");
            venta.setMetodoPago("CREDITO"); venta.setMontoRecibido(BigDecimal.ZERO); venta.setVuelto(BigDecimal.ZERO); venta.setTotal(total);
            int idVenta = ventaDAO.insertarVenta(conn, venta); CuentaPorCobrar cxc = new CuentaPorCobrar(); cxc.setIdCliente(idCliente); cxc.setIdVenta(idVenta); cxc.setNumeroComprobante("CR-" + String.format("%06d", idVenta)); LocalDate hoy = LocalDate.now(ZONA_LIMA); cxc.setFechaEmision(hoy); cxc.setFechaVencimiento(hoy.plusDays(diasCredito > 0 ? diasCredito : Math.max(1, cliente.getDiasCredito()))); cxc.setMontoTotal(total.doubleValue()); cxc.setMontoCobrado(0.0); cxc.setEstado("PENDIENTE"); cxc.setObservaciones("Venta POS a crédito corporativo. Cliente: " + cliente.getRazonSocial()); cuentaPorCobrarDAO.insertar(conn, cxc); return idVenta;
        }); } catch (NegocioException ex) { throw new IllegalStateException(ex.getMessage(), ex); }
    }
}
