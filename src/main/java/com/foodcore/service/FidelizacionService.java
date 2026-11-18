package com.foodcore.service;

import com.foodcore.dao.ClienteDAO;
import com.foodcore.dao.FidelizacionDAO;
import com.foodcore.dao.impl.ClienteDAOImpl;
import com.foodcore.dao.impl.FidelizacionDAOImpl;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Cliente;
import com.foodcore.model.CompraClienteResumen;
import com.foodcore.model.CuponCumpleanos;
import com.foodcore.model.FidelizacionMovimiento;
import com.foodcore.model.HistorialPuntos;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.ConfiguracionFidelizacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FidelizacionService {

    private static final Logger LOGGER = Logger.getLogger(FidelizacionService.class.getName());
    public static final int PUNTOS_POR_SOL = 1; // S/ 1 = 1 punto (valor por defecto)
    public static final int PUNTOS_POR_SOL_DESCUENTO = 10; // 10 puntos = S/ 1 (compatibilidad v2.0)

    public record BeneficioCanje(int bloquesDisponibles,
                                 int puntosPorBloque,
                                 BigDecimal descuentoPorBloqueSoles,
                                 int puntosUtilizados,
                                 BigDecimal descuentoAplicadoSoles) {
    }

    private final FidelizacionDAO fidelizacionDAO;
    private final ClienteDAO clienteDAO;

    public FidelizacionService() {
        this(new FidelizacionDAOImpl(), new ClienteDAOImpl());
    }

    public FidelizacionService(FidelizacionDAO fidelizacionDAO, ClienteDAO clienteDAO) {
        this.fidelizacionDAO = fidelizacionDAO;
        this.clienteDAO = clienteDAO;
    }

    /**
     * Acumula puntos para un cliente tras una venta exitosa según {@code fidelizacion.puntosPorSol} (S/ 1 = 1 punto por defecto).
     */
    public int acumularPuntosPorVenta(int idCliente, Integer idVenta, BigDecimal totalVenta) {
        if (idCliente <= 0 || totalVenta == null || totalVenta.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        try (Connection conn = ConexionBD.getConnection()) {
            return acumularPuntosPorVenta(conn, idCliente, idVenta, totalVenta);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error al acumular puntos para cliente {0}: {1}", new Object[]{idCliente, e.getMessage()});
            return 0;
        }
    }

    /**
     * Versión transaccional que acumula puntos usando la conexión JDBC activa y registra trazabilidad en
     * {@code fidelizacion_movimiento} e {@code historial_puntos}.
     */
    public int acumularPuntosPorVenta(Connection conn, int idCliente, Integer idVenta, BigDecimal totalVenta) throws SQLException {
        if (conn == null || idCliente <= 0 || totalVenta == null || totalVenta.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        int puntosPorSol = ConfiguracionFidelizacion.getPuntosPorSol();
        int puntosGanados = totalVenta.multiply(BigDecimal.valueOf(puntosPorSol)).intValue();
        if (puntosGanados <= 0) {
            return 0;
        }

        int puntosActuales = fidelizacionDAO.obtenerPuntosActuales(conn, idCliente);
        int nuevosPuntos = puntosActuales + puntosGanados;

        fidelizacionDAO.actualizarPuntosCliente(conn, idCliente, nuevosPuntos);

        LocalDateTime ahora = LocalDateTime.now();
        String detalleDesc = "Acumulación por compra S/ " + totalVenta.setScale(2, RoundingMode.HALF_UP);
        FidelizacionMovimiento mov = new FidelizacionMovimiento(
                0, idCliente, idVenta, "ACUMULACION", puntosGanados, detalleDesc, ahora
        );
        fidelizacionDAO.registrarMovimiento(conn, mov);

        HistorialPuntos hist = new HistorialPuntos(
                0L, idCliente, idVenta, puntosGanados, "ACUMULACION", detalleDesc, ahora
        );
        fidelizacionDAO.registrarHistorialPuntos(conn, hist);
        return puntosGanados;
    }

    /**
     * Canjea puntos por un descuento monetario en el POS (10 puntos = S/ 1.00, compatibilidad v2.0).
     */
    public BigDecimal canjearPuntos(int idCliente, int puntosACanjear, BigDecimal totalVenta) {
        if (idCliente <= 0 || puntosACanjear <= 0 || totalVenta == null) {
            return BigDecimal.ZERO;
        }

        try (Connection conn = ConexionBD.getConnection()) {
            int puntosDisponibles = fidelizacionDAO.obtenerPuntosActuales(conn, idCliente);
            if (puntosACanjear > puntosDisponibles) {
                puntosACanjear = puntosDisponibles;
            }
            if (puntosACanjear <= 0) {
                return BigDecimal.ZERO;
            }

            BigDecimal descuentoCalculado = BigDecimal.valueOf(puntosACanjear)
                    .divide(BigDecimal.valueOf(PUNTOS_POR_SOL_DESCUENTO), 2, RoundingMode.HALF_UP);

            if (descuentoCalculado.compareTo(totalVenta) > 0) {
                descuentoCalculado = totalVenta;
                puntosACanjear = totalVenta.multiply(BigDecimal.valueOf(PUNTOS_POR_SOL_DESCUENTO)).intValue();
            }

            int nuevosPuntos = puntosDisponibles - puntosACanjear;
            fidelizacionDAO.actualizarPuntosCliente(conn, idCliente, nuevosPuntos);

            LocalDateTime ahora = LocalDateTime.now();
            String descCanje = "Canje de " + puntosACanjear + " puntos por descuento de S/ " + descuentoCalculado;
            FidelizacionMovimiento mov = new FidelizacionMovimiento(
                    0, idCliente, null, "CANJE", -puntosACanjear, descCanje, ahora
            );
            fidelizacionDAO.registrarMovimiento(conn, mov);
            fidelizacionDAO.registrarHistorialPuntos(conn,
                    new HistorialPuntos(0L, idCliente, null, -puntosACanjear, "CANJE", descCanje, ahora));

            return descuentoCalculado;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error al canjear puntos para cliente {0}: {1}", new Object[]{idCliente, e.getMessage()});
            return BigDecimal.ZERO;
        }
    }

    /**
     * Calcula el beneficio de canje por bloques configurables (ej. 100 pts = S/ 5.00 en {@code application.properties})
     * respetando el tope máximo permitido sobre la venta.
     */
    public BeneficioCanje calcularCanjePorBloques(int puntosDisponibles, int bloquesSolicitados, BigDecimal totalVenta) {
        int puntosPorBloque = ConfiguracionFidelizacion.getPuntosPorCanje();
        BigDecimal descuentoPorBloque = ConfiguracionFidelizacion.getDescuentoPorCanjeSoles();
        int maxBloquesPorPuntos = Math.max(0, puntosDisponibles / puntosPorBloque);
        int maxBloquesPorMonto = 0;
        if (totalVenta != null && totalVenta.compareTo(BigDecimal.ZERO) > 0 && descuentoPorBloque.compareTo(BigDecimal.ZERO) > 0) {
            maxBloquesPorMonto = totalVenta.divide(descuentoPorBloque, 0, RoundingMode.FLOOR).intValue();
        }
        int maxBloques = Math.min(maxBloquesPorPuntos, maxBloquesPorMonto);
        int bloquesUsados = Math.max(0, Math.min(bloquesSolicitados <= 0 ? 1 : bloquesSolicitados, maxBloques));
        int puntosUsados = bloquesUsados * puntosPorBloque;
        BigDecimal descuentoAplicado = descuentoPorBloque.multiply(BigDecimal.valueOf(bloquesUsados))
                .setScale(2, RoundingMode.HALF_UP);
        return new BeneficioCanje(maxBloques, puntosPorBloque, descuentoPorBloque, puntosUsados, descuentoAplicado);
    }

    /**
     * Descuenta los puntos canjeados al confirmar la venta dentro de la transacción ACID (v5.0.0 §2.3).
     */
    public void canjearPuntosConfiguradosEnVenta(Connection conn,
                                                int idCliente,
                                                Integer idVenta,
                                                int puntosACanjear,
                                                BigDecimal descuentoSoles) throws SQLException, NegocioException {
        if (conn == null || idCliente <= 0 || puntosACanjear <= 0) {
            return;
        }
        int disponibles = fidelizacionDAO.obtenerPuntosActuales(conn, idCliente);
        if (disponibles < puntosACanjear) {
            throw new NegocioException("Puntos insuficientes para canje (disponibles: " + disponibles + ", requeridos: " + puntosACanjear + ").");
        }
        int nuevosPuntos = disponibles - puntosACanjear;
        fidelizacionDAO.actualizarPuntosCliente(conn, idCliente, nuevosPuntos);
        LocalDateTime ahora = LocalDateTime.now();
        String desc = "Canje POS de " + puntosACanjear + " puntos por S/ "
                + (descuentoSoles != null ? descuentoSoles.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        fidelizacionDAO.registrarMovimiento(conn,
                new FidelizacionMovimiento(0, idCliente, idVenta, "CANJE", -puntosACanjear, desc, ahora));
        fidelizacionDAO.registrarHistorialPuntos(conn,
                new HistorialPuntos(0L, idCliente, idVenta, -puntosACanjear, "CANJE", desc, ahora));
    }

    public boolean esCumpleanosEnMesActual(Cliente cliente) {
        return cliente != null && cliente.esMesDeCumpleanos();
    }

    public BigDecimal calcularDescuentoCumpleanosPos(BigDecimal totalBruto) {
        if (totalBruto == null || totalBruto.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal pct = ConfiguracionFidelizacion.getCumpleanosDescuentoPorcentaje();
        return totalBruto.multiply(pct)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    /**
     * Verifica si el cliente cumple años en el mes actual y genera automáticamente
     * un cupón del 15% OFF si aún no tiene uno activo para este mes.
     */
    public Optional<CuponCumpleanos> verificarYGenerarCuponCumpleanos(int idCliente) {
        if (idCliente <= 0) {
            return Optional.empty();
        }

        try (Connection conn = ConexionBD.getConnection()) {
            Optional<Cliente> optCliente = clienteDAO.buscarPorId(conn, idCliente);
            if (optCliente.isEmpty()) {
                return Optional.empty();
            }

            Cliente c = optCliente.get();
            LocalDate hoy = LocalDate.now();
            int mesActual = hoy.getMonthValue();

            // Verificar si cumple años en el mes actual
            if (c.getFechaNacimiento() == null || c.getFechaNacimiento().getMonthValue() != mesActual) {
                return Optional.empty();
            }

            // Buscar si ya tiene cupón generado para el mes
            Optional<CuponCumpleanos> existente = fidelizacionDAO.buscarCuponCumpleanosActivo(conn, idCliente, mesActual);
            if (existente.isPresent()) {
                return existente;
            }

            // Generar nuevo cupón 15% off
            String codigo = "BDAY-CLI" + idCliente + "-M" + mesActual + "-" + hoy.getYear();
            CuponCumpleanos nuevo = new CuponCumpleanos(
                    0, idCliente, codigo, new BigDecimal("15.00"), mesActual, false, LocalDateTime.now()
            );
            fidelizacionDAO.generarCuponCumpleanos(conn, nuevo);
            return Optional.of(nuevo);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error al verificar cupon de cumpleanos para cliente {0}: {1}", new Object[]{idCliente, e.getMessage()});
            return Optional.empty();
        }
    }

    public boolean marcarCuponUsado(String codigoCupon) {
        try (Connection conn = ConexionBD.getConnection()) {
            return fidelizacionDAO.marcarCuponUsado(conn, codigoCupon);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error al marcar cupon como usado: {0}", e.getMessage());
            return false;
        }
    }

    public Optional<CuponCumpleanos> buscarCuponPorCodigo(String codigo) {
        try (Connection conn = ConexionBD.getConnection()) {
            return fidelizacionDAO.buscarCuponPorCodigo(conn, codigo);
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    public List<FidelizacionMovimiento> listarHistorialCliente(int idCliente) {
        try (Connection conn = ConexionBD.getConnection()) {
            return fidelizacionDAO.listarPorCliente(conn, idCliente);
        } catch (SQLException e) {
            return List.of();
        }
    }

    public int obtenerPuntosCliente(int idCliente) {
        try (Connection conn = ConexionBD.getConnection()) {
            return fidelizacionDAO.obtenerPuntosActuales(conn, idCliente);
        } catch (SQLException e) {
            return 0;
        }
    }

    public List<CompraClienteResumen> listarUltimasComprasCliente(int idCliente, int limite) {
        if (idCliente <= 0) {
            return List.of();
        }
        try (Connection conn = ConexionBD.getConnection()) {
            return fidelizacionDAO.listarUltimasComprasCliente(conn, idCliente, limite);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error listando últimas compras para cliente {0}: {1}", new Object[]{idCliente, e.getMessage()});
            return List.of();
        }
    }

    public List<HistorialPuntos> listarHistorialPuntos(int idCliente) {
        if (idCliente <= 0) {
            return List.of();
        }
        try (Connection conn = ConexionBD.getConnection()) {
            return fidelizacionDAO.listarHistorialPuntos(conn, idCliente);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error listando historial de puntos para cliente {0}: {1}", new Object[]{idCliente, e.getMessage()});
            return List.of();
        }
    }
}
