package com.foodcore.service.venta;

import com.foodcore.dao.AuditoriaDAO;
import com.foodcore.dao.CajaDAO;
import com.foodcore.dao.VentaDAO;
import com.foodcore.exception.CodigoError;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Caja;
import com.foodcore.model.DesgloseFiscal;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.MovimientoCaja;
import com.foodcore.model.Usuario;
import com.foodcore.model.dto.ResultadoDevolucionParcial;
import com.foodcore.model.enums.Permiso;
import com.foodcore.security.AutorizacionService;
import com.foodcore.security.SesionUsuario;
import com.foodcore.service.CierreContableService;
import com.foodcore.service.FidelizacionService;
import com.foodcore.service.SupervisorAutorizacionService;
import com.foodcore.tx.TransactionManager;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public final class DefaultAnularDevolverVentaUseCase implements AnularDevolverVentaUseCase {
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final String SQL_LOCK_VENTA =
            "SELECT id_caja, id_cliente, total, metodo_pago, estado, fecha_hora FROM venta WHERE id_venta = ? FOR UPDATE";
    private static final String SQL_ANULAR_VENTA =
            "UPDATE venta SET estado = 'ANULADA' WHERE id_venta = ?";
    private static final String SQL_DETALLES_NO_DEVUELTOS =
            "SELECT id_producto, id_combo, cantidad FROM detalle_venta WHERE id_venta = ? AND COALESCE(estado, 'COMPLETADO') <> 'DEVUELTO'";

    private final TransactionManager tx;
    private final VentaDAO ventaDAO;
    private final CajaDAO cajaDAO;
    private final AutorizacionService autorizacionService;
    private final SupervisorAutorizacionService supervisorAutorizacionService;
    private final CierreContableService cierreContableService;
    private final FidelizacionService fidelizacionService;
    private final AuditoriaDAO auditoriaDAO;
    private final VentaCalculoValidator calculoValidator;
    private final VentaInventarioKardexHandler inventarioKardexHandler;

    public DefaultAnularDevolverVentaUseCase(
            TransactionManager tx,
            VentaDAO ventaDAO,
            CajaDAO cajaDAO,
            AutorizacionService autorizacionService,
            SupervisorAutorizacionService supervisorAutorizacionService,
            CierreContableService cierreContableService,
            FidelizacionService fidelizacionService,
            AuditoriaDAO auditoriaDAO,
            VentaCalculoValidator calculoValidator,
            VentaInventarioKardexHandler inventarioKardexHandler) {
        this.tx = tx;
        this.ventaDAO = ventaDAO;
        this.cajaDAO = cajaDAO;
        this.autorizacionService = autorizacionService;
        this.supervisorAutorizacionService = supervisorAutorizacionService;
        this.cierreContableService = cierreContableService;
        this.fidelizacionService = fidelizacionService;
        this.auditoriaDAO = auditoriaDAO;
        this.calculoValidator = calculoValidator;
        this.inventarioKardexHandler = inventarioKardexHandler;
    }

    public static BigDecimal calcularMontoReembolsoProrrateado(
            BigDecimal totalActual, BigDecimal subtotalDevuelto, BigDecimal subtotalRestante)
            throws NegocioException {
        BigDecimal brutoActivo = subtotalDevuelto.add(subtotalRestante);
        if (subtotalRestante.compareTo(BigDecimal.ZERO) <= 0) {
            return DesgloseFiscal.normalizarMoneda(totalActual);
        }
        if (brutoActivo.compareTo(BigDecimal.ZERO) > 0 && totalActual.compareTo(brutoActivo) < 0) {
            return DesgloseFiscal.normalizarMoneda(
                    totalActual.multiply(subtotalDevuelto)
                            .divide(brutoActivo, DesgloseFiscal.ESCALA_MONETARIA, DesgloseFiscal.MODO_REDONDEO)
                            .min(totalActual));
        }
        return DesgloseFiscal.normalizarMoneda(subtotalDevuelto.min(totalActual));
    }

    @Override
    public void anularVenta(int idVenta, boolean platoYaPreparado, SesionUsuario sesion, String motivo)
            throws NegocioException, SQLException {
        autorizacionService.exigir(sesion, Permiso.VENTA_ANULAR);
        if (idVenta <= 0) {
            throw new NegocioException("ID de venta inválido para anulación.");
        }
        String glosaMotivo = motivo != null && !motivo.isBlank() ? motivo.trim() : "Anulación operativa POS";
        ejecutarAnulacionTx(idVenta, platoYaPreparado, sesion.getIdUsuario(), glosaMotivo,
                null, sesion.getNombreCompleto());
    }

    @Override
    public Usuario anularVentaConSupervisor(int idVenta, boolean platoYaPreparado,
                                             SesionUsuario sesionCajero, String pinSupervisor, String motivo)
            throws NegocioException, SQLException {
        if (sesionCajero == null || sesionCajero.haExpirado(LocalDateTime.now(ZONA_LIMA))) {
            throw new NegocioException(CodigoError.ACCESO_DENEGADO,
                    "Debe iniciar sesión vigente para solicitar una anulación supervisada.");
        }
        if (idVenta <= 0) {
            throw new NegocioException("ID de venta inválido para anulación.");
        }
        Usuario supervisor = supervisorAutorizacionService.autenticarSupervisorPorPin(pinSupervisor);
        String glosaMotivo = motivo != null && !motivo.isBlank() ? motivo.trim() : "Anulación supervisada POS";
        ejecutarAnulacionTx(idVenta, platoYaPreparado, sesionCajero.getIdUsuario(), glosaMotivo,
                supervisor, sesionCajero.getNombreCompleto());
        return supervisor;
    }

    private void ejecutarAnulacionTx(int idVenta, boolean platoYaPreparado, long idUsuarioSolicitante,
                                      String glosaMotivo, Usuario supervisorAutorizante,
                                      String usernameSolicitante) throws NegocioException, SQLException {
        tx.executeVoidChecked(conn -> {
            int idCaja;
            Integer idCliente;
            BigDecimal totalVenta;
            String metodoPago;
            String estadoActual;
            LocalDateTime fechaVenta = null;
            try (PreparedStatement ps = conn.prepareStatement(SQL_LOCK_VENTA)) {
                ps.setInt(1, idVenta);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new NegocioException("No se encontró la venta #" + idVenta + " para anular.");
                    }
                    idCaja = rs.getInt("id_caja");
                    int rawCliente = rs.getInt("id_cliente");
                    idCliente = rs.wasNull() || rawCliente <= 0 ? null : rawCliente;
                    totalVenta = rs.getBigDecimal("total");
                    metodoPago = rs.getString("metodo_pago");
                    estadoActual = rs.getString("estado");
                    Timestamp timestamp = rs.getTimestamp("fecha_hora");
                    if (timestamp != null) {
                        fechaVenta = timestamp.toLocalDateTime();
                    }
                }
            }
            if ("ANULADA".equalsIgnoreCase(estadoActual)) {
                throw new NegocioException("La venta #" + idVenta + " ya fue anulada previamente.");
            }
            cierreContableService.validarPeriodoAbierto(conn, fechaVenta);
            try (PreparedStatement ps = conn.prepareStatement(SQL_ANULAR_VENTA)) {
                ps.setInt(1, idVenta);
                ps.executeUpdate();
            }
            int idUsuarioMov = (int) idUsuarioSolicitante;
            String sufijo = supervisorAutorizante != null
                    ? " [Autorizado por " + supervisorAutorizante.getUsername() + "]" : "";
            cajaDAO.registrarMovimiento(conn, new MovimientoCaja(
                    0, idCaja, idUsuarioMov, "ANULACION",
                    calculoValidator.normalizarMedioPagoMovimientoCaja(metodoPago), totalVenta.negate(),
                    "Anulación Venta #" + idVenta + ": " + glosaMotivo + sufijo));
            if (idCliente != null) {
                fidelizacionService.revertirPuntosPorAnulacionVenta(conn, idCliente, idVenta, glosaMotivo);
            }
            if (supervisorAutorizante != null) {
                auditoriaDAO.registrarEventoSeguro(conn, supervisorAutorizante.getIdUsuario(),
                        "ANULACION_VENTA_SUPERVISOR", "venta#" + idVenta,
                        estadoActual + " (S/ " + totalVenta + ")",
                        "ANULADA | Solicitante="
                                + (usernameSolicitante != null ? usernameSolicitante : "cajero#" + idUsuarioSolicitante)
                                + " | Autorizante=" + supervisorAutorizante.getUsername()
                                + " (" + supervisorAutorizante.getNombreRol() + ") | Motivo=" + glosaMotivo);
            }
            Map<Long, BigDecimal> insumos = new TreeMap<>();
            try (PreparedStatement ps = conn.prepareStatement(SQL_DETALLES_NO_DEVUELTOS)) {
                ps.setInt(1, idVenta);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int idProducto = rs.getInt("id_producto");
                        int rawCombo = rs.getInt("id_combo");
                        Integer idCombo = rs.wasNull() || rawCombo <= 0 ? null : rawCombo;
                        inventarioKardexHandler.acumularInsumosParaLinea(
                                conn, insumos, idCombo, idProducto, rs.getInt("cantidad"));
                    }
                }
            }
            inventarioKardexHandler.revertirOMermarInsumosPorAnulacion(
                    conn, idVenta, platoYaPreparado, idUsuarioSolicitante, glosaMotivo, insumos);
        });
    }

    @Override
    public ResultadoDevolucionParcial devolverItemsParcial(
            int idVenta, List<Integer> idsDetalleADevolver, SesionUsuario sesion, String motivo)
            throws NegocioException, SQLException {
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
        for (Integer idDetalle : idsDetalleADevolver) {
            if (idDetalle != null && idDetalle > 0) {
                idsUnicos.add(idDetalle);
            }
        }
        if (idsUnicos.isEmpty()) {
            throw new NegocioException(CodigoError.VALIDACION_ENTRADA,
                    "Debe seleccionar al menos un ítem válido para devolver.");
        }
        String glosaMotivo = motivo != null && !motivo.isBlank() ? motivo.trim() : "Devolución parcial en POS";
        return tx.executeChecked(conn -> {
            int idCajaVenta;
            Integer idCliente;
            BigDecimal totalActual;
            String metodoPago;
            String estadoVenta;
            LocalDateTime fechaVenta = null;
            try (PreparedStatement ps = conn.prepareStatement(SQL_LOCK_VENTA)) {
                ps.setInt(1, idVenta);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new NegocioException(CodigoError.RECURSO_NO_ENCONTRADO,
                                "No se encontró la venta #" + idVenta + ".");
                    }
                    idCajaVenta = rs.getInt("id_caja");
                    int rawCliente = rs.getInt("id_cliente");
                    idCliente = rs.wasNull() || rawCliente <= 0 ? null : rawCliente;
                    BigDecimal total = rs.getBigDecimal("total");
                    totalActual = total != null ? total : BigDecimal.ZERO;
                    metodoPago = rs.getString("metodo_pago");
                    estadoVenta = rs.getString("estado");
                    Timestamp timestamp = rs.getTimestamp("fecha_hora");
                    if (timestamp != null) {
                        fechaVenta = timestamp.toLocalDateTime();
                    }
                }
            }
            if ("ANULADA".equalsIgnoreCase(estadoVenta)) {
                throw new NegocioException("La venta #" + idVenta + " está ANULADA; no admite devoluciones parciales.");
            }
            cierreContableService.validarPeriodoAbierto(conn, fechaVenta);
            List<DetalleVenta> todosDetalles = ventaDAO.listarDetallesPorVenta(conn, idVenta);
            Map<Integer, DetalleVenta> mapaDetalles = new LinkedHashMap<>();
            for (DetalleVenta detalle : todosDetalles) {
                mapaDetalles.put(detalle.getIdDetalleVenta(), detalle);
            }
            BigDecimal subtotalDevuelto = BigDecimal.ZERO;
            BigDecimal subtotalRestante = BigDecimal.ZERO;
            List<DetalleVenta> aDevolver = new ArrayList<>();
            for (Integer idRequerido : idsUnicos) {
                DetalleVenta detalle = mapaDetalles.get(idRequerido);
                if (detalle == null) {
                    throw new NegocioException(CodigoError.RECURSO_NO_ENCONTRADO,
                            "El ítem #" + idRequerido + " no pertenece a la venta #" + idVenta + ".");
                }
                if (detalle.isDevuelto()) {
                    throw new NegocioException("El ítem '" + detalle.getNombreProducto() + "' ya fue devuelto previamente.");
                }
                aDevolver.add(detalle);
                subtotalDevuelto = subtotalDevuelto.add(
                        detalle.getSubtotal() != null ? detalle.getSubtotal() : BigDecimal.ZERO);
            }
            for (DetalleVenta detalle : todosDetalles) {
                if (!detalle.isDevuelto() && !idsUnicos.contains(detalle.getIdDetalleVenta())) {
                    subtotalRestante = subtotalRestante.add(
                            detalle.getSubtotal() != null ? detalle.getSubtotal() : BigDecimal.ZERO);
                }
            }
            BigDecimal montoReembolso = calcularMontoReembolsoProrrateado(
                    totalActual, subtotalDevuelto, subtotalRestante);
            BigDecimal nuevoTotal = DesgloseFiscal.normalizarMoneda(
                    totalActual.subtract(montoReembolso).max(BigDecimal.ZERO));
            DesgloseFiscal desglose = nuevoTotal.compareTo(BigDecimal.ZERO) > 0
                    ? DesgloseFiscal.desdeTotalConImpuestos(nuevoTotal) : null;
            BigDecimal nuevoSubtotal = desglose != null ? desglose.getSubtotalGravado() : BigDecimal.ZERO;
            BigDecimal nuevoIgv = desglose != null ? desglose.getIgv() : BigDecimal.ZERO;
            String nuevoEstado = subtotalRestante.compareTo(BigDecimal.ZERO) <= 0
                    || nuevoTotal.compareTo(BigDecimal.ZERO) <= 0 ? "ANULADA" : "COMPLETADA";
            Map<Long, BigDecimal> insumos = new TreeMap<>();
            for (DetalleVenta detalle : aDevolver) {
                if (ventaDAO.marcarDetalleDevuelto(conn, detalle.getIdDetalleVenta(), glosaMotivo) == 0) {
                    throw new NegocioException("El ítem #" + detalle.getIdDetalleVenta() + " ya se encontraba devuelto.");
                }
                inventarioKardexHandler.acumularInsumosParaLinea(
                        conn, insumos, detalle.getIdCombo(), detalle.getIdProducto(), detalle.getCantidad());
            }
            ventaDAO.actualizarTotalesVentaPorDevolucion(
                    conn, idVenta, nuevoSubtotal, nuevoIgv, nuevoTotal, nuevoEstado);
            Optional<Caja> cajaActiva = cajaDAO.obtenerCajaAbierta(conn);
            int idCajaEgreso = cajaActiva.map(Caja::getIdCaja).orElse(idCajaVenta);
            cajaDAO.registrarMovimiento(conn, new MovimientoCaja(
                    0, idCajaEgreso, (int) sesion.getIdUsuario(), "EGRESO",
                    calculoValidator.normalizarMedioPagoMovimientoCaja(metodoPago), montoReembolso,
                    "Devolución parcial Venta #" + idVenta + " (" + aDevolver.size()
                            + " ítem(s)): " + glosaMotivo));
            if (idCliente != null) {
                fidelizacionService.revertirPuntosPorDevolucionParcial(
                        conn, idCliente, idVenta, montoReembolso,
                        "ANULADA".equalsIgnoreCase(nuevoEstado), glosaMotivo);
            }
            inventarioKardexHandler.revertirInsumosPorDevolucionParcial(
                    conn, idVenta, sesion.getIdUsuario(), glosaMotivo, insumos);
            auditoriaDAO.registrarEventoSeguro(conn, (int) sesion.getIdUsuario(),
                    "DEVOLUCION_PARCIAL_VENTA", "venta#" + idVenta,
                    "Total=S/ " + totalActual,
                    "Reembolso=S/ " + montoReembolso + " | NuevoTotal=S/ " + nuevoTotal
                            + " | Items=" + idsUnicos + " | Motivo=" + glosaMotivo);
            return new ResultadoDevolucionParcial(
                    idVenta, List.copyOf(idsUnicos), montoReembolso, nuevoTotal,
                    nuevoEstado, insumos.size());
        });
    }
}